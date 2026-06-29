package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexBinaryWorkbenchTest {

    @Test
    fun buildHexBinaryFindings_shouldSortBySeverityAndExposeNavigationOffsets() {
        val analysis = HexBinaryAnalysis(
            fileKind = HexFileKind.APK,
            fileSize = 0x1000,
            archive = HexArchiveSummary(
                entries = listOf(
                    HexArchiveEntry(
                        index = 0,
                        name = "../lib/arm64-v8a/libtarget.so",
                        generalPurposeBitFlag = 0,
                        compressionMethod = 0,
                        crc32 = 0,
                        compressedSize = 16,
                        uncompressedSize = 16,
                        localHeaderOffset = 0x40,
                        centralDirectoryOffset = 0x200,
                        nameRisks = setOf(HexArchiveEntryNameRisk.PATH_TRAVERSAL)
                    )
                ),
                nativeLibrarySummaries = listOf(
                    HexArchiveNativeLibrarySummary(
                        entryName = "lib/arm64-v8a/libtarget.so",
                        abi = "arm64-v8a",
                        fileName = "libtarget.so",
                        localHeaderOffset = 0x100,
                        dataOffset = 0x220,
                        compressionMethod = 0,
                        crc32 = 0,
                        compressedSize = 64,
                        uncompressedSize = 64,
                        analyzedBytes = 64,
                        truncated = false,
                        isElf = true,
                        obfuscationMarkers = listOf(
                            HexArchiveNativeObfuscationMarker(
                                type = HexObfuscationFindingType.PROTECTOR_PACKER_MARKER,
                                evidence = "libshell marker",
                                relativeOffset = 0x12
                            )
                        )
                    )
                )
            ),
            obfuscationFindings = listOf(
                HexObfuscationFinding(
                    type = HexObfuscationFindingType.OLLVM_MARKER,
                    confidence = HexFindingConfidence.HIGH,
                    evidence = "ollvm marker",
                    offset = 0x300
                )
            ),
            signals = listOf(
                HexAnalysisSignal(
                    type = HexAnalysisSignalType.APK_NATIVE_LIBRARIES,
                    offset = 0x220
                )
            )
        )

        val findings = buildHexBinaryFindings(analysis)

        assertThat(findings.map { it.kind })
            .containsAtLeast(
                HexBinaryFindingKind.APK_ENTRY_RISK,
                HexBinaryFindingKind.OBFUSCATION,
                HexBinaryFindingKind.APK_NATIVE_LIBRARY,
                HexBinaryFindingKind.SIGNAL
            )
        assertThat(findings.first().kind).isEqualTo(HexBinaryFindingKind.APK_ENTRY_RISK)
        assertThat(findings.first().severity).isEqualTo(HexBinaryFindingSeverity.HIGH)
        assertThat(findings.first().offset).isEqualTo(0x40)
        assertThat(findings.first { it.kind == HexBinaryFindingKind.APK_NATIVE_LIBRARY }.offset)
            .isEqualTo(0x232)
    }

    @Test
    fun formatHexReadOnlyAnalysisScript_shouldUseFindingOffsetAndAvoidPatchWrites() {
        val finding = HexBinaryFinding(
            kind = HexBinaryFindingKind.OBFUSCATION,
            severity = HexBinaryFindingSeverity.WARNING,
            offset = 0x80,
            primary = "OLLVM_MARKER",
            reference = "obfuscation:0"
        )

        val script = formatHexReadOnlyAnalysisScript(
            selectedOffset = 0x100,
            selectionRange = HexSelectionRange(startOffset = 0x120, endOffset = 0x121),
            finding = finding
        )

        assertThat(script).contains("e io.cache=true")
        assertThat(script).contains("s 0x00000080")
        assertThat(script).contains("px 256 @ 0x00000080")
        assertThat(script).contains("px 2 @ 0x00000120")
        assertThat(script).contains("pd 32 @ 0x00000080")
        assertThat(script).doesNotContain("wx ")
    }

    @Test
    fun runtimeTemplates_shouldPreferRuntimeSymbolWhenFindingHasOne() {
        val finding = HexBinaryFinding(
            kind = HexBinaryFindingKind.JNI_REGISTRATION,
            severity = HexBinaryFindingSeverity.WARNING,
            offset = 0x120,
            primary = "JNI_OnLoad",
            reference = "elf-jni:0"
        )

        val fridaTemplate = formatHexFridaHookTemplate(selectedOffset = 0x20, finding = finding)
        val lldbTemplate = formatHexLldbBreakpointTemplate(selectedOffset = 0x20, finding = finding)

        assertThat(fridaTemplate).contains("const fileOffset = ptr('0x00000120');")
        assertThat(fridaTemplate).contains("const symbolName = 'JNI_OnLoad';")
        assertThat(fridaTemplate).contains("Interceptor.attach(targetAddress")
        assertThat(lldbTemplate).contains("breakpoint set --address 0x00000120")
        assertThat(lldbTemplate).contains("disassemble --start-address 0x00000120 --count 32")
    }

    @Test
    fun buildHexReverseActions_shouldExposeScriptsAndReportsFromOnePipeline() {
        val analysis = jniReportAnalysis()

        val actions = buildHexReverseActions(
            selectedOffset = 0x40,
            selectionRange = HexSelectionRange(0x40, 0x43),
            analysis = analysis
        )

        assertThat(actions.map { it.kind })
            .containsExactly(
                HexReverseActionKind.READ_ONLY_ANALYSIS,
                HexReverseActionKind.DISASSEMBLY_PREVIEW,
                HexReverseActionKind.FRIDA_HOOK,
                HexReverseActionKind.LLDB_BREAKPOINT,
                HexReverseActionKind.JNI_MARKDOWN_REPORT,
                HexReverseActionKind.JNI_JSON_REPORT
            )
            .inOrder()
        assertThat(actions.first { it.kind == HexReverseActionKind.READ_ONLY_ANALYSIS }.content)
            .contains("px 4 @ 0x00000040")
        assertThat(actions.first { it.kind == HexReverseActionKind.JNI_MARKDOWN_REPORT }.content)
            .contains("Native Methods")
        assertThat(actions.first { it.kind == HexReverseActionKind.JNI_JSON_REPORT }.content)
            .contains("\"nativeMethods\"")
    }

    @Test
    fun jniAnalysisReport_shouldCollectNativeMethodsLibrariesHintsAndLoadLibraryStrings() {
        val report = buildHexJniAnalysisReport(jniReportAnalysis())

        assertThat(report.nativeMethods).hasSize(1)
        assertThat(report.nativeMethods.single().classDescriptor).isEqualTo("Lcom/example/NativeBridge;")
        assertThat(report.nativeLibraries.single().entryName).isEqualTo("lib/arm64-v8a/libnativebridge.so")
        assertThat(report.jniHints.single().symbolName).isEqualTo("JNI_OnLoad")
        assertThat(report.nativeApis.single().symbolName).isEqualTo("dlopen")
        assertThat(report.loadLibraryStrings.map { it.value }).containsAtLeast("nativebridge", "libnativebridge.so")
        assertThat(report.riskFindings.single().type).isEqualTo(HexElfRiskFindingType.MISSING_RELRO)
    }

    @Test
    fun jniAnalysisReportFormatting_shouldEscapeMarkdownAndJsonValues() {
        val report = buildHexJniAnalysisReport(jniReportAnalysis())

        val markdown = formatHexJniAnalysisMarkdownReport(report)
        val json = formatHexJniAnalysisJsonReport(report)

        assertThat(markdown).contains("Lcom/example/NativeBridge;")
        assertThat(markdown).contains("lib/arm64-v8a/libnativebridge.so")
        assertThat(json).contains("\"classDescriptor\": \"Lcom/example/NativeBridge;\"")
        assertThat(json).contains("\"entryName\": \"lib/arm64-v8a/libnativebridge.so\"")
        assertThat(json).contains("\"offset\": \"0x00000200\"")
    }

    private fun jniReportAnalysis(): HexBinaryAnalysis = HexBinaryAnalysis(
        fileKind = HexFileKind.APK,
        fileSize = 0x4000,
        fingerprint = HexFileFingerprint(
            sha256 = "sha256-demo",
            sha1 = "sha1-demo",
            md5 = "md5-demo",
            crc32 = 0x1234,
            byteCount = 0x4000
        ),
        dex = HexDexSummary(
            version = "035",
            checksum = 0,
            signatureHex = "",
            fileSizeFromHeader = 0x800,
            headerSize = 0x70,
            endianTag = 0x12345678,
            mapOffset = 0,
            stringIdsSize = 2,
            stringIdsOffset = 0x100,
            typeIdsSize = 0,
            typeIdsOffset = 0,
            protoIdsSize = 0,
            protoIdsOffset = 0,
            fieldIdsSize = 0,
            fieldIdsOffset = 0,
            methodIdsSize = 1,
            methodIdsOffset = 0x180,
            classDefsSize = 1,
            classDefsOffset = 0x200,
            dataSize = 0x400,
            dataOffset = 0x300,
            stringEntries = listOf(
                HexDexStringEntry(
                    index = 0,
                    stringIdOffset = 0x100,
                    dataOffset = 0x320,
                    value = "nativebridge"
                )
            ),
            classDataMethodEntries = listOf(
                HexDexClassDataMethodEntry(
                    index = 0,
                    classDefIndex = 0,
                    classDescriptor = "Lcom/example/NativeBridge;",
                    kind = HexDexClassDataMethodKind.DIRECT,
                    methodIndex = 7,
                    methodName = "nativeInit",
                    methodClassDescriptor = "Lcom/example/NativeBridge;",
                    protoSignature = "()V",
                    accessFlags = 0x0100,
                    classDataOffset = 0x240,
                    entryOffset = 0x260,
                    codeOffset = 0
                )
            )
        ),
        elf = HexElfSummary(
            is64Bit = true,
            endian = HexEndian.LITTLE,
            type = 3,
            machine = 183,
            machineName = "AArch64",
            entryPoint = 0,
            programHeaderCount = 0,
            sectionHeaderCount = 0,
            sectionNames = emptyList(),
            riskFindings = listOf(
                HexElfRiskFinding(
                    index = 0,
                    type = HexElfRiskFindingType.MISSING_RELRO,
                    severity = HexElfRiskSeverity.WARNING,
                    evidenceFileOffset = 0x180
                )
            ),
            nativeApiHints = listOf(
                HexElfNativeApiHint(
                    index = 0,
                    category = HexElfNativeApiCategory.DYNAMIC_LOADING,
                    symbolName = "dlopen",
                    evidenceFileOffset = 0x200
                )
            ),
            jniRegistrationHints = listOf(
                HexElfJniRegistrationHint(
                    index = 0,
                    type = HexElfJniRegistrationHintType.JNI_ONLOAD_ENTRY,
                    evidenceFileOffset = 0x120,
                    symbolName = "JNI_OnLoad"
                )
            )
        ),
        archive = HexArchiveSummary(
            entries = emptyList(),
            nativeLibrarySummaries = listOf(
                HexArchiveNativeLibrarySummary(
                    entryName = "lib/arm64-v8a/libnativebridge.so",
                    abi = "arm64-v8a",
                    fileName = "libnativebridge.so",
                    localHeaderOffset = 0x1000,
                    dataOffset = 0x2000,
                    compressionMethod = 0,
                    crc32 = 0x4567,
                    compressedSize = 0x500,
                    uncompressedSize = 0x500,
                    analyzedBytes = 0x500,
                    truncated = false,
                    isElf = true,
                    loadMode = HexArchiveNativeLoadMode.DIRECT_MMAP_READY,
                    obfuscationMarkers = emptyList()
                )
            )
        ),
        strings = listOf(
            HexStringEntry(
                offset = 0x3000,
                value = "libnativebridge.so"
            )
        )
    )
}

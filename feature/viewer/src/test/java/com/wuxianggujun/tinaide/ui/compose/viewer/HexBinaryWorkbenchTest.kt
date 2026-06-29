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
}

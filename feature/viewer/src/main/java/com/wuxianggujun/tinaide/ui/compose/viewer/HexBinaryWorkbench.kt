package com.wuxianggujun.tinaide.ui.compose.viewer

import java.util.Locale

internal data class HexBinaryFinding(
    val kind: HexBinaryFindingKind,
    val severity: HexBinaryFindingSeverity,
    val offset: Long?,
    val primary: String,
    val secondary: String? = null,
    val reference: String
)

internal enum class HexBinaryFindingKind {
    ELF_RISK,
    JNI_REGISTRATION,
    NATIVE_API,
    DEX_NATIVE_METHOD,
    OBFUSCATION,
    APK_NATIVE_LIBRARY,
    APK_ENTRY_RISK,
    SIGNAL
}

internal enum class HexBinaryFindingSeverity {
    HIGH,
    WARNING,
    INFO
}

internal data class HexReverseAction(
    val kind: HexReverseActionKind,
    val targetOffset: Long?,
    val findingReference: String?,
    val content: String
)

internal enum class HexReverseActionKind {
    READ_ONLY_ANALYSIS,
    DISASSEMBLY_PREVIEW,
    FRIDA_HOOK,
    LLDB_BREAKPOINT,
    JNI_MARKDOWN_REPORT,
    JNI_JSON_REPORT
}

internal data class HexJniAnalysisReport(
    val fileKind: HexFileKind,
    val fileSize: Long,
    val fingerprintSha256: String?,
    val nativeMethods: List<HexJniNativeMethodReportEntry>,
    val nativeLibraries: List<HexJniNativeLibraryReportEntry>,
    val jniHints: List<HexJniHintReportEntry>,
    val nativeApis: List<HexJniNativeApiReportEntry>,
    val loadLibraryStrings: List<HexJniLoadLibraryString>,
    val riskFindings: List<HexJniRiskReportEntry>,
    val workbenchFindings: List<HexBinaryFinding>
)

internal data class HexJniNativeMethodReportEntry(
    val classDescriptor: String,
    val methodName: String,
    val protoSignature: String,
    val methodIndex: Long,
    val entryOffset: Long
)

internal data class HexJniNativeLibraryReportEntry(
    val entryName: String,
    val abi: String,
    val fileName: String,
    val dataOffset: Long?,
    val loadMode: HexArchiveNativeLoadMode,
    val isElf: Boolean,
    val obfuscationMarkerCount: Int
)

internal data class HexJniHintReportEntry(
    val type: HexElfJniRegistrationHintType,
    val offset: Long?,
    val symbolName: String?,
    val stringValue: String?
)

internal data class HexJniNativeApiReportEntry(
    val category: HexElfNativeApiCategory,
    val symbolName: String,
    val offset: Long?
)

internal data class HexJniLoadLibraryString(
    val value: String,
    val offset: Long?
)

internal data class HexJniRiskReportEntry(
    val severity: HexElfRiskSeverity,
    val type: HexElfRiskFindingType,
    val offset: Long?,
    val detailValue: String?
)

internal fun buildHexBinaryFindings(
    analysis: HexBinaryAnalysis?,
    maxCount: Int = HEX_BINARY_FINDING_DEFAULT_LIMIT
): List<HexBinaryFinding> {
    if (analysis == null || maxCount <= 0) return emptyList()
    val findings = buildList {
        analysis.elf?.riskFindings.orEmpty().forEach { finding ->
            add(
                HexBinaryFinding(
                    kind = HexBinaryFindingKind.ELF_RISK,
                    severity = finding.severity.toWorkbenchSeverity(),
                    offset = finding.evidenceFileOffset,
                    primary = finding.type.name,
                    secondary = finding.detailValue,
                    reference = "elf-risk:${finding.index}"
                )
            )
        }
        analysis.elf?.jniRegistrationHints.orEmpty().forEach { hint ->
            add(
                HexBinaryFinding(
                    kind = HexBinaryFindingKind.JNI_REGISTRATION,
                    severity = hint.type.toWorkbenchSeverity(),
                    offset = hint.evidenceFileOffset,
                    primary = hint.symbolName ?: hint.stringValue ?: hint.type.name,
                    secondary = hint.type.name,
                    reference = "elf-jni:${hint.index}"
                )
            )
        }
        analysis.elf?.nativeApiHints.orEmpty().forEach { hint ->
            add(
                HexBinaryFinding(
                    kind = HexBinaryFindingKind.NATIVE_API,
                    severity = hint.category.toWorkbenchSeverity(),
                    offset = hint.evidenceFileOffset,
                    primary = hint.symbolName,
                    secondary = hint.category.name,
                    reference = "elf-native-api:${hint.index}"
                )
            )
        }
        analysis.dex?.classDataMethodEntries.orEmpty()
            .filter { method -> method.executionKind == HexDexClassDataMethodExecutionKind.NATIVE }
            .forEach { method ->
                add(
                    HexBinaryFinding(
                        kind = HexBinaryFindingKind.DEX_NATIVE_METHOD,
                        severity = HexBinaryFindingSeverity.WARNING,
                        offset = method.entryOffset,
                        primary = "${method.classDescriptor}->${method.methodName}",
                        secondary = method.protoSignature,
                        reference = "dex-native-method:${method.index}"
                    )
                )
            }
        analysis.obfuscationFindings.forEachIndexed { index, finding ->
            add(
                HexBinaryFinding(
                    kind = HexBinaryFindingKind.OBFUSCATION,
                    severity = finding.confidence.toWorkbenchSeverity(),
                    offset = finding.offset,
                    primary = finding.type.name,
                    secondary = finding.evidence,
                    reference = "obfuscation:$index"
                )
            )
        }
        analysis.archive?.nativeLibrarySummaries.orEmpty().forEachIndexed { libraryIndex, library ->
            library.obfuscationMarkers.forEachIndexed { markerIndex, marker ->
                add(
                    HexBinaryFinding(
                        kind = HexBinaryFindingKind.APK_NATIVE_LIBRARY,
                        severity = marker.type.toWorkbenchSeverity(),
                        offset = library.absoluteMarkerOffset(marker),
                        primary = library.entryName,
                        secondary = marker.evidence,
                        reference = "apk-native:$libraryIndex:$markerIndex"
                    )
                )
            }
        }
        analysis.archive?.entries.orEmpty()
            .filter { entry -> entry.nameRisks.isNotEmpty() }
            .forEach { entry ->
                add(
                    HexBinaryFinding(
                        kind = HexBinaryFindingKind.APK_ENTRY_RISK,
                        severity = entry.nameRisks.toWorkbenchSeverity(),
                        offset = entry.localHeaderOffset,
                        primary = entry.name,
                        secondary = entry.nameRisks.joinToString(separator = ", ") { risk -> risk.name },
                        reference = "apk-entry-risk:${entry.index}"
                    )
                )
            }
        analysis.signals.forEachIndexed { index, signal ->
            add(
                HexBinaryFinding(
                    kind = HexBinaryFindingKind.SIGNAL,
                    severity = HexBinaryFindingSeverity.INFO,
                    offset = signal.offset,
                    primary = signal.type.name,
                    reference = "signal:$index"
                )
            )
        }
    }
    return findings.sortedWith(
        compareBy<HexBinaryFinding> { it.severity.sortRank }
            .thenBy { it.offset ?: Long.MAX_VALUE }
            .thenBy { it.kind.ordinal }
            .thenBy { it.reference }
    ).take(maxCount)
}

internal fun buildHexReverseActions(
    selectedOffset: Long,
    selectionRange: HexSelectionRange?,
    analysis: HexBinaryAnalysis?,
    finding: HexBinaryFinding? = null
): List<HexReverseAction> {
    val activeFinding = finding ?: buildHexBinaryFindings(analysis, maxCount = 1).firstOrNull()
    val targetOffset = activeFinding?.offset ?: selectedOffset
    val report = buildHexJniAnalysisReport(analysis)
    return listOf(
        HexReverseAction(
            kind = HexReverseActionKind.READ_ONLY_ANALYSIS,
            targetOffset = targetOffset,
            findingReference = activeFinding?.reference,
            content = formatHexReadOnlyAnalysisScript(
                selectedOffset = selectedOffset,
                selectionRange = selectionRange,
                finding = activeFinding
            )
        ),
        HexReverseAction(
            kind = HexReverseActionKind.DISASSEMBLY_PREVIEW,
            targetOffset = targetOffset,
            findingReference = activeFinding?.reference,
            content = formatHexDisassemblyPreviewScript(targetOffset)
        ),
        HexReverseAction(
            kind = HexReverseActionKind.FRIDA_HOOK,
            targetOffset = targetOffset,
            findingReference = activeFinding?.reference,
            content = formatHexFridaHookTemplate(
                selectedOffset = selectedOffset,
                finding = activeFinding
            )
        ),
        HexReverseAction(
            kind = HexReverseActionKind.LLDB_BREAKPOINT,
            targetOffset = targetOffset,
            findingReference = activeFinding?.reference,
            content = formatHexLldbBreakpointTemplate(
                selectedOffset = selectedOffset,
                finding = activeFinding
            )
        ),
        HexReverseAction(
            kind = HexReverseActionKind.JNI_MARKDOWN_REPORT,
            targetOffset = null,
            findingReference = null,
            content = formatHexJniAnalysisMarkdownReport(report)
        ),
        HexReverseAction(
            kind = HexReverseActionKind.JNI_JSON_REPORT,
            targetOffset = null,
            findingReference = null,
            content = formatHexJniAnalysisJsonReport(report)
        )
    )
}

internal fun buildHexJniAnalysisReport(analysis: HexBinaryAnalysis?): HexJniAnalysisReport {
    if (analysis == null) {
        return HexJniAnalysisReport(
            fileKind = HexFileKind.UNKNOWN,
            fileSize = 0L,
            fingerprintSha256 = null,
            nativeMethods = emptyList(),
            nativeLibraries = emptyList(),
            jniHints = emptyList(),
            nativeApis = emptyList(),
            loadLibraryStrings = emptyList(),
            riskFindings = emptyList(),
            workbenchFindings = emptyList()
        )
    }
    return HexJniAnalysisReport(
        fileKind = analysis.fileKind,
        fileSize = analysis.fileSize,
        fingerprintSha256 = analysis.fingerprint?.sha256,
        nativeMethods = analysis.dex?.classDataMethodEntries.orEmpty()
            .filter { method -> method.executionKind == HexDexClassDataMethodExecutionKind.NATIVE }
            .map { method ->
                HexJniNativeMethodReportEntry(
                    classDescriptor = method.classDescriptor,
                    methodName = method.methodName,
                    protoSignature = method.protoSignature,
                    methodIndex = method.methodIndex,
                    entryOffset = method.entryOffset
                )
            },
        nativeLibraries = analysis.archive?.nativeLibrarySummaries.orEmpty().map { library ->
            HexJniNativeLibraryReportEntry(
                entryName = library.entryName,
                abi = library.abi,
                fileName = library.fileName,
                dataOffset = library.dataOffset,
                loadMode = library.loadMode,
                isElf = library.isElf,
                obfuscationMarkerCount = library.obfuscationMarkers.size
            )
        },
        jniHints = analysis.elf?.jniRegistrationHints.orEmpty().map { hint ->
            HexJniHintReportEntry(
                type = hint.type,
                offset = hint.evidenceFileOffset,
                symbolName = hint.symbolName,
                stringValue = hint.stringValue
            )
        },
        nativeApis = analysis.elf?.nativeApiHints.orEmpty().map { hint ->
            HexJniNativeApiReportEntry(
                category = hint.category,
                symbolName = hint.symbolName,
                offset = hint.evidenceFileOffset
            )
        },
        loadLibraryStrings = buildHexJniLoadLibraryStrings(analysis),
        riskFindings = analysis.elf?.riskFindings.orEmpty().map { finding ->
            HexJniRiskReportEntry(
                severity = finding.severity,
                type = finding.type,
                offset = finding.evidenceFileOffset,
                detailValue = finding.detailValue
            )
        },
        workbenchFindings = buildHexBinaryFindings(analysis)
    )
}

internal fun formatHexJniAnalysisMarkdownReport(report: HexJniAnalysisReport): String = buildString {
    appendLine("# JNI Analysis Report")
    appendLine()
    appendLine("- File kind: `${report.fileKind.name}`")
    appendLine("- File size: `${report.fileSize}` bytes")
    report.fingerprintSha256?.let { sha256 -> appendLine("- SHA-256: `$sha256`") }
    appendLine("- Native methods: `${report.nativeMethods.size}`")
    appendLine("- Native libraries: `${report.nativeLibraries.size}`")
    appendLine("- JNI hints: `${report.jniHints.size}`")
    appendLine("- Native API hints: `${report.nativeApis.size}`")
    appendLine()
    appendMarkdownTable(
        title = "Native Methods",
        headers = listOf("Class", "Method", "Proto", "Method index", "Entry offset"),
        rows = report.nativeMethods.map { method ->
            listOf(
                method.classDescriptor,
                method.methodName,
                method.protoSignature,
                method.methodIndex.toString(),
                method.entryOffset.toRadareHexAddress()
            )
        }
    )
    appendMarkdownTable(
        title = "Native Libraries",
        headers = listOf("Entry", "ABI", "File", "Data offset", "Load mode", "ELF", "Markers"),
        rows = report.nativeLibraries.map { library ->
            listOf(
                library.entryName,
                library.abi,
                library.fileName,
                library.dataOffset?.toRadareHexAddress() ?: "-",
                library.loadMode.name,
                library.isElf.toString(),
                library.obfuscationMarkerCount.toString()
            )
        }
    )
    appendMarkdownTable(
        title = "JNI Registration Hints",
        headers = listOf("Type", "Symbol", "String", "Offset"),
        rows = report.jniHints.map { hint ->
            listOf(
                hint.type.name,
                hint.symbolName ?: "-",
                hint.stringValue ?: "-",
                hint.offset?.toRadareHexAddress() ?: "-"
            )
        }
    )
    appendMarkdownTable(
        title = "Native API Hints",
        headers = listOf("Category", "Symbol", "Offset"),
        rows = report.nativeApis.map { api ->
            listOf(
                api.category.name,
                api.symbolName,
                api.offset?.toRadareHexAddress() ?: "-"
            )
        }
    )
    appendMarkdownTable(
        title = "Load Library Strings",
        headers = listOf("Value", "Offset"),
        rows = report.loadLibraryStrings.map { candidate ->
            listOf(candidate.value, candidate.offset?.toRadareHexAddress() ?: "-")
        }
    )
    appendMarkdownTable(
        title = "ELF Risk Findings",
        headers = listOf("Severity", "Type", "Offset", "Detail"),
        rows = report.riskFindings.map { risk ->
            listOf(
                risk.severity.name,
                risk.type.name,
                risk.offset?.toRadareHexAddress() ?: "-",
                risk.detailValue ?: "-"
            )
        }
    )
    appendMarkdownTable(
        title = "Workbench Findings",
        headers = listOf("Severity", "Kind", "Primary", "Offset", "Reference"),
        rows = report.workbenchFindings.map { finding ->
            listOf(
                finding.severity.name,
                finding.kind.name,
                finding.primary,
                finding.offset?.toRadareHexAddress() ?: "-",
                finding.reference
            )
        }
    )
}

internal fun formatHexJniAnalysisJsonReport(report: HexJniAnalysisReport): String = buildString {
    appendLine("{")
    appendLine("  \"fileKind\": ${report.fileKind.name.toJsonString()},")
    appendLine("  \"fileSize\": ${report.fileSize},")
    appendLine("  \"sha256\": ${report.fingerprintSha256.toJsonNullableString()},")
    appendLine("  \"nativeMethods\": [")
    appendJsonObjects(report.nativeMethods) { method ->
        listOf(
            "classDescriptor" to method.classDescriptor.toJsonString(),
            "methodName" to method.methodName.toJsonString(),
            "protoSignature" to method.protoSignature.toJsonString(),
            "methodIndex" to method.methodIndex.toString(),
            "entryOffset" to method.entryOffset.toJsonHexString()
        )
    }
    appendLine("  ],")
    appendLine("  \"nativeLibraries\": [")
    appendJsonObjects(report.nativeLibraries) { library ->
        listOf(
            "entryName" to library.entryName.toJsonString(),
            "abi" to library.abi.toJsonString(),
            "fileName" to library.fileName.toJsonString(),
            "dataOffset" to library.dataOffset.toJsonNullableHexString(),
            "loadMode" to library.loadMode.name.toJsonString(),
            "isElf" to library.isElf.toString(),
            "obfuscationMarkerCount" to library.obfuscationMarkerCount.toString()
        )
    }
    appendLine("  ],")
    appendLine("  \"jniHints\": [")
    appendJsonObjects(report.jniHints) { hint ->
        listOf(
            "type" to hint.type.name.toJsonString(),
            "offset" to hint.offset.toJsonNullableHexString(),
            "symbolName" to hint.symbolName.toJsonNullableString(),
            "stringValue" to hint.stringValue.toJsonNullableString()
        )
    }
    appendLine("  ],")
    appendLine("  \"nativeApis\": [")
    appendJsonObjects(report.nativeApis) { api ->
        listOf(
            "category" to api.category.name.toJsonString(),
            "symbolName" to api.symbolName.toJsonString(),
            "offset" to api.offset.toJsonNullableHexString()
        )
    }
    appendLine("  ],")
    appendLine("  \"loadLibraryStrings\": [")
    appendJsonObjects(report.loadLibraryStrings) { candidate ->
        listOf(
            "value" to candidate.value.toJsonString(),
            "offset" to candidate.offset.toJsonNullableHexString()
        )
    }
    appendLine("  ],")
    appendLine("  \"riskFindings\": [")
    appendJsonObjects(report.riskFindings) { risk ->
        listOf(
            "severity" to risk.severity.name.toJsonString(),
            "type" to risk.type.name.toJsonString(),
            "offset" to risk.offset.toJsonNullableHexString(),
            "detailValue" to risk.detailValue.toJsonNullableString()
        )
    }
    appendLine("  ],")
    appendLine("  \"workbenchFindings\": [")
    appendJsonObjects(report.workbenchFindings) { finding ->
        listOf(
            "severity" to finding.severity.name.toJsonString(),
            "kind" to finding.kind.name.toJsonString(),
            "offset" to finding.offset.toJsonNullableHexString(),
            "primary" to finding.primary.toJsonString(),
            "secondary" to finding.secondary.toJsonNullableString(),
            "reference" to finding.reference.toJsonString()
        )
    }
    appendLine("  ]")
    appendLine("}")
}

internal fun formatHexReadOnlyAnalysisScript(
    selectedOffset: Long,
    selectionRange: HexSelectionRange?,
    finding: HexBinaryFinding? = null,
    instructionCount: Int = HEX_BINARY_WORKBENCH_DISASSEMBLY_COUNT
): String {
    val targetOffset = finding?.offset ?: selectedOffset
    val safeInstructionCount = instructionCount.coerceAtLeast(1)
    return buildList {
        add("# read-only Rizin/radare2 workbench")
        add("e io.cache=true")
        add("aaa")
        add(formatHexNavigationScript(targetOffset))
        selectionRange?.let { add(formatHexSelectionDumpScript(it)) }
        add(formatHexDisassemblyPreviewScript(targetOffset, safeInstructionCount))
        add("iI")
        add("iS")
        add("ii")
        add("is")
        add("iz")
    }.joinToString(separator = "\n")
}

internal fun formatHexDisassemblyPreviewScript(
    offset: Long,
    instructionCount: Int = HEX_BINARY_WORKBENCH_DISASSEMBLY_COUNT
): String {
    val safeInstructionCount = instructionCount.coerceAtLeast(1)
    return listOf(
        "aaa",
        "s ${offset.toRadareHexAddress()}",
        "pd $safeInstructionCount @ ${offset.toRadareHexAddress()}"
    ).joinToString(separator = "\n")
}

internal fun formatHexFridaHookTemplate(
    selectedOffset: Long,
    finding: HexBinaryFinding? = null
): String {
    val targetOffset = finding?.offset ?: selectedOffset
    val symbolName = finding?.preferredSymbolForRuntimeTemplate().orEmpty()
    return """
        'use strict';

        const moduleName = 'libtarget.so';
        const fileOffset = ptr('${targetOffset.toRadareHexAddress()}');
        const symbolName = '${symbolName.toJsSingleQuotedLiteral()}';
        const moduleBase = Module.findBaseAddress(moduleName);
        if (moduleBase === null) {
          throw new Error('Module not found: ' + moduleName);
        }

        const symbolAddress = symbolName.length > 0 ? Module.findExportByName(moduleName, symbolName) : null;
        const targetAddress = symbolAddress !== null ? symbolAddress : moduleBase.add(fileOffset);

        Interceptor.attach(targetAddress, {
          onEnter(args) {
            console.log('[hex] enter ' + targetAddress);
            console.log('[hex] arg0=' + args[0] + ' arg1=' + args[1]);
          },
          onLeave(retval) {
            console.log('[hex] leave ' + retval);
          }
        });
    """.trimIndent()
}

internal fun formatHexLldbBreakpointTemplate(
    selectedOffset: Long,
    finding: HexBinaryFinding? = null,
    instructionCount: Int = HEX_BINARY_WORKBENCH_DISASSEMBLY_COUNT
): String {
    val targetOffset = finding?.offset ?: selectedOffset
    val safeInstructionCount = instructionCount.coerceAtLeast(1)
    val address = targetOffset.toRadareHexAddress()
    return listOf(
        "target modules list",
        "image lookup --address $address",
        "breakpoint set --address $address",
        "memory read --format x --count 32 $address",
        "disassemble --start-address $address --count $safeInstructionCount"
    ).joinToString(separator = "\n")
}

private val HexBinaryFindingSeverity.sortRank: Int
    get() = when (this) {
        HexBinaryFindingSeverity.HIGH -> 0
        HexBinaryFindingSeverity.WARNING -> 1
        HexBinaryFindingSeverity.INFO -> 2
    }

private fun HexElfRiskSeverity.toWorkbenchSeverity(): HexBinaryFindingSeverity = when (this) {
    HexElfRiskSeverity.HIGH -> HexBinaryFindingSeverity.HIGH
    HexElfRiskSeverity.WARNING -> HexBinaryFindingSeverity.WARNING
    HexElfRiskSeverity.INFO -> HexBinaryFindingSeverity.INFO
}

private fun HexElfJniRegistrationHintType.toWorkbenchSeverity(): HexBinaryFindingSeverity = when (this) {
    HexElfJniRegistrationHintType.REGISTER_NATIVES_SYMBOL,
    HexElfJniRegistrationHintType.REGISTER_NATIVES_STRING,
    HexElfJniRegistrationHintType.JNI_ONLOAD_ENTRY,
    HexElfJniRegistrationHintType.JNI_ONUNLOAD_ENTRY,
    HexElfJniRegistrationHintType.STATIC_JNI_EXPORT -> HexBinaryFindingSeverity.WARNING
    HexElfJniRegistrationHintType.JAVA_CLASS_DESCRIPTOR,
    HexElfJniRegistrationHintType.JNI_METHOD_SIGNATURE -> HexBinaryFindingSeverity.INFO
}

private fun HexElfNativeApiCategory.toWorkbenchSeverity(): HexBinaryFindingSeverity = when (this) {
    HexElfNativeApiCategory.DYNAMIC_LOADING,
    HexElfNativeApiCategory.MEMORY_PROTECTION,
    HexElfNativeApiCategory.PROCESS_CONTROL,
    HexElfNativeApiCategory.NETWORK,
    HexElfNativeApiCategory.CRYPTO -> HexBinaryFindingSeverity.WARNING
    HexElfNativeApiCategory.FILE_IO,
    HexElfNativeApiCategory.THREADING,
    HexElfNativeApiCategory.LOGGING -> HexBinaryFindingSeverity.INFO
}

private fun HexFindingConfidence.toWorkbenchSeverity(): HexBinaryFindingSeverity = when (this) {
    HexFindingConfidence.HIGH -> HexBinaryFindingSeverity.HIGH
    HexFindingConfidence.MEDIUM -> HexBinaryFindingSeverity.WARNING
    HexFindingConfidence.LOW -> HexBinaryFindingSeverity.INFO
}

private fun HexObfuscationFindingType.toWorkbenchSeverity(): HexBinaryFindingSeverity = when (this) {
    HexObfuscationFindingType.OLLVM_MARKER,
    HexObfuscationFindingType.CONTROL_FLOW_FLATTENING_MARKER,
    HexObfuscationFindingType.BOGUS_CONTROL_FLOW_MARKER,
    HexObfuscationFindingType.INSTRUCTION_SUBSTITUTION_MARKER,
    HexObfuscationFindingType.PROTECTOR_PACKER_MARKER -> HexBinaryFindingSeverity.WARNING
    HexObfuscationFindingType.ANTI_DEBUG_HEURISTIC,
    HexObfuscationFindingType.ANTI_INSTRUMENTATION_HEURISTIC,
    HexObfuscationFindingType.STRING_OBFUSCATION_HEURISTIC,
    HexObfuscationFindingType.STRIPPED_SYMBOLS_HEURISTIC -> HexBinaryFindingSeverity.INFO
}

private fun Set<HexArchiveEntryNameRisk>.toWorkbenchSeverity(): HexBinaryFindingSeverity = when {
    any { risk ->
        risk == HexArchiveEntryNameRisk.PATH_TRAVERSAL ||
            risk == HexArchiveEntryNameRisk.ABSOLUTE_PATH ||
            risk == HexArchiveEntryNameRisk.WINDOWS_DRIVE_PATH
    } -> HexBinaryFindingSeverity.HIGH
    else -> HexBinaryFindingSeverity.WARNING
}

private fun HexArchiveNativeLibrarySummary.absoluteMarkerOffset(
    marker: HexArchiveNativeObfuscationMarker
): Long? {
    val baseOffset = dataOffset ?: return null
    val markerOffset = marker.relativeOffset ?: return baseOffset
    return baseOffset + markerOffset
}

private fun buildHexJniLoadLibraryStrings(analysis: HexBinaryAnalysis): List<HexJniLoadLibraryString> {
    val nativeLibraryBaseNames = analysis.archive?.nativeLibrarySummaries.orEmpty()
        .mapNotNull { library -> library.fileName.toLoadLibraryBaseName() }
        .toSet()
    val dexCandidates = analysis.dex?.stringEntries.orEmpty()
        .filter { entry -> entry.value.isLikelyNativeLibraryString(nativeLibraryBaseNames) }
        .map { entry ->
            HexJniLoadLibraryString(
                value = entry.value,
                offset = entry.dataOffset
            )
        }
    val binaryCandidates = analysis.strings
        .filter { entry -> entry.value.isLikelyNativeLibraryString(nativeLibraryBaseNames) }
        .map { entry ->
            HexJniLoadLibraryString(
                value = entry.value,
                offset = entry.offset
            )
        }
    return (dexCandidates + binaryCandidates)
        .distinctBy { candidate -> candidate.value to candidate.offset }
        .sortedWith(compareBy<HexJniLoadLibraryString> { it.offset ?: Long.MAX_VALUE }.thenBy { it.value })
        .take(HEX_JNI_REPORT_LOAD_LIBRARY_LIMIT)
}

private fun String.isLikelyNativeLibraryString(nativeLibraryBaseNames: Set<String>): Boolean {
    val value = trim()
    if (value.length !in 2..96) return false
    if (value.any { char -> char == '\u0000' || char == '\n' || char == '\r' || char == ';' }) return false
    val lower = value.lowercase(Locale.US)
    return lower in nativeLibraryBaseNames ||
        lower.endsWith(".so") ||
        (lower.startsWith("lib") && value.none { char -> char == '/' || char == '\\' })
}

private fun String.toLoadLibraryBaseName(): String? {
    val lower = lowercase(Locale.US)
    if (!lower.startsWith("lib") || !lower.endsWith(".so") || length <= 5) return null
    return substring(startIndex = 3, endIndex = length - 3).lowercase(Locale.US)
}

private fun HexBinaryFinding.preferredSymbolForRuntimeTemplate(): String? = when (kind) {
    HexBinaryFindingKind.JNI_REGISTRATION,
    HexBinaryFindingKind.NATIVE_API -> primary.takeIf { it.isLikelyRuntimeSymbol() }
    else -> null
}

private fun String.isLikelyRuntimeSymbol(): Boolean =
    isNotBlank() && none { char -> char.isWhitespace() || char == '/' || char == ';' || char == '-' || char == '>' }

private fun String.toJsSingleQuotedLiteral(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun StringBuilder.appendMarkdownTable(
    title: String,
    headers: List<String>,
    rows: List<List<String>>
) {
    appendLine("## $title")
    appendLine()
    if (rows.isEmpty()) {
        appendLine("_None_")
        appendLine()
        return
    }
    appendLine(headers.joinToString(prefix = "| ", separator = " | ", postfix = " |") { header ->
        header.escapeMarkdownTableCell()
    })
    appendLine(headers.joinToString(prefix = "| ", separator = " | ", postfix = " |") { "---" })
    rows.forEach { row ->
        appendLine(row.joinToString(prefix = "| ", separator = " | ", postfix = " |") { cell ->
            cell.escapeMarkdownTableCell()
        })
    }
    appendLine()
}

private fun String.escapeMarkdownTableCell(): String = replace("\\", "\\\\")
    .replace("|", "\\|")
    .replace("\n", " ")
    .replace("\r", " ")

private fun <T> StringBuilder.appendJsonObjects(
    items: List<T>,
    fields: (T) -> List<Pair<String, String>>
) {
    items.forEachIndexed { index, item ->
        appendLine("    {")
        val fieldValues = fields(item)
        fieldValues.forEachIndexed { fieldIndex, (name, value) ->
            val comma = if (fieldIndex == fieldValues.lastIndex) "" else ","
            appendLine("      ${name.toJsonString()}: $value$comma")
        }
        val comma = if (index == items.lastIndex) "" else ","
        appendLine("    }$comma")
    }
}

private fun String.toJsonString(): String = buildString {
    append('"')
    this@toJsonString.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u%04X".format(Locale.US, char.code))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}

private fun String?.toJsonNullableString(): String = this?.toJsonString() ?: "null"

private fun Long.toJsonHexString(): String = toRadareHexAddress().toJsonString()

private fun Long?.toJsonNullableHexString(): String = this?.toJsonHexString() ?: "null"

private fun Long.toRadareHexAddress(): String = "0x%08X".format(Locale.US, this)

private const val HEX_BINARY_FINDING_DEFAULT_LIMIT = 24
private const val HEX_BINARY_WORKBENCH_DISASSEMBLY_COUNT = 32
private const val HEX_JNI_REPORT_LOAD_LIBRARY_LIMIT = 48

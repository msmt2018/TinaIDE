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

private fun HexBinaryFinding.preferredSymbolForRuntimeTemplate(): String? = when (kind) {
    HexBinaryFindingKind.JNI_REGISTRATION,
    HexBinaryFindingKind.NATIVE_API -> primary.takeIf { it.isLikelyRuntimeSymbol() }
    else -> null
}

private fun String.isLikelyRuntimeSymbol(): Boolean =
    isNotBlank() && none { char -> char.isWhitespace() || char == '/' || char == ';' || char == '-' || char == '>' }

private fun String.toJsSingleQuotedLiteral(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun Long.toRadareHexAddress(): String = "0x%08X".format(Locale.US, this)

private const val HEX_BINARY_FINDING_DEFAULT_LIMIT = 24
private const val HEX_BINARY_WORKBENCH_DISASSEMBLY_COUNT = 32

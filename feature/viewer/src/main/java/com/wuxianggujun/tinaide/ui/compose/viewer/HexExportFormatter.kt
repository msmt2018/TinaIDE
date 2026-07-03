package com.wuxianggujun.tinaide.ui.compose.viewer

import java.util.Base64

internal const val MAX_HEX_EXPORT_BYTES = 1024 * 1024

internal fun formatHexExport(
    range: HexSelectionRange,
    bytes: ByteArray,
    format: HexExportFormat
): String = when (format) {
    HexExportFormat.HEX_DUMP -> formatHexDump(range, bytes)
    HexExportFormat.C_ARRAY -> formatCArray(bytes)
    HexExportFormat.KOTLIN_BYTE_ARRAY -> formatKotlinByteArray(bytes)
    HexExportFormat.BASE64 -> Base64.getEncoder().encodeToString(bytes)
    HexExportFormat.ASCII -> bytes.joinToString(separator = "") { it.toPrintableAscii() }
}

internal fun applyHexPatchesToRange(
    range: HexSelectionRange,
    bytes: ByteArray,
    patches: List<HexPatch>
): ByteArray {
    if (bytes.isEmpty() || patches.isEmpty()) return bytes
    val patchedBytes = bytes.copyOf()
    patches.forEach { patch ->
        if (patch.offset in range.firstOffset..range.lastOffset) {
            val localOffset = (patch.offset - range.firstOffset).toInt()
            if (localOffset in patchedBytes.indices) {
                patchedBytes[localOffset] = patch.newByte
            }
        }
    }
    return patchedBytes
}

private fun formatHexDump(range: HexSelectionRange, bytes: ByteArray): String = bytes.asIterable()
    .chunked(HexFileDataManager.BYTES_PER_ROW)
    .mapIndexed { rowIndex, rowBytes ->
        val rowOffset = range.firstOffset + rowIndex * HexFileDataManager.BYTES_PER_ROW
        val hex = rowBytes.joinToString(separator = " ") { it.toHexCellText() }
        val ascii = rowBytes.joinToString(separator = "") { it.toPrintableAscii() }
        "%08X  %-47s  |%s|".format(rowOffset, hex, ascii)
    }
    .joinToString(separator = "\n")

private fun formatCArray(bytes: ByteArray): String {
    val body = bytes.asIterable()
        .chunked(HexFileDataManager.BYTES_PER_ROW)
        .joinToString(separator = ",\n") { rowBytes ->
            "    " + rowBytes.joinToString(separator = ", ") { "0x${it.toHexCellText()}" }
        }
    return "unsigned char data[] = {\n$body\n};"
}

private fun formatKotlinByteArray(bytes: ByteArray): String {
    val body = bytes.asIterable()
        .chunked(HexFileDataManager.BYTES_PER_ROW)
        .joinToString(separator = ",\n") { rowBytes ->
            "    " + rowBytes.joinToString(separator = ", ") { "0x${it.toHexCellText()}.toByte()" }
        }
    return "byteArrayOf(\n$body\n)"
}

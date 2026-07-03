package com.wuxianggujun.tinaide.ui.compose.viewer

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal data class HexSelectionInspector(
    val range: HexSelectionRange,
    val inspectedByteCount: Int,
    val truncated: Boolean,
    val hexPreview: String,
    val asciiPreview: String,
    val utf8Preview: String?,
    val unsigned8: String?,
    val signed8: String?,
    val unsigned16LittleEndian: String?,
    val unsigned16BigEndian: String?,
    val unsigned32LittleEndian: String?,
    val unsigned32BigEndian: String?,
    val unsigned64LittleEndianHex: String?,
    val unsigned64BigEndianHex: String?
)

internal fun inspectHexSelection(
    range: HexSelectionRange,
    bytes: ByteArray,
    sampleLimit: Int = HEX_SELECTION_INSPECT_SAMPLE_BYTES
): HexSelectionInspector {
    val safeSampleLimit = sampleLimit.coerceAtLeast(0)
    val sampleBytes = bytes.take(safeSampleLimit).toByteArray()

    return HexSelectionInspector(
        range = range,
        inspectedByteCount = sampleBytes.size,
        truncated = range.byteCount > sampleBytes.size,
        hexPreview = sampleBytes.toHexPreview(),
        asciiPreview = sampleBytes.toAsciiPreview(),
        utf8Preview = sampleBytes.toUtf8PreviewOrNull(),
        unsigned8 = sampleBytes.readUnsignedValue(byteCount = 1, littleEndian = false)?.toUnsignedLabel(bitCount = 8),
        signed8 = sampleBytes.firstOrNull()?.toInt()?.toString(),
        unsigned16LittleEndian = sampleBytes.readUnsignedValue(byteCount = 2, littleEndian = true)
            ?.toUnsignedLabel(bitCount = 16),
        unsigned16BigEndian = sampleBytes.readUnsignedValue(byteCount = 2, littleEndian = false)
            ?.toUnsignedLabel(bitCount = 16),
        unsigned32LittleEndian = sampleBytes.readUnsignedValue(byteCount = 4, littleEndian = true)
            ?.toUnsignedLabel(bitCount = 32),
        unsigned32BigEndian = sampleBytes.readUnsignedValue(byteCount = 4, littleEndian = false)
            ?.toUnsignedLabel(bitCount = 32),
        unsigned64LittleEndianHex = sampleBytes.readUnsigned64Hex(littleEndian = true),
        unsigned64BigEndianHex = sampleBytes.readUnsigned64Hex(littleEndian = false)
    )
}

private fun ByteArray.toHexPreview(): String = joinToString(separator = " ") { byte ->
    "%02X".format(Locale.US, byte.toInt() and 0xFF)
}

private fun ByteArray.toAsciiPreview(): String = buildString(size) {
    this@toAsciiPreview.forEach { byte ->
        val value = byte.toInt() and 0xFF
        append(if (value in PRINTABLE_ASCII_BYTES) value.toChar() else '.')
    }
}

private fun ByteArray.toUtf8PreviewOrNull(): String? {
    if (isEmpty()) return null
    return runCatching {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
            .toSafeTextPreview()
    }.getOrNull()
}

private fun String.toSafeTextPreview(): String = buildString(length) {
    this@toSafeTextPreview.forEach { char ->
        append(if (char.isISOControl()) '.' else char)
    }
}

private fun ByteArray.readUnsignedValue(byteCount: Int, littleEndian: Boolean): Long? {
    if (size < byteCount || byteCount !in 1..4) return null
    var value = 0L
    val indices = if (littleEndian) {
        (byteCount - 1) downTo 0
    } else {
        0 until byteCount
    }
    indices.forEach { index ->
        value = (value shl 8) or (this[index].toInt() and 0xFF).toLong()
    }
    return value
}

private fun Long.toUnsignedLabel(bitCount: Int): String {
    val hexWidth = bitCount / 4
    return "%d (0x%0${hexWidth}X)".format(Locale.US, this, this)
}

private fun ByteArray.readUnsigned64Hex(littleEndian: Boolean): String? {
    if (size < LONG_BYTES) return null
    val indices = if (littleEndian) {
        (LONG_BYTES - 1) downTo 0
    } else {
        0 until LONG_BYTES
    }
    return indices.joinToString(prefix = "0x", separator = "") { index ->
        "%02X".format(Locale.US, this[index].toInt() and 0xFF)
    }
}

internal const val HEX_SELECTION_INSPECT_SAMPLE_BYTES = 16

private val PRINTABLE_ASCII_BYTES = 0x20..0x7E
private const val LONG_BYTES = 8

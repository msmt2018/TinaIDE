package com.wuxianggujun.tinaide.ui.compose.viewer

import java.util.Locale

data class HexViewerState(
    val filePath: String,
    val fileSize: Long = 0L,
    val bytesPerRow: Int = HexFileDataManager.BYTES_PER_ROW,
    val currentOffset: Long = 0L,
    val selectedOffset: Long = 0L,
    val selectionStartOffset: Long? = null,
    val selectionEndOffset: Long? = null,
    val stagedPatches: List<HexPatch> = emptyList(),
    val redoPatches: List<HexPatch> = emptyList(),
    val bookmarkedOffsets: List<Long> = emptyList(),
    val gotoBackStack: List<Long> = emptyList(),
    val gotoForwardStack: List<Long> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Long> = emptyList(),
    val searchResultIndex: Int = -1,
    val isSearchRunning: Boolean = false,
    val searchError: String? = null,
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val pendingNibble: String = "",
    val error: String? = null
) {
    internal val selectionRange: HexSelectionRange?
        get() {
            val start = selectionStartOffset ?: return null
            val end = selectionEndOffset ?: return null
            return HexSelectionRange(start, end)
        }
}

internal data class HexContextTarget(
    val offset: Long,
    val byte: Byte
)

data class HexPatch(
    val offset: Long,
    val originalByte: Byte,
    val newByte: Byte
)

internal data class HexSelectionRange(
    val startOffset: Long,
    val endOffset: Long
) {
    val firstOffset: Long = minOf(startOffset, endOffset)
    val lastOffset: Long = maxOf(startOffset, endOffset)
    val byteCount: Long = lastOffset - firstOffset + 1

    fun contains(offset: Long): Boolean = offset in firstOffset..lastOffset
}

internal data class HexPatchHistory(
    val stagedPatches: List<HexPatch>,
    val redoPatches: List<HexPatch>
)

internal enum class HexExportFormat {
    HEX_DUMP,
    C_ARRAY,
    KOTLIN_BYTE_ARRAY,
    BASE64,
    ASCII
}

data class HexByteRow(
    val offset: Long,
    val bytes: ByteArray
) {
    companion object {
        fun fromBytes(offset: Long, bytes: ByteArray): HexByteRow = HexByteRow(offset = offset, bytes = bytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HexByteRow) return false
        return offset == other.offset && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

internal fun Byte.toHexCellText(): String = "%02X".format(toInt() and 0xFF)

internal fun Byte.toPrintableAscii(): String {
    val value = toInt() and 0xFF
    return if (value in 0x20..0x7E) value.toChar().toString() else "."
}

internal fun parseOffset(text: String): Long? = parseOffsetExpression(text = text, baseOffset = 0L)

internal fun parseOffsetExpression(text: String, baseOffset: Long): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        when {
            trimmed.startsWith("+") -> baseOffset + parseUnsignedOffset(trimmed.drop(1).trim())
            trimmed.startsWith("-") -> baseOffset - parseUnsignedOffset(trimmed.drop(1).trim())
            else -> parseUnsignedOffset(trimmed)
        }
    }.getOrNull()
}

internal fun parseHexByte(text: String): Byte? {
    val normalized = text.trim()
    if (normalized.length != 2 || !normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return null
    }
    return normalized.toInt(16).toByte()
}

internal fun stageHexPatch(
    patches: List<HexPatch>,
    offset: Long,
    originalByte: Byte,
    newByte: Byte
): List<HexPatch> {
    val existingPatch = patches.firstOrNull { it.offset == offset }
    val stableOriginalByte = existingPatch?.originalByte ?: originalByte
    val withoutTarget = patches.filterNot { it.offset == offset }
    return if (stableOriginalByte == newByte) {
        withoutTarget
    } else {
        withoutTarget + HexPatch(
            offset = offset,
            originalByte = stableOriginalByte,
            newByte = newByte
        )
    }
}

internal fun sortHexPatchesForDisplay(patches: List<HexPatch>): List<HexPatch> = patches.sortedWith(
    compareBy<HexPatch> { it.offset }.thenBy { it.originalByte.toInt() and 0xFF }
)

internal fun discardHexPatchAtOffset(
    patches: List<HexPatch>,
    offset: Long
): List<HexPatch> = patches.filterNot { it.offset == offset }

internal fun sortHexBookmarks(bookmarks: List<Long>): List<Long> = bookmarks.distinct().sorted()

internal fun isHexBookmarked(
    bookmarks: List<Long>,
    offset: Long
): Boolean = bookmarks.any { it == offset }

internal fun toggleHexBookmark(
    bookmarks: List<Long>,
    offset: Long
): List<Long> = if (isHexBookmarked(bookmarks, offset)) {
    bookmarks.filterNot { it == offset }
} else {
    sortHexBookmarks(bookmarks + offset)
}

internal fun markHexBookmarks(
    bookmarks: List<Long>,
    offsets: Iterable<Long>
): List<Long> = sortHexBookmarks(bookmarks + offsets)

internal fun removeHexBookmark(
    bookmarks: List<Long>,
    offset: Long
): List<Long> = bookmarks.filterNot { it == offset }

internal fun formatHexPatchScript(patches: List<HexPatch>): String = sortHexPatchesForDisplay(patches).joinToString(
    separator = "\n"
) { patch ->
    "wx %02X @ 0x%08X".format(Locale.US, patch.newByte.toInt() and 0xFF, patch.offset)
}

internal fun undoLastHexPatch(
    stagedPatches: List<HexPatch>,
    redoPatches: List<HexPatch>
): HexPatchHistory {
    if (stagedPatches.isEmpty()) {
        return HexPatchHistory(stagedPatches = stagedPatches, redoPatches = redoPatches)
    }
    val patch = stagedPatches.last()
    return HexPatchHistory(
        stagedPatches = stagedPatches.dropLast(1),
        redoPatches = redoPatches + patch
    )
}

internal fun redoLastHexPatch(
    stagedPatches: List<HexPatch>,
    redoPatches: List<HexPatch>
): HexPatchHistory {
    if (redoPatches.isEmpty()) {
        return HexPatchHistory(stagedPatches = stagedPatches, redoPatches = redoPatches)
    }
    val patch = redoPatches.last()
    return HexPatchHistory(
        stagedPatches = stageHexPatch(
            patches = stagedPatches,
            offset = patch.offset,
            originalByte = patch.originalByte,
            newByte = patch.newByte
        ),
        redoPatches = redoPatches.dropLast(1)
    )
}

private fun parseUnsignedOffset(text: String): Long = when {
    text.startsWith("0x", ignoreCase = true) -> text.substring(2).toLong(16)
    else -> text.toLong()
}

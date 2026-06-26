package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexByteRowTest {

    @Test
    fun fromBytes_shouldKeepRawBytesWithoutFormattingTheWholeLine() {
        val row = HexByteRow.fromBytes(
            offset = 0x20,
            bytes = byteArrayOf(0x41, 0x00, 0x7E, 0x7F)
        )

        assertThat(row.offset).isEqualTo(0x20L)
        assertThat(row.bytes.toList()).containsExactly(0x41.toByte(), 0x00.toByte(), 0x7E.toByte(), 0x7F.toByte()).inOrder()
    }

    @Test
    fun byteFormatting_shouldBePerCellAndUnsigned() {
        assertThat(0xA0.toByte().toHexCellText()).isEqualTo("A0")
        assertThat(0x41.toByte().toPrintableAscii()).isEqualTo("A")
        assertThat(0x00.toByte().toPrintableAscii()).isEqualTo(".")
        assertThat(0x7F.toByte().toPrintableAscii()).isEqualTo(".")
    }

    @Test
    fun equality_shouldUseByteContentInsteadOfArrayIdentity() {
        val left = HexByteRow.fromBytes(0, byteArrayOf(1, 2, 3))
        val right = HexByteRow.fromBytes(0, byteArrayOf(1, 2, 3))

        assertThat(left).isEqualTo(right)
        assertThat(left.hashCode()).isEqualTo(right.hashCode())
    }

    @Test
    fun parseOffsetExpression_shouldSupportAbsoluteAndRelativeOffsets() {
        assertThat(parseOffsetExpression("0x20", baseOffset = 0x100)).isEqualTo(0x20L)
        assertThat(parseOffsetExpression("+0x20", baseOffset = 0x100)).isEqualTo(0x120L)
        assertThat(parseOffsetExpression("-16", baseOffset = 0x100)).isEqualTo(0xF0L)
        assertThat(parseOffsetExpression("bad", baseOffset = 0x100)).isNull()
    }

    @Test
    fun stageHexPatch_shouldKeepOriginalByteAndDropNoopPatch() {
        val first = stageHexPatch(
            patches = emptyList(),
            offset = 2L,
            originalByte = 0x10,
            newByte = 0x20
        )
        val updated = stageHexPatch(
            patches = first,
            offset = 2L,
            originalByte = 0x20,
            newByte = 0x30
        )
        val dropped = stageHexPatch(
            patches = updated,
            offset = 2L,
            originalByte = 0x30,
            newByte = 0x10
        )

        assertThat(updated).containsExactly(HexPatch(2L, 0x10, 0x30))
        assertThat(dropped).isEmpty()
    }

    @Test
    fun undoRedoPatch_shouldMoveLastPatchBetweenStacks() {
        val patches = listOf(
            HexPatch(1L, 0x01, 0x11),
            HexPatch(2L, 0x02, 0x22)
        )

        val undone = undoLastHexPatch(patches, emptyList())
        val redone = redoLastHexPatch(undone.stagedPatches, undone.redoPatches)

        assertThat(undone.stagedPatches).containsExactly(HexPatch(1L, 0x01, 0x11))
        assertThat(undone.redoPatches).containsExactly(HexPatch(2L, 0x02, 0x22))
        assertThat(redone.stagedPatches).containsExactlyElementsIn(patches).inOrder()
        assertThat(redone.redoPatches).isEmpty()
    }

    @Test
    fun patchDisplayHelpers_shouldSortByOffsetAndDiscardSinglePatch() {
        val patches = listOf(
            HexPatch(0x30L, 0x03, 0x33),
            HexPatch(0x10L, 0x01, 0x11),
            HexPatch(0x20L, 0x02, 0x22)
        )

        val sorted = sortHexPatchesForDisplay(patches)
        val discarded = discardHexPatchAtOffset(patches, offset = 0x20L)

        assertThat(sorted.map { it.offset }).containsExactly(0x10L, 0x20L, 0x30L).inOrder()
        assertThat(discarded)
            .containsExactly(
                HexPatch(0x30L, 0x03, 0x33),
                HexPatch(0x10L, 0x01, 0x11)
            )
            .inOrder()
    }

    @Test
    fun formatHexPatchScript_shouldEmitSortedRadare2WriteCommands() {
        val patches = listOf(
            HexPatch(0x20L, 0x02, 0xA5.toByte()),
            HexPatch(0x10L, 0x01, 0x0F)
        )

        val script = formatHexPatchScript(patches)

        assertThat(script).isEqualTo(
            """
            wx 0F @ 0x00000010
            wx A5 @ 0x00000020
            """.trimIndent()
        )
    }

    @Test
    fun bookmarkHelpers_shouldSortDeduplicateToggleAndRemoveOffsets() {
        val sorted = sortHexBookmarks(listOf(0x30L, 0x10L, 0x30L, 0x20L))
        val added = toggleHexBookmark(sorted, 0x18L)
        val merged = markHexBookmarks(added, listOf(0x08L, 0x18L, 0x40L, 0x10L))
        val removedByToggle = toggleHexBookmark(added, 0x20L)
        val removedExplicitly = removeHexBookmark(removedByToggle, 0x18L)

        assertThat(sorted).containsExactly(0x10L, 0x20L, 0x30L).inOrder()
        assertThat(added).containsExactly(0x10L, 0x18L, 0x20L, 0x30L).inOrder()
        assertThat(merged).containsExactly(0x08L, 0x10L, 0x18L, 0x20L, 0x30L, 0x40L).inOrder()
        assertThat(removedByToggle).containsExactly(0x10L, 0x18L, 0x30L).inOrder()
        assertThat(removedExplicitly).containsExactly(0x10L, 0x30L).inOrder()
        assertThat(isHexBookmarked(added, 0x18L)).isTrue()
        assertThat(isHexBookmarked(removedExplicitly, 0x18L)).isFalse()
    }
}

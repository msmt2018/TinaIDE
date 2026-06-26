package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexExportFormatterTest {

    @Test
    fun applyHexPatchesToRange_shouldOverlayOnlyPatchesInsideSelection() {
        val range = HexSelectionRange(startOffset = 0x10, endOffset = 0x13)
        val bytes = byteArrayOf(0x10, 0x11, 0x12, 0x13)

        val patched = applyHexPatchesToRange(
            range = range,
            bytes = bytes,
            patches = listOf(
                HexPatch(offset = 0x11, originalByte = 0x11, newByte = 0x7F),
                HexPatch(offset = 0x20, originalByte = 0x20, newByte = 0x21)
            )
        )

        assertThat(patched.toList())
            .containsExactly(0x10.toByte(), 0x7F.toByte(), 0x12.toByte(), 0x13.toByte())
            .inOrder()
    }

    @Test
    fun formatHexExport_shouldSupportCommonCopyFormats() {
        val range = HexSelectionRange(startOffset = 0, endOffset = 2)
        val bytes = byteArrayOf(0x41, 0x42, 0x00)

        assertThat(formatHexExport(range, bytes, HexExportFormat.HEX_DUMP))
            .contains("00000000  41 42 00")
        assertThat(formatHexExport(range, bytes, HexExportFormat.C_ARRAY))
            .contains("0x41, 0x42, 0x00")
        assertThat(formatHexExport(range, bytes, HexExportFormat.KOTLIN_BYTE_ARRAY))
            .contains("0x41.toByte(), 0x42.toByte(), 0x00.toByte()")
        assertThat(formatHexExport(range, bytes, HexExportFormat.BASE64)).isEqualTo("QUIA")
        assertThat(formatHexExport(range, bytes, HexExportFormat.ASCII)).isEqualTo("AB.")
    }
}

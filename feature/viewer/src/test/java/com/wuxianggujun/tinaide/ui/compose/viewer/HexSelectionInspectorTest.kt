package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexSelectionInspectorTest {

    @Test
    fun inspectHexSelection_shouldDecodeEndianValuesFromSampleBytes() {
        val range = HexSelectionRange(startOffset = 0x20, endOffset = 0x27)
        val bytes = byteArrayOf(
            0x01,
            0x02,
            0x03,
            0x04,
            0x05,
            0x06,
            0x07,
            0x08
        )

        val inspector = inspectHexSelection(range, bytes)

        assertThat(inspector.inspectedByteCount).isEqualTo(8)
        assertThat(inspector.truncated).isFalse()
        assertThat(inspector.hexPreview).isEqualTo("01 02 03 04 05 06 07 08")
        assertThat(inspector.asciiPreview).isEqualTo("........")
        assertThat(inspector.unsigned8).isEqualTo("1 (0x01)")
        assertThat(inspector.signed8).isEqualTo("1")
        assertThat(inspector.unsigned16LittleEndian).isEqualTo("513 (0x0201)")
        assertThat(inspector.unsigned16BigEndian).isEqualTo("258 (0x0102)")
        assertThat(inspector.unsigned32LittleEndian).isEqualTo("67305985 (0x04030201)")
        assertThat(inspector.unsigned32BigEndian).isEqualTo("16909060 (0x01020304)")
        assertThat(inspector.unsigned64LittleEndianHex).isEqualTo("0x0807060504030201")
        assertThat(inspector.unsigned64BigEndianHex).isEqualTo("0x0102030405060708")
    }

    @Test
    fun inspectHexSelection_shouldBuildTextPreviewsAndMarkTruncation() {
        val textBytes = "Hi\n\u00E9".toByteArray(Charsets.UTF_8)
        val range = HexSelectionRange(startOffset = 0x100, endOffset = 0x120)

        val inspector = inspectHexSelection(
            range = range,
            bytes = textBytes + ByteArray(32) { 0x41 },
            sampleLimit = textBytes.size
        )

        assertThat(inspector.inspectedByteCount).isEqualTo(textBytes.size)
        assertThat(inspector.truncated).isTrue()
        assertThat(inspector.asciiPreview).isEqualTo("Hi...")
        assertThat(inspector.utf8Preview).isEqualTo("Hi.\u00E9")
    }

    @Test
    fun inspectHexSelection_shouldRejectMalformedUtf8Preview() {
        val inspector = inspectHexSelection(
            range = HexSelectionRange(startOffset = 0, endOffset = 1),
            bytes = byteArrayOf(0xC3.toByte(), 0x28)
        )

        assertThat(inspector.utf8Preview).isNull()
    }
}

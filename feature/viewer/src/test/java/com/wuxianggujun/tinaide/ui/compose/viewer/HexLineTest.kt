package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexLineTest {

    @Test
    fun fromBytes_shouldFormatHexAndPrintableAscii() {
        val line = HexLine.fromBytes(
            offset = 0x20,
            bytes = byteArrayOf(0x41, 0x00, 0x7E, 0x7F)
        )

        assertThat(line.offset).isEqualTo(0x20L)
        assertThat(line.hexString).startsWith("41 00 7E 7F")
        assertThat(line.asciiString).isEqualTo("A.~.")
    }

    @Test
    fun equality_shouldUseByteContentInsteadOfArrayIdentity() {
        val left = HexLine.fromBytes(0, byteArrayOf(1, 2, 3))
        val right = HexLine.fromBytes(0, byteArrayOf(1, 2, 3))

        assertThat(left).isEqualTo(right)
        assertThat(left.hashCode()).isEqualTo(right.hashCode())
    }
}

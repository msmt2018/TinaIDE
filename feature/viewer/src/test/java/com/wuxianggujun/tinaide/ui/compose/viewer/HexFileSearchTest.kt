package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class HexFileSearchTest {

    @Test
    fun searchInHexFile_shouldFindHexPatternAndAsciiText() {
        val file = File.createTempFile("tina-hex-search", ".bin").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(0x00, 0x7F, 0x45, 0x4C, 0x46, 0x20, 0x45, 0x4C, 0x46))
        }

        assertThat(searchInHexFile(file, "45 4C 46")).containsExactly(2L, 6L).inOrder()
        assertThat(searchInHexFile(file, "ELF")).containsExactly(2L, 6L).inOrder()
    }

    @Test
    fun searchInHexFile_shouldFindHexPatternWithWildcardBytes() {
        val file = File.createTempFile("tina-hex-search-wildcard", ".bin").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(0x45, 0x4C, 0x46, 0x45, 0x00, 0x46, 0x45, 0x7F, 0x46))
        }

        assertThat(searchInHexFile(file, "45 ?? 46")).containsExactly(0L, 3L, 6L).inOrder()
        assertThat(searchInHexFile(file, "45??46")).containsExactly(0L, 3L, 6L).inOrder()
    }
}

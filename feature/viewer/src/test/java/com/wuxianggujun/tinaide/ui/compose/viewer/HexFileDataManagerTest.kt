package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test

class HexFileDataManagerTest {

    @Test
    fun loadChunkForRow_shouldCacheRequestedLine() = runBlocking {
        val file = tempBinaryFile(ByteArray(32) { it.toByte() })
        val manager = HexFileDataManager(file)

        assertThat(manager.refreshFileSize()).isEqualTo(32L)
        assertThat(manager.loadChunkForRow(1)).isTrue()

        val line = manager.getCachedRow(1)

        assertThat(line).isNotNull()
        assertThat(line!!.offset).isEqualTo(16L)
        assertThat(line.bytes.toList()).containsExactlyElementsIn(ByteArray(16) { (it + 16).toByte() }.toList()).inOrder()
    }

    @Test
    fun writeByte_shouldUpdateFileAndCachedLine() = runBlocking {
        val file = tempBinaryFile(ByteArray(16) { it.toByte() })
        val manager = HexFileDataManager(file)
        manager.loadChunkForRow(0)

        manager.writeByte(2L, 0x7F.toByte())

        assertThat(file.readBytes()[2]).isEqualTo(0x7F.toByte())
        assertThat(manager.getCachedRow(0)!!.bytes[2]).isEqualTo(0x7F.toByte())
    }

    @Test
    fun writePatches_shouldUpdateFileAndCachedLineTogether() = runBlocking {
        val file = tempBinaryFile(ByteArray(16) { it.toByte() })
        val manager = HexFileDataManager(file)
        manager.loadChunkForRow(0)

        manager.writePatches(
            listOf(
                HexPatch(offset = 1L, originalByte = 0x01, newByte = 0x51),
                HexPatch(offset = 3L, originalByte = 0x03, newByte = 0x53)
            )
        )

        assertThat(file.readBytes()[1]).isEqualTo(0x51.toByte())
        assertThat(file.readBytes()[3]).isEqualTo(0x53.toByte())
        assertThat(manager.getCachedByte(1L)).isEqualTo(0x51.toByte())
        assertThat(manager.getCachedByte(3L)).isEqualTo(0x53.toByte())
    }

    @Test
    fun readBytes_shouldClampToFileSize() = runBlocking {
        val file = tempBinaryFile(byteArrayOf(0x01, 0x02, 0x03))
        val manager = HexFileDataManager(file)

        val bytes = manager.readBytes(offset = 1L, byteCount = 16)

        assertThat(bytes.toList()).containsExactly(0x02.toByte(), 0x03.toByte()).inOrder()
    }

    @Test
    fun parseHexByte_shouldAcceptOnlyTwoHexDigits() {
        assertThat(parseHexByte("7f")).isEqualTo(0x7F.toByte())
        assertThat(parseHexByte("A0")).isEqualTo(0xA0.toByte())
        assertThat(parseHexByte("A")).isNull()
        assertThat(parseHexByte("GG")).isNull()
    }

    private fun tempBinaryFile(bytes: ByteArray): File = File.createTempFile("tina-hex", ".bin").apply {
        deleteOnExit()
        writeBytes(bytes)
    }
}

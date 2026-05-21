package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HexViewerStateTest {

    @Test
    fun state_shouldExposeLoadingDefaultsForNewFile() {
        val state = HexViewerState(filePath = "/tmp/demo.bin")

        assertThat(state.filePath).isEqualTo("/tmp/demo.bin")
        assertThat(state.fileSize).isEqualTo(0L)
        assertThat(state.bytesPerRow).isEqualTo(16)
        assertThat(state.currentOffset).isEqualTo(0L)
        assertThat(state.isLoading).isTrue()
        assertThat(state.error).isNull()
    }

    @Test
    fun stateCopy_shouldRepresentLoadedOffsetAndErrorTransitions() {
        val loaded = HexViewerState(filePath = "/tmp/demo.bin").copy(
            fileSize = 4096L,
            currentOffset = 32L,
            isLoading = false,
        )
        val failed = loaded.copy(error = "read failed")

        assertThat(loaded.fileSize).isEqualTo(4096L)
        assertThat(loaded.currentOffset).isEqualTo(32L)
        assertThat(loaded.isLoading).isFalse()
        assertThat(failed.error).isEqualTo("read failed")
    }
}

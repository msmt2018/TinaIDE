package com.wuxianggujun.tinaide.ui.compose.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImagePreviewFormatTest {

    @Test
    fun formatFileSize_shouldUseSingleDecimalBinaryUnits() {
        assertThat(formatFileSize(512)).isEqualTo("512 B")
        assertThat(formatFileSize(1536)).isEqualTo("1.5 KB")
        assertThat(formatFileSize(2L * 1024 * 1024)).isEqualTo("2.0 MB")
        assertThat(formatFileSize(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB")
    }

    @Test
    fun imageInfo_shouldKeepDimensionsFormatAndSize() {
        val info = ImageInfo(
            width = 1920,
            height = 1080,
            format = "PNG",
            fileSize = 2048
        )

        assertThat(info.width).isEqualTo(1920)
        assertThat(info.height).isEqualTo(1080)
        assertThat(info.format).isEqualTo("PNG")
        assertThat(info.fileSize).isEqualTo(2048)
    }
}

package com.wuxianggujun.tinaide.ui.compose.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptchaUiDefaultsTest {

    @Test
    fun imageAspectRatio_shouldMatchInlineImageTokenRatio() {
        val tokenRatio = CaptchaUiDefaults.inlineImageWidth.value /
            CaptchaUiDefaults.inlineImageHeight.value

        assertThat(CaptchaUiDefaults.imageAspectRatio).isEqualTo(tokenRatio)
    }

    @Test
    fun dialogImage_shouldRemainWiderThanInlineImage() {
        assertThat(CaptchaUiDefaults.dialogImageMaxWidth.value)
            .isGreaterThan(CaptchaUiDefaults.inlineImageWidth.value)
    }
}

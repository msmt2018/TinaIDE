package com.wuxianggujun.tinaide.ui.compose.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TinaSpacingScaleTest {

    @Test
    fun baseSpacingTokens_shouldStayInAscendingOrder() {
        val values = listOf(
            TinaSpacing.xxs.value,
            TinaSpacing.xs.value,
            TinaSpacing.sm.value,
            TinaSpacing.md.value,
            TinaSpacing.mdLg.value,
            TinaSpacing.lg.value,
            TinaSpacing.xl.value,
            TinaSpacing.xxl.value,
            TinaSpacing.xxxl.value,
            TinaSpacing.huge.value
        )

        assertThat(values).isEqualTo(values.sorted())
        assertThat(values.toSet()).hasSize(values.size)
    }

    @Test
    fun semanticSpacingTokens_shouldMapToExpectedLayoutDensity() {
        assertThat(TinaSpacing.listItemVertical).isEqualTo(TinaSpacing.xxs)
        assertThat(TinaSpacing.listItemHorizontal).isEqualTo(TinaSpacing.xs)
        assertThat(TinaSpacing.toolbarPadding).isEqualTo(TinaSpacing.md)
        assertThat(TinaSpacing.statusBarPadding).isEqualTo(TinaSpacing.lg)
        assertThat(TinaSpacing.dialogPadding).isEqualTo(TinaSpacing.xxxl)
    }
}

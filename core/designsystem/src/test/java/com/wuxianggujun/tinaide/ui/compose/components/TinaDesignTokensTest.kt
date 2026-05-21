package com.wuxianggujun.tinaide.ui.compose.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TinaDesignTokensTest {

    @Test
    fun spacingAliases_shouldPointToStableBaseTokens() {
        assertThat(TinaSpacing.iconText).isEqualTo(TinaSpacing.sm)
        assertThat(TinaSpacing.cardPadding).isEqualTo(TinaSpacing.lg)
        assertThat(TinaSpacing.buttonGap).isEqualTo(TinaSpacing.md)
        assertThat(TinaSpacing.pageHorizontal).isEqualTo(TinaSpacing.xl)
    }

    @Test
    fun shapeTokens_shouldKeepExpectedRelativeSizes() {
        assertThat(TinaShapes.ExtraSmallCorner.value).isLessThan(TinaShapes.SmallCorner.value)
        assertThat(TinaShapes.SmallCorner.value).isLessThan(TinaShapes.ButtonCorner.value)
        assertThat(TinaShapes.ButtonCorner).isEqualTo(TinaShapes.TextFieldCorner)
        assertThat(TinaShapes.DialogCorner.value).isGreaterThan(TinaShapes.CardCorner.value)
    }
}

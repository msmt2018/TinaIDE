package com.wuxianggujun.tinaide.ui.compose.screens.main.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MAX_SAFE_SCAFFOLD_PADDING = 1024.dp

/**
 * 防御性处理 Scaffold 内边距，避免异常 Insets 导致约束溢出。
 */
@Composable
internal fun PaddingValues.sanitizeForScaffoldContent(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateLeftPadding(layoutDirection).sanitizeDp(),
        top = calculateTopPadding().sanitizeDp(),
        end = calculateRightPadding(layoutDirection).sanitizeDp(),
        bottom = calculateBottomPadding().sanitizeDp()
    )
}

private fun Dp.sanitizeDp(max: Dp = MAX_SAFE_SCAFFOLD_PADDING): Dp {
    if (!value.isFinite()) return 0.dp
    return value.coerceIn(0f, max.value).dp
}

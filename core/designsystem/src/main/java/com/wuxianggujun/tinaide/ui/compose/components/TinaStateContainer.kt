package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * TinaIDE 通用三态容器组件
 *
 * 统一处理 Loading / Error / Empty / Content 四种 UI 状态，
 * 避免各 feature 模块重复实现状态切换逻辑。
 *
 * 使用方式：
 * ```kotlin
 * TinaStateContainer(
 *     isLoading = viewModel.isLoading,
 *     error = viewModel.error,
 *     isEmpty = items.isEmpty(),
 *     onRetry = { viewModel.refresh() }
 * ) {
 *     LazyColumn { ... }
 * }
 * ```
 */

/**
 * UI 状态枚举
 */
enum class TinaUiState {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT
}

/**
 * 通用三态容器
 *
 * @param isLoading 是否正在加载
 * @param error 错误信息（非 null 时显示错误状态）
 * @param isEmpty 数据是否为空（仅在非加载、非错误时生效）
 * @param onRetry 重试回调（可选，提供时在错误状态显示重试按钮）
 * @param modifier Modifier
 * @param loadingContent 自定义加载状态 UI（可选）
 * @param errorContent 自定义错误状态 UI（可选）
 * @param emptyContent 自定义空状态 UI（可选）
 * @param content 正常内容
 */
@Composable
fun TinaStateContainer(
    isLoading: Boolean,
    error: String? = null,
    isEmpty: Boolean = false,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (String, (() -> Unit)?) -> Unit)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val state = when {
        isLoading -> TinaUiState.LOADING
        error != null -> TinaUiState.ERROR
        isEmpty -> TinaUiState.EMPTY
        else -> TinaUiState.CONTENT
    }

    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = TinaAnimation.normalTween()) togetherWith
                    fadeOut(animationSpec = TinaAnimation.normalTween())
        },
        label = "TinaStateContainer"
    ) { targetState ->
        when (targetState) {
            TinaUiState.LOADING -> {
                if (loadingContent != null) {
                    loadingContent()
                } else {
                    TinaDefaultLoadingContent()
                }
            }
            TinaUiState.ERROR -> {
                if (errorContent != null) {
                    errorContent(error ?: "", onRetry)
                } else {
                    TinaDefaultErrorContent(
                        message = error ?: "",
                        onRetry = onRetry
                    )
                }
            }
            TinaUiState.EMPTY -> {
                if (emptyContent != null) {
                    emptyContent()
                } else {
                    TinaDefaultEmptyContent()
                }
            }
            TinaUiState.CONTENT -> {
                content()
            }
        }
    }
}

/**
 * 默认加载状态 UI
 */
@Composable
private fun TinaDefaultLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinaSpacing.xl)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp
            )
            Text(
                text = stringResource(Strings.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 默认错误状态 UI
 *
 * @param message 错误信息
 * @param onRetry 重试回调
 */
@Composable
private fun TinaDefaultErrorContent(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TinaSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinaSpacing.md)
        ) {
            Text(
                text = stringResource(Strings.tina_state_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message.ifEmpty { stringResource(Strings.tina_state_error_subtitle) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(TinaSpacing.md))
                TinaPrimaryButton(
                    text = stringResource(Strings.btn_retry),
                    onClick = onRetry
                )
            }
        }
    }
}

/**
 * 默认空状态 UI
 */
@Composable
private fun TinaDefaultEmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TinaSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TinaSpacing.md)
        ) {
            Text(
                text = stringResource(Strings.tina_state_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Strings.tina_state_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

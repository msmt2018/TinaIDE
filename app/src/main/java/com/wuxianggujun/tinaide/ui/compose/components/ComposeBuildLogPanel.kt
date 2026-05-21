package com.wuxianggujun.tinaide.ui.compose.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.compile.BuildLogEntry
import com.wuxianggujun.tinaide.core.compile.BuildLogLevel
import com.wuxianggujun.tinaide.core.i18n.Strings
import kotlinx.coroutines.launch

/**
 * 纯 Compose 实现的构建日志面板
 *
 * 使用 SelectionContainer + verticalScroll 实现可自由选择文本的日志面板。
 * 所有日志渲染为带颜色的 AnnotatedString，支持跨行拖选复制。
 *
 * 功能：
 * - 自由文本选择（跨行拖选复制）
 * - 自动滚动到底部
 * - 顶部工具栏：复制全部、清空日志、滚动到底部
 * - 根据日志级别显示不同颜色
 * - 水平滚动支持（长行不截断）
 */
@Composable
fun ComposeBuildLogPanel(
    logs: List<BuildLogEntry>,
    modifier: Modifier = Modifier,
    autoScroll: Boolean = true,
    fontSizeSp: Float = 12f,
    onClearLogs: (() -> Unit)? = null,
    @StringRes emptyMessageRes: Int = Strings.build_log_empty,
    @StringRes clipboardLabelRes: Int = Strings.build_log_clipboard_label
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val toastCopied = stringResource(Strings.toast_copied)
    val toastEmpty = stringResource(emptyMessageRes)
    val buildLogClipboardLabel = stringResource(clipboardLabelRes)

    // 构建带颜色的 AnnotatedString
    val annotatedLogs: AnnotatedString = remember(logs) {
        buildColoredLogText(logs, fontSizeSp)
    }

    // 纯文本版本（用于复制全部）
    val plainText: String = remember(logs) {
        buildPlainLogText(logs)
    }

    // 自动滚动到底部
    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部工具栏
        BuildLogToolbar(
            logCount = logs.size,
            onCopyAll = {
                if (plainText.isNotEmpty()) {
                    copyTextToClipboard(
                        context = context,
                        label = buildLogClipboardLabel,
                        text = plainText,
                        toastMessage = toastCopied
                    )
                } else {
                    Toast.makeText(context, toastEmpty, Toast.LENGTH_SHORT).show()
                }
            },
            onClear = onClearLogs,
            onScrollToBottom = {
                scope.launch {
                    verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                }
            }
        )

        TinaOverlayPanelSurface(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            SelectionContainer(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (logs.isEmpty()) {
                        // 空状态下不显示文本（由外层 BuildLogContent 处理）
                    } else {
                        Text(
                            text = annotatedLogs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.5f).sp,
                            // 不限制最大行数，让文本自然换行
                            softWrap = false // 禁用自动换行，通过水平滚动查看长行
                        )
                    }
                }
            }
        }
    }
}

/**
 * 构建日志工具栏
 */
@Composable
private fun BuildLogToolbar(
    logCount: Int,
    onCopyAll: () -> Unit,
    onClear: (() -> Unit)?,
    onScrollToBottom: () -> Unit
) {
    val scrollToBottomDesc = stringResource(Strings.content_desc_scroll_to_bottom)
    val clearLogsDesc = stringResource(Strings.content_desc_clear_logs)

    TinaOverlayPanelSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$logCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TinaPanelSegmentButton(
                    onClick = onCopyAll,
                    modifier = Modifier.size(32.dp),
                    minHeight = 32.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(Strings.content_desc_copy),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TinaPanelSegmentButton(
                    onClick = onScrollToBottom,
                    modifier = Modifier.size(32.dp),
                    minHeight = 32.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignBottom,
                        contentDescription = scrollToBottomDesc,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onClear != null) {
                    TinaPanelSegmentButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp),
                        minHeight = 32.dp,
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = clearLogsDesc,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ============ 辅助函数 ============

/**
 * 构建带颜色的日志文本
 *
 * 每条日志一行，时间戳用淡色，消息用对应级别的颜色
 */
private fun buildColoredLogText(
    logs: List<BuildLogEntry>,
    fontSizeSp: Float
): AnnotatedString = buildAnnotatedString {
    val timestampColor = Color(0xFF6E6E6E)

    logs.forEachIndexed { index, entry ->
        // 时间戳（淡色）
        withStyle(SpanStyle(color = timestampColor)) {
            append(entry.formattedTime)
            append("  ")
        }

        // 日志消息（按级别着色）
        withStyle(
            SpanStyle(
                color = getLevelColorValue(entry.level),
                fontWeight = getLevelFontWeight(entry.level)
            )
        ) {
            append(entry.message)
        }

        // 除最后一行外，添加换行
        if (index < logs.size - 1) {
            append("\n")
        }
    }
}

/**
 * 构建纯文本日志（用于复制全部）
 */
private fun buildPlainLogText(logs: List<BuildLogEntry>): String = logs.joinToString("\n") { it.fullText }

/**
 * 根据日志级别获取颜色值
 */
private fun getLevelColorValue(level: BuildLogLevel): Color = when (level) {
    BuildLogLevel.VERBOSE -> TinaSemanticColors.Log.verbose
    BuildLogLevel.DEBUG -> TinaSemanticColors.Log.debug
    BuildLogLevel.PROGRESS -> TinaSemanticColors.Log.success
    BuildLogLevel.INFO -> TinaSemanticColors.Log.info
    BuildLogLevel.WARN -> TinaSemanticColors.Log.warn
    BuildLogLevel.ERROR -> TinaSemanticColors.Log.error
    BuildLogLevel.SUCCESS -> TinaSemanticColors.Log.success
    BuildLogLevel.FAIL -> TinaSemanticColors.Log.fail
}

private fun getLevelFontWeight(level: BuildLogLevel): FontWeight? = when (level) {
    BuildLogLevel.PROGRESS -> FontWeight.SemiBold
    else -> null
}

/**
 * 复制文本到剪贴板
 */
private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
    toastMessage: String
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboardManager.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

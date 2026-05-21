package com.wuxianggujun.tinaide.ui.compose.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wuxianggujun.tinaide.core.compile.BuildLogEntry
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 构建日志内容
 *
 * 使用纯 Compose 实现的构建日志面板，避免 AndroidView 布局冲突
 */
@Composable
fun BuildLogContent(
    logs: List<BuildLogEntry>,
    modifier: Modifier = Modifier,
    onClearLogs: (() -> Unit)? = null,
    @StringRes emptyMessageRes: Int = Strings.build_log_empty,
    @StringRes clipboardLabelRes: Int = Strings.build_log_clipboard_label
) {
    if (logs.isEmpty()) {
        EmptyStateContent(
            message = stringResource(emptyMessageRes),
            modifier = modifier
        )
    } else {
        ComposeBuildLogPanel(
            logs = logs,
            modifier = modifier,
            fontSizeSp = 12f,
            autoScroll = true,
            onClearLogs = onClearLogs,
            emptyMessageRes = emptyMessageRes,
            clipboardLabelRes = clipboardLabelRes
        )
    }
}

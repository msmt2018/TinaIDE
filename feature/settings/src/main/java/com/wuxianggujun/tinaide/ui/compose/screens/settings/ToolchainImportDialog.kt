package com.wuxianggujun.tinaide.ui.compose.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogScaffold
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogActionRow
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogMessageCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaOutlinedButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaPanelSegmentButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton

/**
 * 工具链导入对话框
 *
 * 显示详细的导入进度、日志和结果
 */
@Composable
fun ToolchainImportDialog(
    state: ToolchainImportState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit = {}
) {
    if (state is ToolchainImportState.Idle) return

    TinaCustomDialog(
        onDismissRequest = {
            // 只有在成功或失败时才允许关闭
            if (state is ToolchainImportState.Success || state is ToolchainImportState.Failed) {
                onDismiss()
            }
        },
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.8f),
        properties = DialogProperties(
            dismissOnBackPress = state !is ToolchainImportState.Importing,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        contentPadding = PaddingValues(16.dp)
    ) {
        TinaCustomDialogScaffold(
            modifier = Modifier.fillMaxSize(),
            header = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TinaCustomDialogHeader(
                        title = stringResource(Strings.toolchain_import_title),
                        subtitle = when (state) {
                            is ToolchainImportState.Importing -> state.fileName
                            is ToolchainImportState.Success -> stringResource(
                                Strings.toolchain_import_toolchain_info,
                                state.toolchainName,
                                state.toolchainId
                            )
                            is ToolchainImportState.Failed,
                            ToolchainImportState.Idle -> null
                        },
                        trailingContent = if (state is ToolchainImportState.Success || state is ToolchainImportState.Failed) {
                            {
                                ToolchainImportActionButton(onClick = onDismiss) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(Strings.btn_close),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
                }
            },
            footer = when (state) {
                is ToolchainImportState.Success -> {
                    {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider()
                            TinaDialogActionRow(
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                TinaOutlinedButton(
                                    text = stringResource(Strings.action_close),
                                    onClick = onDismiss
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TinaPrimaryButton(
                                    text = stringResource(Strings.action_ok),
                                    onClick = {
                                        onConfirm()
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
                is ToolchainImportState.Failed -> {
                    {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider()
                            TinaDialogActionRow(
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                TinaPrimaryButton(
                                    text = stringResource(Strings.action_close),
                                    onClick = onDismiss
                                )
                            }
                        }
                    }
                }
                ToolchainImportState.Idle,
                is ToolchainImportState.Importing -> null
            }
        ) {
            when (state) {
                is ToolchainImportState.Importing -> {
                    ImportingContent(state, Modifier.fillMaxSize())
                }
                is ToolchainImportState.Success -> {
                    SuccessContent(state, Modifier.fillMaxSize())
                }
                is ToolchainImportState.Failed -> {
                    FailedContent(state, Modifier.fillMaxSize())
                }
                is ToolchainImportState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ImportingContent(
    state: ToolchainImportState.Importing,
    modifier: Modifier = Modifier
) {
    TinaDialogContentColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FileInfoCard(
            fileName = state.fileName,
            fileSize = state.fileSize,
            fileType = state.fileType,
            targetPath = state.targetPath
        )

        ToolchainImportSectionCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = state.currentStep,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LogPanel(
            logs = state.logs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun SuccessContent(
    state: ToolchainImportState.Success,
    modifier: Modifier = Modifier
) {
    TinaDialogContentColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TinaDialogMessageCard(
            message = stringResource(Strings.toolchain_import_success),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            textColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        ToolchainImportSectionCard(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(
                        Strings.toolchain_import_toolchain_info,
                        state.toolchainName,
                        state.toolchainId
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LogPanel(
            logs = state.logs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun FailedContent(
    state: ToolchainImportState.Failed,
    modifier: Modifier = Modifier
) {
    TinaDialogContentColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TinaDialogMessageCard(
            message = state.error,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
            textColor = MaterialTheme.colorScheme.onErrorContainer
        )
        LogPanel(
            logs = state.logs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun FileInfoCard(
    fileName: String,
    fileSize: Long,
    fileType: String,
    targetPath: String
) {
    ToolchainImportSectionCard(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        InfoRow(stringResource(Strings.toolchain_import_file_name), fileName)
        InfoRow(stringResource(Strings.toolchain_import_file_size), formatFileSize(fileSize))
        InfoRow(stringResource(Strings.toolchain_import_file_type), fileType)
        InfoRow(stringResource(Strings.toolchain_import_target_path), targetPath)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.7f)
        )
    }
}

@Composable
private fun LogPanel(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    // 自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
        }
    }

    ToolchainImportSectionCard(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 标题
            Text(
                text = stringResource(Strings.toolchain_import_log_title),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(8.dp),
                fontWeight = FontWeight.Medium
            )
            HorizontalDivider()

            // 日志内容
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                        .padding(8.dp)
                ) {
                    Text(
                        text = logs.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolchainImportActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable BoxScope.() -> Unit
) {
    TinaPanelSegmentButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        minHeight = 36.dp,
        color = color,
        contentPadding = PaddingValues(0.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ToolchainImportSectionCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    TinaDialogCard(
        modifier = modifier,
        color = color,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

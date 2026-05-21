package com.wuxianggujun.tinaide.ui.projectlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogActionRow
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.components.MarkdownViewer

/**
 * 公告弹窗组件
 * 
 * 用于以弹窗形式展示重要公告
 * 
 * @param announcement 公告数据
 * @param onDismiss 关闭回调
 * @param onAction 操作按钮回调（可选）
 */
@Composable
fun AnnouncementDialog(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    TinaCustomDialog(
        onDismissRequest = { if (announcement.dismissible) onDismiss() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = announcement.dismissible,
            dismissOnClickOutside = announcement.dismissible
        ),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 12.dp,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
    ) {
        TinaDialogContentColumn {
            TinaCustomDialogHeader(
                title = announcement.title,
                leadingContent = {
                    AnnouncementTypeBadge(
                        type = announcement.type,
                        iconPadding = 12.dp,
                        iconSize = 24.dp
                    )
                },
                trailingContent = if (announcement.dismissible) {
                    {
                        ProjectDialogActionButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp),
                            color = colorScheme.surfaceContainerHigh
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Strings.btn_close),
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    null
                }
            )

            if (!announcement.content.isNullOrBlank()) {
                ProjectDialogSectionSurface(
                    modifier = Modifier.heightIn(max = 320.dp),
                    color = colorScheme.surfaceContainerLow,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = colorScheme.onSurfaceVariant
                        )
                    ) {
                        MarkdownViewer(
                            markdown = announcement.content,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            contentPadding = PaddingValues(0.dp)
                        )
                    }
                }
            }

            TinaDialogActionRow {
                if (announcement.dismissible) {
                    TinaTextButton(
                        text = stringResource(Strings.btn_close),
                        onClick = onDismiss
                    )
                }

                if (announcement.actionText != null && onAction != null) {
                    if (announcement.dismissible) {
                        Spacer(Modifier.width(8.dp))
                    }
                    TinaPrimaryButton(
                        text = announcement.actionText,
                        onClick = onAction
                    )
                }
            }
        }
    }
}

package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogMessageCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton

/**
 * 存储权限请求对话框
 *
 * Android 11+ 跳转系统"所有文件访问权限"设置页；
 * Android 10 及以下走运行时 READ/WRITE_EXTERNAL_STORAGE 弹窗。
 *
 * @param onRequestPermission 用户点击授予权限
 * @param onDismiss 用户点击稍后
 */
@Composable
fun StoragePermissionDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = { TinaDialogTitleText(stringResource(Strings.permission_storage_title)) },
        text = {
            TinaDialogContentColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TinaDialogMessageCard(
                    message = stringResource(Strings.permission_storage_message)
                )
                TinaDialogCard {
                    Text(
                        text = stringResource(Strings.permission_storage_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TinaPrimaryButton(
                text = stringResource(Strings.permission_btn_grant),
                onClick = onRequestPermission
            )
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.permission_btn_later),
                onClick = onDismiss
            )
        }
    )
}

package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 关闭未保存文件的确认对话框
 *
 * @param fileName 文件名
 * @param onSaveAndClose 点击"保存并关闭"
 * @param onDiscardAndClose 点击"不保存关闭"
 * @param onCancel 点击"取消"
 */
@Composable
fun UnsavedFileDialog(
    fileName: String,
    onSaveAndClose: () -> Unit,
    onDiscardAndClose: () -> Unit,
    onCancel: () -> Unit
) {
    TinaAlertDialog(
        onDismissRequest = onCancel,
        title = { TinaDialogTitleText(stringResource(Strings.unsaved_changes_title)) },
        text = {
            TinaDialogContentColumn {
                TinaDialogMessageCard(
                    message = stringResource(Strings.unsaved_changes_message, fileName)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinaDangerOutlinedButton(
                    text = stringResource(Strings.btn_dont_save),
                    onClick = onDiscardAndClose
                )
                TinaPrimaryButton(
                    text = stringResource(Strings.btn_save_and_close),
                    onClick = onSaveAndClose
                )
            }
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_cancel),
                onClick = onCancel
            )
        }
    )
}

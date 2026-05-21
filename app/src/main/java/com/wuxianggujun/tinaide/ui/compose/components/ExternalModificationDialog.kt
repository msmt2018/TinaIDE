package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.common.simplifyPath
import com.wuxianggujun.tinaide.core.i18n.Strings
import java.io.File

@Composable
fun ExternalModificationDialog(
    file: File,
    onReload: () -> Unit,
    onKeepMine: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targetPath = simplifyPath(file.absolutePath, context)

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = { TinaDialogTitleText(stringResource(Strings.editor_conflict_title)) },
        text = {
            TinaDialogContentColumn(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TinaDialogMessageCard(
                    message = stringResource(Strings.editor_conflict_message, file.name)
                )

                CreationTargetSection(
                    title = stringResource(Strings.label_target_path),
                    value = targetPath
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinaDangerOutlinedButton(
                    text = stringResource(Strings.editor_conflict_overwrite),
                    onClick = onKeepMine
                )
                TinaPrimaryButton(
                    text = stringResource(Strings.editor_conflict_reload),
                    onClick = onReload
                )
            }
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.editor_conflict_cancel),
                onClick = onDismiss
            )
        }
    )
}

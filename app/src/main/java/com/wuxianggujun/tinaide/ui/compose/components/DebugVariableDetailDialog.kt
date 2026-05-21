package com.wuxianggujun.tinaide.ui.compose.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr

/**
 * 调试变量详情对话框
 *
 * 显示变量的完整信息，支持复制变量值
 */
@Composable
fun DebugVariableDetailDialog(
    variable: DebugVariable,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val copyToastText = Strings.toast_copied.strOr(context)
    val variableValueLabel = stringResource(Strings.debug_variable_value)

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            copyToastText,
            Toast.LENGTH_SHORT
        ).show()
    }

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TinaDialogTitleText(stringResource(Strings.debug_variable_detail_title))
        },
        text = {
            TinaDialogContentColumn {
                TinaDialogCard(
                    contentPadding = PaddingValues(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                ) {
                    VariableDetailRow(
                        label = stringResource(Strings.debug_variable_name),
                        value = variable.name,
                        isCopyable = true,
                        onCopy = { copyText(it, variable.name) }
                    )

                    VariableDetailRow(
                        label = stringResource(Strings.debug_variable_type),
                        value = variable.type,
                        isCopyable = false,
                        onCopy = {}
                    )

                    TinaDialogContentColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(Strings.debug_variable_value),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TinaOverlayPanelSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    copyText(variableValueLabel, variable.value)
                                },
                            shape = MaterialTheme.shapes.small,
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = variable.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(Strings.content_desc_copy),
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_ok),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun VariableDetailRow(
    label: String,
    value: String,
    isCopyable: Boolean,
    onCopy: (String) -> Unit
) {
    TinaDialogContentColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TinaOverlayPanelSurface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isCopyable) {
                        Modifier.clickable {
                            onCopy(label)
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = MaterialTheme.shapes.small,
            containerColor = if (isCopyable) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                if (isCopyable) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(Strings.content_desc_copy),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

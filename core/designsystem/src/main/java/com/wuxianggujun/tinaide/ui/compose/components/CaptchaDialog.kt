package com.wuxianggujun.tinaide.ui.compose.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 验证码对话框
 *
 * @param showDialog 是否显示对话框
 * @param captchaImage Base64 编码的验证码图片
 * @param captchaCode 验证码输入值
 * @param onCaptchaCodeChange 验证码输入变化回调
 * @param onRefresh 刷新验证码回调
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 * @param isLoading 是否正在加载
 * @param title 对话框标题
 */
@Composable
fun CaptchaDialog(
    showDialog: Boolean,
    captchaImage: String?,
    captchaCode: String,
    onCaptchaCodeChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    title: String? = null
) {
    if (showDialog) {
        val resolvedTitle = title ?: stringResource(Strings.captcha_title_default)
        TinaCustomDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            TinaCustomDialogScaffold(
                header = {
                    TinaCustomDialogHeader(title = resolvedTitle)
                },
                footer = {
                    TinaDialogActionRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TinaTextButton(
                            text = stringResource(Strings.btn_cancel),
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        )

                        TinaPrimaryButton(
                            text = stringResource(Strings.btn_confirm),
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && captchaCode.length >= 4
                        )
                    }
                }
            ) {
                TinaDialogContentColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TinaDialogCard(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // 验证码图片
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = CaptchaUiDefaults.dialogImageMaxWidth)
                                .aspectRatio(CaptchaUiDefaults.imageAspectRatio)
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isLoading) { onRefresh() },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLoading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                                captchaImage != null -> {
                                    CaptchaImage(captchaImage)
                                }
                                else -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(Strings.captcha_load),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(Strings.captcha_click_to_load),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = captchaCode,
                        onValueChange = onCaptchaCodeChange,
                        label = { Text(stringResource(Strings.captcha_label)) },
                        placeholder = { Text(stringResource(Strings.captcha_placeholder)) },
                        enabled = !isLoading,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptchaImage(base64Image: String) {
    val bitmap = remember(base64Image) {
        try {
            val base64Data = if (base64Image.contains(",")) {
                base64Image.substringAfter(",")
            } else {
                base64Image
            }

            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(Strings.captcha_content_desc),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(
            text = stringResource(Strings.captcha_load_failed_retry),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

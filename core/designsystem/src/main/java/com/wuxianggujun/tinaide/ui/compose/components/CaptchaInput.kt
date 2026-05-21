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
 * 验证码输入组件
 *
 * @param captchaImage Base64 编码的验证码图片（data:image/png;base64,xxx）
 * @param captchaCode 验证码输入值
 * @param onCaptchaCodeChange 验证码输入变化回调
 * @param onRefresh 刷新验证码回调
 * @param isLoading 是否正在加载
 * @param enabled 是否启用
 * @param modifier Modifier
 */
@Composable
fun CaptchaInput(
    captchaImage: String?,
    captchaCode: String,
    onCaptchaCodeChange: (String) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 验证码输入框
        OutlinedTextField(
            value = captchaCode,
            onValueChange = onCaptchaCodeChange,
            label = { Text(stringResource(Strings.captcha_label)) },
            placeholder = { Text(stringResource(Strings.captcha_placeholder)) },
            enabled = enabled && !isLoading,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        // 验证码图片
        Box(
            modifier = Modifier
                .width(CaptchaUiDefaults.inlineImageWidth)
                .height(CaptchaUiDefaults.inlineImageHeight)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled && !isLoading) { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                captchaImage != null -> {
                    CaptchaImage(captchaImage)
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Strings.captcha_load),
                        tint = MaterialTheme.colorScheme.primary
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
            // 移除 data:image/png;base64, 前缀
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
            text = stringResource(Strings.captcha_load_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

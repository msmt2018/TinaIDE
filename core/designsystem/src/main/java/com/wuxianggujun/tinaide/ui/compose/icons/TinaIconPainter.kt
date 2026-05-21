package com.wuxianggujun.tinaide.ui.compose.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * 统一的 Compose Drawable 加载入口。
 *
 * 这里不走 Compose 自带的 painterResource(XML vector) 解析链，
 * 统一通过 AppCompat 先加载 Drawable，再转成 BitmapPainter，
 * 避免 Release 包在 Android 15/16 上解析部分 XML vector 时直接崩溃。
 */
@Composable
fun rememberTinaPainter(@DrawableRes resId: Int): Painter {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return remember(context, configuration, density.density, resId) {
        val drawable = requireNotNull(context.getDrawable(resId)) {
            "Drawable resource not found: $resId"
        }.mutate()

        val fallbackSize = (24 * density.density).roundToInt().coerceAtLeast(1)
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: fallbackSize
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: fallbackSize
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)

        BitmapPainter(
            image = bitmap.asImageBitmap()
        )
    }
}

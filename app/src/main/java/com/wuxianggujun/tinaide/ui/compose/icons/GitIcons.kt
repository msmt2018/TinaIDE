package com.wuxianggujun.tinaide.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Git 相关自定义图标
 */
object GitIcons {

    /**
     * Diff 图标 - 两个文档对比样式
     * 左边文档带减号（删除），右边文档带加号（新增）
     */
    val Diff: ImageVector
        get() = ImageVector.Builder(
            name = "Diff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 左边文档（带减号表示删除）
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                // 左文档轮廓
                moveTo(3f, 3f)
                lineTo(10f, 3f)
                lineTo(10f, 17f)
                lineTo(3f, 17f)
                close()
                // 镂空内部
                moveTo(4f, 4f)
                lineTo(4f, 16f)
                lineTo(9f, 16f)
                lineTo(9f, 4f)
                close()
            }
            // 左文档减号（红色删除线）
            path(
                fill = SolidColor(Color(0xFFE06C75)),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(5f, 9.5f)
                lineTo(8f, 9.5f)
                lineTo(8f, 10.5f)
                lineTo(5f, 10.5f)
                close()
            }
            // 右边文档（带加号表示新增）
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                // 右文档轮廓
                moveTo(14f, 7f)
                lineTo(21f, 7f)
                lineTo(21f, 21f)
                lineTo(14f, 21f)
                close()
                // 镂空内部
                moveTo(15f, 8f)
                lineTo(15f, 20f)
                lineTo(20f, 20f)
                lineTo(20f, 8f)
                close()
            }
            // 右文档加号（绿色新增）
            path(
                fill = SolidColor(Color(0xFF98C379)),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                // 横线
                moveTo(16f, 13.5f)
                lineTo(19f, 13.5f)
                lineTo(19f, 14.5f)
                lineTo(16f, 14.5f)
                close()
                // 竖线
                moveTo(17f, 12.5f)
                lineTo(18f, 12.5f)
                lineTo(18f, 15.5f)
                lineTo(17f, 15.5f)
                close()
            }
            // 中间箭头（表示对比）
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.6f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(11f, 9f)
                lineTo(13f, 11f)
                lineTo(11f, 13f)
                lineTo(11f, 12f)
                lineTo(12f, 11f)
                lineTo(11f, 10f)
                close()
            }
        }.build()
}

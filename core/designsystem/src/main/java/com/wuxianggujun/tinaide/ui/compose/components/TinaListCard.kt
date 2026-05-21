package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 通用列表卡片组件
 *
 * 用于插件市场、已安装插件、包管理器等列表页面的统一卡片样式
 *
 * @param title 主标题
 * @param subtitle 副标题（可选）
 * @param icon 左侧图标 Composable（可选，默认使用首字母占位符）
 * @param iconText 图标占位符文本（当 icon 为 null 时使用）
 * @param metadata 元数据行（标签、版本号等）
 * @param actions 右侧操作区域（按钮、进度条等）
 * @param onClick 点击事件
 * @param onLongClick 长按事件（可选）
 * @param modifier Modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TinaListCard(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    iconText: String = title,
    metadata: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(TinaShapes.CardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TinaSpacing.xl),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧图标
            if (icon != null) {
                icon()
            } else {
                // 默认首字母占位符
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(TinaShapes.SmallCorner))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconText.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(TinaSpacing.lg))

            // 内容区域
            Column(modifier = Modifier.weight(1f)) {
                // 标题
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 副标题
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 元数据行（标签、版本等）
                metadata?.let {
                    Spacer(modifier = Modifier.height(TinaSpacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TinaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        content = it
                    )
                }
            }

            // 右侧操作区域
            actions?.let {
                Spacer(modifier = Modifier.width(TinaSpacing.md))
                actions()
            }
        }
    }
}

/**
 * 列表卡片标签组件
 *
 * 用于显示状态、类型等标签信息
 *
 * @param text 标签文本
 * @param containerColor 背景色
 * @param contentColor 文字颜色
 */
@Composable
fun TinaListCardBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = modifier
            .background(
                containerColor,
                RoundedCornerShape(TinaShapes.ExtraSmallCorner)
            )
            .padding(horizontal = TinaSpacing.sm, vertical = TinaSpacing.xxs)
    )
}

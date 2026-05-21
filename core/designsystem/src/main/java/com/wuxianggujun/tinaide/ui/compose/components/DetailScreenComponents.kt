package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 详情页通用组件
 *
 * 用于统一插件市场、已安装插件、包管理器等详情页的 UI 风格
 */

/**
 * 详情页图标占位符
 *
 * @param text 显示的文本（通常是首字母）
 * @param size 图标大小（默认 80.dp）
 */
@Composable
fun DetailIconPlaceholder(
    text: String,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(TinaShapes.CardCorner))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 详情页信息卡片
 *
 * 使用 TinaCard 作为底层实现，保持设计系统一致性
 *
 * @param title 卡片标题
 * @param content 卡片内容（Composable）
 */
@Composable
fun DetailInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    TinaCard(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(TinaSpacing.xl)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(TinaSpacing.md))
            content()
        }
    }
}

/**
 * 详情页头部卡片
 *
 * 使用 TinaCard 作为底层实现，保持设计系统一致性
 *
 * 包含图标、标题、副标题和操作按钮的标准头部布局
 *
 * @param icon 图标 Composable
 * @param title 主标题
 * @param subtitle 副标题（可选）
 * @param metadata 元数据行（可选，如下载量、评分等）
 * @param actions 操作按钮区域（可选）
 */
@Composable
fun DetailHeaderCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    metadata: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TinaCard(
        modifier = modifier.fillMaxWidth(),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(TinaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            icon()

            Spacer(modifier = Modifier.height(TinaSpacing.xl))

            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // 副标题
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 元数据
            metadata?.let {
                Spacer(modifier = Modifier.height(TinaSpacing.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TinaSpacing.xl),
                    content = it
                )
            }

            // 操作按钮
            actions?.let {
                Spacer(modifier = Modifier.height(TinaSpacing.xl))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = it
                )
            }
        }
    }
}

/**
 * 详情页元数据项
 *
 * 用于显示图标+文本的元数据（如下载量、评分等）
 *
 * @param icon 图标
 * @param text 文本
 */
@Composable
fun RowScope.DetailMetadataItem(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(16.dp)) {
            icon()
        }
        Spacer(modifier = Modifier.width(TinaSpacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

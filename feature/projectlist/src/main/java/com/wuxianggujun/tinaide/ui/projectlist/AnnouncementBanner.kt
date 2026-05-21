package com.wuxianggujun.tinaide.ui.projectlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 公告横幅组件
 * 
 * 显示在搜索框上方的可关闭公告卡片
 * 
 * @param announcement 公告数据
 * @param onDismiss 关闭回调
 * @param onAction 点击操作按钮回调
 * @param modifier Modifier
 */
@Composable
fun AnnouncementBanner(
    announcement: Announcement,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, iconTint, icon) = when (announcement.type) {
        AnnouncementType.NEW_RELEASE -> Triple(
            Color(0xFFE8F5E9), // 浅绿色背景
            Color(0xFF34A853), // 绿色图标
            Icons.Outlined.NewReleases
        )
        AnnouncementType.INFO -> Triple(
            Color(0xFFE3F2FD), // 浅蓝色背景
            Color(0xFF4285F4), // 蓝色图标
            Icons.Outlined.Info
        )
        AnnouncementType.IMPORTANT -> Triple(
            Color(0xFFFFF3E0), // 浅橙色背景
            Color(0xFFFF9800), // 橙色图标
            Icons.Outlined.Campaign
        )
        AnnouncementType.WARNING -> Triple(
            Color(0xFFFFEBEE), // 浅红色背景
            Color(0xFFEA4335), // 红色图标
            Icons.Outlined.Warning
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onAction != null) { onAction?.invoke() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!announcement.content.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = announcement.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 操作按钮或关闭按钮
            if (announcement.actionText != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = announcement.actionText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = iconTint
                    )
                }
            }

            // 关闭按钮
            if (announcement.dismissible) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Strings.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 带动画的公告横幅
 * 
 * @param announcement 公告数据，为 null 时隐藏
 * @param onDismiss 关闭回调
 * @param onAction 点击操作按钮回调
 * @param modifier Modifier
 */
@Composable
fun AnimatedAnnouncementBanner(
    announcement: Announcement?,
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = announcement != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        announcement?.let {
            AnnouncementBanner(
                announcement = it,
                onDismiss = onDismiss,
                onAction = onAction
            )
        }
    }
}


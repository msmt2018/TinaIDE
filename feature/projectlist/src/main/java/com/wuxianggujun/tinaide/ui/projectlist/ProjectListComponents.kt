package com.wuxianggujun.tinaide.ui.projectlist

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.storage.ProjectPaths
import java.io.File
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.ui.compose.components.TinaSemanticColors

/**
 * 顶部标题栏：TinaIDE Logo + 应用名称 + 通知图标 + 设置按钮
 *
 * @param onSettings 点击设置按钮的回调
 * @param onNotification 点击通知图标的回调
 * @param hasUnreadNotification 是否有未读通知（显示红点）
 */
@Composable
fun TopHeader(
    onSettings: () -> Unit,
    onNotification: () -> Unit = {},
    hasUnreadNotification: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // TinaIDE Logo - 蓝色圆角方块 + T 字母
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TinaSemanticColors.Project.logoBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "TinaIDE",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        // 右侧按钮组：通知 + 设置
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 通知按钮（带红点指示器）
            Box {
                IconButton(onClick = onNotification) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = stringResource(Strings.content_desc_notifications),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 未读通知红点
                if (hasUnreadNotification) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TinaSemanticColors.Project.unreadDot)
                    )
                }
            }
            
            // 设置按钮
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Strings.content_desc_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 搜索框组件
 */
@Composable
fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 快捷操作卡片：新建项目 + 从 Git 导入
 */
@Composable
fun QuickActionCards(
    onNewProject: () -> Unit,
    onImportFromGit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.CreateNewFolder,
            iconBackgroundColor = TinaSemanticColors.Project.quickActionBlueBg,
            iconTint = TinaSemanticColors.Project.quickActionBlueIcon,
            title = stringResource(Strings.action_new_project),
            subtitle = stringResource(Strings.subtitle_local_storage),
            onClick = onNewProject
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.CloudDownload,
            iconBackgroundColor = TinaSemanticColors.Project.quickActionGreenBg,
            iconTint = TinaSemanticColors.Project.quickActionGreenIcon,
            title = stringResource(Strings.action_import_from_git),
            subtitle = stringResource(Strings.subtitle_git_platforms),
            onClick = onImportFromGit
        )
    }
}

/**
 * 单个快捷操作卡片
 */
@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 图标背景
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 区域标题
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Medium
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * 项目标签芯片 —— 支持 Git / CMake / Makefile / 多语言标签
 */
@Composable
fun ProjectTagChip(tag: ProjectTag) {
    val context = LocalContext.current
    val (backgroundColor, textColor) = when (tag) {
        ProjectTag.PUBLIC_SOURCE -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        ProjectTag.PRIVATE_SOURCE -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        ProjectTag.GIT -> Pair(TinaSemanticColors.Language.gitBg, TinaSemanticColors.Language.gitText)
        ProjectTag.CMAKE -> Pair(TinaSemanticColors.Language.cmakeBg, TinaSemanticColors.Language.cmakeText)
        ProjectTag.MAKEFILE -> Pair(TinaSemanticColors.Language.makefileBg, TinaSemanticColors.Language.makefileText)
        ProjectTag.PLUGIN -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ProjectTag.C_CPP -> Pair(TinaSemanticColors.Language.cppBg, TinaSemanticColors.Language.cppText)
        ProjectTag.JAVA -> Pair(TinaSemanticColors.Language.javaBg, TinaSemanticColors.Language.javaText)
        ProjectTag.KOTLIN -> Pair(TinaSemanticColors.Language.kotlinBg, TinaSemanticColors.Language.kotlinText)
        ProjectTag.PYTHON -> Pair(TinaSemanticColors.Language.pythonBg, TinaSemanticColors.Language.pythonText)
        ProjectTag.RUST -> Pair(TinaSemanticColors.Language.rustBg, TinaSemanticColors.Language.rustText)
        ProjectTag.GO -> Pair(TinaSemanticColors.Language.goBg, TinaSemanticColors.Language.goText)
        ProjectTag.JAVASCRIPT -> Pair(TinaSemanticColors.Language.jsBg, TinaSemanticColors.Language.jsText)
        ProjectTag.TYPESCRIPT -> Pair(TinaSemanticColors.Language.tsBg, TinaSemanticColors.Language.tsText)
        ProjectTag.SHELL -> Pair(TinaSemanticColors.Language.shellBg, TinaSemanticColors.Language.shellText)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = tag.getDisplayName(context),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = textColor
        )
    }
}

/**
 * 空项目视图 - 美化版
 * 使用文件夹图标和更友好的提示
 */
@Composable
fun EmptyProjectsView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标容器 - 带背景的圆形
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            // 标题
            Text(
                text = stringResource(Strings.empty_no_projects),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // 提示文字
            Text(
                text = stringResource(Strings.empty_create_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 添加一个指向 FAB 的小提示
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TinaSemanticColors.Project.fabHint
                )
                Text(
                    text = stringResource(Strings.empty_fab_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TinaSemanticColors.Project.fabHint
                )
            }
        }
    }
}

/**
 * 信息行组件
 */
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp)
        )
    }
}

// ===== 工具函数 =====

/**
 * 格式化路径显示
 */
fun formatPathForDisplay(basePath: String, projectName: String, context: android.content.Context): String {
    val internalPath = ProjectPaths.getPrivateProjectsRootPath(context)
    val displayBase = if (File(basePath).absolutePath == internalPath) "projects" else basePath
    return if (projectName.isNotEmpty()) "$displayBase/$projectName" else displayBase
}

// simplifyPath moved to core:common (PathDisplayUtils.kt)

/**
 * 计算目录大小
 */
fun calculateDirectorySize(dir: File): Long {
    var size: Long = 0
    dir.walkTopDown().forEach { file ->
        if (file.isFile) {
            size += file.length()
        }
    }
    return size
}

/**
 * 统计文件数量
 */
fun countFiles(dir: File): Int {
    return dir.walkTopDown().count { it.isFile }
}

/**
 * 格式化文件大小
 */
fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * 从 Git URL 中提取项目名称
 */
fun extractProjectNameFromUrl(url: String): String {
    if (url.isBlank()) return ""
    
    // 移除末尾的 .git
    val cleanUrl = url.trim().removeSuffix(".git").removeSuffix("/")
    
    // 提取最后一个路径段作为项目名称
    val lastSegment = cleanUrl.substringAfterLast("/")
    
    // 过滤非法字符
    return lastSegment.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
}

/**
 * 验证 Git URL 是否有效
 */
fun isValidGitUrl(url: String): Boolean {
    val trimmedUrl = url.trim()
    
    // 支持的 URL 格式：
    // - https://github.com/user/repo.git
    // - https://github.com/user/repo
    // - git@github.com:user/repo.git
    // - ssh://git@github.com/user/repo.git
    
    return trimmedUrl.startsWith("https://") ||
           trimmedUrl.startsWith("http://") ||
           trimmedUrl.startsWith("git@") ||
           trimmedUrl.startsWith("ssh://")
}


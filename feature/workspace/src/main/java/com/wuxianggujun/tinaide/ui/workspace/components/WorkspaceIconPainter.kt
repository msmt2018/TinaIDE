package com.wuxianggujun.tinaide.ui.workspace.components

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.ui.compose.icons.rememberTinaPainter

/**
 * Workspace 模块统一的图标加载入口。
 *
 * 已知会在 Release 中触发 Compose XML Vector 解析链的图标，统一改走代码 ImageVector；
 * 其他资源继续使用原本的 painterResource 兜底，避免大范围改动影响现有界面。
 */
@Composable
internal fun rememberWorkspacePainter(@DrawableRes iconRes: Int): Painter {
    val vector = workspaceImageVectorOrNull(iconRes)
    return if (vector != null) {
        rememberVectorPainter(vector)
    } else {
        rememberTinaPainter(iconRes)
    }
}

private fun workspaceImageVectorOrNull(@DrawableRes iconRes: Int): ImageVector? = when (iconRes) {
    Drawables.ic_arrow_back -> Icons.AutoMirrored.Filled.ArrowBack
    Drawables.ic_arrow_forward -> Icons.AutoMirrored.Filled.ArrowForward
    Drawables.ic_arrow_down -> Icons.Default.ExpandMore
    Drawables.ic_build -> Icons.Default.Build
    Drawables.ic_check -> Icons.Default.Check
    Drawables.ic_check_circle -> Icons.Default.CheckCircle
    Drawables.ic_chevron_right -> Icons.Default.ChevronRight
    Drawables.ic_clock -> Icons.Default.Schedule
    Drawables.ic_close -> Icons.Default.Close
    Drawables.ic_download_circle -> Icons.Default.Download
    Drawables.ic_file_text -> Icons.Default.Description
    Drawables.ic_folder -> Icons.Default.Folder
    Drawables.ic_info_outline -> Icons.Default.Info
    Drawables.ic_lightbulb -> Icons.Default.Lightbulb
    Drawables.ic_linux_default -> Icons.Default.Computer
    Drawables.ic_menu_exit -> Icons.AutoMirrored.Filled.ExitToApp
    Drawables.ic_more_vert -> Icons.Default.MoreVert
    Drawables.ic_package -> Icons.Default.Archive
    Drawables.ic_pause -> Icons.Default.Pause
    Drawables.ic_search -> Icons.Default.Search
    Drawables.ic_settings -> Icons.Default.Settings
    Drawables.ic_sync -> Icons.Default.Sync
    Drawables.ic_upload -> Icons.Default.Upload
    Drawables.ic_warning_amber -> Icons.Default.WarningAmber
    else -> null
}

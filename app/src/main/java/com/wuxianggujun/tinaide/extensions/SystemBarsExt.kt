package com.wuxianggujun.tinaide.extensions

import android.app.Activity
import androidx.core.view.WindowCompat
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.ui.TerminalActivity

/**
 * - 浅色主题：深色图标
 * - 深色/灰色主题：浅色图标
 */
fun Activity.applyTinaSystemBars() {
    // TerminalActivity 的 UI 固定为深色（终端背景），即使全局主题是浅色也应使用浅色系统栏图标。
    val useDarkIcons = if (this is TerminalActivity) false else !Prefs.useDarkMode
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = useDarkIcons
        isAppearanceLightNavigationBars = useDarkIcons
    }
}

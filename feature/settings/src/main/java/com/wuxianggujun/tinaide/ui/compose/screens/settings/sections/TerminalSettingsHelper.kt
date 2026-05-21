package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.wuxianggujun.tinaide.core.terminal.BackendMode
import com.wuxianggujun.tinaide.core.terminal.ITerminalPreferences
import com.wuxianggujun.tinaide.core.terminal.ShellType
import com.wuxianggujun.tinaide.core.terminal.TerminalLocale

/**
 * 终端设置辅助工具
 *
 * 提供类型转换和常量访问，避免 feature:settings 直接依赖 feature:terminal 的具体类型。
 */
object TerminalSettingsHelper {

    // Shell 类型常量
    const val SHELL_TYPE_AUTO = "auto"
    const val SHELL_TYPE_SH = "sh"
    const val SHELL_TYPE_BASH = "bash"
    const val SHELL_TYPE_ZSH = "zsh"

    // 后端模式常量
    const val BACKEND_AUTO = "auto"
    const val BACKEND_PROOT = "proot"
    const val BACKEND_HOST = "host"

    // 字体类型常量
    const val FONT_TYPE_BUILTIN = "builtin"
    const val FONT_TYPE_SYSTEM = "system"
    const val FONT_TYPE_CUSTOM = "custom"

    // 字体大小常量
    val MIN_FONT_SIZE = ITerminalPreferences.MIN_FONT_SIZE
    val MAX_FONT_SIZE = ITerminalPreferences.MAX_FONT_SIZE

    // 光标闪烁常量
    val CURSOR_BLINK_RATE_MIN = ITerminalPreferences.CURSOR_BLINK_RATE_MIN
    val CURSOR_BLINK_RATE_MAX = ITerminalPreferences.CURSOR_BLINK_RATE_MAX

    /**
     * 从字符串值获取 ShellType 枚举
     */
    fun getShellTypeFromValue(value: String): ShellType = ShellType.fromValue(value)

    /**
     * 从字符串值获取 BackendMode 枚举
     */
    fun getBackendModeFromValue(value: String): BackendMode = BackendMode.fromValue(value)

    /**
     * 从字符串值获取 TerminalLocale 枚举
     */
    fun getTerminalLocaleFromValue(value: String): TerminalLocale = TerminalLocale.fromValue(value)

    /**
     * 获取所有 ShellType 枚举值
     */
    fun getAllShellTypes(): List<ShellType> = ShellType.entries

    /**
     * 获取所有 BackendMode 枚举值
     */
    fun getAllBackendModes(): List<BackendMode> = BackendMode.entries

    /**
     * 获取所有 TerminalLocales
     */
    fun getAllTerminalLocales(): List<TerminalLocale> = TerminalLocale.entries
}

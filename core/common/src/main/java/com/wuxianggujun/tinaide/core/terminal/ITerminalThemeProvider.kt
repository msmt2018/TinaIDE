package com.wuxianggujun.tinaide.core.terminal

/**
 * 终端主题数据类
 *
 * 用于在 core 层传递主题信息，避免依赖 feature:terminal 的具体实现。
 */
data class TerminalThemeData(
    val name: String,
    val defaultForeground: Int,
    val defaultBackground: Int,
    val cursorColor: Int,
    val ansiColors: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TerminalThemeData

        if (name != other.name) return false
        if (defaultForeground != other.defaultForeground) return false
        if (defaultBackground != other.defaultBackground) return false
        if (cursorColor != other.cursorColor) return false
        if (!ansiColors.contentEquals(other.ansiColors)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + defaultForeground
        result = 31 * result + defaultBackground
        result = 31 * result + cursorColor
        result = 31 * result + ansiColors.contentHashCode()
        return result
    }
}

/**
 * 终端主题提供者接口
 *
 * 抽象主题访问，避免 feature:settings 直接依赖 feature:terminal。
 */
interface ITerminalThemeProvider {
    /**
     * 获取所有可用主题
     */
    fun getAllThemes(): List<TerminalThemeData>

    /**
     * 根据名称获取主题
     */
    fun getThemeByName(name: String): TerminalThemeData
}

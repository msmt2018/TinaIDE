package com.wuxianggujun.tinaide.core.terminal

/**
 * 终端后端类型
 */
enum class TerminalBackendType {
    PROOT,
    HOST
}

/**
 * Shell 可用性信息
 */
data class ShellAvailabilityInfo(
    val backend: TerminalBackendType,
    val autoResolved: ShellType? = null,
    val availableShells: List<ShellType> = emptyList()
)

/**
 * Shell 解析器接口
 *
 * 抽象 Shell 检测和解析功能，避免 feature:settings 直接依赖 feature:terminal。
 */
interface IShellResolver {
    /**
     * 检查指定 Shell 类型是否可用
     * @param shellType Shell 类型值（如 "bash", "zsh"）
     */
    suspend fun isShellAvailable(shellType: String): Boolean

    /**
     * 探测当前可用的 Shell 和后端
     */
    suspend fun probeAvailability(): ShellAvailabilityInfo

    /**
     * 检查 PRoot 是否已安装
     */
    fun isPRootInstalled(): Boolean
}

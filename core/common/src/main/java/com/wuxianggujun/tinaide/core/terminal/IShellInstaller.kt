package com.wuxianggujun.tinaide.core.terminal

/**
 * Shell 安装结果
 */
sealed class ShellInstallResult {
    data object Success : ShellInstallResult()
    data class Error(val message: String) : ShellInstallResult()
}

/**
 * Shell 安装器接口
 *
 * 抽象 Shell（如 Zsh）安装功能，避免 feature:settings 直接依赖 feature:terminal。
 */
interface IShellInstaller {
    /**
     * 检查 Shell 是否已安装
     */
    suspend fun isInstalled(): Boolean

    /**
     * 安装 Shell
     * @param force 为 true 时即使已安装也强制重新安装
     * @param onProgress 进度回调
     */
    suspend fun install(
        force: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): ShellInstallResult
}

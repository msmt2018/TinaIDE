package com.wuxianggujun.tinaide.core.terminal

/**
 * Guest Linux 开发基础包安装结果
 */
sealed class GuestDevPackagesInstallResult {
    data object Success : GuestDevPackagesInstallResult()
    data class Error(val message: String) : GuestDevPackagesInstallResult()
}

/**
 * Guest Linux 开发基础包状态
 */
data class GuestDevPackagesCommandGroupStatus(
    val commands: List<String>,
    val available: Boolean,
)

data class GuestDevPackagesStatus(
    val installed: Boolean,
    val plannedPackages: List<String>,
    val commandGroupStatuses: List<GuestDevPackagesCommandGroupStatus>,
) {
    val missingCommandGroups: List<List<String>>
        get() = commandGroupStatuses.filterNot { it.available }.map { it.commands }
}

/**
 * Guest Linux 开发基础包安装器
 *
 * 用于在当前 active rootfs 中安装常用开发基础包，
 * 避免 feature:settings 直接依赖 feature:terminal 的具体实现。
 */
interface IGuestDevPackagesInstaller {
    /**
     * 检查并返回开发基础包状态
     */
    suspend fun inspectStatus(): GuestDevPackagesStatus

    /**
     * 检查开发基础包是否已安装
     */
    suspend fun isInstalled(): Boolean

    /**
     * 安装开发基础包
     * @param force 为 true 时即使已安装也强制重新安装
     * @param onProgress 进度回调
     */
    suspend fun install(
        force: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): GuestDevPackagesInstallResult
}

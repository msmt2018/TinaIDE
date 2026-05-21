package com.wuxianggujun.tinaide.core.terminal

/**
 * Locale 安装结果
 */
sealed class LocaleInstallResult {
    data object Success : LocaleInstallResult()
    data class Error(val message: String) : LocaleInstallResult()
}

/**
 * Locale 安装器接口
 *
 * 抽象语言包安装功能，避免 feature:settings 直接依赖 feature:terminal。
 */
interface ILocaleInstaller {
    /**
     * 检查是否需要安装语言包
     */
    suspend fun needsLocalePackage(locale: String): Boolean

    /**
     * 安装语言包
     * @param locale 语言环境（如 "zh_CN.UTF-8"）
     * @param force 为 true 时即使已安装也强制重新安装/重建
     * @param onProgress 进度回调
     */
    suspend fun installLocalePackage(
        locale: String,
        force: Boolean = false,
        onProgress: (String) -> Unit = {}
    ): LocaleInstallResult
}

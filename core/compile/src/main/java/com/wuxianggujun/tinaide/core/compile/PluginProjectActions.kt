package com.wuxianggujun.tinaide.core.compile

import java.io.File

/**
 * 插件项目开发动作。
 *
 * 由 app 层实现，避免 core:compile 直接依赖 core:plugin；编译入口只关心
 * “打包”和“安装到当前 IDE”这两个开发语义。
 */
interface PluginProjectActions {
    suspend fun build(projectRoot: File, buildDir: File): Result<PluginProjectActionResult>

    suspend fun install(projectRoot: File, buildDir: File): Result<PluginProjectActionResult>
}

data class PluginProjectActionResult(
    val packageFile: File,
    val pluginId: String,
    val pluginName: String,
    val pluginVersion: String,
    val installed: Boolean,
    val diagnostics: List<PluginProjectDiagnostic> = emptyList(),
) {
    val errorCount: Int
        get() = diagnostics.count { it.severity == PluginProjectDiagnosticSeverity.ERROR }

    val warningCount: Int
        get() = diagnostics.count { it.severity == PluginProjectDiagnosticSeverity.WARNING }
}

data class PluginProjectDiagnostic(
    val severity: PluginProjectDiagnosticSeverity,
    val message: String,
    val fixHint: String? = null,
)

enum class PluginProjectDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

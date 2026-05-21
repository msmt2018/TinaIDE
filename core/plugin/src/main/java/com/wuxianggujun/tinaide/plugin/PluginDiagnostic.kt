package com.wuxianggujun.tinaide.plugin

enum class PluginDiagnosticSeverity(val priority: Int) {
    INFO(0),
    WARNING(1),
    ERROR(2),
}

enum class PluginDiagnosticCategory {
    MANIFEST,
    PERMISSIONS,
    CONTRIBUTIONS,
    COMPATIBILITY,
    RUNTIME,
    LSP,
}

data class PluginDiagnosticIssue(
    val severity: PluginDiagnosticSeverity,
    val category: PluginDiagnosticCategory,
    val message: String,
    val fixHint: String? = null,
)

data class PluginHealthReport(
    val pluginId: String,
    val pluginName: String,
    val issues: List<PluginDiagnosticIssue>,
) {
    val isHealthy: Boolean
        get() = issues.isEmpty()

    val highestSeverity: PluginDiagnosticSeverity?
        get() = issues.maxByOrNull { it.severity.priority }?.severity
}

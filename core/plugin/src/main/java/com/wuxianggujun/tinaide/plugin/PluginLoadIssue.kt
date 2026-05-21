package com.wuxianggujun.tinaide.plugin

enum class PluginLoadIssueType {
    MISSING_MANIFEST,
    INVALID_MANIFEST,
}

data class PluginLoadIssue(
    val directoryName: String,
    val pluginId: String? = null,
    val pluginName: String? = null,
    val type: PluginLoadIssueType,
    val message: String,
)

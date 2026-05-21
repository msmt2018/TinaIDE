package com.wuxianggujun.tinaide.plugin

internal fun isSafePluginRelativePath(path: String): Boolean {
    if (path.isBlank()) return false
    val normalized = path.replace('\\', '/')
    if (normalized.startsWith("/")) return false
    if (normalized.contains("../")) return false
    return true
}

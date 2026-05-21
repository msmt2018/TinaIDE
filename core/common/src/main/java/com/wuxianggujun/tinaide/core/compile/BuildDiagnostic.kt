package com.wuxianggujun.tinaide.core.compile

/**
 * 构建诊断信息
 */
data class BuildDiagnostic(
    val file: String,
    val line: Int,
    val column: Int,
    val severity: Severity,
    val message: String
) {
    enum class Severity {
        ERROR,
        WARNING,
        NOTE
    }
}

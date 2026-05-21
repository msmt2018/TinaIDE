package com.wuxianggujun.tinaide.ai.tools.executor.diagnostics

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str

/**
 * 诊断工具执行器的回调接口
 */
interface DiagnosticsCallbacks {
    /**
     * 获取文件的诊断信息
     */
    fun getDiagnostics(request: DiagnosticsRequest): DiagnosticsResult

    /**
     * 获取项目的所有诊断信息
     */
    fun getAllDiagnostics(): DiagnosticsResult

    /**
     * 清除文件的诊断信息
     */
    fun clearDiagnostics(filePath: String): Boolean
}

/**
 * 诊断请求
 */
data class DiagnosticsRequest(
    val filePath: String? = null,
    val severity: DiagnosticSeverity? = null,
    val includeWarnings: Boolean = true,
    val includeInfo: Boolean = false
)

/**
 * 诊断结果
 */
data class DiagnosticsResult(
    val diagnostics: List<Diagnostic>,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val hintCount: Int
)

/**
 * 诊断信息
 */
data class Diagnostic(
    val filePath: String,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val severity: DiagnosticSeverity,
    val message: String,
    val code: String? = null,
    val source: String? = null
)

/**
 * 诊断严重程度
 */
enum class DiagnosticSeverity(@StringRes private val displayNameRes: Int) {
    ERROR(Strings.ai_diag_error),
    WARNING(Strings.ai_diag_warning),
    INFO(Strings.ai_diag_info),
    HINT(Strings.ai_diag_hint);

    val displayName: String
        get() = displayNameRes.str()
}

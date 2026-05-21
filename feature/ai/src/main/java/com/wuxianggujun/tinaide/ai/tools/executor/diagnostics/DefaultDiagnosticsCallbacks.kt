package com.wuxianggujun.tinaide.ai.tools.executor.diagnostics

/**
 * 默认的诊断回调实现
 * 提供基础的诊断功能实现
 */
class DefaultDiagnosticsCallbacks : DiagnosticsCallbacks {

    private val diagnosticsCache = mutableMapOf<String, List<Diagnostic>>()

    override fun getDiagnostics(request: DiagnosticsRequest): DiagnosticsResult {
        val allDiagnostics = if (request.filePath != null) {
            diagnosticsCache[request.filePath] ?: emptyList()
        } else {
            diagnosticsCache.values.flatten()
        }

        // Filter by severity
        val filtered = allDiagnostics.filter { diagnostic ->
            when {
                request.severity != null -> diagnostic.severity == request.severity
                !request.includeWarnings && diagnostic.severity == DiagnosticSeverity.WARNING -> false
                !request.includeInfo && (diagnostic.severity == DiagnosticSeverity.INFO || diagnostic.severity == DiagnosticSeverity.HINT) -> false
                else -> true
            }
        }

        return DiagnosticsResult(
            diagnostics = filtered,
            errorCount = filtered.count { it.severity == DiagnosticSeverity.ERROR },
            warningCount = filtered.count { it.severity == DiagnosticSeverity.WARNING },
            infoCount = filtered.count { it.severity == DiagnosticSeverity.INFO },
            hintCount = filtered.count { it.severity == DiagnosticSeverity.HINT }
        )
    }

    override fun getAllDiagnostics(): DiagnosticsResult {
        val allDiagnostics = diagnosticsCache.values.flatten()

        return DiagnosticsResult(
            diagnostics = allDiagnostics,
            errorCount = allDiagnostics.count { it.severity == DiagnosticSeverity.ERROR },
            warningCount = allDiagnostics.count { it.severity == DiagnosticSeverity.WARNING },
            infoCount = allDiagnostics.count { it.severity == DiagnosticSeverity.INFO },
            hintCount = allDiagnostics.count { it.severity == DiagnosticSeverity.HINT }
        )
    }

    override fun clearDiagnostics(filePath: String): Boolean = diagnosticsCache.remove(filePath) != null

    /**
     * 更新文件的诊断信息
     */
    fun updateDiagnostics(filePath: String, diagnostics: List<Diagnostic>) {
        if (diagnostics.isEmpty()) {
            diagnosticsCache.remove(filePath)
        } else {
            diagnosticsCache[filePath] = diagnostics
        }
    }

    /**
     * 清除所有诊断信息
     */
    fun clearAll() {
        diagnosticsCache.clear()
    }
}

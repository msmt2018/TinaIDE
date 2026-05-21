package com.wuxianggujun.tinaide.ai.integration

import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.*
import com.wuxianggujun.tinaide.core.lsp.Diagnostic as LspDiagnostic
import com.wuxianggujun.tinaide.ui.BottomPanelViewModel

/**
 * 诊断回调实现
 *
 * 提供代码诊断相关的回调功能
 * 集成 BottomPanelViewModel 的诊断信息管理
 */
class DiagnosticsCallbacksImpl(
    private val bottomPanelViewModel: BottomPanelViewModel,
    private val projectRoot: String
) : DiagnosticsCallbacks {

    override fun getDiagnostics(request: DiagnosticsRequest): DiagnosticsResult {
        // 从 BottomPanelViewModel 获取诊断信息
        val allDiagnostics = bottomPanelViewModel.diagnostics.value

        // 根据请求过滤
        val filtered = if (request.filePath != null) {
            // 规范化请求的文件路径
            val requestPath = PathUtils.normalizeFilePath(request.filePath!!)
            val requestRelativePath = PathUtils.toRelativePath(requestPath, projectRoot)

            allDiagnostics.filter { diagnostic ->
                // 规范化诊断信息中的文件路径并转换为相对路径
                val diagnosticPath = PathUtils.normalizeFilePath(diagnostic.fileUri)
                val diagnosticRelativePath = PathUtils.toRelativePath(diagnosticPath, projectRoot)
                diagnosticRelativePath == requestRelativePath
            }
        } else {
            allDiagnostics
        }.filter { diagnostic ->
            // 根据严重程度过滤
            when (convertSeverity(diagnostic.severity)) {
                DiagnosticSeverity.ERROR -> true
                DiagnosticSeverity.WARNING -> request.includeWarnings
                DiagnosticSeverity.INFO -> request.includeInfo
                DiagnosticSeverity.HINT -> request.includeInfo
            }
        }.filter { diagnostic ->
            // 根据指定的严重程度过滤
            request.severity == null || convertSeverity(diagnostic.severity) == request.severity
        }

        // 转换为 AI 工具的 Diagnostic 格式
        val converted = filtered.map { convertDiagnostic(it) }

        // 统计数量
        val errorCount = converted.count { it.severity == DiagnosticSeverity.ERROR }
        val warningCount = converted.count { it.severity == DiagnosticSeverity.WARNING }
        val infoCount = converted.count { it.severity == DiagnosticSeverity.INFO }
        val hintCount = converted.count { it.severity == DiagnosticSeverity.HINT }

        return DiagnosticsResult(
            diagnostics = converted,
            errorCount = errorCount,
            warningCount = warningCount,
            infoCount = infoCount,
            hintCount = hintCount
        )
    }

    override fun getAllDiagnostics(): DiagnosticsResult {
        // 获取所有文件的诊断信息
        return getDiagnostics(DiagnosticsRequest())
    }

    override fun clearDiagnostics(filePath: String): Boolean = try {
        // 规范化并转换为相对路径
        val normalizedPath = PathUtils.normalizeFilePath(filePath)
        val path = PathUtils.toAbsolutePath(normalizedPath, projectRoot)

        // 清除指定文件的诊断信息
        bottomPanelViewModel.replaceDiagnosticsForFile("file://$path", emptyList())
        true
    } catch (_: Exception) {
        false
    }

    /**
     * 转换 LSP 诊断信息到 AI 工具诊断信息
     */
    private fun convertDiagnostic(lspDiagnostic: LspDiagnostic): Diagnostic {
        // 规范化并转换为相对路径
        val normalizedPath = PathUtils.normalizeFilePath(lspDiagnostic.fileUri)
        val relativePath = PathUtils.toRelativePath(normalizedPath, projectRoot)

        return Diagnostic(
            filePath = relativePath,
            line = lspDiagnostic.line,
            column = lspDiagnostic.column,
            endLine = lspDiagnostic.line, // LSP Diagnostic 没有 endLine，使用相同的行
            endColumn = lspDiagnostic.column + 1, // 简单估计
            severity = convertSeverity(lspDiagnostic.severity),
            message = lspDiagnostic.message,
            code = null, // LSP Diagnostic 没有 code 字段
            source = null // LSP Diagnostic 没有 source 字段
        )
    }

    /**
     * 转换 LSP 严重程度到 AI 工具严重程度
     */
    private fun convertSeverity(lspSeverity: LspDiagnostic.Severity): DiagnosticSeverity = when (lspSeverity) {
        LspDiagnostic.Severity.ERROR -> DiagnosticSeverity.ERROR
        LspDiagnostic.Severity.WARNING -> DiagnosticSeverity.WARNING
        LspDiagnostic.Severity.INFO -> DiagnosticSeverity.INFO
        LspDiagnostic.Severity.HINT -> DiagnosticSeverity.HINT
    }
}

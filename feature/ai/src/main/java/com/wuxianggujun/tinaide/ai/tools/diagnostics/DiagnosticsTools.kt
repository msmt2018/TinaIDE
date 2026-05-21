package com.wuxianggujun.tinaide.ai.tools.diagnostics

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolParameterParser
import com.wuxianggujun.tinaide.ai.tools.appendLocalizedToolLine
import com.wuxianggujun.tinaide.ai.tools.executor.diagnostics.*
import com.wuxianggujun.tinaide.ai.tools.localizedToolText
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 获取诊断信息工具
 * 获取代码的错误、警告和提示信息
 */
object GetDiagnosticsTool : AiTool {
    override val name = "get_diagnostics"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_diagnostics,
            "Get code diagnostics (errors, warnings, hints) for a file or the entire project. Returns detailed information about syntax errors, type errors, linting issues, and code quality problems with line numbers and messages."
        )
    override val category = ToolCategory.DIAGNOSTICS

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "file_path",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_file_path_to_get_diagnostics_for_omit,
                                "The file path to get diagnostics for (omit for all files)"
                            )
                        )
                    }
                )
                put(
                    "severity",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_filter_by_severity_level,
                                "Filter by severity level"
                            )
                        )
                        put(
                            "enum",
                            buildJsonArray {
                                add(JsonPrimitive("error"))
                                add(JsonPrimitive("warning"))
                                add(JsonPrimitive("info"))
                                add(JsonPrimitive("hint"))
                            }
                        )
                    }
                )
                put(
                    "include_warnings",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_include_warnings_in_results,
                                "Include warnings in results"
                            )
                        )
                        put("default", true)
                    }
                )
                put(
                    "include_info",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_include_info_and_hints_in_results,
                                "Include info and hints in results"
                            )
                        )
                        put("default", false)
                    }
                )
            }
        )
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["diagnosticsCallbacks"] as? DiagnosticsCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_diagnostics_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val filePath = args["file_path"]
        val severityStr = args["severity"]
        val includeWarnings = ToolParameterParser.getBooleanParameter(args, "include_warnings", true)
        val includeInfo = ToolParameterParser.getBooleanParameter(args, "include_info", false)

        val severity = severityStr?.let {
            try {
                DiagnosticSeverity.valueOf(it.uppercase())
            } catch (e: Exception) {
                null
            }
        }

        val request = DiagnosticsRequest(
            filePath = filePath,
            severity = severity,
            includeWarnings = includeWarnings,
            includeInfo = includeInfo
        )

        return try {
            val result = callbacks.getDiagnostics(request)

            val metadata = mapOf(
                "errorCount" to result.errorCount,
                "warningCount" to result.warningCount,
                "infoCount" to result.infoCount,
                "hintCount" to result.hintCount,
                "totalCount" to result.diagnostics.size
            )

            if (result.diagnostics.isEmpty()) {
                return ToolExecutionResult.Success(Strings.ai_tool_success_no_diagnostics_found.str(), metadata)
            }

            val content = buildString {
                appendLocalizedToolLine(
                    Strings.ai_tool_output_found_diagnostics,
                    "Found %1\$s diagnostic(s):",
                    result.diagnostics.size
                )
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_errors_count, "Errors: %1\$s", result.errorCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_warnings_count, "Warnings: %1\$s", result.warningCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_info_count, "Info: %1\$s", result.infoCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_hints_count, "Hints: %1\$s", result.hintCount)
                appendLine()

                result.diagnostics.groupBy { it.filePath }.forEach { (file, diagnostics) ->
                    appendLine("$file:")
                    diagnostics.forEach { diag ->
                        val severityIcon = when (diag.severity) {
                            DiagnosticSeverity.ERROR -> "❌"
                            DiagnosticSeverity.WARNING -> "⚠️"
                            DiagnosticSeverity.INFO -> "ℹ️"
                            DiagnosticSeverity.HINT -> "💡"
                        }
                        append("  ")
                        appendLocalizedToolLine(
                            Strings.ai_tool_output_diagnostic_line,
                            "%1\$s Line %2\$s:%3\$s - %4\$s",
                            severityIcon,
                            diag.line,
                            diag.column,
                            diag.message
                        )
                        diag.code?.let {
                            append("     ")
                            appendLocalizedToolLine(Strings.ai_tool_output_code_label, "Code: %1\$s", it)
                        }
                    }
                    appendLine()
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_diagnostics.str(e.message))
        }
    }
}

/**
 * 获取所有诊断信息工具
 * 快速获取整个项目的所有诊断问题
 */
object GetAllDiagnosticsTool : AiTool {
    override val name = "get_all_diagnostics"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_all_diagnostics,
            "Get all diagnostics for the entire project. Returns a comprehensive list of all errors, warnings, and issues across all files. Useful for getting an overview of project health."
        )
    override val category = ToolCategory.DIAGNOSTICS

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["diagnosticsCallbacks"] as? DiagnosticsCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_diagnostics_callbacks_unavailable.str())

        return try {
            val result = callbacks.getAllDiagnostics()

            val metadata = mapOf(
                "errorCount" to result.errorCount,
                "warningCount" to result.warningCount,
                "infoCount" to result.infoCount,
                "hintCount" to result.hintCount,
                "totalCount" to result.diagnostics.size,
                "fileCount" to result.diagnostics.map { it.filePath }.distinct().size
            )

            if (result.diagnostics.isEmpty()) {
                return ToolExecutionResult.Success(Strings.ai_tool_success_project_clean.str(), metadata)
            }

            val content = buildString {
                appendLocalizedToolLine(
                    Strings.ai_tool_output_project_diagnostics_summary,
                    "Project Diagnostics Summary:"
                )
                append("  ")
                appendLocalizedToolLine(
                    Strings.ai_tool_output_total_issues,
                    "Total Issues: %1\$s",
                    result.diagnostics.size
                )
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_errors_count, "Errors: %1\$s", result.errorCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_warnings_count, "Warnings: %1\$s", result.warningCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_info_count, "Info: %1\$s", result.infoCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_hints_count, "Hints: %1\$s", result.hintCount)
                append("  ")
                appendLocalizedToolLine(
                    Strings.ai_tool_output_affected_files,
                    "Affected Files: %1\$s",
                    result.diagnostics.map { it.filePath }.distinct().size
                )
                appendLine()

                // Group by file and show top issues
                val byFile = result.diagnostics.groupBy { it.filePath }
                appendLocalizedToolLine(Strings.ai_tool_output_files_with_issues, "Files with Issues:")
                byFile.entries.take(10).forEach { (file, diagnostics) ->
                    val errors = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
                    val warnings = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
                    append("  ")
                    appendLocalizedToolLine(
                        Strings.ai_tool_output_file_issue_counts,
                        "%1\$s: %2\$s error(s), %3\$s warning(s)",
                        file,
                        errors,
                        warnings
                    )
                }

                if (byFile.size > 10) {
                    append("  ")
                    appendLocalizedToolLine(
                        Strings.ai_tool_output_more_files,
                        "... and %1\$s more file(s)",
                        byFile.size - 10
                    )
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_all_diagnostics.str(e.message))
        }
    }
}

/**
 * 清除诊断信息工具
 * 清除指定文件的诊断缓存
 */
object ClearDiagnosticsTool : AiTool {
    override val name = "clear_diagnostics"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_clear_diagnostics,
            "Clear cached diagnostics for a specific file. Use this to refresh diagnostic information after making code changes."
        )
    override val category = ToolCategory.DIAGNOSTICS

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "file_path",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_file_path_to_clear_diagnostics_for,
                                "The file path to clear diagnostics for"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("file_path"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["diagnosticsCallbacks"] as? DiagnosticsCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_diagnostics_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val filePath = ToolParameterParser.getStringParameter(args, "file_path")

        if (filePath.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_file_path_required.str())
        }

        return try {
            val success = callbacks.clearDiagnostics(filePath)
            if (success) {
                ToolExecutionResult.Success(Strings.ai_tool_success_diagnostics_cleared_for_file.str(filePath))
            } else {
                ToolExecutionResult.Error(Strings.ai_tool_error_failed_clear_diagnostics_for_file.str(filePath))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_clear_diagnostics.str(e.message))
        }
    }
}

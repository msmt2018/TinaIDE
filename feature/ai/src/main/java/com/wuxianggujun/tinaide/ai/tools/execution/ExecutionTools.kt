package com.wuxianggujun.tinaide.ai.tools.execution

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolParameterParser
import com.wuxianggujun.tinaide.ai.tools.appendLocalizedToolLine
import com.wuxianggujun.tinaide.ai.tools.executor.execution.*
import com.wuxianggujun.tinaide.ai.tools.localizedToolText
import com.wuxianggujun.tinaide.ai.tools.rethrowIfCancellation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 运行项目工具
 * 使用 CompileProjectUseCase 执行项目运行
 */
object RunProjectTool : AiTool {
    override val name = "run_project"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_run_project,
            "Run the C/C++ project using the current run configuration. Compiles the project if needed and executes the binary. Returns execution status, output, and any errors. Use this to test the application or verify changes work correctly."
        )
    override val category = ToolCategory.EXECUTION
    override val isDangerous = true

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "configuration",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_run_configuration_name_to_use_optional_uses,
                                "The run configuration name to use (optional, uses current selected config if not specified)"
                            )
                        )
                    }
                )
                put(
                    "arguments",
                    buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_command_line_arguments_passed_to_the_executable,
                                "Optional command-line arguments passed to the executable"
                            )
                        )
                    }
                )
                put(
                    "working_directory",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_working_directory_used_when_launching_the_executable,
                                "Optional working directory used when launching the executable"
                            )
                        )
                    }
                )
            }
        )
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val configuration = args["configuration"]?.trim()?.takeIf { it.isNotEmpty() }
        val arguments = ToolParameterParser.getStringListParameter(args, "arguments")
        val workingDirectory = args["working_directory"]?.trim()?.takeIf { it.isNotEmpty() }

        val request = RunRequest(
            configuration = configuration,
            arguments = arguments,
            workingDirectory = workingDirectory
        )

        return try {
            val result = callbacks.runProject(request)

            val metadata = mapOf(
                "executionId" to result.executionId,
                "exitCode" to result.exitCode,
                "duration" to result.duration,
                "status" to result.status.name
            )

            val content = buildString {
                when (result.status) {
                    ExecutionStatus.RUNNING -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_project_execution_started, "Project execution started")
                        if (configuration != null) {
                            appendLocalizedToolLine(Strings.ai_tool_output_configuration, "Configuration: %1\$s", configuration)
                        }
                        appendLocalizedToolLine(Strings.ai_tool_output_execution_id, "Execution ID: %1\$s", result.executionId)
                        appendLine()
                        appendLocalizedToolLine(Strings.ai_tool_output_project_compiling_then_run, "The project is being compiled and will run shortly.")
                        appendLocalizedToolLine(Strings.ai_tool_output_use_get_execution_status, "Use get_execution_status to check progress.")
                    }
                    ExecutionStatus.SUCCESS -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_execution_completed, "Execution completed successfully")
                        appendLocalizedToolLine(Strings.ai_tool_output_exit_code, "Exit Code: %1\$s", result.exitCode)
                        appendLocalizedToolLine(Strings.ai_tool_output_duration_ms, "Duration: %1\$sms", result.duration)
                        appendLine()
                        if (result.output.isNotBlank()) {
                            appendLocalizedToolLine(Strings.ai_tool_output_output_label, "Output:")
                            appendLine(result.output.take(5000))
                            if (result.output.length > 5000) {
                                appendLocalizedToolLine(Strings.ai_tool_output_truncated, "... (output truncated)")
                            }
                        }
                    }
                    ExecutionStatus.FAILED -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_execution_failed, "Execution failed")
                        appendLocalizedToolLine(Strings.ai_tool_output_exit_code, "Exit Code: %1\$s", result.exitCode)
                        appendLocalizedToolLine(Strings.ai_tool_output_duration_ms, "Duration: %1\$sms", result.duration)
                        appendLine()
                        if (result.errorOutput.isNotBlank()) {
                            appendLocalizedToolLine(Strings.ai_tool_output_error_label, "Error:")
                            appendLine(result.errorOutput.take(2000))
                        }
                    }
                    else -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_execution_status, "Execution status: %1\$s", result.status)
                        if (result.output.isNotBlank()) {
                            appendLine(result.output)
                        }
                    }
                }
            }

            if (result.success || result.status == ExecutionStatus.RUNNING) {
                ToolExecutionResult.Success(content, metadata)
            } else {
                ToolExecutionResult.Error(content)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_run_project.str(e.message))
        }
    }
}

/**
 * 构建项目工具
 * 使用 CompileProjectUseCase 编译项目
 */
object BuildProjectTool : AiTool {
    override val name = "build_project"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_build_project,
            "Build/compile the C/C++ project using CMake or Makefile. Compiles source files and generates the executable binary. Returns build output, success status, and any compilation errors. Use before running to ensure code compiles correctly."
        )
    override val category = ToolCategory.BUILD
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "clean",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_clean_build_artifacts_before_building_default_false,
                                "Clean build artifacts before building (default: false)"
                            )
                        )
                        put("default", false)
                    }
                )
                put(
                    "target",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_specific_build_target_name_optional_uses_current_selected,
                                "Specific build target name (optional, uses current selected config if not specified)"
                            )
                        )
                    }
                )
            }
        )
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val clean = ToolParameterParser.getBooleanParameter(args, "clean", false)
        val target = args["target"]?.trim()?.takeIf { it.isNotEmpty() }

        val request = BuildRequest(
            clean = clean,
            rebuild = false,
            target = target
        )

        return try {
            val result = callbacks.buildProject(request)

            val metadata = mapOf(
                "executionId" to result.executionId,
                "exitCode" to result.exitCode,
                "duration" to result.duration,
                "status" to result.status.name
            )

            val content = buildString {
                when (result.status) {
                    ExecutionStatus.RUNNING -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_build_started, "Build started")
                        if (clean) appendLocalizedToolLine(Strings.ai_tool_output_mode_clean_build, "Mode: Clean build")
                        target?.let { appendLocalizedToolLine(Strings.ai_tool_output_target, "Target: %1\$s", it) }
                        appendLocalizedToolLine(Strings.ai_tool_output_execution_id, "Execution ID: %1\$s", result.executionId)
                        appendLine()
                        appendLocalizedToolLine(Strings.ai_tool_output_project_compiling, "The project is being compiled.")
                        appendLocalizedToolLine(Strings.ai_tool_output_use_get_execution_status, "Use get_execution_status to check progress.")
                    }
                    ExecutionStatus.SUCCESS -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_build_completed, "Build completed successfully")
                        appendLocalizedToolLine(Strings.ai_tool_output_duration_ms, "Duration: %1\$sms", result.duration)
                        appendLine()
                        if (result.output.isNotBlank()) {
                            appendLocalizedToolLine(Strings.ai_tool_output_build_output_label, "Build Output:")
                            appendLine(result.output.take(5000))
                            if (result.output.length > 5000) {
                                appendLocalizedToolLine(Strings.ai_tool_output_truncated, "... (output truncated)")
                            }
                        }
                    }
                    ExecutionStatus.FAILED -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_build_failed, "Build failed")
                        appendLocalizedToolLine(Strings.ai_tool_output_duration_ms, "Duration: %1\$sms", result.duration)
                        appendLine()
                        if (result.errorOutput.isNotBlank()) {
                            appendLocalizedToolLine(Strings.ai_tool_output_build_errors_label, "Build Errors:")
                            appendLine(result.errorOutput.take(2000))
                        }
                    }
                    else -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_build_status, "Build status: %1\$s", result.status)
                        if (result.output.isNotBlank()) {
                            appendLine(result.output)
                        }
                    }
                }
            }

            if (result.success || result.status == ExecutionStatus.RUNNING) {
                ToolExecutionResult.Success(content, metadata)
            } else {
                ToolExecutionResult.Error(content)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_build_project.str(e.message))
        }
    }
}

/**
 * 停止执行工具
 * 停止正在运行的编译或执行任务
 */
object StopExecutionTool : AiTool {
    override val name = "stop_execution"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_stop_execution,
            "Stop a running execution (project run or build) by its execution ID. Terminates the process and cancels the operation. Use this to cancel long-running or stuck processes."
        )
    override val category = ToolCategory.EXECUTION
    override val isDangerous = true

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "execution_id",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_execution_id_to_stop_obtained_from_run,
                                "The execution ID to stop (obtained from run_project or build_project)"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("execution_id"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val executionId = ToolParameterParser.getStringParameter(args, "execution_id")

        if (executionId.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_execution_id_required.str())
        }

        return try {
            val success = callbacks.stopExecution(executionId)
            if (success) {
                ToolExecutionResult.Success(Strings.ai_tool_success_execution_stopped.str(executionId))
            } else {
                ToolExecutionResult.Error(Strings.ai_tool_error_failed_stop_execution_by_id.str(executionId))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_stop_execution.str(e.message))
        }
    }
}

/**
 * 获取执行状态工具
 * 查询执行任务的当前状态
 */
object GetExecutionStatusTool : AiTool {
    override val name = "get_execution_status"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_execution_status,
            "Get the current status of a running or completed execution by its ID. Returns status (pending, running, success, failed, cancelled, timeout) and helps track long-running operations."
        )
    override val category = ToolCategory.EXECUTION
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "execution_id",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_execution_id_to_check_obtained_from_run,
                                "The execution ID to check (obtained from run_project or build_project)"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("execution_id"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val executionId = ToolParameterParser.getStringParameter(args, "execution_id")

        if (executionId.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_execution_id_required.str())
        }

        return try {
            val status = callbacks.getExecutionStatus(executionId)
            if (status != null) {
                val statusIcon = when (status) {
                    ExecutionStatus.PENDING -> "⏳"
                    ExecutionStatus.RUNNING -> "▶️"
                    ExecutionStatus.SUCCESS -> "✅"
                    ExecutionStatus.FAILED -> "❌"
                    ExecutionStatus.CANCELLED -> "🚫"
                    ExecutionStatus.TIMEOUT -> "⏱️"
                }
                val content = buildString {
                    appendLocalizedToolLine(Strings.ai_tool_output_execution_status_with_icon, "%1\$s Execution Status: %2\$s", statusIcon, status.name)
                    appendLocalizedToolLine(Strings.ai_tool_output_execution_id, "Execution ID: %1\$s", executionId)
                    appendLine()
                    when (status) {
                        ExecutionStatus.PENDING -> appendLocalizedToolLine(Strings.ai_tool_output_execution_queued, "The execution is queued and waiting to start.")
                        ExecutionStatus.RUNNING -> appendLocalizedToolLine(Strings.ai_tool_output_execution_in_progress, "The execution is currently in progress.")
                        ExecutionStatus.SUCCESS -> appendLocalizedToolLine(Strings.ai_tool_output_execution_completed_status, "The execution completed successfully.")
                        ExecutionStatus.FAILED -> appendLocalizedToolLine(Strings.ai_tool_output_execution_failed_status, "The execution failed. Check the execution result for details.")
                        ExecutionStatus.CANCELLED -> appendLocalizedToolLine(Strings.ai_tool_output_execution_cancelled_status, "The execution was cancelled by user.")
                        ExecutionStatus.TIMEOUT -> appendLocalizedToolLine(Strings.ai_tool_output_execution_timeout_status, "The execution timed out.")
                    }
                }
                ToolExecutionResult.Success(content)
            } else {
                ToolExecutionResult.Error(Strings.ai_tool_error_execution_not_found.str(executionId))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_execution_status.str(e.message))
        }
    }
}

/**
 * 获取执行输出工具
 * 获取指定执行任务的输出内容
 */
object GetExecutionOutputTool : AiTool {
    override val name = "get_execution_output"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_execution_output,
            "Get the output and error output of a completed or running execution by its ID. Returns stdout, stderr, exit code, and current status. Use this to retrieve detailed execution results and diagnose issues."
        )
    override val category = ToolCategory.EXECUTION
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "execution_id",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_execution_id_to_get_output_from_obtained,
                                "The execution ID to get output from (obtained from run_project or build_project)"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("execution_id"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val executionId = ToolParameterParser.getStringParameter(args, "execution_id")

        if (executionId.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_execution_id_required.str())
        }

        return try {
            val result = callbacks.getExecutionOutput(executionId)
            if (result != null) {
                val metadata = mapOf(
                    "executionId" to result.executionId,
                    "status" to result.status.name,
                    "exitCode" to result.exitCode
                )

                val content = buildString {
                    appendLocalizedToolLine(Strings.ai_tool_output_execution_output_title, "Execution Output")
                    appendLocalizedToolLine(Strings.ai_tool_output_execution_id, "Execution ID: %1\$s", result.executionId)
                    appendLocalizedToolLine(Strings.ai_tool_output_status, "Status: %1\$s", result.status.name)
                    appendLocalizedToolLine(Strings.ai_tool_output_exit_code, "Exit Code: %1\$s", result.exitCode)
                    appendLine()

                    if (result.output.isNotBlank()) {
                        appendLocalizedToolLine(Strings.ai_tool_output_standard_output_label, "Standard Output:")
                        appendLine("─".repeat(50))
                        val truncatedOutput = if (result.output.length > 10000) {
                            result.output.take(10000) + "\n" +
                                localizedToolText(
                                    Strings.ai_tool_output_truncated_first_chars,
                                    "... (output truncated, showing first %1\$s characters)",
                                    10000
                                )
                        } else {
                            result.output
                        }
                        appendLine(truncatedOutput)
                        appendLine("─".repeat(50))
                        appendLine()
                    }

                    if (result.errorOutput.isNotBlank()) {
                        appendLocalizedToolLine(Strings.ai_tool_output_error_output_label, "Error Output:")
                        appendLine("─".repeat(50))
                        val truncatedError = if (result.errorOutput.length > 5000) {
                            result.errorOutput.take(5000) + "\n" +
                                localizedToolText(
                                    Strings.ai_tool_output_error_truncated_first_chars,
                                    "... (error output truncated, showing first %1\$s characters)",
                                    5000
                                )
                        } else {
                            result.errorOutput
                        }
                        appendLine(truncatedError)
                        appendLine("─".repeat(50))
                    }
                }

                ToolExecutionResult.Success(content, metadata)
            } else {
                ToolExecutionResult.Error(Strings.ai_tool_error_execution_output_not_found.str(executionId))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_execution_output.str(e.message))
        }
    }
}

/**
 * 获取构建错误工具
 * 获取当前项目的构建错误和警告信息
 */
object GetBuildErrorsTool : AiTool {
    override val name = "get_build_errors"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_build_errors,
            "Get all build errors and warnings from the current project or a specific build execution. Returns error count, warning count, and detailed error messages with file locations and line numbers. Use this to diagnose compilation issues and fix code problems."
        )
    override val category = ToolCategory.BUILD
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "execution_id",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_execution_id_to_get_errors_from_a,
                                "Optional execution ID to get errors from a specific build (obtained from build_project). If not provided, returns all current build errors."
                            )
                        )
                    }
                )
            }
        )
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        return try {
            val args = ToolParameterParser.parseArguments(toolCall)
            val executionId = args["execution_id"]

            val result = callbacks.getBuildErrors(executionId)

            val metadata = mapOf(
                "hasErrors" to result.hasErrors,
                "errorCount" to result.errorCount,
                "warningCount" to result.warningCount,
                "executionId" to (executionId ?: "current")
            )

            val content = buildString {
                if (result.hasErrors) {
                    appendLocalizedToolLine(Strings.ai_tool_output_build_errors_found, "❌ Build Errors Found")
                } else if (result.warningCount > 0) {
                    appendLocalizedToolLine(Strings.ai_tool_output_build_warnings_found, "⚠️ Build Warnings Found")
                } else {
                    appendLocalizedToolLine(Strings.ai_tool_output_no_build_errors_or_warnings, "✅ No Build Errors or Warnings")
                }

                if (executionId != null) {
                    appendLocalizedToolLine(Strings.ai_tool_output_execution_id, "Execution ID: %1\$s", executionId)
                }
                appendLine()
                appendLocalizedToolLine(Strings.ai_tool_output_summary_label, "Summary:")
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_errors_count, "Errors: %1\$s", result.errorCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_warnings_count, "Warnings: %1\$s", result.warningCount)
                appendLine()

                if (result.errors.isNotEmpty()) {
                    appendLocalizedToolLine(Strings.ai_tool_output_details_label, "Details:")
                    appendLine("─".repeat(70))

                    result.errors.take(50).forEach { error ->
                        val severityIcon = when (error.severity) {
                            ErrorSeverity.ERROR -> "❌"
                            ErrorSeverity.WARNING -> "⚠️"
                            ErrorSeverity.INFO -> "ℹ️"
                        }

                        append("$severityIcon ")
                        if (error.file != null) {
                            append("${error.file}")
                            if (error.line != null) {
                                append(":${error.line}")
                                if (error.column != null) {
                                    append(":${error.column}")
                                }
                            }
                            append(" - ")
                        }
                        appendLine(error.message)
                    }

                    if (result.errors.size > 50) {
                        appendLine()
                        appendLocalizedToolLine(
                            Strings.ai_tool_output_more_errors_warnings,
                            "... and %1\$s more errors/warnings",
                            result.errors.size - 50
                        )
                    }

                    appendLine("─".repeat(70))
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_build_errors.str(e.message))
        }
    }
}

/**
 * 跳转到运行输出工具
 * 在IDE中打开运行输出面板
 */
object NavigateToRunOutputTool : AiTool {
    override val name = "navigate_to_run_output"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_navigate_to_run_output,
            "Open and focus the run output panel in the IDE. Use this after running a project to show the user where to view the execution results and program output."
        )
    override val category = ToolCategory.EXECUTION
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        return try {
            callbacks.navigateToRunOutput()
            ToolExecutionResult.Success(Strings.ai_tool_success_navigated_run_output.str())
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_navigate_run_output.str(e.message))
        }
    }
}

/**
 * 跳转到构建日志工具
 * 在IDE中打开构建日志面板
 */
object NavigateToBuildLogTool : AiTool {
    override val name = "navigate_to_build_log"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_navigate_to_build_log,
            "Open and focus the build log panel in the IDE. Use this after building a project to show the user where to view compilation logs, errors, and warnings."
        )
    override val category = ToolCategory.BUILD
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["executionCallbacks"] as? ExecutionCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_execution_callbacks_unavailable.str())

        return try {
            callbacks.navigateToBuildLog()
            ToolExecutionResult.Success(Strings.ai_tool_success_navigated_build_log.str())
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_navigate_build_log.str(e.message))
        }
    }
}

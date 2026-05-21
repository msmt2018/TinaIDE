package com.wuxianggujun.tinaide.ai.tools.execution

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolParameterParser
import com.wuxianggujun.tinaide.ai.tools.appendLocalizedToolLine
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionStatus
import com.wuxianggujun.tinaide.ai.tools.executor.execution.TestRequest
import com.wuxianggujun.tinaide.ai.tools.localizedToolText
import com.wuxianggujun.tinaide.ai.tools.rethrowIfCancellation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 运行测试工具
 * 优先执行名为 test 的构建目标，其次运行包含 test 的可执行目标。
 */
object RunTestsTool : AiTool {
    override val name = "run_tests"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_run_tests,
            "Run tests for the current C/C++ project. Prefer a test build target such as CMake/Ninja 'test', or run an executable test target when available. Use this after code changes to verify behavior."
        )
    override val category = ToolCategory.EXECUTION
    override val isDangerous = true

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "target",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_test_target_name_for_cmake_make_this,
                                "Optional test target name. For CMake/Make this can be 'test' or a specific executable test target."
                            )
                        )
                    }
                )
                put(
                    "test_class",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_test_class_or_target_name_for_c,
                                "Optional test class or target name. For C/C++ projects this is treated as a target name."
                            )
                        )
                    }
                )
                put(
                    "test_method",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_test_method_or_filter_argument_passed_to,
                                "Optional test method or filter argument passed to executable test targets."
                            )
                        )
                    }
                )
                put(
                    "test_package",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_optional_test_package_or_target_group_for_c,
                                "Optional test package or target group. For C/C++ projects this can be treated as a target name."
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
                                Strings.ai_tool_param_desc_optional_arguments_passed_to_executable_test_targets,
                                "Optional arguments passed to executable test targets."
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
        val target = args["target"]?.trim()?.takeIf { it.isNotEmpty() }
        val request = TestRequest(
            testClass = args["test_class"]?.trim()?.takeIf { it.isNotEmpty() } ?: target,
            testMethod = args["test_method"]?.trim()?.takeIf { it.isNotEmpty() },
            testPackage = args["test_package"]?.trim()?.takeIf { it.isNotEmpty() },
            arguments = ToolParameterParser.getStringListParameter(args, "arguments")
        )

        return try {
            val result = callbacks.runTests(request)
            val metadata = mapOf(
                "executionId" to result.executionId,
                "exitCode" to result.exitCode,
                "duration" to result.duration,
                "status" to result.status.name
            )

            val content = buildString {
                when (result.status) {
                    ExecutionStatus.RUNNING -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_test_execution_started, "Test execution started")
                        target?.let { appendLocalizedToolLine(Strings.ai_tool_output_target, "Target: %1\$s", it) }
                        appendLocalizedToolLine(Strings.ai_tool_output_execution_id, "Execution ID: %1\$s", result.executionId)
                        appendLine()
                        appendLocalizedToolLine(Strings.ai_tool_output_test_target_preparing, "The test target is being prepared and will run shortly.")
                        appendLocalizedToolLine(Strings.ai_tool_output_use_get_execution_status, "Use get_execution_status to check progress.")
                    }
                    ExecutionStatus.SUCCESS -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_tests_completed, "Tests completed successfully")
                        appendLocalizedToolLine(Strings.ai_tool_output_exit_code, "Exit Code: %1\$s", result.exitCode)
                        appendLocalizedToolLine(Strings.ai_tool_output_duration_ms, "Duration: %1\$sms", result.duration)
                        if (result.output.isNotBlank()) {
                            appendLine()
                            appendLine(result.output.take(5000))
                        }
                    }
                    ExecutionStatus.FAILED -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_tests_failed, "Tests failed")
                        appendLocalizedToolLine(Strings.ai_tool_output_exit_code, "Exit Code: %1\$s", result.exitCode)
                        appendLocalizedToolLine(Strings.ai_tool_output_duration_ms, "Duration: %1\$sms", result.duration)
                        if (result.errorOutput.isNotBlank()) {
                            appendLine()
                            appendLine(result.errorOutput.take(2000))
                        }
                    }
                    else -> {
                        appendLocalizedToolLine(Strings.ai_tool_output_test_status, "Test status: %1\$s", result.status)
                        if (result.output.isNotBlank()) appendLine(result.output)
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
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_run_tests.str(e.message))
        }
    }
}

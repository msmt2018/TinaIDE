package com.wuxianggujun.tinaide.ai.integration

import com.wuxianggujun.tinaide.ai.tools.executor.execution.TestRequest
import com.wuxianggujun.tinaide.core.compile.CompileProjectUseCase
import com.wuxianggujun.tinaide.core.compile.OutputMode
import com.wuxianggujun.tinaide.core.compile.RunConfiguration
import com.wuxianggujun.tinaide.core.compile.TargetInfo

internal data class AiTestExecutionPlan(
    val operation: CompileProjectUseCase.Operation,
    val runConfig: RunConfiguration,
    val targetName: String?,
    val description: String
)

internal object AiTestExecutionPlanner {
    fun resolve(
        request: TestRequest,
        selectedConfig: RunConfiguration,
        targets: List<TargetInfo>
    ): AiTestExecutionPlan {
        val requestedTarget = listOfNotNull(
            request.testClass?.takeIf { it.isNotBlank() },
            request.testPackage?.takeIf { it.isNotBlank() }
        ).firstOrNull()
        val selectedTarget = selectTestTarget(requestedTarget, targets)
        val shouldRunExecutable = selectedTarget?.type == TargetInfo.Type.EXECUTABLE
        val args = buildTestArguments(request)
        val config = selectedConfig.copy(
            targetName = selectedTarget?.name.orEmpty(),
            outputMode = OutputMode.TERMINAL,
            args = args.joinToString(" ")
        ).normalized()

        return AiTestExecutionPlan(
            operation = if (shouldRunExecutable) {
                CompileProjectUseCase.Operation.forRun()
            } else {
                CompileProjectUseCase.Operation.forBuild()
            },
            runConfig = config,
            targetName = selectedTarget?.name,
            description = buildString {
                append("Test target: ${selectedTarget?.name ?: "current build target"}")
                if (shouldRunExecutable) append(" (executable)") else append(" (build target)")
                if (args.isNotEmpty()) append("\nArguments: ${args.joinToString(" ")}")
            }
        )
    }

    private fun selectTestTarget(requestedTarget: String?, targets: List<TargetInfo>): TargetInfo? {
        if (!requestedTarget.isNullOrBlank()) {
            targets.firstOrNull { it.name == requestedTarget }?.let { return it }
        }
        return targets.firstOrNull { it.name == "test" }
            ?: targets.firstOrNull { it.type == TargetInfo.Type.EXECUTABLE && it.name.contains("test", ignoreCase = true) }
            ?: requestedTarget?.let { TargetInfo(name = it, type = TargetInfo.Type.EXECUTABLE, sources = emptyList()) }
    }

    private fun buildTestArguments(request: TestRequest): List<String> = buildList {
        addAll(request.arguments.filter { it.isNotBlank() })
        request.testMethod?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
}

internal fun buildTestStartMessage(request: TestRequest): String = buildString {
    append("Test execution started")
    request.testClass?.let { append("\nTest class/target: $it") }
    request.testMethod?.let { append("\nTest method/filter: $it") }
    request.testPackage?.let { append("\nTest package/target: $it") }
    if (request.arguments.isNotEmpty()) {
        append("\nArguments: ${request.arguments.joinToString(" ")}")
    }
}

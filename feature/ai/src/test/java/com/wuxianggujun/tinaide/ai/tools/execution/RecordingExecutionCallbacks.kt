package com.wuxianggujun.tinaide.ai.tools.execution

import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildError
import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildErrorsResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildRequest
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ErrorSeverity
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionOutputResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionStatus
import com.wuxianggujun.tinaide.ai.tools.executor.execution.RunRequest
import com.wuxianggujun.tinaide.ai.tools.executor.execution.TestRequest

internal class RecordingExecutionCallbacks(
    private val executionResult: ExecutionResult = runningResult(),
    private val statusResult: ExecutionStatus? = executionResult.status,
    private val outputResult: ExecutionOutputResult? = ExecutionOutputResult(
        executionId = executionResult.executionId,
        output = executionResult.output,
        errorOutput = executionResult.errorOutput,
        status = executionResult.status,
        exitCode = executionResult.exitCode
    ),
    private val stopResult: Boolean = true,
    private val buildErrorsResult: BuildErrorsResult = emptyBuildErrorsResult()
) : ExecutionCallbacks {
    var lastRunRequest: RunRequest? = null
        private set
    var lastBuildRequest: BuildRequest? = null
        private set
    var lastTestRequest: TestRequest? = null
        private set
    var lastStatusExecutionId: String? = null
        private set
    var lastOutputExecutionId: String? = null
        private set
    var lastStoppedExecutionId: String? = null
        private set
    var lastBuildErrorsExecutionId: String? = null
        private set
    var didNavigateToRunOutput: Boolean = false
        private set
    var didNavigateToBuildLog: Boolean = false
        private set

    override fun runProject(request: RunRequest): ExecutionResult {
        lastRunRequest = request
        return executionResult
    }

    override fun runTests(request: TestRequest): ExecutionResult {
        lastTestRequest = request
        return executionResult
    }

    override fun buildProject(request: BuildRequest): ExecutionResult {
        lastBuildRequest = request
        return executionResult
    }

    override fun stopExecution(executionId: String): Boolean {
        lastStoppedExecutionId = executionId
        return stopResult
    }

    override fun getExecutionStatus(executionId: String): ExecutionStatus? {
        lastStatusExecutionId = executionId
        return statusResult
    }

    override fun getExecutionOutput(executionId: String): ExecutionOutputResult? {
        lastOutputExecutionId = executionId
        return outputResult?.copy(executionId = executionId)
    }

    override fun getBuildErrors(executionId: String?): BuildErrorsResult {
        lastBuildErrorsExecutionId = executionId
        return buildErrorsResult
    }

    override fun navigateToRunOutput() {
        didNavigateToRunOutput = true
    }

    override fun navigateToBuildLog() {
        didNavigateToBuildLog = true
    }

    companion object {
        fun runningResult(): ExecutionResult = ExecutionResult(
            executionId = "exec-1",
            success = true,
            exitCode = 0,
            output = "started",
            errorOutput = "",
            duration = 0,
            status = ExecutionStatus.RUNNING
        )

        fun emptyBuildErrorsResult(): BuildErrorsResult = BuildErrorsResult(
            hasErrors = false,
            errorCount = 0,
            warningCount = 0,
            errors = emptyList()
        )

        fun failedBuildErrorsResult(): BuildErrorsResult = BuildErrorsResult(
            hasErrors = true,
            errorCount = 1,
            warningCount = 1,
            errors = listOf(
                BuildError(
                    file = "src/main.cpp",
                    line = 12,
                    column = 5,
                    message = "missing semicolon",
                    severity = ErrorSeverity.ERROR
                ),
                BuildError(
                    file = "src/main.cpp",
                    line = 18,
                    column = null,
                    message = "unused variable",
                    severity = ErrorSeverity.WARNING
                )
            )
        )
    }
}

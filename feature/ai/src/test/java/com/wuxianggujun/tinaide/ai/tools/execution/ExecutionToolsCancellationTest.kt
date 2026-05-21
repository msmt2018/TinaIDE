package com.wuxianggujun.tinaide.ai.tools.execution

import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.assertCancellationRethrown
import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildErrorsResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.BuildRequest
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionOutputResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionResult
import com.wuxianggujun.tinaide.ai.tools.executor.execution.ExecutionStatus
import com.wuxianggujun.tinaide.ai.tools.executor.execution.RunRequest
import com.wuxianggujun.tinaide.ai.tools.executor.execution.TestRequest
import com.wuxianggujun.tinaide.ai.tools.toolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ExecutionToolsCancellationTest {

    @Test
    fun `execution tools rethrow cancellation exception`(): Unit = runBlocking {
        val context = ToolExecutionContext(additionalData = mapOf("executionCallbacks" to cancellingExecutionCallbacks()))

        assertCancellationRethrown {
            RunProjectTool.execute(toolCall(RunProjectTool.name), context)
        }
        assertCancellationRethrown {
            BuildProjectTool.execute(toolCall(BuildProjectTool.name), context)
        }
        assertCancellationRethrown {
            RunTestsTool.execute(toolCall(RunTestsTool.name, """{"target":"unit_tests"}"""), context)
        }
        assertCancellationRethrown {
            GetExecutionStatusTool.execute(toolCall(GetExecutionStatusTool.name, """{"execution_id":"exec-1"}"""), context)
        }
        assertCancellationRethrown {
            GetExecutionOutputTool.execute(toolCall(GetExecutionOutputTool.name, """{"execution_id":"exec-1"}"""), context)
        }
        assertCancellationRethrown {
            StopExecutionTool.execute(toolCall(StopExecutionTool.name, """{"execution_id":"exec-1"}"""), context)
        }
        assertCancellationRethrown {
            GetBuildErrorsTool.execute(toolCall(GetBuildErrorsTool.name), context)
        }
        assertCancellationRethrown {
            NavigateToRunOutputTool.execute(toolCall(NavigateToRunOutputTool.name), context)
        }
        assertCancellationRethrown {
            NavigateToBuildLogTool.execute(toolCall(NavigateToBuildLogTool.name), context)
        }
    }

    private fun cancellingExecutionCallbacks(): ExecutionCallbacks = object : ExecutionCallbacks {
        override fun runProject(request: RunRequest): ExecutionResult = throw CancellationException("cancelled")

        override fun runTests(request: TestRequest): ExecutionResult = throw CancellationException("cancelled")

        override fun buildProject(request: BuildRequest): ExecutionResult = throw CancellationException("cancelled")

        override fun stopExecution(executionId: String): Boolean = throw CancellationException("cancelled")

        override fun getExecutionStatus(executionId: String): ExecutionStatus? = throw CancellationException("cancelled")

        override fun getExecutionOutput(executionId: String): ExecutionOutputResult? = throw CancellationException("cancelled")

        override fun getBuildErrors(executionId: String?): BuildErrorsResult = throw CancellationException("cancelled")

        override fun navigateToRunOutput(): Unit = throw CancellationException("cancelled")

        override fun navigateToBuildLog(): Unit = throw CancellationException("cancelled")
    }
}

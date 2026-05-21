package com.wuxianggujun.tinaide.ai.tools.executor.execution

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultExecutionCallbacksTest {

    @Test
    fun `default run build and test callbacks create failed results with retrievable output`() {
        val callbacks = DefaultExecutionCallbacks()

        val run = callbacks.runProject(RunRequest(configuration = "Debug"))
        val tests = callbacks.runTests(TestRequest(testClass = "ExampleTest"))
        val build = callbacks.buildProject(BuildRequest(clean = true))

        listOf(run, tests, build).forEach { result ->
            assertThat(result.executionId).isNotEmpty()
            assertThat(result.success).isFalse()
            assertThat(result.status).isEqualTo(ExecutionStatus.FAILED)
            assertThat(callbacks.getExecutionStatus(result.executionId)).isEqualTo(ExecutionStatus.RUNNING)
            assertThat(callbacks.getExecutionOutput(result.executionId)?.exitCode).isEqualTo(-1)
        }
    }

    @Test
    fun `stop and clear execution status update lifecycle maps`() {
        val callbacks = DefaultExecutionCallbacks()
        val result = callbacks.runProject(RunRequest())

        assertThat(callbacks.stopExecution(result.executionId)).isTrue()
        assertThat(callbacks.getExecutionStatus(result.executionId)).isEqualTo(ExecutionStatus.CANCELLED)

        callbacks.clearExecutionStatus(result.executionId)

        assertThat(callbacks.getExecutionStatus(result.executionId)).isNull()
        assertThat(callbacks.getExecutionOutput(result.executionId)).isNull()
        assertThat(callbacks.stopExecution(result.executionId)).isFalse()
    }

    @Test
    fun `pending status can be stopped while terminal status cannot`() {
        val callbacks = DefaultExecutionCallbacks()
        val pendingId = "pending-execution"
        val failedId = "failed-execution"

        callbacks.updateExecutionStatus(pendingId, ExecutionStatus.PENDING)
        callbacks.updateExecutionStatus(failedId, ExecutionStatus.FAILED)

        assertThat(callbacks.stopExecution(pendingId)).isTrue()
        assertThat(callbacks.getExecutionStatus(pendingId)).isEqualTo(ExecutionStatus.CANCELLED)
        assertThat(callbacks.stopExecution(failedId)).isFalse()
        assertThat(callbacks.getExecutionStatus(failedId)).isEqualTo(ExecutionStatus.FAILED)
    }

    @Test
    fun `navigation callbacks are safe no ops`() {
        val callbacks = DefaultExecutionCallbacks()

        callbacks.navigateToRunOutput()
        callbacks.navigateToBuildLog()

        assertThat(callbacks.getExecutionStatus("missing")).isNull()
    }

    @Test
    fun `default build errors are empty`() {
        val callbacks = DefaultExecutionCallbacks()

        val result = callbacks.getBuildErrors()

        assertThat(result.hasErrors).isFalse()
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.warningCount).isEqualTo(0)
        assertThat(result.errors).isEmpty()
    }
}

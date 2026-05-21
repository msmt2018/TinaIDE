package com.wuxianggujun.tinaide.ai.retry

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ApiException
import java.net.SocketTimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryCoordinatorTest {

    private val policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 1_000L)

    @Test
    fun `schedules retry when policy allows`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val coordinator = RetryCoordinator(scope)
        var executed = 0

        val scheduled = coordinator.scheduleRetry(
            error = SocketTimeoutException("slow"),
            policy = policy,
            isStopped = { false },
            onProgress = { _, _ -> },
            onExecute = { executed++ },
        )

        assertThat(scheduled).isTrue()
        assertThat(coordinator.currentAttempt).isEqualTo(1)

        scope.advanceUntilIdle()
        assertThat(executed).isEqualTo(1)
    }

    @Test
    fun `returns false for non-retriable errors`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = RetryCoordinator(scope)

        val scheduled = coordinator.scheduleRetry(
            error = ApiException(code = 401, message = "unauth"),
            policy = policy,
            isStopped = { false },
            onProgress = { _, _ -> },
            onExecute = { /* unreachable */ },
        )

        assertThat(scheduled).isFalse()
        assertThat(coordinator.currentAttempt).isEqualTo(0)
    }

    @Test
    fun `does not execute when stop flag flips during delay`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val coordinator = RetryCoordinator(scope)
        var stopped = false
        var executed = 0

        coordinator.scheduleRetry(
            error = SocketTimeoutException("slow"),
            policy = policy,
            isStopped = { stopped },
            onProgress = { _, _ -> },
            onExecute = { executed++ },
        )

        // 进入 delay 循环后才翻 stopFlag,模拟用户按下"停止生成"。
        scope.advanceTimeBy(500)
        stopped = true
        scope.advanceUntilIdle()

        assertThat(executed).isEqualTo(0)
    }

    @Test
    fun `reset clears attempt counter`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = RetryCoordinator(scope)

        coordinator.scheduleRetry(
            error = SocketTimeoutException("slow"),
            policy = policy,
            isStopped = { false },
            onProgress = { _, _ -> },
            onExecute = { },
        )
        scope.advanceUntilIdle()
        assertThat(coordinator.currentAttempt).isEqualTo(1)

        coordinator.reset()
        assertThat(coordinator.currentAttempt).isEqualTo(0)
    }

    @Test
    fun `cancel drops pending delay job`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val coordinator = RetryCoordinator(scope)
        var executed = 0

        coordinator.scheduleRetry(
            error = SocketTimeoutException("slow"),
            policy = policy,
            isStopped = { false },
            onProgress = { _, _ -> },
            onExecute = { executed++ },
        )
        coordinator.cancel()
        scope.advanceUntilIdle()

        assertThat(executed).isEqualTo(0)
    }
}

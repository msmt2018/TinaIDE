package com.wuxianggujun.tinaide.ai.retry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 重试调度协调器:把"决策 + 延迟 + 执行"封装成一个可取消的状态机,
 * 让 `AiChatViewModel` 的 `onError` 分支不再内嵌 Job/delay 细节。
 *
 * 使用契约:
 * - [scheduleRetry] 命中白名单时立刻返回 true,并在后台 Job 中等待 `delayMs`,
 *   等待期间每秒检查 [isStopped];被中断则不再调用 [onExecute]。
 * - 正常回复完成后调用 [reset];停止生成调用 [cancel]。
 * - 本协调器不接管 `stopGeneration` 状态本身,只通过 [isStopped] lambda 读取。
 */
class RetryCoordinator(private val scope: CoroutineScope) {

    /** 已经尝试过的重试次数(不含首次请求)。传给 [RetryPolicy.decide]。 */
    var currentAttempt: Int = 0
        private set

    private var delayJob: Job? = null

    /**
     * @return true 表示决策为重试,已调度 Job;false 表示应走终局失败路径。
     */
    fun scheduleRetry(
        error: Throwable,
        policy: RetryPolicy,
        isStopped: () -> Boolean,
        onProgress: (attempt: Int, delayMs: Long) -> Unit,
        onExecute: suspend () -> Unit,
    ): Boolean {
        if (isStopped()) return false
        val decision = policy.decide(error, currentAttempt)
        if (decision !is RetryPolicy.Decision.Retry) return false

        currentAttempt++
        onProgress(currentAttempt, decision.delayMs)

        delayJob?.cancel()
        delayJob = scope.launch {
            var remaining = decision.delayMs
            while (remaining > 0 && !isStopped()) {
                val sleep = minOf(1000L, remaining)
                delay(sleep)
                remaining -= sleep
            }
            if (!isStopped()) onExecute()
        }
        return true
    }

    fun reset() {
        currentAttempt = 0
    }

    fun cancel() {
        delayJob?.cancel()
        delayJob = null
    }
}

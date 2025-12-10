package com.wuxianggujun.tinaide.editor.language.common

import android.os.SystemClock
import android.util.Log
import com.wuxianggujun.tinaide.lsp.model.CompletionResult
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Blocks until the dispatcher returns a completion result so the
 * completion thread stays alive when the user picks an item.
 */
internal object LspCompletionAwaiter {

    private const val DEFAULT_COMPLETION_TIMEOUT_MS = 2_500L
    private const val DISPATCHER_WAIT_MARGIN_MS = 350L
    private const val POLL_INTERVAL_MS = 25L

    fun awaitResult(
        key: String,
        logTag: String,
        timeoutOverrideMs: Long?,
        publisher: CompletionPublisher,
        registerRequest: (callback: (CompletionResult?) -> Unit) -> Unit
    ): CompletionResult? {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<CompletionResult?>()
        val consumeResult = AtomicBoolean(true)
        registerRequest { result ->
            if (!consumeResult.get()) {
                return@registerRequest
            }
            resultRef.set(result)
            latch.countDown()
        }

        val waitBudgetMs = (timeoutOverrideMs ?: DEFAULT_COMPLETION_TIMEOUT_MS) + DISPATCHER_WAIT_MARGIN_MS
        val deadline = SystemClock.elapsedRealtime() + waitBudgetMs
        while (true) {
            publisher.checkCancelled()
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                consumeResult.set(false)
                Log.w(logTag, "Completion dispatcher wait timed out for key=$key (timeout=${waitBudgetMs}ms)")
                return resultRef.get()
            }
            val waitSlice = min(remaining, POLL_INTERVAL_MS)
            try {
                if (latch.await(waitSlice, TimeUnit.MILLISECONDS)) {
                    return resultRef.get()
                }
            } catch (ie: InterruptedException) {
                consumeResult.set(false)
                Thread.currentThread().interrupt()
                throw CompletionCancelledException()
            }
        }
    }
}

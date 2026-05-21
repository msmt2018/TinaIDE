package com.wuxianggujun.tinaide.ai.retry

import com.wuxianggujun.tinaide.ai.api.ApiException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * AI 请求重试策略。
 *
 * 取代原来 `AiChatViewModel.shouldAutoRetry` 的"兜底 true"逻辑:
 * - 只对**确定值得重试**的情况放行 (白名单):
 *   - HTTP 429 (优先尊重 `Retry-After`)
 *   - HTTP 500 / 502 / 503 / 504 (网关/服务端偶发)
 *   - `SocketTimeoutException` / `ConnectException` / `UnknownHostException` / 其它 [IOException]
 * - 其它 4xx/5xx (401/403/404/422/501/505 等) **不**重试——
 *   客户端错误重试既无意义,还会放大错误曝光。
 */
class RetryPolicy(
    private val maxAttempts: Int,
    private val baseDelayMs: Long,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
) {

    sealed interface Decision {
        data object DoNotRetry : Decision
        data class Retry(val delayMs: Long) : Decision
    }

    /**
     * @param attempt 已经尝试过的次数 (从 0 开始)。当 attempt >= maxAttempts 时放弃。
     */
    fun decide(error: Throwable, attempt: Int): Decision {
        if (attempt >= maxAttempts) return Decision.DoNotRetry
        return when (error) {
            is ApiException -> decideForApiError(error)
            is SocketTimeoutException,
            is ConnectException,
            is UnknownHostException,
            -> Decision.Retry(baseDelayMs)
            is IOException -> Decision.Retry(baseDelayMs)
            else -> Decision.DoNotRetry
        }
    }

    private fun decideForApiError(error: ApiException): Decision = when (error.code) {
        429 -> {
            val delay = error.retryAfterMillis?.coerceIn(0L, maxBackoffMs) ?: baseDelayMs
            Decision.Retry(delay)
        }
        in RETRIABLE_SERVER_CODES -> Decision.Retry(baseDelayMs)
        else -> Decision.DoNotRetry
    }

    companion object {
        const val DEFAULT_MAX_BACKOFF_MS: Long = 60_000L
        private val RETRIABLE_SERVER_CODES = setOf(500, 502, 503, 504)
    }
}

package com.wuxianggujun.tinaide.ai.retry

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.api.ApiException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Test

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 10_000L)

    @Test
    fun `stops once attempts reach max`() {
        val error = ApiException(code = 503, message = "svc down")
        assertThat(policy.decide(error, attempt = 3))
            .isEqualTo(RetryPolicy.Decision.DoNotRetry)
    }

    @Test
    fun `4xx other than 429 are not retried`() {
        listOf(400, 401, 403, 404, 422).forEach { code ->
            val decision = policy.decide(ApiException(code, "err"), attempt = 0)
            assertThat(decision).isEqualTo(RetryPolicy.Decision.DoNotRetry)
        }
    }

    @Test
    fun `429 without retryAfter falls back to base delay`() {
        val decision = policy.decide(ApiException(429, "rate"), attempt = 0)
        assertThat(decision).isEqualTo(RetryPolicy.Decision.Retry(10_000L))
    }

    @Test
    fun `429 respects Retry-After when within bound`() {
        val decision = policy.decide(
            ApiException(429, "rate", retryAfterMillis = 5_000L),
            attempt = 1,
        )
        assertThat(decision).isEqualTo(RetryPolicy.Decision.Retry(5_000L))
    }

    @Test
    fun `429 Retry-After is capped by maxBackoff`() {
        val capped = RetryPolicy(maxAttempts = 3, baseDelayMs = 1_000L, maxBackoffMs = 30_000L)
        val decision = capped.decide(
            ApiException(429, "rate", retryAfterMillis = 3_600_000L),
            attempt = 0,
        )
        assertThat(decision).isEqualTo(RetryPolicy.Decision.Retry(30_000L))
    }

    @Test
    fun `only 500 502 503 504 among 5xx are retried`() {
        listOf(500, 502, 503, 504).forEach { code ->
            val decision = policy.decide(ApiException(code, "svc"), attempt = 0)
            assertThat(decision).isEqualTo(RetryPolicy.Decision.Retry(10_000L))
        }
        listOf(501, 505, 507).forEach { code ->
            val decision = policy.decide(ApiException(code, "svc"), attempt = 0)
            assertThat(decision).isEqualTo(RetryPolicy.Decision.DoNotRetry)
        }
    }

    @Test
    fun `network IO errors are retried`() {
        val errors: List<Throwable> = listOf(
            SocketTimeoutException("slow"),
            ConnectException("refused"),
            UnknownHostException("dns"),
            IOException("generic"),
        )
        errors.forEach { err ->
            assertThat(policy.decide(err, attempt = 0))
                .isEqualTo(RetryPolicy.Decision.Retry(10_000L))
        }
    }

    @Test
    fun `non-network non-api errors are not retried`() {
        val decision = policy.decide(IllegalStateException("bad parse"), attempt = 0)
        assertThat(decision).isEqualTo(RetryPolicy.Decision.DoNotRetry)
    }
}

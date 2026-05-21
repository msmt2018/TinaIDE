package com.wuxianggujun.tinaide.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApiResultTest {

    @Test
    fun success_shouldExposeDataAndMapValue() {
        val result = ApiResult.Success(21)
        val mapped = result.map { it * 2 }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
        assertThat(result.getOrNull()).isEqualTo(21)
        assertThat(mapped).isEqualTo(ApiResult.Success(42))
        assertThat(mapped.toResult().getOrThrow()).isEqualTo(42)
    }

    @Test
    fun error_shouldPreserveCodeAndSkipSuccessMapping() {
        val result: ApiResult<Int> = ApiResult.Error(code = 403, message = "forbidden")
        val mapped = result.map { value: Int -> value * 2 }
        val messages = mutableListOf<String>()

        mapped.onError { messages += it }

        assertThat(mapped).isSameInstanceAs(result)
        assertThat(result.isSuccess).isFalse()
        assertThat(result.isError).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.getErrorMessage()).isEqualTo("forbidden")
        assertThat(messages).containsExactly("forbidden")
        assertThat(result.toResult().isFailure).isTrue()
    }

    @Test
    fun networkError_shouldBehaveAsErrorWithoutHttpCode() {
        val result: ApiResult<String> = ApiResult.NetworkError("connection timed out")

        assertThat(result.isError).isTrue()
        assertThat(result.getErrorMessage()).isEqualTo("connection timed out")
        assertThat(result.map { value: String -> value.uppercase() }).isSameInstanceAs(result)
        assertThat(result.toResult().exceptionOrNull()!!.message).isEqualTo("connection timed out")
    }
}

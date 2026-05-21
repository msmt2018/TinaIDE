package com.wuxianggujun.tinaide.ai.api

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.AppStrings
import io.mockk.every
import io.mockk.mockk
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Before
import org.junit.Test

class AiApiErrorHandlerTest {

    @Before
    fun setUp() {
        resetAppStrings()
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getString(any<Int>()) } answers { "string-${firstArg<Int>()}" }
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            "string-${firstArg<Int>()}-formatted"
        }
        AppStrings.initialize(context)
    }

    @After
    fun tearDown() {
        resetAppStrings()
    }

    @Test
    fun `api exceptions map to expected error types and codes`() {
        val auth = AiApiErrorHandler.handleError(ApiException(401, "unauthorized"), "auth")
        val rateLimit = AiApiErrorHandler.handleError(ApiException(429, "slow down"), "rate")
        val invalid = AiApiErrorHandler.handleError(ApiException(422, "bad payload"), "request")
        val server = AiApiErrorHandler.handleError(ApiException(503, "offline"), "server")

        assertThat(auth.type).isEqualTo(AiApiErrorHandler.ErrorType.AUTHENTICATION)
        assertThat(auth.code).isEqualTo(401)
        assertThat(auth.technicalMessage).contains("HTTP 401")
        assertThat(auth.suggestion).isNotNull()
        assertThat(rateLimit.type).isEqualTo(AiApiErrorHandler.ErrorType.RATE_LIMIT)
        assertThat(invalid.type).isEqualTo(AiApiErrorHandler.ErrorType.INVALID_REQUEST)
        assertThat(server.type).isEqualTo(AiApiErrorHandler.ErrorType.SERVER)
    }

    @Test
    fun `client side api exception surfaces localized message`() {
        val formatted = AiApiErrorHandler.handleError(ApiException(-1, "stream unparseable"), "stream")

        assertThat(formatted.type).isEqualTo(AiApiErrorHandler.ErrorType.UNKNOWN)
        assertThat(formatted.userMessage).isEqualTo("stream unparseable")
        assertThat(formatted.suggestion).isNotNull()
    }

    @Test
    fun `network and parse exceptions map to specialized error types`() {
        assertThat(AiApiErrorHandler.handleError(UnknownHostException("dns")).type)
            .isEqualTo(AiApiErrorHandler.ErrorType.NETWORK)
        assertThat(AiApiErrorHandler.handleError(ConnectException("refused")).type)
            .isEqualTo(AiApiErrorHandler.ErrorType.NETWORK)
        assertThat(AiApiErrorHandler.handleError(SSLException("cert")).type)
            .isEqualTo(AiApiErrorHandler.ErrorType.NETWORK)
        assertThat(AiApiErrorHandler.handleError(SocketTimeoutException("slow")).type)
            .isEqualTo(AiApiErrorHandler.ErrorType.TIMEOUT)
        assertThat(AiApiErrorHandler.handleError(SerializationException("bad json")).type)
            .isEqualTo(AiApiErrorHandler.ErrorType.INVALID_REQUEST)
    }

    @Test
    fun `friendly and detailed messages include suggestion and technical details`() {
        val formatted = AiApiErrorHandler.handleError(ApiException(429, "quota exceeded"), "chat")

        val friendly = AiApiErrorHandler.getUserFriendlyMessage(formatted)
        val detailed = AiApiErrorHandler.getDetailedMessage(formatted)

        assertThat(friendly).contains(formatted.userMessage)
        assertThat(friendly).contains(formatted.suggestion)
        assertThat(detailed).contains(formatted.technicalMessage)
        assertThat(detailed).contains("429")
    }

    private fun resetAppStrings() {
        val field = AppStrings::class.java.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(AppStrings, null)
    }
}

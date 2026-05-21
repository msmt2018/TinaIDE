package com.wuxianggujun.tinaide.ai.api

import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import timber.log.Timber

/**
 * AI API 错误处理器
 * 负责格式化错误信息和记录日志
 */
object AiApiErrorHandler {
    private const val TAG = "AiApiError"

    /**
     * 错误类型
     */
    enum class ErrorType {
        NETWORK, // 网络错误
        API, // API错误
        TIMEOUT, // 超时
        AUTHENTICATION, // 认证错误
        RATE_LIMIT, // 速率限制
        INVALID_REQUEST, // 无效请求
        SERVER, // 服务器错误
        UNKNOWN // 未知错误
    }

    /**
     * 格式化的错误信息
     *
     * @property userMessage 用户友好的错误信息
     * @property technicalMessage 技术详细信息
     * @property suggestion 解决建议
     */
    data class FormattedError(
        val type: ErrorType,
        val userMessage: String,
        val technicalMessage: String,
        val code: Int? = null,
        val suggestion: String? = null
    )

    /**
     * 处理异常并返回格式化的错误信息
     */
    fun handleError(error: Throwable, context: String = ""): FormattedError {
        val formatted = when (error) {
            is ApiException -> handleApiException(error)
            is java.net.UnknownHostException -> FormattedError(
                type = ErrorType.NETWORK,
                userMessage = Strings.ai_error_unable_connect_server.str(),
                technicalMessage = "UnknownHostException: ${error.message}",
                suggestion = Strings.ai_error_suggest_check_network_server.str()
            )
            is java.net.SocketTimeoutException -> FormattedError(
                type = ErrorType.TIMEOUT,
                userMessage = Strings.ai_error_request_timeout.str(),
                technicalMessage = "SocketTimeoutException: ${error.message}",
                suggestion = Strings.ai_error_suggest_check_network_retry.str()
            )
            is java.net.ConnectException -> FormattedError(
                type = ErrorType.NETWORK,
                userMessage = Strings.ai_error_connect_failed.str(),
                technicalMessage = "ConnectException: ${error.message}",
                suggestion = Strings.ai_error_suggest_check_server_address.str()
            )
            is javax.net.ssl.SSLException -> FormattedError(
                type = ErrorType.NETWORK,
                userMessage = Strings.ai_error_ssl_failed.str(),
                technicalMessage = "SSLException: ${error.message}",
                suggestion = Strings.ai_error_suggest_check_certificate.str()
            )
            is kotlinx.serialization.SerializationException -> FormattedError(
                type = ErrorType.INVALID_REQUEST,
                userMessage = Strings.ai_error_parse_failed.str(),
                technicalMessage = "SerializationException: ${error.message}",
                suggestion = Strings.ai_error_suggest_invalid_server_data.str()
            )
            else -> FormattedError(
                type = ErrorType.UNKNOWN,
                userMessage = Strings.ai_error_unknown.str(),
                technicalMessage = "${error.javaClass.simpleName}: ${error.message}",
                suggestion = Strings.ai_error_suggest_check_logs.str()
            )
        }

        // 记录日志
        logError(formatted, error, context)

        return formatted
    }

    /**
     * 处理 API 异常
     */
    private fun handleApiException(error: ApiException): FormattedError {
        val type = when (error.code) {
            in Int.MIN_VALUE..-1 -> ErrorType.UNKNOWN
            401 -> ErrorType.AUTHENTICATION
            429 -> ErrorType.RATE_LIMIT
            400, 422 -> ErrorType.INVALID_REQUEST
            in 500..599 -> ErrorType.SERVER
            else -> ErrorType.API
        }

        val userMessage = when (error.code) {
            in Int.MIN_VALUE..-1 -> error.message?.takeIf { it.isNotBlank() } ?: Strings.ai_error_unknown.str()
            401 -> Strings.ai_error_auth_failed.str()
            403 -> Strings.ai_error_access_denied.str()
            404 -> Strings.ai_error_service_not_found.str()
            429 -> Strings.ai_error_rate_limit.str()
            500 -> Strings.ai_error_server_internal.str()
            502 -> Strings.ai_error_gateway.str()
            503 -> Strings.ai_error_service_unavailable.str()
            else -> Strings.ai_error_api_failed.str(error.code)
        }

        val suggestion = when (error.code) {
            in Int.MIN_VALUE..-1 -> Strings.ai_error_suggest_check_logs.str()
            401 -> Strings.ai_error_suggest_check_api_key.str()
            403 -> Strings.ai_error_suggest_check_permission.str()
            404 -> Strings.ai_error_suggest_check_server_model.str()
            429 -> Strings.ai_error_suggest_retry_or_upgrade.str()
            in 500..599 -> Strings.ai_error_suggest_server_retry.str()
            else -> null
        }

        return FormattedError(
            type = type,
            userMessage = userMessage,
            technicalMessage = "HTTP ${error.code}: ${error.message}",
            code = error.code,
            suggestion = suggestion
        )
    }

    /**
     * 记录错误日志
     */
    private fun logError(formatted: FormattedError, error: Throwable, context: String) {
        val logMessage = buildString {
            if (context.isNotEmpty()) {
                appendLine("Context: $context")
            }
            appendLine("Error Type: ${formatted.type}")
            appendLine("User Message: ${formatted.userMessage}")
            appendLine("Technical: ${formatted.technicalMessage}")
            formatted.code?.let { appendLine("HTTP Code: $it") }
            formatted.suggestion?.let { appendLine("Suggestion: $it") }
        }

        Timber.tag(TAG).e(error, logMessage)
    }

    /**
     * 获取用户友好的错误信息（包含建议）
     */
    fun getUserFriendlyMessage(formatted: FormattedError): String = buildString {
        append(formatted.userMessage)
        formatted.suggestion?.let {
            append("\n\n")
            append(Strings.ai_error_suggestion_prefix.str())
            append(it)
        }
    }

    /**
     * 获取完整的错误信息（用于调试）
     */
    fun getDetailedMessage(formatted: FormattedError): String = buildString {
        appendLine(Strings.ai_error_detail_title.str(formatted.userMessage))
        appendLine()
        appendLine(Strings.ai_error_detail_info_title.str())
        appendLine(formatted.technicalMessage)
        formatted.code?.let {
            appendLine()
            appendLine(Strings.ai_error_detail_code.str(it))
        }
        formatted.suggestion?.let {
            appendLine()
            appendLine(Strings.ai_error_detail_suggestion_title.str())
            appendLine(it)
        }
    }
}

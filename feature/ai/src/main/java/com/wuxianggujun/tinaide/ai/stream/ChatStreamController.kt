package com.wuxianggujun.tinaide.ai.stream

import com.wuxianggujun.tinaide.ai.api.AiApiClient
import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.api.ChatUsage
import com.wuxianggujun.tinaide.ai.api.ToolCall
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 流式聊天控制器:把原本在 `AiChatViewModel.launchStreamingReply` 里
 * ~150 行嵌套 onEvent/onError lambda 抽成独立组件。
 *
 * 职责:
 * - 维护内部(高频更新)与对外(100ms 节流后)两组 StateFlow;
 * - 把 [AiApiClient.chatStream] 的 callback 包装成 suspend 调用,返回 [StreamResult];
 * - 对外提供累积快照 [snapshotPartialMessage] 用于"停止生成时保存部分消息"场景。
 *
 * ViewModel 侧只负责消费结果(更新 uiState、写 repo),不再关心节流 Job 和 event 分派。
 */
class ChatStreamController(private val scope: CoroutineScope) {

    private val contentInternal = MutableStateFlow("")
    private val reasoningInternal = MutableStateFlow("")

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _reasoning = MutableStateFlow("")
    val reasoning: StateFlow<String> = _reasoning.asStateFlow()

    private val _toolCalls = MutableStateFlow<List<ToolCall>>(emptyList())
    val toolCalls: StateFlow<List<ToolCall>> = _toolCalls.asStateFlow()

    private val _usage = MutableStateFlow<ChatUsage?>(null)
    val usage: StateFlow<ChatUsage?> = _usage.asStateFlow()

    private val streamVersion = AtomicLong(0)
    private var throttleJob: Job? = null

    sealed interface StreamResult {
        data class Completed(val message: ChatMessage) : StreamResult
        data class Failed(val error: Throwable) : StreamResult
    }

    /**
     * 发起一次流式请求;完成或失败时返回 [StreamResult]。
     * 进入函数前会自动清空先前累积;不自动 [reset],留给调用方在"进入下一轮"时决定。
     */
    suspend fun stream(client: AiApiClient, messages: List<ChatMessage>): StreamResult {
        val version = streamVersion.incrementAndGet()
        resetState()
        startThrottle(version)

        val deferred = CompletableDeferred<StreamResult>()

        fun isActiveStream(): Boolean = streamVersion.get() == version && !deferred.isCompleted

        client.chatStream(
            messages = messages,
            onEvent = { event ->
                if (isActiveStream()) {
                    when (event) {
                        is AiApiClient.ChatStreamEvent.TextDelta -> {
                            contentInternal.update { it + event.text }
                        }
                        is AiApiClient.ChatStreamEvent.ReasoningDelta -> {
                            reasoningInternal.update { it + event.text }
                        }
                        is AiApiClient.ChatStreamEvent.ToolCallsUpdate -> {
                            _toolCalls.value = event.toolCalls
                        }
                        is AiApiClient.ChatStreamEvent.Usage -> {
                            _usage.value = event.usage
                        }
                        AiApiClient.ChatStreamEvent.Done -> {
                            throttleJob?.cancel()
                            // 最后一次同步把 internal flush 到对外 flow
                            _content.value = contentInternal.value
                            _reasoning.value = reasoningInternal.value

                            val message = ChatMessage(
                                role = ChatRole.ASSISTANT,
                                content = contentInternal.value,
                                reasoningContent = reasoningInternal.value.takeIf { it.isNotBlank() },
                                toolCalls = _toolCalls.value.takeIf { it.isNotEmpty() },
                                usage = _usage.value,
                            )
                            deferred.complete(StreamResult.Completed(message))
                        }
                    }
                }
            },
            onError = { error ->
                if (isActiveStream()) {
                    throttleJob?.cancel()
                    deferred.complete(StreamResult.Failed(error))
                }
            },
        )

        return try {
            deferred.await()
        } finally {
            throttleJob?.cancel()
            throttleJob = null
            if (!deferred.isCompleted && streamVersion.get() == version) {
                streamVersion.incrementAndGet()
                client.cancelRequest()
            }
        }
    }

    /**
     * 停止生成时用于保存"已经吐出来的部分内容"。
     * @param suffix 可选追加(例如"[已停止生成]")
     * @return 如果所有累积都为空返回 null。
     */
    fun snapshotPartialMessage(suffix: String? = null): ChatMessage? {
        val content = contentInternal.value
        val reasoning = reasoningInternal.value
        val calls = _toolCalls.value
        if (content.isBlank() && reasoning.isBlank() && calls.isEmpty()) return null

        val body = if (suffix.isNullOrBlank()) content else "$content\n\n$suffix".trim()
        return ChatMessage(
            role = ChatRole.ASSISTANT,
            content = body,
            reasoningContent = reasoning.takeIf { it.isNotBlank() },
            toolCalls = calls.takeIf { it.isNotEmpty() },
            usage = _usage.value,
        )
    }

    fun reset() {
        streamVersion.incrementAndGet()
        resetState()
    }

    fun cancel() {
        streamVersion.incrementAndGet()
        throttleJob?.cancel()
        throttleJob = null
    }

    private fun resetState() {
        contentInternal.value = ""
        reasoningInternal.value = ""
        _content.value = ""
        _reasoning.value = ""
        _toolCalls.value = emptyList()
        _usage.value = null
    }

    private fun startThrottle(version: Long) {
        throttleJob?.cancel()
        throttleJob = scope.launch {
            while (streamVersion.get() == version) {
                delay(THROTTLE_INTERVAL_MS)
                _content.value = contentInternal.value
                _reasoning.value = reasoningInternal.value
            }
        }
    }

    companion object {
        private const val THROTTLE_INTERVAL_MS = 100L
    }
}

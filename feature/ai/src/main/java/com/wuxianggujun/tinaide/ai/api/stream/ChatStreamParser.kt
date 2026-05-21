package com.wuxianggujun.tinaide.ai.api.stream

import com.wuxianggujun.tinaide.ai.api.ChatUsage
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolCallDelta
import com.wuxianggujun.tinaide.ai.api.ToolFunction

/**
 * 一次 SSE 数据帧经解析后的中间结构。
 *
 * [recognized] 表示"至少命中了一种我们认得的 schema 片段",
 * 用于区分"无法解析的噪声"(返回 null) 与"空增量但整体合法"(如纯 usage 帧)。
 */
data class ParsedStreamPayload(
    val chunkId: String? = null,
    val usage: ChatUsage? = null,
    val textDeltas: List<String> = emptyList(),
    val reasoningDeltas: List<String> = emptyList(),
    val toolCallDeltas: List<ToolCallDelta> = emptyList(),
    val errorMessage: String? = null,
    val errorCode: Int? = null,
    val recognized: Boolean = false,
)

/**
 * 聊天流解析器:把单次 SSE `data:` payload 翻译成 [ParsedStreamPayload]。
 *
 * 抽成接口后,[com.wuxianggujun.tinaide.ai.api.AiApiClient] 不再硬编码
 * OpenAI/Anthropic 的 schema 细节,便于未来针对个别上游做 per-provider parser。
 */
interface ChatStreamParser {
    fun parse(payload: String): ParsedStreamPayload?
}

/**
 * 累积 SSE 流中分片到达的 tool_calls。
 *
 * 同一个 tool call 会被拆成多帧下发 (每帧可能只带 id / 只带 arguments 片段),
 * 需要按 index 或 id 合并。
 */
class ToolCallAccumulator {

    private data class CallState(
        var id: String? = null,
        var type: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )

    private val byIndex = linkedMapOf<Int, CallState>()
    private val byId = linkedMapOf<String, CallState>()

    fun applyDelta(delta: ToolCallDelta) {
        val call = when {
            delta.index != null -> byIndex.getOrPut(delta.index) { CallState() }
            !delta.id.isNullOrBlank() -> byId.getOrPut(delta.id!!) { CallState() }
            else -> null
        } ?: return

        if (!delta.id.isNullOrBlank()) {
            call.id = delta.id
            byId.putIfAbsent(delta.id!!, call)
        }
        if (!delta.type.isNullOrBlank()) {
            call.type = delta.type
        }

        val function = delta.function
        if (!function?.name.isNullOrBlank()) {
            call.name = function?.name
        }
        if (!function?.arguments.isNullOrBlank()) {
            call.arguments.append(function?.arguments)
        }
    }

    fun hasCalls(): Boolean = byIndex.isNotEmpty() || byId.isNotEmpty()

    fun toToolCalls(): List<ToolCall> {
        val calls = if (byIndex.isNotEmpty()) byIndex.values else byId.values
        return calls.mapIndexed { index, call ->
            ToolCall(
                id = call.id ?: "call_${index + 1}",
                type = call.type ?: "function",
                function = ToolFunction(
                    name = call.name ?: "unknown",
                    arguments = call.arguments.toString().trim().ifBlank { null },
                ),
            )
        }
    }
}

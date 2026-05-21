package com.wuxianggujun.tinaide.ai.api.stream

import com.wuxianggujun.tinaide.ai.api.ChatUsage
import com.wuxianggujun.tinaide.ai.api.ToolCallDelta
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * OpenAI-compatible / Anthropic-compatible / DeepSeek-compatible schema 的兼容解析器。
 *
 * 优先读取以下字段:
 * - `choices[].delta.content|tool_calls|function_call|reasoning_content`
 * - `choices[].message.*` / `choices[].message_delta.*` (部分上游)
 * - 顶层 `message.*`
 * - 顶层 `usage`
 * - 顶层 `error.{message,code}`
 *
 * 无法命中任何已知 schema 时返回 null,由上游决定是当作噪声还是错误。
 */
class OpenAiCompatibleStreamParser(private val json: Json) : ChatStreamParser {

    override fun parse(payload: String): ParsedStreamPayload? {
        return try {
            val root = json.parseToJsonElement(payload) as? JsonObject ?: return null

            val errorObject = root["error"] as? JsonObject
            if (errorObject != null) {
                return ParsedStreamPayload(
                    errorMessage = errorObject["message"].asStringOrNull()
                        ?: root["message"].asStringOrNull()
                        ?: payload.take(500),
                    errorCode = errorObject["code"].asIntOrNull(),
                    recognized = true,
                )
            }

            val usage = root["usage"]?.let(::decodeUsageOrNull)
            val textDeltas = mutableListOf<String>()
            val reasoningDeltas = mutableListOf<String>()
            val toolCallDeltas = mutableListOf<ToolCallDelta>()
            var recognized = usage != null

            val choices = root["choices"] as? JsonArray
            if (choices != null) {
                choices.forEachIndexed { index, element ->
                    val choice = element as? JsonObject ?: return@forEachIndexed
                    recognized = true

                    val delta = (choice["delta"] as? JsonObject)
                        ?: (choice["message"] as? JsonObject)
                        ?: (choice["message_delta"] as? JsonObject)

                    if (delta != null) {
                        textDeltas += extractTextFragments(delta["content"])
                        reasoningDeltas += extractReasoningFragments(delta)
                        toolCallDeltas += extractToolCallDeltas(delta, index)
                    }

                    textDeltas += extractTextFragments(choice["text"])
                    reasoningDeltas += extractReasoningFragments(choice)
                    toolCallDeltas += extractToolCallDeltas(choice, index)
                }
            }

            val message = root["message"] as? JsonObject
            if (message != null) {
                recognized = true
                textDeltas += extractTextFragments(message["content"])
                reasoningDeltas += extractReasoningFragments(message)
                toolCallDeltas += extractToolCallDeltas(message, 0)
            }

            if (!recognized && textDeltas.isEmpty() && reasoningDeltas.isEmpty() && toolCallDeltas.isEmpty()) {
                return null
            }

            ParsedStreamPayload(
                chunkId = root["id"].asStringOrNull(),
                usage = usage,
                textDeltas = textDeltas,
                reasoningDeltas = reasoningDeltas,
                toolCallDeltas = toolCallDeltas,
                recognized = recognized ||
                    textDeltas.isNotEmpty() ||
                    reasoningDeltas.isNotEmpty() ||
                    toolCallDeltas.isNotEmpty(),
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse stream payload")
            null
        }
    }

    private fun decodeUsageOrNull(element: JsonElement): ChatUsage? = runCatching { json.decodeFromString<ChatUsage>(element.toString()) }.getOrNull()

    private fun extractReasoningFragments(container: JsonObject): List<String> = buildList {
        addAll(extractTextFragments(container["reasoning_content"]))
        addAll(extractTextFragments(container["reasoning"]))
        addAll(extractTextFragments(container["reasoning_text"]))
        addAll(extractTextFragments(container["reasoningContent"]))
    }.filter { it.isNotBlank() }

    private fun extractToolCallDeltas(container: JsonObject, defaultIndex: Int): List<ToolCallDelta> {
        val toolCalls = mutableListOf<ToolCallDelta>()

        val toolCallsElement = container["tool_calls"]
        if (toolCallsElement != null) {
            val decoded = runCatching {
                json.decodeFromString<List<ToolCallDelta>>(toolCallsElement.toString())
            }.getOrNull()
            if (decoded != null) {
                toolCalls += decoded
            }
        }

        val functionCall = container["function_call"] as? JsonObject
        if (functionCall != null) {
            toolCalls += ToolCallDelta(
                index = defaultIndex,
                type = "function",
                function = com.wuxianggujun.tinaide.ai.api.ToolFunctionDelta(
                    name = functionCall["name"].asStringOrNull(),
                    arguments = functionCall["arguments"].asStringOrNull(),
                ),
            )
        }

        return toolCalls
    }

    private fun extractTextFragments(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList()
        is JsonArray -> element.flatMap { extractTextFragments(it) }
        is JsonObject -> listOf(
            element["text"],
            element["content"],
            element["value"],
            element["output_text"],
        ).flatMap { extractTextFragments(it) }
    }

    private fun JsonElement?.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.toIntOrNull()
    }

    companion object {
        private const val TAG = "AiStreamParser"
    }
}

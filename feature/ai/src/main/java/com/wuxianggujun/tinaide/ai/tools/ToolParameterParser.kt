package com.wuxianggujun.tinaide.ai.tools

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.json.*

/**
 * 工具参数解析辅助类
 */
object ToolParameterParser {
    private val json = JsonSerializer.default

    fun parseArguments(toolCall: ToolCall): Map<String, String> {
        val arguments = toolCall.function?.arguments ?: return emptyMap()
        return try {
            val jsonElement = json.parseToJsonElement(arguments)
            jsonElement.jsonObject.mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> value.content
                    is JsonArray, is JsonObject -> value.toString()
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid tool arguments: ${e.message}")
        }
    }

    fun getStringParameter(args: Map<String, String>, key: String, default: String = ""): String = args[key] ?: default

    fun getBooleanParameter(args: Map<String, String>, key: String, default: Boolean = false): Boolean = args[key]?.toBooleanStrictOrNull() ?: default

    fun getIntParameter(args: Map<String, String>, key: String, default: Int = 0): Int = args[key]?.toIntOrNull() ?: default

    fun getStringListParameter(args: Map<String, String>, key: String): List<String> {
        val raw = args[key] ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                (element as? JsonPrimitive)
                    ?.takeUnless { it is JsonNull }
                    ?.content
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            }
        }.getOrElse {
            raw.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        }
    }
}

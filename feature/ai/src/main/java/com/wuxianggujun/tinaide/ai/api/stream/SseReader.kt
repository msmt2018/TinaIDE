package com.wuxianggujun.tinaide.ai.api.stream

import okio.BufferedSource

/**
 * 标准 SSE 帧读取器。
 *
 * 职责:
 * - 按 [SSE 规范](https://html.spec.whatwg.org/multipage/server-sent-events.html) 读行;
 * - 聚合 `data:` 行并以空行为界分派事件;
 * - 忽略 `event:` / `id:` / `retry:` / 注释 (`:` 开头) 行;
 * - 对缺乏规范 `data:` 前缀的上游 (偶见 DeepSeek/Anthropic 变体) 做兜底:
 *   整行看起来像 JSON/ `[DONE]` 时直接当一帧下发。
 *
 * 产出统一走 [Event] 两种形态:
 * - [Event.Payload] 携带聚合后的 payload 文本 (不含 `data:` 前缀)。
 * - [Event.Done] 代表流结束。
 *
 * 用法:```kotlin
 *     val reader = SseReader(source)
 *     while (true) when (val ev = reader.readEvent() ?: break) {
 *         SseReader.Event.Done -> { ...; return }
 *         is SseReader.Event.Payload -> parse(ev.data)
 *     }
 * ```
 */
class SseReader(private val source: BufferedSource) {

    sealed interface Event {
        data class Payload(val data: String) : Event
        data object Done : Event
    }

    private val buffer = StringBuilder()

    /**
     * @return 下一个事件;流读完返回 null。
     */
    fun readEvent(): Event? {
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue

            if (line.isBlank()) {
                val flushed = flush() ?: continue
                return flushed
            }

            if (line.startsWith(":")) continue

            if (line.startsWith("data:")) {
                val content = line.substringAfter("data:").trimStart()
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(content)
                continue
            }

            if (
                line.startsWith("event:") ||
                line.startsWith("id:") ||
                line.startsWith("retry:")
            ) {
                continue
            }

            // 命中"多行聚合"场景:后续行属于当前事件 payload。
            if (buffer.isNotEmpty()) {
                buffer.append('\n').append(line)
                continue
            }

            // 兜底:上游未严格遵循 SSE 但直接吐 JSON / `[DONE]`。
            if (looksLikeStreamPayload(line)) {
                val trimmed = line.trim()
                return if (trimmed == "[DONE]") Event.Done else Event.Payload(trimmed)
            }
        }
        // 流读完前还有未 flush 的 payload。
        return flush()
    }

    private fun flush(): Event? {
        if (buffer.isEmpty()) return null
        val payload = buffer.toString()
        buffer.setLength(0)
        val trimmed = payload.trim()
        return if (trimmed == "[DONE]") Event.Done else Event.Payload(payload)
    }

    private fun looksLikeStreamPayload(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "[DONE]" ||
            (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }
}

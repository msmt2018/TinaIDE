package com.wuxianggujun.tinaide.core.common.snippet

/**
 * 将 LSP/TextMate snippet 展开为纯文本。
 *
 * 支持：
 * - `${1:name}` 保留默认值
 * - `${1}` / `$1` / `$0` 删除占位符标记
 * - `${1|one,two|}` 取第一个选项
 * - `\$`、`\}`、`\\` 转义
 *
 * 暂不支持嵌套占位符和变量替换。
 */
fun expandSnippetToPlainText(snippet: String): String {
    val textBuilder = StringBuilder()
    val pendingText = StringBuilder()
    var i = 0
    val len = snippet.length

    fun flushText() {
        if (pendingText.isNotEmpty()) {
            textBuilder.append(pendingText)
            pendingText.clear()
        }
    }

    while (i < len) {
        val ch = snippet[i]

        if (ch == '\\' && i + 1 < len) {
            val next = snippet[i + 1]
            if (next == '$' || next == '}' || next == '\\') {
                pendingText.append(next)
                i += 2
                continue
            }
        }

        if (ch == '$' && i + 1 < len && snippet[i + 1] == '{') {
            val closeBrace = snippet.indexOf('}', i + 2)
            if (closeBrace > i + 2) {
                val body = snippet.substring(i + 2, closeBrace)
                val choice = parseChoiceBody(body)
                if (choice != null) {
                    flushText()
                    textBuilder.append(choice.second.firstOrNull().orEmpty())
                    i = closeBrace + 1
                    continue
                }

                val colonPos = body.indexOf(':')
                if (colonPos >= 0) {
                    if (body.substring(0, colonPos).toIntOrNull() != null) {
                        flushText()
                        textBuilder.append(body.substring(colonPos + 1))
                        i = closeBrace + 1
                        continue
                    }
                    pendingText.append(snippet, i, closeBrace + 1)
                    i = closeBrace + 1
                    continue
                }

                if (body.toIntOrNull() != null) {
                    flushText()
                    i = closeBrace + 1
                    continue
                }
            }
        }

        if (ch == '$' && i + 1 < len && snippet[i + 1].isDigit()) {
            flushText()
            var numEnd = i + 1
            while (numEnd < len && snippet[numEnd].isDigit()) {
                numEnd++
            }
            if (snippet.substring(i + 1, numEnd).toIntOrNull() != null) {
                i = numEnd
                continue
            }
        }

        pendingText.append(ch)
        i++
    }

    flushText()
    return textBuilder.toString()
}

private fun parseChoiceBody(body: String): Pair<Int, List<String>>? {
    val pipeFirst = body.indexOf('|')
    if (pipeFirst < 1) return null
    val pipeLast = body.lastIndexOf('|')
    if (pipeLast <= pipeFirst) return null
    val index = body.substring(0, pipeFirst).toIntOrNull() ?: return null
    val choices = body.substring(pipeFirst + 1, pipeLast)
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (choices.isEmpty()) return null
    return index to choices
}

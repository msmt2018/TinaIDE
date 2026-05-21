package com.wuxianggujun.tinaide.project

/**
 * 将用户输入的原生编译/链接参数拆分为参数数组。
 *
 * 规则：
 * - 默认按空白分隔
 * - 支持单引号、双引号包裹参数
 * - 支持在非单引号模式下使用反斜杠转义
 */
object NativeBuildFlagTokenizer {

    fun tokenize(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaping = false

        fun flushToken() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        raw.forEach { ch ->
            when {
                escaping -> {
                    current.append(ch)
                    escaping = false
                }

                ch == '\\' && !inSingleQuote -> {
                    escaping = true
                }

                ch == '\'' && !inDoubleQuote -> {
                    inSingleQuote = !inSingleQuote
                }

                ch == '"' && !inSingleQuote -> {
                    inDoubleQuote = !inDoubleQuote
                }

                ch.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                    flushToken()
                }

                else -> {
                    current.append(ch)
                }
            }
        }

        if (escaping) {
            current.append('\\')
        }
        flushToken()
        return tokens.filter { it.isNotBlank() }
    }
}

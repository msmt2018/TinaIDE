package com.wuxianggujun.tinaide.core.compile

import com.wuxianggujun.tinaide.core.util.NativeExecutableRunner.shellQuotePosix

/**
 * 统一收口运行时额外环境变量的校验与序列化逻辑。
 */
object LaunchEnvironment {
    private val variableNamePattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun sanitized(environment: Map<String, String>): Map<String, String> {
        if (environment.isEmpty()) return emptyMap()
        return environment.entries
            .asSequence()
            .filter { (key, _) -> variableNamePattern.matches(key) }
            .sortedBy { it.key }
            .associate { (key, value) -> key to value }
    }

    fun buildShellPrefix(environment: Map<String, String>): String {
        val normalized = sanitized(environment)
        if (normalized.isEmpty()) return ""
        return normalized.entries
            .joinToString(separator = " ", postfix = " ") { (key, value) ->
                "$key=${shellQuotePosix(value)}"
            }
    }
}

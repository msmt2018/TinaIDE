package com.wuxianggujun.tinaide.core.proot

/**
 * PRoot 命令执行结果。
 */
data class PRootResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean
        get() = exitCode == 0 && !timedOut

    val combinedOutput: String
        get() = buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                append(stderr)
            }
        }
}

typealias RunResult = PRootResult

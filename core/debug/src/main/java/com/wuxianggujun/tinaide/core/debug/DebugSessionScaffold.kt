package com.wuxianggujun.tinaide.core.debug

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试会话脚手架：记录产物信息，方便 UI 给出下一步提示。
 * 真正的 Native Debug Stub 实现可以读取这些描述文件继续执行。
 */
object DebugSessionScaffold {

    data class Descriptor(
        val sessionId: String,
        val descriptorPath: String,
        val instructions: List<String>
    )

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun prepareSession(sessionRoot: File, soFile: File, entrySymbol: String, sessionStore: DebugSessionStore): Descriptor {
        sessionRoot.mkdirs()
        val sessionId = "dbg-" + System.currentTimeMillis()
        val descriptorFile = File(sessionRoot, "$sessionId.txt")
        val content = buildString {
            appendLine("session=$sessionId")
            appendLine("createdAt=${timeFormat.format(Date())}")
            appendLine("soPath=${soFile.absolutePath}")
            appendLine("entry=$entrySymbol")
        }
        descriptorFile.writeText(content)

        val instructions = listOf(
            "Native debug session created: $sessionId",
            "Shared library: ${soFile.absolutePath}",
            "Descriptor file: ${descriptorFile.absolutePath}",
            "Debug context is written to the descriptor file. Native Debug Stub can read it to set breakpoints."
        )
        val descriptor = Descriptor(
            sessionId = sessionId,
            descriptorPath = descriptorFile.absolutePath,
            instructions = instructions
        )
        sessionStore.update(descriptor)
        return descriptor
    }
}

package com.wuxianggujun.tinaide.core.compile.pipeline

import android.content.Context
import com.wuxianggujun.tinaide.core.compile.BuildOptions
import com.wuxianggujun.tinaide.core.compile.BuildSystem
import com.wuxianggujun.tinaide.core.compile.strategy.BuildContext
import java.io.File
import java.security.MessageDigest

/**
 * 构建上下文工厂:把 UI/AI 层的高层输入翻译成 [BuildContext]。
 *
 * 在 P4 调用面切换时,UseCase / ViewModel / AI Callbacks 先用此工厂组合上下文,
 * 再把 [BuildContext] + [com.wuxianggujun.tinaide.core.compile.action.CompileRequest]
 * 送到 [BuildOrchestrator.run]。
 *
 * `projectId` 由 `projectRoot` 绝对路径的 SHA-256 前 16 字节派生,跨会话稳定。
 */
class BuildContextFactory {

    fun create(
        appContext: Context,
        projectRoot: File,
        buildDir: File,
        buildSystem: BuildSystem,
        options: BuildOptions,
        target: String? = null,
    ): BuildContext = BuildContext(
        appContext = appContext,
        projectRoot = projectRoot,
        buildDir = buildDir,
        buildSystem = buildSystem,
        options = options,
        projectId = projectIdFor(projectRoot),
        target = target,
    )

    companion object {
        /** projectId = SHA-256(projectRoot.absolutePath) 前 16 字节十六进制(32 字符)。 */
        fun projectIdFor(projectRoot: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(projectRoot.absolutePath.toByteArray(Charsets.UTF_8))
            val bytes = digest.digest()
            return buildString(32) {
                for (i in 0 until 16) {
                    val b = bytes[i].toInt() and 0xFF
                    append(b.toString(16).padStart(2, '0'))
                }
            }
        }
    }
}

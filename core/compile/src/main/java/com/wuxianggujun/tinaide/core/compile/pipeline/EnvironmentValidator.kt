package com.wuxianggujun.tinaide.core.compile.pipeline

import com.wuxianggujun.tinaide.core.compile.action.CompileRequest
import com.wuxianggujun.tinaide.core.compile.strategy.BuildContext
import timber.log.Timber

/**
 * 环境前置校验。只做"便宜校验",避免早期失败浪费后续阶段。
 *
 * - 项目根目录必须存在且为目录
 * - buildDir 若不存在则尝试创建,失败返回错误
 * - 更详细的工具链/SDK 检查留给 Strategy.execute 内部处理,避免重复
 *
 * 返回 null 表示通过;非 null 是错误原因(直接进 [com.wuxianggujun.tinaide.core.compile.event.BuildReport.Invalid])。
 */
class EnvironmentValidator {

    companion object {
        private const val TAG = "EnvironmentValidator"
    }

    @Suppress("UNUSED_PARAMETER")
    fun validate(request: CompileRequest, ctx: BuildContext): String? {
        if (!ctx.projectRoot.isDirectory) {
            return "project root is not a directory: ${ctx.projectRoot.absolutePath}"
        }
        val buildDir = ctx.buildDir
        if (buildDir.exists() && !buildDir.isDirectory) {
            return "buildDir conflicts with an existing file: ${buildDir.absolutePath}"
        }
        if (!buildDir.exists() && !buildDir.mkdirs()) {
            return "failed to create buildDir: ${buildDir.absolutePath}"
        }
        if (ctx.projectId.isBlank()) {
            Timber.tag(TAG).w("projectId is blank; ArtifactStore keys will collide across projects")
        }
        return null
    }
}

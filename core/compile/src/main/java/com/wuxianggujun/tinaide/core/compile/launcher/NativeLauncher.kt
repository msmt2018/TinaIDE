package com.wuxianggujun.tinaide.core.compile.launcher

import com.wuxianggujun.tinaide.core.compile.artifact.Artifact
import com.wuxianggujun.tinaide.core.compile.event.BuildEvent
import com.wuxianggujun.tinaide.core.compile.event.BuildEventEmitter
import com.wuxianggujun.tinaide.core.compile.strategy.BuildContext
import timber.log.Timber
import java.io.File

/**
 * 原生启动器:产物直接由 ProcessManager 运行(LOG 输出模式)。
 *
 * 校验产物存在,发射 `Launch.Prepared` 语义,返回 [LaunchDescriptor.Native]。
 * UI 层拿到 descriptor 后用 ProcessManager 真正拉起进程。
 */
class NativeLauncher : Launcher {

    companion object {
        private const val TAG = "NativeLauncher"
    }

    override suspend fun launch(
        artifact: Artifact,
        ctx: BuildContext,
        emitter: BuildEventEmitter,
    ): LaunchOutcome {
        val file = File(artifact.absolutePath)
        if (!file.isFile) {
            val reason = "artifact not found: ${artifact.absolutePath}"
            Timber.tag(TAG).w(reason)
            emitter.emit(BuildEvent.Launch.Failed(reason, wasArtifactCached = false))
            return LaunchOutcome.Failed(reason)
        }
        Timber.tag(TAG).d("native launch prepared: %s", file.absolutePath)
        emitter.emit(BuildEvent.Launch.Completed)
        return LaunchOutcome.Prepared(LaunchDescriptor.Native(artifact = artifact, outputPath = artifact.absolutePath))
    }
}

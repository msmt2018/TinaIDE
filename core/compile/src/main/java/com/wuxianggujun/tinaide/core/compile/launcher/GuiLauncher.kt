package com.wuxianggujun.tinaide.core.compile.launcher

import com.wuxianggujun.tinaide.core.compile.artifact.Artifact
import com.wuxianggujun.tinaide.core.compile.artifact.ArtifactKind
import com.wuxianggujun.tinaide.core.compile.event.BuildEvent
import com.wuxianggujun.tinaide.core.compile.event.BuildEventEmitter
import com.wuxianggujun.tinaide.core.compile.strategy.BuildContext
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import timber.log.Timber
import java.io.File

/**
 * GUI 启动器:把 `.so` 产物交给 GUI 宿主加载。
 *
 * 校验产物是 SHARED_LIBRARY 且文件存在,返回 [LaunchDescriptor.Gui]。
 * UI 层(CompileActionsHelper)负责向 GUI 宿主发出 OpenGui 指令。
 */
class GuiLauncher : Launcher {

    companion object {
        private const val TAG = "GuiLauncher"
    }

    override suspend fun launch(
        artifact: Artifact,
        ctx: BuildContext,
        emitter: BuildEventEmitter,
    ): LaunchOutcome {
        val file = File(artifact.absolutePath)
        if (!file.isFile) {
            val reason = Strings.sdl_runtime_error_main_library_invalid.strOr(ctx.appContext, artifact.absolutePath)
            emitter.emit(BuildEvent.Launch.Failed(reason, wasArtifactCached = false))
            return LaunchOutcome.Failed(reason)
        }
        if (artifact.kind != ArtifactKind.SHARED_LIBRARY || !file.name.endsWith(".so", ignoreCase = true)) {
            val reason = Strings.gui_runtime_invalid_shared_library.strOr(ctx.appContext, artifact.absolutePath)
            emitter.emit(BuildEvent.Launch.Failed(reason, wasArtifactCached = false))
            return LaunchOutcome.Failed(reason)
        }
        Timber.tag(TAG).d("gui launch prepared: %s", file.absolutePath)
        emitter.emit(BuildEvent.Launch.Completed)
        return LaunchOutcome.Prepared(LaunchDescriptor.Gui(artifact = artifact, libraryPath = artifact.absolutePath))
    }
}

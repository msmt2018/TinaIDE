package com.wuxianggujun.tinaide.ui

import com.wuxianggujun.tinaide.ui.compose.components.FileTreeState
import java.io.File

/**
 * MainActivity 的文件树宿主桥接。
 *
 * 负责把非 Compose 侧真正需要的文件树动作收敛到稳定接口，
 * 避免宿主层直接暴露 FileTreeState。
 */
class MainActivityFileTreeActionBridge {
    private var revealAction: (suspend (File, Boolean) -> Unit)? = null

    fun bind(fileTreeState: FileTreeState) {
        revealAction = { file, selectTarget ->
            fileTreeState.reveal(file, selectTarget = selectTarget)
        }
    }

    suspend fun refreshAndRevealExportedArtifact(exportedArtifactPath: String?) {
        val reveal = revealAction ?: return

        exportedArtifactPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf(File::exists)
            ?.let { exportedArtifact ->
                reveal(exportedArtifact, false)
            }
    }

    suspend fun reveal(file: File, selectTarget: Boolean) {
        val reveal = revealAction ?: return
        reveal(file, selectTarget)
    }

    fun clear() {
        revealAction = null
    }
}

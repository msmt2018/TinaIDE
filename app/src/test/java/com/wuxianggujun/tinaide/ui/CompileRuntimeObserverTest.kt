package com.wuxianggujun.tinaide.ui

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.compile.CompileProjectUseCase
import com.wuxianggujun.tinaide.core.compile.ProcessManager
import com.wuxianggujun.tinaide.ui.compose.components.FileTreeState
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompileRuntimeObserverTest {

    @Test
    fun `handleProcessStateChanged maps process state before delegating`() = runTest {
        val helper = mockk<CompileActionsHelper>(relaxed = true)
        val synchronizer = mockk<CompileRuntimeObserver.FileTreeSynchronizer>(relaxed = true)
        val observer = CompileRuntimeObserver(helper, synchronizer)

        observer.handleProcessStateChanged(ProcessManager.ProcessState.RUNNING)

        coVerify(exactly = 1) {
            helper.handleProcessStateChanged(CompileActionsHelper.ExecutionProcessState.RUNNING)
        }
    }

    @Test
    fun `handleCompileEvent delegates success and syncs file tree`() = runTest {
        val helper = mockk<CompileActionsHelper>(relaxed = true)
        val synchronizer = mockk<CompileRuntimeObserver.FileTreeSynchronizer>(relaxed = true)
        val observer = CompileRuntimeObserver(helper, synchronizer)
        val event = CompileEvent.Success(
            CompileProjectUseCase.Report(
                action = CompileProjectUseCase.Action.BUILD,
                summary = "done",
                artifact = CompileProjectUseCase.BuildArtifact(
                    path = "/tmp/demo",
                    exportedPath = "/tmp/.tinaide/artifacts/demo",
                    kind = CompileProjectUseCase.BuildArtifactKind.EXECUTABLE
                )
            )
        )

        observer.handleCompileEvent(event)

        coVerify(exactly = 1) { helper.handleCompileSuccess(event) }
        coVerify(exactly = 1) {
            synchronizer.refreshAndRevealExportedArtifact("/tmp/.tinaide/artifacts/demo")
        }
    }

    @Test
    fun `handleCompileEvent delegates error without syncing file tree`() = runTest {
        val helper = mockk<CompileActionsHelper>(relaxed = true)
        val synchronizer = mockk<CompileRuntimeObserver.FileTreeSynchronizer>(relaxed = true)
        val observer = CompileRuntimeObserver(helper, synchronizer)
        val event = CompileEvent.Error(
            action = CompileProjectUseCase.Action.BUILD,
            message = "failed",
            throwable = null
        )

        observer.handleCompileEvent(event)

        coVerify(exactly = 1) { helper.handleCompileError(event) }
        coVerify(exactly = 0) { synchronizer.refreshAndRevealExportedArtifact(any()) }
    }

    @Test
    fun `file tree synchronizer reveals existing exported artifact without refresh`() = runTest {
        val fileTreeState = mockk<FileTreeState>(relaxed = true)
        val synchronizer = FileTreeStateCompileFileTreeSynchronizer { fileTreeState }
        val exportedArtifact = Files.createTempFile("compile-runtime-observer", ".bin").toFile()

        try {
            synchronizer.refreshAndRevealExportedArtifact(exportedArtifact.absolutePath)

            coVerify(exactly = 0) { fileTreeState.refresh() }
            coVerify(exactly = 1) {
                fileTreeState.reveal(
                    match { it.absolutePath == exportedArtifact.absolutePath },
                    selectTarget = false
                )
            }
        } finally {
            exportedArtifact.delete()
        }
    }

    @Test
    fun `file tree synchronizer skips missing exported artifact`() = runTest {
        val fileTreeState = mockk<FileTreeState>(relaxed = true)
        val synchronizer = FileTreeStateCompileFileTreeSynchronizer { fileTreeState }
        val missingPath = Files.createTempDirectory("compile-runtime-observer-missing")
            .resolve("missing.bin")
            .toFile()

        synchronizer.refreshAndRevealExportedArtifact(missingPath.absolutePath)

        coVerify(exactly = 0) { fileTreeState.refresh() }
        coVerify(exactly = 0) { fileTreeState.reveal(any(), any()) }
        assertThat(missingPath.exists()).isFalse()
    }
}

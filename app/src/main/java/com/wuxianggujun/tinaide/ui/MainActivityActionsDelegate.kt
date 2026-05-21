package com.wuxianggujun.tinaide.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import java.io.File
import kotlinx.coroutines.launch

/**
 * MainActivity 的编辑动作宿主委托。
 *
 * 负责转发编辑动作入口，并观察 ActionsViewModel 的 UI 事件。
 */
class MainActivityActionsDelegate(
    private val lifecycleOwner: LifecycleOwner,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val actionsViewModel: MainActivityActionsViewModel,
    private val onToastSuccess: (String) -> Unit,
    private val onToastError: (String) -> Unit,
    private val onToastInfo: (String) -> Unit,
) {
    fun registerObservers() {
        observeUiEvents()
    }

    fun saveCurrentFile(editorContainerState: EditorContainerState) {
        actionsViewModel.saveCurrentFile(editorContainerState)
    }

    fun saveAllFiles(editorContainerState: EditorContainerState? = null) {
        actionsViewModel.saveAllFiles(editorContainerState)
    }

    fun performUndo(editorContainerState: EditorContainerState) {
        actionsViewModel.performUndo(editorContainerState)
    }

    fun performRedo(editorContainerState: EditorContainerState) {
        actionsViewModel.performRedo(editorContainerState)
    }

    fun toggleBookmark(editorContainerState: EditorContainerState) {
        actionsViewModel.toggleBookmark(editorContainerState)
    }

    fun goToNextBookmark(editorContainerState: EditorContainerState) {
        actionsViewModel.goToNextBookmark(editorContainerState)
    }

    fun goToPreviousBookmark(editorContainerState: EditorContainerState) {
        actionsViewModel.goToPreviousBookmark(editorContainerState)
    }

    fun navigateToBookmark(
        editorContainerState: EditorContainerState,
        filePath: String,
        line: Int,
    ) {
        actionsViewModel.navigateToBookmark(editorContainerState, filePath, line)
    }

    fun formatCode(editorContainerState: EditorContainerState) {
        actionsViewModel.formatCode(editorContainerState)
    }

    fun copyPathToClipboard(file: File) {
        actionsViewModel.copyPathToClipboard(file)
    }

    fun copyNameToClipboard(file: File) {
        actionsViewModel.copyNameToClipboard(file)
    }

    fun copyRelativePathToClipboard(file: File) {
        actionsViewModel.copyRelativePathToClipboard(file)
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                actionsViewModel.uiEvents.collect { event ->
                    when (event) {
                        is MainActivityActionsViewModel.UiEvent.ShowToast -> {
                            when (event.type) {
                                MainActivityActionsViewModel.ToastType.SUCCESS -> {
                                    onToastSuccess(event.message)
                                }

                                MainActivityActionsViewModel.ToastType.ERROR -> {
                                    onToastError(event.message)
                                }

                                MainActivityActionsViewModel.ToastType.INFO -> {
                                    onToastInfo(event.message)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun createMainActivityActionsDelegate(
    activity: ComponentActivity,
    actionsViewModel: MainActivityActionsViewModel,
    onToastSuccess: (String) -> Unit,
    onToastError: (String) -> Unit,
    onToastInfo: (String) -> Unit,
): MainActivityActionsDelegate = MainActivityActionsDelegate(
    lifecycleOwner = activity,
    lifecycleScope = activity.lifecycleScope,
    actionsViewModel = actionsViewModel,
    onToastSuccess = onToastSuccess,
    onToastError = onToastError,
    onToastInfo = onToastInfo,
)

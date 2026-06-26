package com.wuxianggujun.tinaide.ui.compose.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.ui.GitViewModel
import com.wuxianggujun.tinaide.ui.compose.components.FileTreeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun mainActivityHostEffects(
    projectContext: IProjectContext,
    fileTreeState: FileTreeState,
    gitViewModel: GitViewModel,
    uiScope: CoroutineScope,
) {
    val projectRoot = projectContext.getCurrentProject()?.rootPath
    LaunchedEffect(projectRoot) {
        val rootPath = projectRoot ?: return@LaunchedEffect
        fileTreeState.loadRoot(rootPath)
        gitViewModel.setProjectPath(rootPath)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, fileTreeState, uiScope) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> fileTreeState.setAppVisibility(true)
                Lifecycle.Event.ON_STOP -> fileTreeState.setAppVisibility(false)
                Lifecycle.Event.ON_RESUME -> {
                    if (fileTreeState.consumePendingResumeRefresh()) {
                        uiScope.launch { fileTreeState.refresh() }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

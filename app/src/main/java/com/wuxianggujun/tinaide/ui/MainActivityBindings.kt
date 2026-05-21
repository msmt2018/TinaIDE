package com.wuxianggujun.tinaide.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.wuxianggujun.tinaide.core.commands.HostCommandExecutor
import com.wuxianggujun.tinaide.file.FileChangeListener
import com.wuxianggujun.tinaide.file.IFileWatchService
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.plugin.script.api.PluginDiagnosticsProviderHolder
import com.wuxianggujun.tinaide.plugin.script.api.PluginEditorBridgeHolder
import com.wuxianggujun.tinaide.plugin.script.api.PluginHostCommandExecutorHolder
import com.wuxianggujun.tinaide.plugin.script.api.PluginHostEventDispatcher
import com.wuxianggujun.tinaide.ui.TinaPluginEditorBridge
import com.wuxianggujun.tinaide.ui.compose.components.BottomPanelDragState
import com.wuxianggujun.tinaide.ui.compose.components.FileTreeState
import com.wuxianggujun.tinaide.ui.compose.screens.main.MainActivityLocationDialogState
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorActionsState
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val FILE_TREE_WATCH_DEBOUNCE_MS = 96L

@Composable
internal fun BindMainActivityFileTreeState(
    fileTreeState: FileTreeState,
    fileTreeActionBridge: MainActivityFileTreeActionBridge,
) {
    val fileWatchService: IFileWatchService = koinInject()
    val projectContext: IProjectContext = koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentProject by projectContext.currentProjectFlow.collectAsState()

    DisposableEffect(fileTreeState, fileTreeActionBridge) {
        fileTreeActionBridge.bind(fileTreeState)
        onDispose {
            fileTreeActionBridge.clear()
        }
    }

    DisposableEffect(fileTreeState, fileWatchService, currentProject?.rootPath) {
        val rootPath = currentProject?.rootPath
        if (rootPath.isNullOrBlank()) {
            onDispose { }
        } else {
            val pendingLock = Any()
            val pendingChanges = linkedMapOf<String, FileTreeState.PendingFileChange>()
            var flushJob: Job? = null

            fun enqueueFileChange(file: java.io.File, kind: FileTreeState.FileChangeKind) {
                val normalizedPath = file.absolutePath
                synchronized(pendingLock) {
                    val change = FileTreeState.PendingFileChange(normalizedPath, kind)
                    val merged = mergePendingFileChange(
                        existing = pendingChanges[normalizedPath],
                        incoming = change
                    )
                    if (merged == null) {
                        pendingChanges.remove(normalizedPath)
                    } else {
                        pendingChanges[normalizedPath] = merged
                    }
                    flushJob?.cancel()
                    flushJob = scope.launch {
                        delay(FILE_TREE_WATCH_DEBOUNCE_MS)
                        val snapshot = synchronized(pendingLock) {
                            flushJob = null
                            pendingChanges.values.toList().also { pendingChanges.clear() }
                        }
                        if (snapshot.isNotEmpty()) {
                            fileTreeState.handleFileChanges(snapshot)
                        }
                    }
                }
            }

            val listener = object : FileChangeListener {
                override fun onFileCreated(file: java.io.File) {
                    enqueueFileChange(file, FileTreeState.FileChangeKind.CREATED)
                }

                override fun onFileModified(file: java.io.File) = Unit

                override fun onFileDeleted(file: java.io.File) {
                    enqueueFileChange(file, FileTreeState.FileChangeKind.DELETED)
                }
            }
            val registration = fileWatchService.addFileWatcher(rootPath, listener)
            onDispose {
                synchronized(pendingLock) {
                    flushJob?.cancel()
                    flushJob = null
                    pendingChanges.clear()
                }
                registration.dispose()
            }
        }
    }
}

private fun mergePendingFileChange(
    existing: FileTreeState.PendingFileChange?,
    incoming: FileTreeState.PendingFileChange
): FileTreeState.PendingFileChange? {
    if (existing == null) return incoming
    if (existing.kind == incoming.kind) return incoming

    return when {
        existing.kind == FileTreeState.FileChangeKind.CREATED &&
            incoming.kind == FileTreeState.FileChangeKind.DELETED -> null

        existing.kind == FileTreeState.FileChangeKind.DELETED &&
            incoming.kind == FileTreeState.FileChangeKind.CREATED -> {
            incoming.copy(kind = FileTreeState.FileChangeKind.CREATED)
        }

        else -> incoming
    }
}

@Composable
internal fun BindMainActivityBottomPanelState(
    bottomPanelState: BottomPanelDragState,
    bottomPanelController: MainActivityBottomPanelActionBridge,
) {
    DisposableEffect(bottomPanelState, bottomPanelController) {
        bottomPanelController.bind(bottomPanelState)
        onDispose {
            bottomPanelController.clear()
        }
    }
}

@Composable
internal fun BindMainActivityEditorState(
    editorContainerState: EditorContainerState,
    editorActionBridge: MainActivityEditorActionBridge,
    actionsDelegate: MainActivityActionsDelegate,
    shortcutDispatcher: MainActivityShortcutDispatcher,
    lspNavigationDelegate: LspNavigationDelegate,
    lspEditorActionsDelegate: LspEditorActionsDelegate,
    editorActionsState: EditorActionsState,
    locationDialogState: MainActivityLocationDialogState,
    onFileNotExist: () -> Unit,
    onToastInfo: (String) -> Unit,
    onToastError: (String) -> Unit,
) {
    LaunchedEffect(
        editorContainerState,
        editorActionBridge,
        actionsDelegate,
        shortcutDispatcher,
        lspNavigationDelegate,
        lspEditorActionsDelegate,
        editorActionsState,
        locationDialogState,
    ) {
        editorActionBridge.bind(
            editorContainerState = editorContainerState,
            onFileNotExist = onFileNotExist,
        )
        shortcutDispatcher.bind(
            editorContainerState = editorContainerState,
            actionsDelegate = actionsDelegate,
        )
        lspNavigationDelegate.bind(
            editorContainerState = editorContainerState,
            locationDialogState = locationDialogState,
            onToastInfo = onToastInfo,
            onToastError = onToastError,
        )
        lspEditorActionsDelegate.bind(
            editorContainerState = editorContainerState,
            editorActionsState = editorActionsState,
            onToastInfo = onToastInfo,
            onToastError = onToastError,
        )
    }
}

@Composable
internal fun BindMainActivityEditorHost(
    editorContainerState: EditorContainerState,
    editorActionBridge: MainActivityEditorActionBridge,
    actionsDelegate: MainActivityActionsDelegate,
    shortcutDispatcher: MainActivityShortcutDispatcher,
    editorActionsState: EditorActionsState,
    locationDialogState: MainActivityLocationDialogState,
    scope: CoroutineScope,
    onFileNotExist: () -> Unit,
    onToastInfo: (String) -> Unit,
    onToastError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lspNavigationDelegate = remember(context, scope) {
        LspNavigationDelegate(
            context = context,
            scope = scope,
        )
    }
    val lspEditorActionsDelegate = remember(context, scope) {
        LspEditorActionsDelegate(
            context = context,
            scope = scope,
        )
    }

    BindMainActivityEditorState(
        editorContainerState = editorContainerState,
        editorActionBridge = editorActionBridge,
        actionsDelegate = actionsDelegate,
        shortcutDispatcher = shortcutDispatcher,
        lspNavigationDelegate = lspNavigationDelegate,
        lspEditorActionsDelegate = lspEditorActionsDelegate,
        editorActionsState = editorActionsState,
        locationDialogState = locationDialogState,
        onFileNotExist = onFileNotExist,
        onToastInfo = onToastInfo,
        onToastError = onToastError,
    )
    BindPluginEditorBridge(editorContainerState)
}

@Composable
internal fun BindPluginEditorBridge(
    editorContainerState: EditorContainerState,
) {
    DisposableEffect(editorContainerState) {
        val bridge = TinaPluginEditorBridge(
            stateProvider = { editorContainerState }
        )
        PluginEditorBridgeHolder.set(bridge)
        onDispose {
            if (PluginEditorBridgeHolder.get() === bridge) {
                PluginEditorBridgeHolder.clear()
            }
        }
    }
}

@Composable
internal fun BindPluginHostCommandExecutor(
    hostCommandExecutor: HostCommandExecutor,
) {
    DisposableEffect(hostCommandExecutor) {
        PluginHostCommandExecutorHolder.set(hostCommandExecutor)
        onDispose {
            if (PluginHostCommandExecutorHolder.get() === hostCommandExecutor) {
                PluginHostCommandExecutorHolder.clear()
            }
        }
    }
}

@Composable
internal fun BindPluginDiagnosticsProvider(
    bottomPanelViewModel: BottomPanelViewModel,
    projectRootProvider: () -> String?,
) {
    val latestProjectRootProvider = rememberUpdatedState(projectRootProvider)
    DisposableEffect(bottomPanelViewModel) {
        val provider = TinaPluginDiagnosticsProvider(
            diagnosticsProvider = { bottomPanelViewModel.diagnostics.value },
            projectRootProvider = { latestProjectRootProvider.value() }
        )
        PluginDiagnosticsProviderHolder.set(provider)
        onDispose {
            if (PluginDiagnosticsProviderHolder.get() === provider) {
                PluginDiagnosticsProviderHolder.clear()
            }
        }
    }
}

@Composable
internal fun BindPluginHostEvents(
    projectContext: IProjectContext,
    isCompiling: Boolean,
) {
    val currentProject by projectContext.currentProjectFlow.collectAsState()
    val currentProjectRoot = currentProject?.rootPath
    var previousProjectRoot by remember { mutableStateOf<String?>(null) }
    var previousIsCompiling by remember { mutableStateOf(false) }

    LaunchedEffect(currentProjectRoot) {
        val previous = previousProjectRoot
        if (previous != currentProjectRoot) {
            if (!previous.isNullOrBlank()) {
                PluginHostEventDispatcher.emitProjectClosed(previous)
            }
            if (!currentProjectRoot.isNullOrBlank()) {
                PluginHostEventDispatcher.emitProjectOpened(currentProjectRoot)
            }
            previousProjectRoot = currentProjectRoot
        }
    }

    LaunchedEffect(isCompiling, currentProjectRoot) {
        if (isCompiling != previousIsCompiling) {
            if (isCompiling) {
                PluginHostEventDispatcher.emitBuildStarted(currentProjectRoot)
            } else {
                PluginHostEventDispatcher.emitBuildFinished(currentProjectRoot)
            }
            previousIsCompiling = isCompiling
        }
    }
}

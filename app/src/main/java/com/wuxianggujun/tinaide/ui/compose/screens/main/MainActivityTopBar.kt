package com.wuxianggujun.tinaide.ui.compose.screens.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuxianggujun.tinaide.core.compile.RunConfiguration
import com.wuxianggujun.tinaide.core.compile.RunConfigurationManager
import com.wuxianggujun.tinaide.core.config.DebugToolbarPosition
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.DebugViewModel
import com.wuxianggujun.tinaide.ui.compose.components.DebugBar
import com.wuxianggujun.tinaide.ui.compose.components.DebugStatus
import com.wuxianggujun.tinaide.ui.compose.components.RunConfigSelector
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDivider
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionTitle
import com.wuxianggujun.tinaide.ui.compose.icons.rememberTinaPainter
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState.SplitEditorLayout

internal class TopBarCallbacks(
    val onOpenDrawer: () -> Unit,
    val onOpenCommandPalette: () -> Unit,
    val onBuild: () -> Unit,
    val onCompile: () -> Unit,
    val onRebuildAndRun: () -> Unit,
    val onCompileInTerminal: () -> Unit,
    val onDebug: () -> Unit,
    val onSave: () -> Unit,
    val onSaveAll: () -> Unit,
    val onFormatCode: () -> Unit,
    val onGotoLine: () -> Unit,
    val onNavigateBack: () -> Unit = {},
    val onNavigateForward: () -> Unit = {},
    val onPeekDefinition: () -> Unit = {},
    val onGotoDefinition: () -> Unit = {},
    val onFindReferences: () -> Unit = {},
    val onGotoTypeDefinition: () -> Unit = {},
    val onGotoImplementation: () -> Unit = {},
    val onCallHierarchyIncoming: () -> Unit = {},
    val onCodeActions: () -> Unit = {},
    val onRenameSymbol: () -> Unit = {},
    val onSwitchHeaderSource: () -> Unit = {},
    val onToggleSplitEditor: () -> Unit = {},
    val onSetSplitEditorLayout: (SplitEditorLayout) -> Unit = {},
    val onMoveTabToSecondaryPane: () -> Unit = {},
    val onCopyTabToSecondaryPane: () -> Unit = {},
    val onOpenExplorer: () -> Unit,
    val onOpenGlobalSearch: () -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onPrevBookmark: () -> Unit,
    val onNextBookmark: () -> Unit,
    val onOpenTerminal: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onExitWorkspace: () -> Unit,
    val onPackageApk: () -> Unit = {},
    val onCmakeOpenArtifactsDir: () -> Unit = {},
    val onCmakeReconfigure: () -> Unit = {},
    val onCmakeCleanAndReconfigure: () -> Unit = {},
    val onCmakeClearBuildDir: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainActivityTopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    isCompiling: Boolean,
    isDirty: Boolean,
    isDebugActive: Boolean,
    debugStatus: DebugStatus,
    runConfigManager: RunConfigurationManager,
    onRunConfigManagerChange: (RunConfigurationManager) -> Unit,
    onEditConfig: (RunConfiguration?) -> Unit,
    onShowRunConfigDialog: () -> Unit,
    callbacks: TopBarCallbacks,
    debugViewModel: DebugViewModel,
    @StringRes overflowCommandSectionTitleRes: Int,
    overflowCommands: List<MainActivityCommand>,
    onExecuteCommand: (MainActivityCommand) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val debugToolbarPosition by Prefs.debugToolbarPositionFlow.collectAsStateWithLifecycle()
    val showDebugBarInTop =
        isDebugActive && debugToolbarPosition != DebugToolbarPosition.BOTTOM

    @Composable
    fun OverflowMenuButton() {
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Strings.content_desc_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TinaDropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                TinaDropdownMenuItem(
                    text = { Text(stringResource(Strings.command_palette_title)) },
                    onClick = {
                        showMenu = false
                        callbacks.onOpenCommandPalette()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                if (overflowCommands.isNotEmpty()) {
                    TinaDropdownMenuDivider()
                    TinaDropdownMenuSectionHeader {
                        TinaDropdownMenuSectionTitle(
                            text = stringResource(overflowCommandSectionTitleRes),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    overflowCommands.forEach { command ->
                        TinaDropdownMenuItem(
                            text = { Text(command.titleText()) },
                            enabled = command.enabled,
                            onClick = {
                                showMenu = false
                                onExecuteCommand(command)
                            }
                        )
                    }
                }
            }
        }
    }

    TopAppBar(
        expandedHeight = 48.dp,
        title = {
            val screenWidthPx = LocalWindowInfo.current.containerSize.width
            val compactTitleWidthPx = with(LocalDensity.current) { 360.dp.toPx() }
            val useCompactTitleLayout = screenWidthPx < compactTitleWidthPx
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDebugBarInTop) {
                    DebugBar(
                        debugStatus = debugStatus,
                        onContinue = { debugViewModel.continueExecution() },
                        onStepOver = { debugViewModel.stepOver() },
                        onStepInto = { debugViewModel.stepInto() },
                        onStepOut = { debugViewModel.stepOut() },
                        onPause = { debugViewModel.pauseExecution() },
                        onStop = { debugViewModel.stopSession() },
                        modifier = if (useCompactTitleLayout) Modifier.fillMaxWidth() else Modifier
                    )
                } else if (!isDebugActive) {
                    val defaultRunConfigName = stringResource(Strings.run_config_default_name)
                    RunConfigSelector(
                        configManager = runConfigManager,
                        onSelectConfig = { id ->
                            onRunConfigManagerChange(runConfigManager.selectConfig(id))
                        },
                        onAddConfig = {
                            onEditConfig(RunConfiguration(name = defaultRunConfigName))
                            onShowRunConfigDialog()
                        },
                        onEditConfig = {
                            onEditConfig(runConfigManager.selectedConfig)
                            onShowRunConfigDialog()
                        },
                        onDuplicateConfig = { id ->
                            onRunConfigManagerChange(runConfigManager.duplicateConfig(id))
                        },
                        onDeleteConfig = { id ->
                            onRunConfigManagerChange(runConfigManager.removeConfig(id))
                        },
                        onBuild = callbacks.onBuild,
                        onRun = callbacks.onCompile,
                        onRebuildAndRun = callbacks.onRebuildAndRun,
                        onRunInTerminal = callbacks.onCompileInTerminal,
                        onDebug = callbacks.onDebug,
                        isBuildEnabled = !isCompiling,
                        isRunEnabled = !isCompiling,
                        isDebugEnabled = !isCompiling,
                        buildIconRes = Drawables.ic_build,
                        debugIconRes = Drawables.ic_debug,
                        runTint = Color(0xFF4CAF50),
                        disabledTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        configSegmentMaxWidth = if (useCompactTitleLayout) 72.dp else 110.dp,
                        showBuildButton = true
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onOpenDrawer) {
                Icon(Icons.Default.Menu, stringResource(Strings.content_desc_open_file_tree))
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 0.dp)
            ) {
                if (!isDebugActive) {
                    SaveActionButton(
                        isDirty = isDirty,
                        onSave = callbacks.onSave
                    )
                }
                OverflowMenuButton()
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun SaveActionButton(
    isDirty: Boolean,
    onSave: () -> Unit
) {
    IconButton(onClick = onSave, enabled = isDirty) {
        Icon(
            painter = rememberTinaPainter(Drawables.ic_save),
            contentDescription = stringResource(Strings.content_desc_save),
            tint = if (isDirty) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MainActivityCommand.titleText(): String {
    return when (val commandTitle = title) {
        is MainActivityCommandText.Literal -> commandTitle.value
        is MainActivityCommandText.Resource -> stringResource(commandTitle.resId)
    }
}

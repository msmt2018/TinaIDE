package com.wuxianggujun.tinaide.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.wuxianggujun.tinaide.editor.IEditorManager
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.file.IProjectSession

/**
 * 收口 MainActivity 的工作区宿主装配，避免 Activity 继续堆叠对话框、
 * 外部文件与工作区动作的初始化细节。
 */
internal class MainActivityWorkspaceHost(
    val dialogCoordinator: MainActivityDialogCoordinator,
    val externalFileLauncher: MainActivityExternalFileLauncher,
    val workspaceActions: MainActivityWorkspaceActionsDelegate,
)

internal fun createMainActivityWorkspaceHost(
    activity: ComponentActivity,
    projectSession: IProjectSession,
    projectContext: IProjectContext,
    editorManager: IEditorManager,
    gitViewModel: GitViewModel,
    bottomPanelViewModel: BottomPanelViewModel,
    bottomPanelController: BottomPanelController,
    onToastSuccess: (String) -> Unit,
    onToastError: (String) -> Unit,
    onToastInfo: (String) -> Unit,
): MainActivityWorkspaceHost {
    val dialogCoordinator = MainActivityDialogCoordinator(
        activity = activity,
        lifecycleScope = activity.lifecycleScope,
        editorManager = editorManager,
        projectSession = projectSession,
        projectContext = projectContext,
        onToastSuccess = onToastSuccess,
        onToastError = onToastError,
    )
    val externalFileLauncher = MainActivityExternalFileLauncherDelegate(
        context = activity,
        scope = activity.lifecycleScope,
        onInfo = onToastInfo,
        onError = onToastError,
    )
    val workspaceActions = MainActivityWorkspaceActionsDelegate(
        context = activity,
        activityStarter = activity::startActivity,
        lifecycleScope = activity.lifecycleScope,
        projectContext = projectContext,
        editorManager = editorManager,
        gitViewModel = gitViewModel,
        bottomPanelViewModel = bottomPanelViewModel,
        bottomPanelController = bottomPanelController,
        onToastSuccess = onToastSuccess,
        onToastError = onToastError,
        onToastInfo = onToastInfo,
    )

    return MainActivityWorkspaceHost(
        dialogCoordinator = dialogCoordinator,
        externalFileLauncher = externalFileLauncher,
        workspaceActions = workspaceActions,
    )
}

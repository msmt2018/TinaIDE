package com.wuxianggujun.tinaide.ui

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.wuxianggujun.tinaide.file.IProjectContext

/**
 * 收口 MainActivity 的导航宿主装配，避免 Activity 继续堆叠
 * ActivityResult 注册和导航委托初始化细节。
 */
internal class MainActivityNavigationHost(
    val navigationDelegate: MainActivityNavigationDelegate,
)

internal fun createMainActivityNavigationHost(
    activity: ComponentActivity,
    projectContext: IProjectContext,
    editorActionBridge: MainActivityEditorActionBridge,
    bottomPanelController: BottomPanelController,
    onToastError: (String) -> Unit,
): MainActivityNavigationHost {
    var navigationDelegate: MainActivityNavigationDelegate? = null
    val globalSearchLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        navigationDelegate?.handleGlobalSearchResult(result.resultCode, result.data)
    }
    val delegate = MainActivityNavigationDelegate(
        context = activity,
        projectContext = projectContext,
        launchIntent = globalSearchLauncher::launch,
        onNavigateToSearchResult = { filePath, lineNumber ->
            editorActionBridge.navigateToSearchResult(filePath, lineNumber)
        },
        onNavigateToDiagnostic = { diagnostic, editorContainerState ->
            MainActivityNavigationHelper.navigateToDiagnostic(
                diagnostic = diagnostic,
                editorContainerState = editorContainerState,
                bottomPanelController = bottomPanelController,
                scope = activity.lifecycleScope,
            )
        },
        onToastError = onToastError,
    )
    navigationDelegate = delegate

    return MainActivityNavigationHost(
        navigationDelegate = delegate,
    )
}

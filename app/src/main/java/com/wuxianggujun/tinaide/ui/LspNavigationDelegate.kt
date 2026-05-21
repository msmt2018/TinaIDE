package com.wuxianggujun.tinaide.ui

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.lsp.LocationItem
import com.wuxianggujun.tinaide.ui.compose.screens.main.MainActivityLocationDialogState
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * LSP 导航请求的处理委托。
 *
 * 从 [MainActivity] 提取出来，负责处理"跳转到定义/引用/类型定义/实现/头文件切换"等
 * LSP 导航请求的调度和结果处理。
 */
class LspNavigationDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    /**
     * 导航结果回调，由调用方设置用于更新 UI 状态。
     */
    var onNavigationStarted: (() -> Unit)? = null
    var onNavigationResults: ((title: String, results: List<LocationItem>) -> Unit)? = null
    var onNavigationDismissed: (() -> Unit)? = null
    var onToastInfo: ((String) -> Unit)? = null
    var onToastError: ((String) -> Unit)? = null

    /**
     * 将 MainActivity 的 UI 状态和编辑器导航入口绑定到当前委托。
     */
    internal fun bind(
        editorContainerState: EditorContainerState,
        locationDialogState: MainActivityLocationDialogState,
        onToastInfo: (String) -> Unit,
        onToastError: (String) -> Unit,
    ) {
        onNavigationStarted = locationDialogState::showLoading
        onNavigationResults = locationDialogState::showResults
        onNavigationDismissed = locationDialogState::dismiss
        this.onToastInfo = onToastInfo
        this.onToastError = onToastError
        editorContainerState.onLspNavigationRequested = { tabId, navigationType ->
            handleNavigationRequest(tabId, navigationType, editorContainerState)
        }
    }

    /**
     * 处理 LSP 导航请求。
     *
     * 该方法作为 [EditorContainerState.onLspNavigationRequested] 的回调实现。
     */
    fun handleNavigationRequest(
        tabId: String,
        navigationType: String,
        editorContainerState: EditorContainerState,
    ) {
        val cursor = editorContainerState.getCursorPositionInActiveTab() ?: return
        val line = cursor.line
        val column = cursor.column
        val isPeekDefinition = navigationType == "peekDefinition"

        if (isPeekDefinition) {
            onNavigationDismissed?.invoke()
        } else {
            editorContainerState.dismissPeekDefinitionPanel()
            onNavigationStarted?.invoke()
        }

        scope.launch {
            try {
                when (navigationType) {
                    "definition" -> {
                        val title = context.getString(com.wuxianggujun.tinaide.core.i18n.R.string.lsp_location_dialog_title_definition)
                        val results = editorContainerState.gotoDefinition(tabId, line, column)
                        dispatchResults(title, results, editorContainerState)
                    }
                    "peekDefinition" -> {
                        val title = context.getString(com.wuxianggujun.tinaide.core.i18n.R.string.lsp_location_dialog_title_peek_definition)
                        editorContainerState.showPeekDefinitionLoading(tabId, title)
                        val results = editorContainerState.gotoDefinition(tabId, line, column)
                        editorContainerState.showPeekDefinitionResults(tabId, title, results)
                    }
                    "references" -> {
                        val results = editorContainerState.findReferences(tabId, line, column)
                        val title = context.getString(com.wuxianggujun.tinaide.core.i18n.R.string.lsp_location_dialog_title_references, results.size)
                        dispatchResults(title, results, editorContainerState)
                    }
                    "typeDefinition" -> {
                        val title = context.getString(com.wuxianggujun.tinaide.core.i18n.R.string.lsp_location_dialog_title_type_definition)
                        val results = editorContainerState.gotoTypeDefinition(tabId, line, column)
                        dispatchResults(title, results, editorContainerState)
                    }
                    "implementation" -> {
                        val title = context.getString(com.wuxianggujun.tinaide.core.i18n.R.string.lsp_location_dialog_title_implementation)
                        val results = editorContainerState.gotoImplementation(tabId, line, column)
                        dispatchResults(title, results, editorContainerState)
                    }
                    "callHierarchyIncoming" -> {
                        val results = editorContainerState.callHierarchyIncomingCalls(tabId, line, column)
                        if (results.isEmpty()) {
                            onNavigationDismissed?.invoke()
                            onToastInfo?.invoke(Strings.lsp_call_hierarchy_no_incoming_calls.strOr(context))
                            return@launch
                        }
                        val title = context.getString(
                            com.wuxianggujun.tinaide.core.i18n.R.string.lsp_location_dialog_title_call_hierarchy_incoming,
                            results.size
                        )
                        dispatchResults(
                            title = title,
                            results = results,
                            editorContainerState = editorContainerState,
                            autoNavigateSingle = false
                        )
                    }
                    "switchHeaderSource" -> {
                        onNavigationDismissed?.invoke()
                        val targetPath = editorContainerState.switchSourceHeader(tabId)
                        if (targetPath != null) {
                            val targetFile = File(targetPath)
                            if (targetFile.isFile) {
                                editorContainerState.openFile(targetFile)
                            } else {
                                onToastInfo?.invoke(Strings.lsp_no_results.strOr(context))
                            }
                        } else {
                            onToastInfo?.invoke(Strings.lsp_no_results.strOr(context))
                        }
                    }
                    else -> {
                        onNavigationDismissed?.invoke()
                    }
                }
            } catch (e: Exception) {
                if (isPeekDefinition) {
                    editorContainerState.dismissPeekDefinitionPanel(tabId)
                } else {
                    onNavigationDismissed?.invoke()
                }
                val errorMessage = if (navigationType == "callHierarchyIncoming") {
                    Strings.lsp_error_call_hierarchy_failed.strOr(context)
                } else {
                    Strings.lsp_error_navigation_failed.strOr(context)
                }
                onToastError?.invoke(errorMessage)
            }
        }
    }

    private fun dispatchResults(
        title: String,
        results: List<LocationItem>,
        editorContainerState: EditorContainerState,
        autoNavigateSingle: Boolean = true,
    ) {
        if (autoNavigateSingle && results.size == 1) {
            MainActivityNavigationHelper.navigateToLocation(results.first(), editorContainerState)
            onNavigationDismissed?.invoke()
        } else {
            onNavigationResults?.invoke(title, results)
        }
    }
}

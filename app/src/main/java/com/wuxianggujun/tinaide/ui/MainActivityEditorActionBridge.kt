package com.wuxianggujun.tinaide.ui

import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState

/**
 * MainActivity 的编辑器宿主桥接。
 *
 * 用最小动作面连接 Activity 外部入口与 Compose 内部编辑器状态，
 * 避免在宿主层直接持有可变的编辑器状态引用。
 */
class MainActivityEditorActionBridge {
    private var navigateToSearchResultAction: ((String, Int) -> Unit)? = null

    fun bind(
        editorContainerState: EditorContainerState,
        onFileNotExist: () -> Unit,
    ) {
        navigateToSearchResultAction = { filePath, lineNumber ->
            MainActivityNavigationHelper.navigateToSearchResult(
                filePath = filePath,
                lineNumber = lineNumber,
                editorContainerState = editorContainerState,
                onFileNotExist = onFileNotExist,
            )
        }
    }

    fun navigateToSearchResult(filePath: String, lineNumber: Int): Boolean {
        val action = navigateToSearchResultAction ?: return false
        action(filePath, lineNumber)
        return true
    }

    fun clear() {
        navigateToSearchResultAction = null
    }
}

package com.wuxianggujun.tinaide.ai.tools.editor

import com.wuxianggujun.tinaide.ai.tools.executor.editor.CurrentFileInfo
import com.wuxianggujun.tinaide.ai.tools.executor.editor.EditorToolCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.editor.SelectedCodeInfo

internal class RecordingEditorToolCallbacks(
    private val currentFile: CurrentFileInfo? = null,
    private val selectedCode: SelectedCodeInfo? = null,
    private val formatCodeResult: Boolean = true,
    private val formatCodeRangeResult: Boolean = true
) : EditorToolCallbacks {
    var insertedCode: String? = null
        private set
    var replacedCode: String? = null
        private set
    var formattedFilePath: String? = null
        private set
    var formattedRange: Triple<String, Int, Int>? = null
        private set

    override fun getCurrentFile(): CurrentFileInfo? = currentFile

    override fun getSelectedCode(): SelectedCodeInfo? = selectedCode

    override fun insertCode(code: String) {
        insertedCode = code
    }

    override fun replaceSelectedCode(code: String) {
        replacedCode = code
    }

    override suspend fun formatCode(filePath: String): Boolean {
        formattedFilePath = filePath
        return formatCodeResult
    }

    override suspend fun formatCodeRange(filePath: String, startLine: Int, endLine: Int): Boolean {
        formattedRange = Triple(filePath, startLine, endLine)
        return formatCodeRangeResult
    }
}

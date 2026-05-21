package com.wuxianggujun.tinaide.ai.tools.executor.editor

/**
 * 编辑器工具执行器的回调接口
 */
interface EditorToolCallbacks {
    fun getCurrentFile(): CurrentFileInfo?
    fun getSelectedCode(): SelectedCodeInfo?
    fun insertCode(code: String)
    fun replaceSelectedCode(code: String)

    /**
     * 格式化指定文件的代码
     * @param filePath 文件路径（相对于项目根目录）
     * @return 是否格式化成功
     */
    suspend fun formatCode(filePath: String): Boolean

    /**
     * 格式化指定文件的代码范围
     * @param filePath 文件路径（相对于项目根目录）
     * @param startLine 起始行号（1-based）
     * @param endLine 结束行号（1-based）
     * @return 是否格式化成功
     */
    suspend fun formatCodeRange(filePath: String, startLine: Int, endLine: Int): Boolean
}

data class CurrentFileInfo(
    val fileName: String,
    val language: String,
    val content: String
)

data class SelectedCodeInfo(
    val fileName: String,
    val language: String,
    val startLine: Int,
    val endLine: Int,
    val content: String
)

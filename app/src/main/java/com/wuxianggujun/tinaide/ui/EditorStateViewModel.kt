package com.wuxianggujun.tinaide.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 编辑器状态 ViewModel
 *
 * 职责：
 * - 管理编辑器的全局状态（是否有打开的文件、撤销/重做/脏状态）
 * - 从 EditorContainer 的回调中更新状态
 * - 为编辑器工具栏提供状态
 *
 * 设计原则：
 * - 保持与 MainActivity 中原有逻辑完全一致
 * - 使用 StateFlow 暴露状态给 Compose
 * - 简单的状态管理，没有复杂的业务逻辑
 */
class EditorStateViewModel : ViewModel() {

    // ============ UI 状态 ============

    private val _hasOpenFiles = MutableStateFlow(false)
    val hasOpenFiles: StateFlow<Boolean> = _hasOpenFiles.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    // 光标位置状态
    private val _cursorLine = MutableStateFlow(1)
    val cursorLine: StateFlow<Int> = _cursorLine.asStateFlow()

    private val _cursorColumn = MutableStateFlow(1)
    val cursorColumn: StateFlow<Int> = _cursorColumn.asStateFlow()

    // 文件编码
    private val _fileEncoding = MutableStateFlow("UTF-8")
    val fileEncoding: StateFlow<String> = _fileEncoding.asStateFlow()

    // ============ 公共方法 ============

    /**
     * 更新编辑器状态
     *
     * 从 EditorContainer 的 onEditorStateChanged 回调中调用
     * 逻辑与 MainActivity:463-467 完全一致
     *
     * @param hasFiles 是否有打开的文件
     * @param undo 是否可以撤销
     * @param redo 是否可以重做
     * @param dirty 当前文件是否有未保存修改
     */
    fun updateEditorState(hasFiles: Boolean, undo: Boolean, redo: Boolean, dirty: Boolean) {
        _hasOpenFiles.value = hasFiles
        _canUndo.value = undo
        _canRedo.value = redo
        _isDirty.value = dirty
    }

    /**
     * 更新光标位置
     *
     * @param line 行号（从1开始）
     * @param column 列号（从1开始）
     */
    fun updateCursorPosition(line: Int, column: Int) {
        _cursorLine.value = line
        _cursorColumn.value = column
    }

    /**
     * 更新文件编码
     *
     * @param encoding 文件编码（如 UTF-8, GBK 等）
     */
    fun updateFileEncoding(encoding: String) {
        _fileEncoding.value = encoding
    }
}

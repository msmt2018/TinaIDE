package com.wuxianggujun.tinaide.core.editor

import java.io.File

/**
 * 文档会话状态信息
 *
 * 架构说明：
 * - 数据模型定义在 core:common 层
 * - feature:editor 层的 DocumentSessionState 需要转换为此类型
 * - app 层通过此类型访问文档会话状态
 */
data class DocumentSessionInfo(
    val tabId: String,
    val file: File,
    val title: String,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lastSavedAt: Long? = null,
    val lastEditAt: Long? = null,
    val lastError: String? = null,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val hasExternalModification: Boolean = false
)

/**
 * 编辑器视图状态
 *
 * 用于保存和恢复编辑器的光标位置和滚动位置
 */
data class EditorViewState(
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val scrollX: Int = 0,
    val scrollY: Int = 0
)

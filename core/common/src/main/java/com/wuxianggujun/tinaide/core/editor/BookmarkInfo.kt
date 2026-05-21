package com.wuxianggujun.tinaide.core.editor

/**
 * 书签信息（按行标记）
 *
 * 约定：
 * - line 为 0-based（与 CodeEditor/EditorMotionEvent 一致）
 * - filePath 使用绝对路径
 *
 * 架构说明：
 * - 数据模型定义在 core:common 层
 * - feature:editor 层的 Bookmark 类型需要转换为此类型
 */
data class BookmarkInfo(
    val filePath: String,
    val line: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

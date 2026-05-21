package com.wuxianggujun.tinaide.ui.compose.components.editor

import java.io.File

/**
 * 内容类型枚举
 */
enum class ContentType {
    CODE, // 代码编辑器
    LARGE_TEXT, // 超大文件：分块只读查看器
    IMAGE, // 图片预览
    HEX, // 十六进制查看器
    JSON // JSON 树形查看器
}

/**
 * 编辑器标签页状态
 */
data class EditorTabState(
    val id: String,
    val file: File,
    val contentType: ContentType = ContentType.CODE,
    val isDirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    /**
     * 获取基本显示名称（不含路径）
     */
    val displayName: String
        get() = when (contentType) {
            ContentType.HEX -> "${file.name} [Hex]"
            ContentType.LARGE_TEXT -> "${file.name} [Large]"
            else -> file.name
        }

    /**
     * 获取带路径的显示名称（用于区分同名文件）
     * @param disambiguationPath 用于区分的路径部分，如果为空则只显示文件名
     */
    fun getDisplayNameWithPath(disambiguationPath: String?): String {
        val baseName = displayName
        return if (disambiguationPath.isNullOrEmpty()) {
            baseName
        } else {
            "$baseName ($disambiguationPath)"
        }
    }
}

/**
 * 编辑器工具栏状态（内部使用）
 */
internal data class EditorToolBarState(
    val hasFiles: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isDirty: Boolean
)

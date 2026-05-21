package com.wuxianggujun.tinaide.editor

import java.io.File

/**
 * 编辑器标签页数据
 */
data class EditorTab(
    val id: String,
    val file: File
)

/**
 * 编辑器标签页提供者接口（最小化接口，供 core 模块使用）
 *
 * 完整的 IEditorManager 接口保留在 app 模块中，
 * 此接口仅暴露 compile/plugin 等 core 模块所需的方法。
 */
interface IEditorTabProvider {
    fun getOpenTabs(): List<EditorTab>
    fun getActiveTabId(): String?
    fun getActiveFile(): File? {
        val activeTabId = getActiveTabId() ?: return null
        return getOpenTabs().firstOrNull { it.id == activeTabId }?.file
    }
}

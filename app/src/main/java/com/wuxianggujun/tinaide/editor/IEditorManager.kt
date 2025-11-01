package com.wuxianggujun.tinaide.editor

import java.io.File

/**
 * 编辑器管理器接口
 * 负责管理代码编辑器实例和文件编辑会话
 */
interface IEditorManager {
    /**
     * 打开文件
     */
    fun openFile(file: File): EditorTab
    
    /**
     * 关闭文件
     */
    fun closeFile(tab: EditorTab)
    
    /**
     * 保存文件
     */
    fun saveFile(tab: EditorTab)
    
    /**
     * 保存所有文件
     */
    fun saveAllFiles()
    
    /**
     * 获取所有打开的标签页
     */
    fun getOpenTabs(): List<EditorTab>
    
    /**
     * 切换到指定标签页
     */
    fun switchToTab(tab: EditorTab)
    
    /**
     * 获取当前活动的标签页
     */
    fun getCurrentTab(): EditorTab?
    
    /**
     * 设置字体大小
     */
    fun setFontSize(size: Int)
    
    /**
     * 撤销操作
     */
    fun undo(tab: EditorTab)
    
    /**
     * 重做操作
     */
    fun redo(tab: EditorTab)
    
    /**
     * 查找文本
     */
    fun find(query: String)
    
    /**
     * 替换文本
     */
    fun replace(query: String, replacement: String)
}

/**
 * 编辑器标签页数据类
 */
data class EditorTab(
    val id: String,
    val file: File,
    var isDirty: Boolean = false,
    var cursorPosition: Int = 0,
    var scrollPosition: Int = 0
)

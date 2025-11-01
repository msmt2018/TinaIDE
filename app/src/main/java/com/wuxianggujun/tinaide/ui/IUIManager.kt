package com.wuxianggujun.tinaide.ui

/**
 * UI 管理器接口
 * 负责管理 IDE 的整体 UI 布局和主题
 */
interface IUIManager {
    /**
     * 显示面板
     */
    fun showPanel(panel: PanelType)
    
    /**
     * 隐藏面板
     */
    fun hidePanel(panel: PanelType)
    
    /**
     * 切换面板显示状态
     */
    fun togglePanel(panel: PanelType)
    
    /**
     * 检查面板是否可见
     */
    fun isPanelVisible(panel: PanelType): Boolean
    
    /**
     * 设置主题
     */
    fun setTheme(theme: Theme)
    
    /**
     * 获取当前主题
     */
    fun getCurrentTheme(): Theme
    
    /**
     * 保存布局状态
     */
    fun saveLayoutState()
    
    /**
     * 恢复布局状态
     */
    fun restoreLayoutState()
}

/**
 * 面板类型枚举
 */
enum class PanelType {
    EDITOR,      // 编辑器
    FILE_TREE,   // 文件树
    TERMINAL,    // 终端
    TOOLBAR      // 工具栏
}

/**
 * 主题枚举
 */
enum class Theme {
    LIGHT,  // 亮色主题
    DARK,   // 暗色主题
    AUTO    // 跟随系统
}

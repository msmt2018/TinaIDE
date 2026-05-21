package com.wuxianggujun.tinaide.ui

import android.app.Activity
import android.content.res.Configuration
import com.wuxianggujun.tinaide.core.ServiceLifecycle
import com.wuxianggujun.tinaide.core.config.AppTheme
import com.wuxianggujun.tinaide.core.config.ConfigChangeListener
import com.wuxianggujun.tinaide.core.config.ConfigKeys
import com.wuxianggujun.tinaide.core.config.IConfigManager
import com.wuxianggujun.tinaide.core.config.Prefs

/**
 * UI 管理器实现
 * 管理 IDE 的整体 UI 布局和主题
 */
class UIManager(
    private val activity: Activity,
    private val configManager: IConfigManager
) : IUIManager,
    ServiceLifecycle {
    companion object {
        private const val KEY_PANEL_PREFIX = "ui.panel."
    }

    private val panelVisibility = mutableMapOf<PanelType, Boolean>()
    private var currentTheme: AppTheme = AppTheme.DARK
    private val themeChangeListener = object : ConfigChangeListener {
        override fun onConfigChanged(key: String, newValue: Any?) {
            if (key != ConfigKeys.Theme.key) return
            val updatedTheme = parseTheme(newValue as? String ?: newValue?.toString())
            if (updatedTheme == currentTheme) return
            currentTheme = updatedTheme
            activity.runOnUiThread {
                applyTheme(updatedTheme)
            }
        }
    }

    override fun onCreate() {
        // 恢复主题设置
        val themeName = configManager.get(ConfigKeys.Theme)
        currentTheme = parseTheme(themeName)
        applyTheme(currentTheme)
        configManager.addListener(ConfigKeys.Theme.key, themeChangeListener)

        // 恢复面板可见性
        PanelType.values().forEach { panel ->
            val key = KEY_PANEL_PREFIX + panel.name
            panelVisibility[panel] = configManager.get(ConfigKeys.panelVisible(panel.name, getDefaultVisibility(panel)))
        }
    }

    override fun onDestroy() {
        configManager.removeListener(ConfigKeys.Theme.key, themeChangeListener)
        saveLayoutState()
    }

    override fun showPanel(panel: PanelType) {
        if (panelVisibility[panel] == true) return

        panelVisibility[panel] = true

        // 保存状态
        val key = KEY_PANEL_PREFIX + panel.name
        configManager.set(key, true)
    }

    override fun hidePanel(panel: PanelType) {
        if (panelVisibility[panel] == false) return

        panelVisibility[panel] = false

        // 保存状态
        val key = KEY_PANEL_PREFIX + panel.name
        configManager.set(key, false)
    }

    override fun togglePanel(panel: PanelType) {
        if (isPanelVisible(panel)) {
            hidePanel(panel)
        } else {
            showPanel(panel)
        }
    }

    override fun isPanelVisible(panel: PanelType): Boolean = panelVisibility[panel] ?: getDefaultVisibility(panel)

    override fun setTheme(theme: AppTheme) {
        if (currentTheme == theme) return

        currentTheme = theme
        applyTheme(theme)

        // 保存主题设置
        configManager.set(ConfigKeys.Theme, theme.name)
    }

    override fun getCurrentTheme(): AppTheme = currentTheme

    override fun saveLayoutState() {
        // 保存面板可见性
        panelVisibility.forEach { (panel, visible) ->
            val key = KEY_PANEL_PREFIX + panel.name
            configManager.set(key, visible)
        }
    }

    override fun restoreLayoutState() {
        // 恢复主题
        val themeName = configManager.get(ConfigKeys.Theme)
        currentTheme = parseTheme(themeName)
        applyTheme(currentTheme)

        // 恢复面板可见性
        PanelType.values().forEach { panel ->
            val key = KEY_PANEL_PREFIX + panel.name
            val visible = configManager.get(ConfigKeys.panelVisible(panel.name, getDefaultVisibility(panel)))
            panelVisibility[panel] = visible
        }
    }

    private fun parseTheme(themeName: String?): AppTheme {
        if (themeName.isNullOrBlank()) {
            return AppTheme.LIGHT
        }
        return AppTheme.fromString(themeName)
    }

    /**
     * 应用主题
     */
    private fun applyTheme(theme: AppTheme) {
        Prefs.applyNightMode(theme.name)
    }

    /**
     * 获取面板默认可见性
     */
    private fun getDefaultVisibility(panel: PanelType): Boolean = when (panel) {
        PanelType.EDITOR -> true
        PanelType.FILE_TREE -> true
        PanelType.TERMINAL -> false
        PanelType.TOOLBAR -> true
    }

    /**
     * 检查当前是否为暗色模式
     */
    fun isDarkMode(): Boolean = when (currentTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.GRAY -> true
        AppTheme.AUTO -> {
            val nightMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

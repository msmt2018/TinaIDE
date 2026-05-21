package com.wuxianggujun.tinaide.ai.tools.config

import android.content.Context
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolRegistry
import kotlinx.serialization.Serializable

/**
 * 工具配置管理器
 */
object ToolConfigManager {

    /**
     * 获取所有工具的配置
     */
    fun getAllToolConfigs(context: Context): List<ToolConfig> = ToolRegistry.getAllTools().map { tool ->
        ToolConfig(
            name = tool.name,
            displayName = tool.getFriendlyName(context),
            description = tool.getDetailedDescription(),
            category = tool.category,
            enabled = ToolRegistry.isEnabled(tool.name),
            enabledByDefault = tool.enabledByDefault
        )
    }

    /**
     * 按分类获取工具配置
     */
    fun getToolConfigsByCategory(context: Context): Map<ToolCategory, List<ToolConfig>> = getAllToolConfigs(context).groupBy { it.category }

    /**
     * 保存工具启用状态
     */
    fun saveToolEnabledState(toolName: String, enabled: Boolean) {
        if (enabled) {
            ToolRegistry.enableTool(toolName)
        } else {
            ToolRegistry.disableTool(toolName)
        }
    }

    /**
     * 批量保存工具启用状态
     */
    fun saveToolEnabledStates(states: Map<String, Boolean>) {
        states.forEach { (toolName, enabled) ->
            saveToolEnabledState(toolName, enabled)
        }
    }

    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        ToolRegistry.getAllTools().forEach { tool ->
            if (tool.enabledByDefault) {
                ToolRegistry.enableTool(tool.name)
            } else {
                ToolRegistry.disableTool(tool.name)
            }
        }
    }

    /**
     * 启用所有工具
     */
    fun enableAllTools() {
        ToolRegistry.getAllTools().forEach { tool ->
            ToolRegistry.enableTool(tool.name)
        }
    }

    /**
     * 禁用所有工具
     */
    fun disableAllTools() {
        ToolRegistry.getAllTools().forEach { tool ->
            ToolRegistry.disableTool(tool.name)
        }
    }

    /**
     * 按分类启用/禁用工具
     */
    fun setToolsCategoryEnabled(category: ToolCategory, enabled: Boolean) {
        ToolRegistry.getToolsByCategory(category).forEach { tool ->
            if (enabled) {
                ToolRegistry.enableTool(tool.name)
            } else {
                ToolRegistry.disableTool(tool.name)
            }
        }
    }
}

/**
 * 工具配置数据类
 */
@Serializable
data class ToolConfig(
    val name: String,
    val displayName: String,
    val description: String,
    val category: ToolCategory,
    val enabled: Boolean,
    val enabledByDefault: Boolean
)

package com.wuxianggujun.tinaide.ai.tools

import com.wuxianggujun.tinaide.ai.api.ChatRequestTool
import java.util.concurrent.ConcurrentHashMap

/**
 * AI工具注册表
 * 负责管理所有可用的AI工具
 */
object ToolRegistry {
    private val tools = ConcurrentHashMap<String, AiTool>()
    private val enabledTools = ConcurrentHashMap<String, Boolean>()
    private val listeners = mutableListOf<ToolRegistryListener>()

    /**
     * 注册工具
     */
    fun register(tool: AiTool) {
        tools[tool.name] = tool
        enabledTools.putIfAbsent(tool.name, tool.enabledByDefault)
        notifyToolRegistered(tool)
    }

    /**
     * 批量注册工具
     */
    fun registerAll(vararg tools: AiTool) {
        tools.forEach { register(it) }
    }

    /**
     * 注销工具
     */
    fun unregister(toolName: String) {
        tools.remove(toolName)?.let { tool ->
            enabledTools.remove(toolName)
            notifyToolUnregistered(tool)
        }
    }

    /**
     * 获取工具
     */
    fun getTool(name: String): AiTool? = tools[name]

    /**
     * 获取所有工具
     */
    fun getAllTools(): List<AiTool> = tools.values.toList()

    /**
     * 获取已启用的工具
     */
    fun getEnabledTools(): List<AiTool> = tools.values.filter { isEnabled(it.name) }

    /**
     * 获取指定分类的工具
     */
    fun getToolsByCategory(category: ToolCategory): List<AiTool> = tools.values.filter { it.category == category }

    /**
     * 启用工具
     */
    fun enableTool(toolName: String) {
        if (tools.containsKey(toolName)) {
            enabledTools[toolName] = true
            notifyToolEnabled(toolName)
        }
    }

    /**
     * 禁用工具
     */
    fun disableTool(toolName: String) {
        if (tools.containsKey(toolName)) {
            enabledTools[toolName] = false
            notifyToolDisabled(toolName)
        }
    }

    /**
     * 检查工具是否启用
     */
    fun isEnabled(toolName: String): Boolean = enabledTools[toolName] ?: false

    /**
     * 获取已启用工具的API请求格式
     */
    fun getEnabledRequestTools(): List<ChatRequestTool> = getEnabledTools().map { it.toRequestTool() }

    /**
     * 获取所有工具的启用状态
     */
    fun getToolEnabledStates(): Map<String, Boolean> = enabledTools.toMap()

    /**
     * 批量设置工具启用状态
     */
    fun setToolEnabledStates(states: Map<String, Boolean>) {
        states.forEach { (toolName, enabled) ->
            if (tools.containsKey(toolName)) {
                enabledTools[toolName] = enabled
            }
        }
    }

    /**
     * 清空所有工具
     */
    fun clear() {
        tools.clear()
        enabledTools.clear()
        notifyRegistryCleared()
    }

    /**
     * 添加监听器
     */
    fun addListener(listener: ToolRegistryListener) {
        listeners.add(listener)
    }

    /**
     * 移除监听器
     */
    fun removeListener(listener: ToolRegistryListener) {
        listeners.remove(listener)
    }

    private fun notifyToolRegistered(tool: AiTool) {
        listeners.forEach { it.onToolRegistered(tool) }
    }

    private fun notifyToolUnregistered(tool: AiTool) {
        listeners.forEach { it.onToolUnregistered(tool) }
    }

    private fun notifyToolEnabled(toolName: String) {
        listeners.forEach { it.onToolEnabled(toolName) }
    }

    private fun notifyToolDisabled(toolName: String) {
        listeners.forEach { it.onToolDisabled(toolName) }
    }

    private fun notifyRegistryCleared() {
        listeners.forEach { it.onRegistryCleared() }
    }
}

/**
 * 工具注册表监听器
 */
interface ToolRegistryListener {
    fun onToolRegistered(tool: AiTool) {}
    fun onToolUnregistered(tool: AiTool) {}
    fun onToolEnabled(toolName: String) {}
    fun onToolDisabled(toolName: String) {}
    fun onRegistryCleared() {}
}

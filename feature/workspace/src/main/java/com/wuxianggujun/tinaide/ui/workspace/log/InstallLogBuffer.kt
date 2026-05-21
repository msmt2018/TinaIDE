package com.wuxianggujun.tinaide.ui.workspace.log

import com.wuxianggujun.tinaide.core.proot.InstallLogEntry
import com.wuxianggujun.tinaide.core.proot.InstallLogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 安装日志缓冲区
 *
 * 线程安全的日志存储，专用于安装日志界面
 */
class InstallLogBuffer(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    companion object {
        const val DEFAULT_MAX_ENTRIES = 1000
    }

    /**
     * 日志监听器
     */
    fun interface LogListener {
        fun onLogAdded(entry: InstallLogEntry)
    }

    private val entries = CopyOnWriteArrayList<InstallLogEntry>()
    private val listeners = CopyOnWriteArraySet<LogListener>()

    private val _entriesFlow = MutableStateFlow<List<InstallLogEntry>>(emptyList())
    val entriesFlow: StateFlow<List<InstallLogEntry>> = _entriesFlow.asStateFlow()

    /**
     * 添加日志条目
     */
    fun add(entry: InstallLogEntry) {
        // 如果超过最大条目数，移除最旧的
        while (entries.size >= maxEntries) {
            entries.removeAt(0)
        }
        entries.add(entry)
        _entriesFlow.value = entries.toList()
        
        // 通知监听器
        listeners.forEach { it.onLogAdded(entry) }
    }

    /**
     * 添加日志（便捷方法）
     */
    fun add(
        level: InstallLogLevel,
        message: String,
        tag: String = ""
    ) {
        add(InstallLogEntry.create(level, message, tag))
    }

    /**
     * 批量添加日志条目
     */
    fun addAll(newEntries: List<InstallLogEntry>) {
        // 计算需要移除的数量
        val totalSize = entries.size + newEntries.size
        if (totalSize > maxEntries) {
            val removeCount = totalSize - maxEntries
            repeat(removeCount.coerceAtMost(entries.size)) {
                entries.removeAt(0)
            }
        }
        entries.addAll(newEntries)
        _entriesFlow.value = entries.toList()
        
        // 通知监听器
        newEntries.forEach { entry ->
            listeners.forEach { it.onLogAdded(entry) }
        }
    }

    /**
     * 清空所有日志
     */
    fun clear() {
        entries.clear()
        _entriesFlow.value = emptyList()
    }

    /**
     * 获取所有日志条目
     */
    fun getAll(): List<InstallLogEntry> = entries.toList()

    /**
     * 获取指定范围的日志条目
     */
    fun getRange(startIndex: Int, endIndex: Int): List<InstallLogEntry> {
        val safeStart = startIndex.coerceIn(0, entries.size)
        val safeEnd = endIndex.coerceIn(0, entries.size)
        return entries.subList(safeStart, safeEnd).toList()
    }

    /**
     * 获取指定索引的日志条目
     */
    fun get(index: Int): InstallLogEntry? {
        return entries.getOrNull(index)
    }

    /**
     * 获取日志条目数量
     */
    fun size(): Int = entries.size

    /**
     * 添加监听器
     */
    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    /**
     * 移除监听器
     */
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }

    /**
     * 按级别过滤日志
     */
    fun filterByLevel(vararg levels: InstallLogLevel): List<InstallLogEntry> {
        val levelSet = levels.toSet()
        return entries.filter { it.level in levelSet }
    }

    /**
     * 搜索日志
     */
    fun search(keyword: String, ignoreCase: Boolean = true): List<InstallLogEntry> {
        if (keyword.isBlank()) return entries.toList()
        return entries.filter { 
            it.message.contains(keyword, ignoreCase) || 
            it.tag.contains(keyword, ignoreCase) 
        }
    }

    /**
     * 获取完整的日志文本
     */
    fun getFullText(): String {
        return entries.joinToString("\n") { it.fullText }
    }

    /**
     * 重放所有日志到监听器
     */
    fun replayTo(listener: LogListener) {
        entries.forEach { listener.onLogAdded(it) }
    }
}

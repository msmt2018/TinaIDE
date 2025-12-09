package com.wuxianggujun.tinaide.ui

import android.content.Context
import com.wuxianggujun.tinaide.output.LogLevel
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 全局底部日志缓冲区
 * - 内存中只保留最近的日志用于 UI 显示
 * - 所有日志异步写入文件持久化
 * - 切换项目时清空内存缓冲区
 */
object BottomLogBuffer {
    
    private const val MAX_MEMORY_LOGS = 500  // 内存中最多保留的日志条数
    private const val FLUSH_INTERVAL_MS = 1000L  // 写入文件的间隔
    
    data class LogEntry(
        val level: LogLevel,
        val timestamp: String,
        val tag: String,
        val message: String
    ) {
        fun toFullText(): String = "[$timestamp] $tag: $message"
        fun toFileFormat(): String = "$timestamp ${level.prefix.first()} $tag: $message"
    }

    fun interface LogListener {
        fun onLog(entry: LogEntry)
    }

    private val logs = mutableListOf<LogEntry>()
    private val listeners = CopyOnWriteArraySet<LogListener>()
    private val lock = Any()
    
    // 文件写入相关
    private var logFile: File? = null
    private var fileWriter: BufferedWriter? = null
    private val pendingWrites = mutableListOf<LogEntry>()
    private val writeLock = Any()
    private var writeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 初始化日志文件（APP 启动时调用）
     */
    fun init(context: Context) {
        val logsDir = File(context.filesDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        
        // 清理旧日志文件（保留最近 5 个）
        cleanOldLogs(logsDir, keepCount = 5)
        
        // 创建新的日志文件
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val fileName = "log_${dateFormat.format(Date())}.txt"
        logFile = File(logsDir, fileName)
        
        try {
            fileWriter = BufferedWriter(FileWriter(logFile, true))
            fileWriter?.write("=== TinaIDE Log Session Started: ${Date()} ===\n")
            fileWriter?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // 启动定时写入任务
        startFlushJob()
    }

    private fun cleanOldLogs(logsDir: File, keepCount: Int) {
        try {
            val logFiles = logsDir.listFiles { file -> file.name.startsWith("log_") && file.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            
            if (logFiles.size > keepCount) {
                logFiles.drop(keepCount).forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun startFlushJob() {
        writeJob?.cancel()
        writeJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flushToFile()
            }
        }
    }
    
    private fun flushToFile() {
        val toWrite: List<LogEntry>
        synchronized(writeLock) {
            if (pendingWrites.isEmpty()) return
            toWrite = pendingWrites.toList()
            pendingWrites.clear()
        }
        
        try {
            fileWriter?.let { writer ->
                toWrite.forEach { entry ->
                    writer.write(entry.toFileFormat())
                    writer.newLine()
                }
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 追加日志
     */
    fun append(level: LogLevel, timestamp: String, tag: String, message: String) {
        val entry = LogEntry(level, timestamp, tag, message)
        
        // 添加到内存缓冲区（限制大小）
        synchronized(lock) {
            if (logs.size >= MAX_MEMORY_LOGS) {
                logs.removeAt(0)
            }
            logs.add(entry)
        }
        
        // 添加到待写入队列
        synchronized(writeLock) {
            pendingWrites.add(entry)
        }
        
        // 通知监听器
        listeners.forEach { listener ->
            listener.onLog(entry)
        }
    }

    /**
     * 清空内存缓冲区（切换项目时调用）
     */
    fun clear() {
        synchronized(lock) {
            logs.clear()
        }
        // 立即刷新待写入的日志到文件
        flushToFile()
    }
    
    /**
     * 开始新的日志会话（切换项目时调用）
     */
    fun startNewSession(projectName: String) {
        // 先刷新旧日志
        flushToFile()
        
        // 清空内存
        synchronized(lock) {
            logs.clear()
        }
        
        // 写入分隔标记
        try {
            fileWriter?.write("\n=== Project Switched: $projectName at ${Date()} ===\n\n")
            fileWriter?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun replayTo(listener: LogListener) {
        val snapshot = synchronized(lock) { logs.toList() }
        snapshot.forEach { entry -> listener.onLog(entry) }
    }

    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
    
    /**
     * 获取当前日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    /**
     * 关闭资源（APP 退出时调用）
     */
    fun shutdown() {
        writeJob?.cancel()
        flushToFile()
        try {
            fileWriter?.write("=== Log Session Ended: ${Date()} ===\n")
            fileWriter?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        fileWriter = null
        logFile = null
    }
}

package com.wuxianggujun.tinaide.terminal

import com.termux.terminal.TerminalSession

/**
 * 终端管理器接口
 * 负责管理终端会话和 Termux 环境
 */
interface ITerminalManager {
    /**
     * 创建终端会话
     */
    fun createSession(): TerminalSession
    
    /**
     * 关闭终端会话
     */
    fun closeSession(session: TerminalSession)
    
    /**
     * 获取所有会话
     */
    fun getSessions(): List<TerminalSession>
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(session: TerminalSession)
    
    /**
     * 初始化环境
     */
    fun initializeEnvironment(): Boolean
    
    /**
     * 获取环境变量
     */
    fun getEnvironmentVariables(): Map<String, String>
    
    /**
     * 设置环境变量
     */
    fun setEnvironmentVariable(key: String, value: String)
    
    /**
     * 执行命令
     */
    fun executeCommand(command: String)
    
    /**
     * 发送输入
     */
    fun sendInput(input: String)
    
    /**
     * 设置字体大小
     */
    fun setFontSize(size: Int)
    
    /**
     * 设置颜色方案
     */
    fun setColorScheme(scheme: TerminalColorScheme)
}

/**
 * 终端颜色方案
 */
data class TerminalColorScheme(
    val background: Int,
    val foreground: Int,
    val cursor: Int,
    val palette: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TerminalColorScheme

        if (background != other.background) return false
        if (foreground != other.foreground) return false
        if (cursor != other.cursor) return false
        if (!palette.contentEquals(other.palette)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background
        result = 31 * result + foreground
        result = 31 * result + cursor
        result = 31 * result + palette.contentHashCode()
        return result
    }
}

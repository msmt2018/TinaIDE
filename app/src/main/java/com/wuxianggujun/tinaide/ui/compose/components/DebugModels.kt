package com.wuxianggujun.tinaide.ui.compose.components

/**
 * 断点数据（用于UI显示）
 */
data class BreakpointInfo(
    val id: Int,
    val file: String,
    val line: Int,
    val enabled: Boolean,
    val verified: Boolean = false
)

/**
 * 调试状态
 */
enum class DebugStatus {
    IDLE, // 空闲
    STARTING, // 启动中
    PAUSED, // 暂停（可以单步）
    RUNNING, // 运行中
    TERMINATED // 已终止
}

/**
 * 调试变量数据
 */
data class DebugVariable(
    val name: String,
    val value: String,
    val type: String
)

/**
 * 栈帧数据
 */
data class StackFrame(
    val id: Int,
    val name: String,
    val file: String,
    val line: Int
)

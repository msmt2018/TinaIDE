package com.wuxianggujun.tinaide.core.debug

/**
 * 调试命令（Kotlin -> Native Stub）
 */
sealed class DebugCommand {
    /** 初始化调试会话 */
    object Initialize : DebugCommand()

    /** 继续执行 */
    object Continue : DebugCommand()

    /** 单步执行（Step Over） */
    object StepOver : DebugCommand()

    /** 单步进入（Step Into） */
    object StepInto : DebugCommand()

    /** 单步跳出（Step Out） */
    object StepOut : DebugCommand()

    /** 暂停执行 */
    object Pause : DebugCommand()

    /** 终止会话 */
    object Terminate : DebugCommand()

    /** 设置断点 */
    data class SetBreakpoint(
        val file: String,
        val line: Int,
        val condition: String? = null
    ) : DebugCommand()

    /** 移除断点 */
    data class RemoveBreakpoint(val address: Long) : DebugCommand()

    /** 获取调用栈 */
    object GetBacktrace : DebugCommand()

    /** 获取变量 */
    data class GetVariables(val frameId: Int) : DebugCommand()

    /** 求值表达式 */
    data class Evaluate(val expression: String) : DebugCommand()

    /** 读取内存 */
    data class ReadMemory(val address: Long, val length: Int) : DebugCommand()

    /** 读取寄存器 */
    object ReadRegisters : DebugCommand()
}

/**
 * 调试响应（Native Stub -> Kotlin）
 */
sealed class DebugResponse {
    /** 目标就绪 */
    data class Ready(val location: SourceLocation?) : DebugResponse()

    /** 目标停止 */
    data class Stopped(
        val reason: PauseReason,
        val location: SourceLocation?,
        val threadId: Int = 0
    ) : DebugResponse()

    /** 目标退出 */
    data class Exited(val exitCode: Int) : DebugResponse()

    /** 目标崩溃 */
    data class Crashed(val signal: String) : DebugResponse()

    /** 断点已设置 */
    data class BreakpointSet(val address: Long) : DebugResponse()

    /** 断点已移除 */
    data class BreakpointRemoved(val success: Boolean) : DebugResponse()

    /** 调用栈 */
    data class Backtrace(val frames: List<StackFrame>) : DebugResponse()

    /** 变量列表 */
    data class Variables(val variables: List<Variable>) : DebugResponse()

    /** 求值结果 */
    data class EvaluateResult(val value: String, val type: String?) : DebugResponse()

    /** 内存数据 */
    data class MemoryData(val data: ByteArray) : DebugResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MemoryData) return false
            return data.contentEquals(other.data)
        }
        override fun hashCode(): Int = data.contentHashCode()
    }

    /** 寄存器值 */
    data class Registers(val values: Map<String, Long>) : DebugResponse()

    /** 错误 */
    data class Error(val message: String) : DebugResponse()

    /** 确认（通用成功响应） */
    object Ack : DebugResponse()
}

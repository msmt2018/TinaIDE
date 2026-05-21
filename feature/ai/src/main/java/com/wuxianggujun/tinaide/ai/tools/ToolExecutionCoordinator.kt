package com.wuxianggujun.tinaide.ai.tools

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.model.ToolExecutionMode
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * 工具调用的"纯决策"门面。
 *
 * 设计意图:
 * - `AiChatViewModel` 有大量"查找工具 → 执行 → 判定是否高危 → 构造取消结果"的零散代码;
 *   集中到这里,让 ViewModel 只剩 uiState/repo 的业务编排。
 * - 本类**不**触碰 `AiChatUiState` 或 `ConversationRepository`——那是 ViewModel 的职责。
 * - 通过 [ToolLocator] 间接读取工具,便于 JVM 单元测试脱离 `ToolRegistry` object。
 */
class ToolExecutionCoordinator(
    private val locator: ToolLocator = DefaultToolLocator,
) {

    /**
     * 查找工具的最小接口。默认实现包装 [ToolRegistry];测试可注入 stub。
     */
    fun interface ToolLocator {
        fun find(name: String): AiTool?
    }

    object DefaultToolLocator : ToolLocator {
        override fun find(name: String): AiTool? = ToolRegistry.getTool(name)
    }

    companion object {
        private const val TAG = "ToolExecCoordinator"
    }

    /**
     * 执行一次工具调用;捕获所有异常到 [ToolExecutionResult.Error]。
     *
     * @return 纯语义结果;由调用方负责更新 UI/Repository。
     */
    suspend fun execute(
        toolCall: ToolCall,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val toolName = toolCall.function?.name
        if (toolName.isNullOrBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_call_missing_name.str())
        }

        val tool = locator.find(toolName) ?: run {
            Timber.tag(TAG).e("Tool not found: %s", toolName)
            return ToolExecutionResult.Error(Strings.ai_tool_not_found.str(toolName))
        }

        return try {
            tool.execute(toolCall, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Tool execution exception: %s", toolName)
            ToolExecutionResult.Error(Strings.ai_tool_execution_failed.str(e.message))
        }
    }

    /**
     * 在 [ToolExecutionMode.AUTO] 模式下,判断是否可以**自动**执行给定工具。
     * 手动模式下调用者不该用这个方法——自动决策权在用户。
     */
    fun isAutoExecutionAllowed(toolCall: ToolCall, allowDangerousAuto: Boolean): Boolean {
        val toolName = toolCall.function?.name ?: return false
        val tool = locator.find(toolName) ?: return false
        return !tool.isDangerous || allowDangerousAuto
    }

    /**
     * 构造一个"因为前一个工具失败/被取消而级联取消"的结果描述,用于状态传播。
     */
    fun buildCascadeCancelReason(previousStatus: PreviousStatus): String = when (previousStatus) {
        PreviousStatus.FAILED -> Strings.ai_tool_cancelled_previous_failed.str()
        PreviousStatus.CANCELLED -> Strings.ai_tool_cancelled_previous_cancelled.str()
    }

    enum class PreviousStatus { FAILED, CANCELLED }
}

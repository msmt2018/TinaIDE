package com.wuxianggujun.tinaide.ai.tools

import android.content.Context
import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.ai.api.ChatRequestTool
import com.wuxianggujun.tinaide.ai.api.ChatRequestToolFunction
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.serialization.json.JsonElement

/**
 * AI工具接口
 * 所有AI工具都需要实现此接口
 */
interface AiTool {
    /**
     * 工具名称（唯一标识符）
     */
    val name: String

    /**
     * 工具描述
     */
    val description: String

    /**
     * 工具分类
     */
    val category: ToolCategory

    /**
     * 是否默认启用
     */
    val enabledByDefault: Boolean get() = true

    /**
     * 是否为高危工具
     * 高危工具包括：文件删除、系统命令执行、权限修改等可能造成数据丢失或系统损坏的操作
     */
    val isDangerous: Boolean get() = false

    /**
     * 获取危险工具的确认对话框配置
     * 只有当 isDangerous = true 时才会被调用
     * @param toolCall 工具调用信息，可用于生成动态的确认消息
     * @return 确认对话框配置，如果返回 null 则使用默认配置
     */
    fun getDangerousConfirmation(toolCall: ToolCall): DangerousToolConfirmation? = null

    /**
     * 参数定义（JSON Schema）
     */
    fun getParameters(): JsonElement

    /**
     * 转换为API请求工具
     */
    fun toRequestTool(): ChatRequestTool = ChatRequestTool(
        function = ChatRequestToolFunction(
            name = name,
            description = description,
            parameters = getParameters()
        )
    )

    /**
     * 获取工具的友好名称（用于UI显示）
     * 使用国际化字符串
     */
    fun getFriendlyName(context: Context): String = ToolI18n.getToolName(context, name)

    /**
     * 获取工具的详细描述（用于UI显示）
     */
    fun getDetailedDescription(): String = description

    /**
     * 执行工具调用
     * @param toolCall 工具调用信息
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult
}

internal fun localizedToolText(@StringRes resId: Int, fallback: String): String = runCatching { resId.str() }.getOrDefault(fallback)

internal fun localizedToolText(@StringRes resId: Int, fallback: String, vararg formatArgs: Any?): String = runCatching { resId.str(*formatArgs) }.getOrElse { fallback.formatToolFallback(*formatArgs) }

internal fun StringBuilder.appendLocalizedToolLine(
    @StringRes resId: Int,
    fallback: String,
    vararg formatArgs: Any?
) {
    appendLine(localizedToolText(resId, fallback, *formatArgs))
}

private fun String.formatToolFallback(vararg formatArgs: Any?): String = runCatching { format(*formatArgs) }.getOrDefault(this)

/**
 * 工具分类
 */
enum class ToolCategory {
    EDITOR,
    FILE_SYSTEM,
    CODE_ANALYSIS,
    DIAGNOSTICS,
    BUILD,
    EXECUTION,
    REFACTOR,
    GIT,
    TERMINAL,
    WEB,
    CUSTOM;

    /**
     * 获取本地化的分类名称
     */
    fun getDisplayName(context: Context): String = ToolI18n.getCategoryName(context, this)
}

/**
 * 工具执行上下文
 */
data class ToolExecutionContext(
    val projectRoot: String? = null,
    val currentFile: String? = null,
    val currentFileContent: String? = null,
    val selectedCode: String? = null,
    val cursorPosition: Int? = null,
    val additionalData: Map<String, Any> = emptyMap()
)

/**
 * 工具执行结果
 */
sealed class ToolExecutionResult {
    open fun toJsonString(): String = "{}"

    data class Success(val content: String, val metadata: Map<String, Any> = emptyMap()) : ToolExecutionResult() {

        override fun toJsonString(): String {
            val metadataJson = metadata.entries.joinToString(", ") { (key, value) ->
                "\"$key\": \"$value\""
            }
            return """
                {
                    "status": "success",
                    "result": "$content",
                    "message": "ok",
                    "metadata": {
                        $metadataJson
                    }
                }
            """.trimIndent()
        }
    }
    data class Error(val message: String) : ToolExecutionResult() {
        override fun toJsonString(): String = """
                {
                    "status": "error",
                    "result": null,
                    "message": "$message"
                }
        """.trimIndent()
    }
    data class Cancelled(val reason: String) : ToolExecutionResult() {
        override fun toJsonString(): String = """
                {
                    "status": "cancel",
                    "result": null,
                    "message": "$reason"
                }
        """.trimIndent()
    }
}

/**
 * 危险工具确认对话框配置
 */
data class DangerousToolConfirmation(
    /**
     * 对话框标题
     */
    val title: String,

    /**
     * 警告消息
     */
    val message: String,

    /**
     * 详细信息（可选）
     * 例如：要删除的文件路径、要执行的命令等
     */
    val details: String? = null,

    /**
     * 确认按钮文本
     */
    val confirmButtonText: String = Strings.btn_confirm.str(),

    /**
     * 取消按钮文本
     */
    val cancelButtonText: String = Strings.btn_cancel.str(),

    /**
     * 警告级别
     */
    val severity: ConfirmationSeverity = ConfirmationSeverity.WARNING
)

/**
 * 确认对话框严重程度
 */
enum class ConfirmationSeverity {
    /**
     * 警告 - 黄色图标
     */
    WARNING,

    /**
     * 危险 - 红色图标
     */
    DANGER,

    /**
     * 严重 - 深红色图标，需要额外确认
     */
    CRITICAL
}

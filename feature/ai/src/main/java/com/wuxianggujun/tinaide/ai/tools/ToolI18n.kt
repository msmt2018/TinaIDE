package com.wuxianggujun.tinaide.ai.tools

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.R

/**
 * AI 工具国际化支持
 *
 * 提供工具名称和分类的国际化字符串
 */
object ToolI18n {
    /**
     * 获取工具分类的本地化名称
     */
    fun getCategoryName(context: Context, category: ToolCategory): String = context.getString(
        when (category) {
            ToolCategory.EDITOR -> R.string.ai_tool_category_editor
            ToolCategory.FILE_SYSTEM -> R.string.ai_tool_category_file_system
            ToolCategory.CODE_ANALYSIS -> R.string.ai_tool_category_code_analysis
            ToolCategory.DIAGNOSTICS -> R.string.ai_tool_category_diagnostics
            ToolCategory.BUILD -> R.string.ai_tool_category_build
            ToolCategory.EXECUTION -> R.string.ai_tool_category_execution
            ToolCategory.GIT -> R.string.ai_tool_category_git
            ToolCategory.TERMINAL -> R.string.ai_tool_category_terminal
            ToolCategory.REFACTOR -> R.string.ai_tool_category_refactor
            ToolCategory.WEB -> R.string.ai_tool_category_web
            ToolCategory.CUSTOM -> R.string.ai_tool_category_custom
        }
    )

    /**
     * 获取工具的本地化名称
     */
    fun getToolName(context: Context, toolName: String): String {
        val resId = when (toolName) {
            // Editor tools
            "get_current_file" -> R.string.ai_tool_get_current_file
            "get_selected_code" -> R.string.ai_tool_get_selected_code
            "insert_code" -> R.string.ai_tool_insert_code
            "replace_selected_code" -> R.string.ai_tool_replace_selected_code

            // File system tools
            "read_file" -> R.string.ai_tool_read_file
            "write_file" -> R.string.ai_tool_write_file
            "list_files" -> R.string.ai_tool_list_files
            "delete_file" -> R.string.ai_tool_delete_file
            "create_directory" -> R.string.ai_tool_create_directory
            "move_file" -> R.string.ai_tool_move_file
            "copy_file" -> R.string.ai_tool_copy_file
            "get_file_info" -> R.string.ai_tool_get_file_info

            // Code analysis tools
            "search_code" -> R.string.ai_tool_search_code
            "find_definition" -> R.string.ai_tool_find_definition
            "find_references" -> R.string.ai_tool_find_references
            "get_outline" -> R.string.ai_tool_get_outline

            // Diagnostics tools
            "get_diagnostics" -> R.string.ai_tool_get_diagnostics
            "get_all_diagnostics" -> R.string.ai_tool_get_all_diagnostics
            "clear_diagnostics" -> R.string.ai_tool_clear_diagnostics

            // Execution tools
            "run_project" -> R.string.ai_tool_run_project
            "run_tests" -> R.string.ai_tool_run_tests
            "build_project" -> R.string.ai_tool_build_project
            "stop_execution" -> R.string.ai_tool_stop_execution
            "get_execution_status" -> R.string.ai_tool_get_execution_status

            // Project tools
            "get_project_structure" -> R.string.ai_tool_get_project_structure
            "find_files" -> R.string.ai_tool_find_files
            "count_lines" -> R.string.ai_tool_count_lines

            // Refactor tools
            "format_code" -> R.string.ai_tool_format_code
            "extract_method" -> R.string.ai_tool_extract_method
            "add_doc_comment" -> R.string.ai_tool_add_doc_comment

            else -> null
        }

        return if (resId != null) {
            context.getString(resId)
        } else {
            // 如果没有找到对应的资源，返回工具名称的友好格式
            toolName.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        }
    }
}

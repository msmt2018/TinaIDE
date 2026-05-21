package com.wuxianggujun.tinaide.core.commands

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 宿主内置命令白名单（配置插件可绑定）。
 *
 * 阶段 1.5 约束：插件只能"引用"宿主命令，不能注入可执行代码。
 *
 * 命令分类：
 * - file.*: 文件操作
 * - editor.*: 编辑器操作
 * - terminal.*: 终端操作
 * - project.*: 项目/工作区操作
 * - view.*: 视图切换
 */
object HostCommands {

    // ==================== 文件操作 ====================
    const val FILE_NEW: String = "file.new"
    const val FILE_NEW_FOLDER: String = "file.newFolder"
    const val FILE_RENAME: String = "file.rename"
    const val FILE_DELETE: String = "file.delete"
    const val FILE_COPY_PATH: String = "file.copyPath"
    const val FILE_COPY_NAME: String = "file.copyName"
    const val FILE_COPY_RELATIVE_PATH: String = "file.copyRelativePath"
    const val FILE_DUPLICATE: String = "file.duplicate"
    const val FILE_OPEN_WITH: String = "file.openWith"
    const val FILE_SHARE: String = "file.share"
    const val FILE_REVEAL_IN_FILE_MANAGER: String = "file.revealInFileManager"

    // ==================== 编辑器操作 ====================
    const val EDITOR_SAVE: String = "editor.save"
    const val EDITOR_SAVE_ALL: String = "editor.saveAll"
    const val EDITOR_CLOSE: String = "editor.close"
    const val EDITOR_CLOSE_ALL: String = "editor.closeAll"
    const val EDITOR_CLOSE_OTHERS: String = "editor.closeOthers"
    const val EDITOR_UNDO: String = "editor.undo"
    const val EDITOR_REDO: String = "editor.redo"
    const val EDITOR_SELECT_ALL: String = "editor.selectAll"
    const val EDITOR_COPY: String = "editor.copy"
    const val EDITOR_CUT: String = "editor.cut"
    const val EDITOR_PASTE: String = "editor.paste"
    const val EDITOR_FIND: String = "editor.find"
    const val EDITOR_REPLACE: String = "editor.replace"
    const val EDITOR_GOTO_LINE: String = "editor.gotoLine"
    const val EDITOR_NAVIGATE_BACK: String = "editor.navigateBack"
    const val EDITOR_NAVIGATE_FORWARD: String = "editor.navigateForward"
    const val EDITOR_TOGGLE_WORD_WRAP: String = "editor.toggleWordWrap"
    const val EDITOR_FORMAT: String = "editor.format"
    const val EDITOR_TOGGLE_COMMENT: String = "editor.toggleComment"
    const val EDITOR_PEEK_DEFINITION: String = "editor.peekDefinition"
    const val EDITOR_GOTO_DEFINITION: String = "editor.gotoDefinition"
    const val EDITOR_FIND_REFERENCES: String = "editor.findReferences"
    const val EDITOR_GOTO_TYPE_DEFINITION: String = "editor.gotoTypeDefinition"
    const val EDITOR_GOTO_IMPLEMENTATION: String = "editor.gotoImplementation"
    const val EDITOR_CODE_ACTIONS: String = "editor.codeActions"
    const val EDITOR_RENAME_SYMBOL: String = "editor.renameSymbol"
    const val EDITOR_SWITCH_HEADER_SOURCE: String = "editor.switchHeaderSource"

    // ==================== 终端操作 ====================
    const val TERMINAL_TOGGLE: String = "terminal.toggle"
    const val TERMINAL_NEW: String = "terminal.new"
    const val TERMINAL_OPEN_HERE: String = "terminal.openHere"
    const val TERMINAL_CLEAR: String = "terminal.clear"

    // ==================== 项目/工作区操作 ====================
    const val PROJECT_REFRESH: String = "project.refresh"
    const val PROJECT_BUILD: String = "project.build"
    const val PROJECT_RUN: String = "project.run"
    const val PROJECT_SETTINGS: String = "project.settings"
    const val PROJECT_CLOSE: String = "project.close"

    // ==================== 视图切换 ====================
    const val VIEW_TOGGLE_FILE_TREE: String = "view.toggleFileTree"
    const val VIEW_TOGGLE_SYMBOLS: String = "view.toggleSymbols"
    const val VIEW_TOGGLE_TERMINAL: String = "view.toggleTerminal"
    const val VIEW_SETTINGS: String = "view.settings"

    private val titleResById: Map<String, Int> = linkedMapOf(
        // 文件操作
        FILE_NEW to Strings.action_new_file,
        FILE_NEW_FOLDER to Strings.action_new_folder,
        FILE_RENAME to Strings.action_rename,
        FILE_DELETE to Strings.btn_delete,
        FILE_COPY_PATH to Strings.action_copy_path,
        FILE_COPY_NAME to Strings.action_copy_name,
        FILE_COPY_RELATIVE_PATH to Strings.action_copy_relative_path,
        FILE_DUPLICATE to Strings.cmd_file_duplicate,
        FILE_OPEN_WITH to Strings.cmd_file_open_with,
        FILE_SHARE to Strings.cmd_file_share,
        FILE_REVEAL_IN_FILE_MANAGER to Strings.cmd_file_reveal_in_file_manager,

        // 编辑器操作
        EDITOR_SAVE to Strings.cmd_editor_save,
        EDITOR_SAVE_ALL to Strings.cmd_editor_save_all,
        EDITOR_CLOSE to Strings.action_close_current_tab,
        EDITOR_CLOSE_ALL to Strings.action_close_all_tabs,
        EDITOR_CLOSE_OTHERS to Strings.action_close_other_tabs,
        EDITOR_UNDO to Strings.cmd_editor_undo,
        EDITOR_REDO to Strings.cmd_editor_redo,
        EDITOR_SELECT_ALL to Strings.action_select_all,
        EDITOR_COPY to Strings.action_copy,
        EDITOR_CUT to Strings.cmd_editor_cut,
        EDITOR_PASTE to Strings.cmd_editor_paste,
        EDITOR_FIND to Strings.cmd_editor_find,
        EDITOR_REPLACE to Strings.cmd_editor_replace,
        EDITOR_GOTO_LINE to Strings.cmd_editor_goto_line,
        EDITOR_NAVIGATE_BACK to Strings.cmd_editor_navigate_back,
        EDITOR_NAVIGATE_FORWARD to Strings.cmd_editor_navigate_forward,
        EDITOR_TOGGLE_WORD_WRAP to Strings.cmd_editor_toggle_word_wrap,
        EDITOR_FORMAT to Strings.cmd_editor_format,
        EDITOR_TOGGLE_COMMENT to Strings.cmd_editor_toggle_comment,
        EDITOR_PEEK_DEFINITION to Strings.lsp_peek_definition,
        EDITOR_GOTO_DEFINITION to Strings.lsp_goto_definition,
        EDITOR_FIND_REFERENCES to Strings.lsp_find_references,
        EDITOR_GOTO_TYPE_DEFINITION to Strings.lsp_goto_type_definition,
        EDITOR_GOTO_IMPLEMENTATION to Strings.lsp_goto_implementation,
        EDITOR_CODE_ACTIONS to Strings.code_actions_title,
        EDITOR_RENAME_SYMBOL to Strings.lsp_template_rename,
        EDITOR_SWITCH_HEADER_SOURCE to Strings.cmd_editor_switch_header_source,

        // 终端操作
        TERMINAL_TOGGLE to Strings.cmd_terminal_toggle,
        TERMINAL_NEW to Strings.cmd_terminal_new,
        TERMINAL_OPEN_HERE to Strings.cmd_terminal_open_here,
        TERMINAL_CLEAR to Strings.action_clear,

        // 项目操作
        PROJECT_REFRESH to Strings.menu_refresh,
        PROJECT_BUILD to Strings.cmd_project_build,
        PROJECT_RUN to Strings.action_run,
        PROJECT_SETTINGS to Strings.cmd_project_settings,
        PROJECT_CLOSE to Strings.action_close_project,

        // 视图切换
        VIEW_TOGGLE_FILE_TREE to Strings.cmd_view_toggle_file_tree,
        VIEW_TOGGLE_SYMBOLS to Strings.cmd_view_toggle_symbols,
        VIEW_TOGGLE_TERMINAL to Strings.cmd_view_toggle_terminal,
        VIEW_SETTINGS to Strings.menu_settings
    )

    /**
     * 检查命令是否在白名单中
     */
    fun isSupported(commandId: String): Boolean = titleResById.containsKey(commandId)

    /**
     * 获取命令的显示标题资源 ID
     */
    @StringRes
    fun titleResOrNull(commandId: String): Int? = titleResById[commandId]

    /**
     * 获取所有支持的命令 ID 列表
     */
    fun getAllCommandIds(): List<String> = titleResById.keys.toList()

    /**
     * 按类别获取命令
     */
    fun getCommandsByCategory(category: String): List<String> {
        val prefix = "$category."
        return titleResById.keys.filter { it.startsWith(prefix) }
    }
}


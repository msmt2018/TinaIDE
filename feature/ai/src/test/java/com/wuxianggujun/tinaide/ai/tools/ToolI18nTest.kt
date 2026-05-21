package com.wuxianggujun.tinaide.ai.tools

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ToolI18nTest {

    @Test
    fun `getToolName uses string resource for known tool`() {
        val context = mockk<Context>()
        every { context.getString(R.string.ai_tool_read_file) } returns "Read File"

        val name = ToolI18n.getToolName(context, "read_file")

        assertThat(name).isEqualTo("Read File")
        verify(exactly = 1) { context.getString(R.string.ai_tool_read_file) }
    }

    @Test
    fun `getToolName falls back to title case for unknown tool`() {
        val context = mockk<Context>(relaxed = true)

        val name = ToolI18n.getToolName(context, "custom_internal_tool")

        assertThat(name).isEqualTo("Custom Internal Tool")
    }

    @Test
    fun `getToolName maps every known tool to a resource string`() {
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } answers { "res-${firstArg<Int>()}" }
        val knownToolNames = listOf(
            "get_current_file",
            "get_selected_code",
            "insert_code",
            "replace_selected_code",
            "read_file",
            "write_file",
            "list_files",
            "delete_file",
            "create_directory",
            "move_file",
            "copy_file",
            "get_file_info",
            "search_code",
            "find_definition",
            "find_references",
            "get_outline",
            "get_diagnostics",
            "get_all_diagnostics",
            "clear_diagnostics",
            "run_project",
            "run_tests",
            "build_project",
            "stop_execution",
            "get_execution_status",
            "get_project_structure",
            "find_files",
            "count_lines",
            "format_code",
            "extract_method",
            "add_doc_comment",
        )

        val names = knownToolNames.map { ToolI18n.getToolName(context, it) }

        assertThat(names).hasSize(knownToolNames.size)
        assertThat(names).containsNoDuplicates()
        names.forEach { name -> assertThat(name).startsWith("res-") }
        verify(exactly = knownToolNames.size) { context.getString(any<Int>()) }
    }

    @Test
    fun `getCategoryName maps every category to a resource string`() {
        val context = mockk<Context>()
        every { context.getString(any<Int>()) } answers { "res-${firstArg<Int>()}" }

        val names = ToolCategory.entries.associateWith { ToolI18n.getCategoryName(context, it) }

        assertThat(names).hasSize(ToolCategory.entries.size)
        assertThat(names.values).doesNotContain("")
        ToolCategory.entries.forEach { category ->
            assertThat(names[category]).startsWith("res-")
        }
    }
}

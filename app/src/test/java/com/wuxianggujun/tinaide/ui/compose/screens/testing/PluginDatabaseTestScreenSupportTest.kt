package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PluginDatabaseTestScreenSupportTest {

    @Test
    fun appendLog_shouldPrependNewestEntry() {
        val first = PluginDatabaseTestScreenSupport.appendLog(
            current = "",
            msg = "Database initialized",
            timestamp = "12:00:00"
        )
        val second = PluginDatabaseTestScreenSupport.appendLog(
            current = first,
            msg = "Query returned 2 rows",
            timestamp = "12:00:05"
        )

        assertThat(first).isEqualTo("[12:00:00] Database initialized")
        assertThat(second).isEqualTo(
            "[12:00:05] Query returned 2 rows\n[12:00:00] Database initialized"
        )
    }

    @Test
    fun isSelectQuery_shouldHandleWhitespaceAndCaseInsensitiveSelect() {
        assertThat(
            PluginDatabaseTestScreenSupport.isSelectQuery(" \n\tselect * from test_todos")
        ).isTrue()
        assertThat(
            PluginDatabaseTestScreenSupport.isSelectQuery("SELECT * FROM test_todos")
        ).isTrue()
        assertThat(
            PluginDatabaseTestScreenSupport.isSelectQuery("DELETE FROM test_todos")
        ).isFalse()
        assertThat(
            PluginDatabaseTestScreenSupport.isSelectQuery("")
        ).isFalse()
    }

    @Test
    fun buildQueryResultTable_shouldRenderHeaderSeparatorAndRows() {
        val rendered = PluginDatabaseTestScreenSupport.buildQueryResultTable(
            columnNames = listOf("id", "title", "completed"),
            rows = listOf(
                listOf("1", "Task 1", "0"),
                listOf("2", "Task 2", "1")
            )
        )
        val header = "id | title | completed"

        assertThat(rendered).isEqualTo(
            buildString {
                appendLine(header)
                appendLine("-".repeat(header.length))
                appendLine("1 | Task 1 | 0")
                appendLine("2 | Task 2 | 1")
            }
        )
    }

    @Test
    fun buildQueryResultTable_shouldKeepHeaderForEmptyRowsAndFallbackEmptyColumns() {
        val headerOnly = PluginDatabaseTestScreenSupport.buildQueryResultTable(
            columnNames = listOf("id", "title"),
            rows = emptyList()
        )
        val empty = PluginDatabaseTestScreenSupport.buildQueryResultTable(
            columnNames = emptyList(),
            rows = listOf(listOf("1"))
        )
        val header = "id | title"

        assertThat(headerOnly).isEqualTo(
            buildString {
                appendLine(header)
                appendLine("-".repeat(header.length))
            }
        )
        assertThat(empty).isEmpty()
    }
}

package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.plugin.PluginLogEntry
import com.wuxianggujun.tinaide.plugin.PluginLogLevel
import org.junit.Test

class PluginLogScreenSupportTest {

    @Test
    fun resolveHighlightedLogId_shouldReturnLatestErrorWhenOpenedWithErrorFilter() {
        val logs = listOf(
            pluginLogEntry(id = 1L, level = PluginLogLevel.ERROR, message = "first"),
            pluginLogEntry(id = 2L, level = PluginLogLevel.ERROR, message = "second"),
        )

        assertThat(
            PluginLogScreenSupport.resolveHighlightedLogId(
                initialLevel = PluginLogLevel.ERROR,
                selectedLevel = PluginLogLevel.ERROR,
                searchQuery = "",
                filteredLogs = logs,
            )
        ).isEqualTo(2L)
    }

    @Test
    fun resolveHighlightedLogId_shouldDisableHighlightWhenFilterChangedOrSearching() {
        val logs = listOf(
            pluginLogEntry(id = 1L, level = PluginLogLevel.ERROR, message = "first"),
        )

        assertThat(
            PluginLogScreenSupport.resolveHighlightedLogId(
                initialLevel = PluginLogLevel.ERROR,
                selectedLevel = null,
                searchQuery = "",
                filteredLogs = logs,
            )
        ).isNull()
        assertThat(
            PluginLogScreenSupport.resolveHighlightedLogId(
                initialLevel = PluginLogLevel.ERROR,
                selectedLevel = PluginLogLevel.ERROR,
                searchQuery = "reload",
                filteredLogs = logs,
            )
        ).isNull()
        assertThat(
            PluginLogScreenSupport.resolveHighlightedLogId(
                initialLevel = null,
                selectedLevel = PluginLogLevel.ERROR,
                searchQuery = "",
                filteredLogs = logs,
            )
        ).isNull()
    }

    private fun pluginLogEntry(
        id: Long,
        level: PluginLogLevel,
        message: String,
    ): PluginLogEntry = PluginLogEntry(
        id = id,
        timestamp = id,
        pluginId = "demo.plugin",
        pluginName = "Demo Plugin",
        level = level,
        message = message,
        stackTrace = null,
    )
}

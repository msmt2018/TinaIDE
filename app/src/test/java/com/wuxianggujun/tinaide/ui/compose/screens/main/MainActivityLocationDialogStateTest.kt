package com.wuxianggujun.tinaide.ui.compose.screens.main

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.lsp.LocationItem
import java.io.File
import org.junit.Test

class MainActivityLocationDialogStateTest {

    @Test
    fun showLoading_shouldResetPreviousResultsBeforeEnteringLoadingState() {
        val state = MainActivityLocationDialogState()
        state.showResults(
            title = "Old title",
            results = listOf(locationItem("Old.kt", line = 1, column = 2))
        )

        state.showLoading()

        assertThat(state.showDialog).isTrue()
        assertThat(state.isLoading).isTrue()
        assertThat(state.title).isEmpty()
        assertThat(state.locations).isEmpty()
    }

    @Test
    fun dismiss_shouldClearDialogContent() {
        val state = MainActivityLocationDialogState()
        state.showResults(
            title = "Definitions",
            results = listOf(locationItem("Main.kt", line = 5, column = 7))
        )

        state.dismiss()

        assertThat(state.showDialog).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.title).isEmpty()
        assertThat(state.locations).isEmpty()
    }

    private fun locationItem(fileName: String, line: Int, column: Int): LocationItem {
        val file = File(fileName)
        return LocationItem(
            uri = file.toURI().toString(),
            filePath = file.absolutePath,
            fileName = file.name,
            line = line,
            column = column,
            endLine = line,
            endColumn = column + 1
        )
    }
}

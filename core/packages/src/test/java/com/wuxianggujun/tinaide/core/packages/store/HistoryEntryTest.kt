package com.wuxianggujun.tinaide.core.packages.store

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HistoryEntryTest {

    @Test
    fun actionDisplayName_shouldMapKnownActionsAndPreserveUnknownAction() {
        assertThat(historyEntry(action = "install").actionDisplayName).isEqualTo("Installed")
        assertThat(historyEntry(action = "uninstall").actionDisplayName).isEqualTo("Uninstalled")
        assertThat(historyEntry(action = "update").actionDisplayName).isEqualTo("Updated")
        assertThat(historyEntry(action = "repair").actionDisplayName).isEqualTo("repair")
    }

    private fun historyEntry(action: String): HistoryEntry = HistoryEntry(
        packageId = "pkg",
        packageName = "Package",
        platform = "ANDROID",
        action = action,
        success = true,
        timestamp = 1L
    )
}

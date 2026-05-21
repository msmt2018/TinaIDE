package com.wuxianggujun.tinaide.editor

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class IEditorTabProviderTest {

    @Test
    fun `getActiveFile returns file for active tab`() {
        val expectedFile = File("src/main/cpp/main.cpp")
        val provider = object : IEditorTabProvider {
            override fun getOpenTabs(): List<EditorTab> = listOf(
                EditorTab(id = "tab-1", file = expectedFile),
                EditorTab(id = "tab-2", file = File("src/main/cpp/other.cpp"))
            )

            override fun getActiveTabId(): String? = "tab-1"
        }

        assertThat(provider.getActiveFile()).isEqualTo(expectedFile)
    }

    @Test
    fun `getActiveFile returns null when active tab id is missing from open tabs`() {
        val provider = object : IEditorTabProvider {
            override fun getOpenTabs(): List<EditorTab> = listOf(
                EditorTab(id = "tab-1", file = File("src/main/cpp/main.cpp"))
            )

            override fun getActiveTabId(): String? = "missing-tab"
        }

        assertThat(provider.getActiveFile()).isNull()
    }
}

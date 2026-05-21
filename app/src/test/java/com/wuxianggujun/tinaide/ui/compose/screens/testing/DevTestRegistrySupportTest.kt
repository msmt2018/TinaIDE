package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DevTestRegistrySupportTest {

    @Test
    fun createSnapshot_shouldKeepStableOrderAndExposeLookupMap() {
        val editorBackedItems = listOf(testItem(DevTestIds.ThemePreview), testItem(DevTestIds.TreeSitter))
        val manualItems = listOf(testItem(DevTestIds.CompilerDiagnostics), testItem(DevTestIds.AiChat))

        val snapshot = DevTestRegistrySupport.createSnapshot(
            editorBackedItems = editorBackedItems,
            manualItems = manualItems,
        )

        assertThat(snapshot.items.map { it.id }).containsExactly(
            DevTestIds.ThemePreview,
            DevTestIds.TreeSitter,
            DevTestIds.CompilerDiagnostics,
            DevTestIds.AiChat,
        ).inOrder()
        assertThat(snapshot.itemsById.keys).containsExactly(
            DevTestIds.ThemePreview,
            DevTestIds.TreeSitter,
            DevTestIds.CompilerDiagnostics,
            DevTestIds.AiChat,
        )
        assertThat(snapshot.itemsById[DevTestIds.TreeSitter]).isSameInstanceAs(snapshot.items[1])
        assertThat(snapshot.itemsById["missing"]).isNull()
    }

    @Test
    fun createSnapshot_shouldRejectDuplicateIdsAcrossSources() {
        val error = runCatching {
            DevTestRegistrySupport.createSnapshot(
                editorBackedItems = listOf(testItem(DevTestIds.Clangd)),
                manualItems = listOf(testItem(DevTestIds.Clangd)),
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error?.message).isEqualTo("Duplicate developer test id: ${DevTestIds.Clangd}")
    }

    private fun testItem(id: String): DevTestItem = DevTestItem(
        id = id,
        titleRes = 0,
        descriptionRes = 0,
        content = {},
    )
}

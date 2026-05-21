package com.wuxianggujun.tinaide.ui.compose.screens.testing

internal data class DevTestRegistrySnapshot(
    val items: List<DevTestItem>,
    val itemsById: Map<String, DevTestItem>,
)

internal object DevTestRegistrySupport {

    fun createSnapshot(
        editorBackedItems: List<DevTestItem>,
        manualItems: List<DevTestItem>,
    ): DevTestRegistrySnapshot {
        val items = buildList<DevTestItem> {
            (editorBackedItems + manualItems).forEach { item ->
                require(none { existing -> existing.id == item.id }) {
                    "Duplicate developer test id: ${item.id}"
                }
                add(item)
            }
        }
        return DevTestRegistrySnapshot(
            items = items,
            itemsById = items.associateBy(DevTestItem::id),
        )
    }
}

package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * 测试项定义
 *
 * @param id 唯一标识符，用于路由
 * @param titleRes 标题字符串资源
 * @param descriptionRes 描述字符串资源
 * @param content 测试界面的 Composable 内容
 */
data class DevTestItem(
    val id: String,
    @param:StringRes @get:StringRes val titleRes: Int,
    @param:StringRes @get:StringRes val descriptionRes: Int,
    val content: @Composable (onNavigateBack: () -> Unit) -> Unit
)

/**
 * 开发者测试注册表
 *
 * 统一管理所有测试入口，新增测试只需在此注册即可
 */
object DevTestRegistry {

    private val snapshot = DevTestRegistrySupport.createSnapshot(
        editorBackedItems = DevEditorTestCatalog.toRegistryItems(),
        manualItems = DevManualTestCatalog.toRegistryItems()
    )

    /**
     * 获取所有已注册的测试项
     */
    fun getAllTests(): List<DevTestItem> = snapshot.items

    /**
     * 根据 ID 查找测试项
     */
    fun findById(id: String): DevTestItem? = snapshot.itemsById[id]
}

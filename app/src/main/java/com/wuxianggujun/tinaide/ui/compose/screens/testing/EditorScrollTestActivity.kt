package com.wuxianggujun.tinaide.ui.compose.screens.testing

/**
 * 编辑器滚动测试页面 Activity（开发者选项）
 */
class EditorScrollTestActivity : LegacyDevTestRedirectActivity() {
    override val targetTestId: String = DevEditorTestCatalog.editorScroll.registryId
}

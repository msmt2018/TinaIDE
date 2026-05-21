package com.wuxianggujun.tinaide.ui.compose.screens.testing

/**
 * 主题预览测试页面 Activity（开发者选项）
 */
class ThemePreviewTestActivity : LegacyDevTestRedirectActivity() {
    override val targetTestId: String = DevEditorTestCatalog.themePreview.registryId
}

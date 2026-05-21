package com.wuxianggujun.tinaide.ui.compose.screens.testing

/**
 * Clangd LSP 测试页面 Activity
 */
class ClangdTestActivity : LegacyDevTestRedirectActivity() {
    override val targetTestId: String = DevEditorTestCatalog.clangd.registryId
}

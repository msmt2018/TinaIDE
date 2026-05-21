package com.wuxianggujun.tinaide.ui.compose.screens.testing

/**
 * Tree-sitter 语法高亮测试页面 Activity（开发者选项）
 */
class TreeSitterTestActivity : LegacyDevTestRedirectActivity() {
    override val targetTestId: String = DevEditorTestCatalog.treeSitter.registryId
}

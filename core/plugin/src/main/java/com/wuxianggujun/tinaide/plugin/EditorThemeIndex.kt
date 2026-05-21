package com.wuxianggujun.tinaide.plugin

import kotlinx.coroutines.flow.StateFlow

/**
 * 编辑器主题索引接口
 *
 * 将主题列表的读取契约下沉到 core:plugin，
 * 使 feature:settings 无需依赖 feature:editor 即可展示主题列表。
 */
interface EditorThemeIndex {
    val themesFlow: StateFlow<Map<String, ThemeConfig>>
}

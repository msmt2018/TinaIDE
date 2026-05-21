package com.wuxianggujun.tinaide.search.replace

/**
 * 替换选项
 */
data class ReplaceOptions(
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val wholeWord: Boolean = false,
    val preserveCase: Boolean = false,      // 保留原始大小写（如 Foo -> Bar, foo -> bar）
    val useRegexGroups: Boolean = false     // 支持正则捕获组 ($1, $2 等)
)

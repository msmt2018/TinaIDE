package com.wuxianggujun.tinaide.search

/**
 * 项目级搜索选项
 */
data class ProjectSearchOptions(
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val wholeWord: Boolean = false,           // 全词匹配
    val fileExtensions: Set<String>? = null,  // null = 所有文本文件
    val maxResults: Int = 1000,
    val maxFileSize: Long = 1024 * 1024,      // 1MB
    val contextLines: Int = 0,                // 上下文行数 (0 = 不显示上下文)
    val includePatterns: List<String> = emptyList(),  // 包含文件模式 (glob)
    val excludePatterns: List<String> = emptyList()   // 排除文件模式 (glob)
)

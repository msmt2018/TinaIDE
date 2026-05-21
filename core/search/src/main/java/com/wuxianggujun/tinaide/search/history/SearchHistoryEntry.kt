package com.wuxianggujun.tinaide.search.history

/**
 * 搜索历史条目
 */
data class SearchHistoryEntry(
    val id: Long,
    val query: String,
    val replacement: String? = null,
    val options: SearchHistoryOptions,
    val timestamp: Long,
    val isFavorite: Boolean = false
)

/**
 * 搜索历史选项（用于记录搜索时的选项状态）
 */
data class SearchHistoryOptions(
    val caseSensitive: Boolean = false,
    val useRegex: Boolean = false,
    val wholeWord: Boolean = false
)

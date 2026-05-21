package com.wuxianggujun.tinaide.snippet.viewmodel

import com.wuxianggujun.tinaide.snippet.model.SnippetDetail
import com.wuxianggujun.tinaide.snippet.model.SnippetSummary

/**
 * 代码片段市场列表状态
 */
data class SnippetListState(
    val isLoading: Boolean = false,
    val snippets: List<SnippetSummary> = emptyList(),
    val searchQuery: String? = null,
    val onlyMine: Boolean = false,
    val error: String? = null
)

/**
 * 代码片段详情状态
 */
data class SnippetDetailState(
    val isLoading: Boolean = false,
    val snippet: SnippetDetail? = null,
    val error: String? = null
)

/**
 * 我的代码片段列表状态
 */
data class MySnippetsState(
    val isLoading: Boolean = false,
    val snippets: List<SnippetSummary> = emptyList(),
    val error: String? = null
)

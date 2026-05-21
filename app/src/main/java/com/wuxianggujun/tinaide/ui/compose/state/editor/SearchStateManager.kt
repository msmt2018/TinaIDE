package com.wuxianggujun.tinaide.ui.compose.state.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wuxianggujun.tinaide.editor.SearchState
import com.wuxianggujun.tinaide.search.CodeSearchResult as CodeSearchHit
import com.wuxianggujun.tinaide.search.HexSearchEngine
import com.wuxianggujun.tinaide.search.HexSearchResult as HexSearchHit
import com.wuxianggujun.tinaide.search.SearchOptions
import com.wuxianggujun.tinaide.search.SearchResult

/**
 * 搜索状态管理器
 *
 * 职责：
 * - 管理搜索框的显示/隐藏状态
 * - 管理搜索关键词和匹配结果
 * - 执行代码编辑器、JSON、Hex 查看器的搜索
 * - 导航到上一个/下一个匹配
 *
 * 从 EditorContainerState 中提取，简化主状态类
 */
class SearchStateManager {

    // 当前搜索状态
    var currentSearchState by mutableStateOf(SearchState())
        private set

    private val searchResults = mutableMapOf<String, List<SearchResult>>()

    // 通用代码查看器回调（用于 TinaEditor 等非 CodeEditor 实现）
    data class CodeViewerCallback(
        val search: (String, SearchOptions) -> List<CodeSearchHit>,
        val goToMatch: (CodeSearchHit) -> Unit
    )
    private val codeViewerCallbacks = mutableMapOf<String, CodeViewerCallback>()

    // Hex 查看器回调
    data class HexViewerCallback(
        val search: (String) -> List<Long>,
        val goToOffset: (Long) -> Unit
    )
    private val hexViewerCallbacks = mutableMapOf<String, HexViewerCallback>()

    /**
     * 显示搜索框
     */
    fun showSearch() {
        currentSearchState = currentSearchState.copy(isActive = true)
    }

    /**
     * 隐藏搜索框
     */
    fun hideSearch(activeTabId: String?) {
        currentSearchState = currentSearchState.copy(isActive = false)
        activeTabId?.let { clearSearchForTab(it) }
    }

    /**
     * 更新搜索关键词
     */
    fun updateSearchQuery(query: String) {
        currentSearchState = currentSearchState.copy(query = query)
        if (query.isEmpty()) {
            currentSearchState = currentSearchState.copy(matchCount = 0, currentIndex = -1)
        }
    }

    fun toggleSearchCaseSensitive() {
        currentSearchState = currentSearchState.copy(
            caseSensitive = !currentSearchState.caseSensitive
        )
    }

    fun toggleSearchUseRegex() {
        currentSearchState = currentSearchState.copy(
            useRegex = !currentSearchState.useRegex
        )
    }

    /**
     * 执行搜索（通用代码查看器）
     */
    fun searchInCodeViewer(tabId: String): Int {
        val query = currentSearchState.query
        val callback = codeViewerCallbacks[tabId] ?: return 0

        if (query.isEmpty()) {
            searchResults.remove(tabId)
            currentSearchState = currentSearchState.copy(matchCount = 0, currentIndex = -1)
            return 0
        }

        val results = callback.search(query, buildSearchOptions())
        searchResults[tabId] = results

        val firstMatch = results.firstOrNull()
        if (firstMatch != null) {
            callback.goToMatch(firstMatch)
        }

        val matchCount = results.size
        currentSearchState = currentSearchState.copy(
            matchCount = matchCount,
            currentIndex = if (matchCount > 0) 0 else -1
        )

        return matchCount
    }

    /**
     * 执行搜索（Hex 查看器）
     */
    fun searchInHexViewer(tabId: String): Int {
        val query = currentSearchState.query
        if (query.isEmpty()) return 0

        val callback = hexViewerCallbacks[tabId] ?: return 0
        val results = HexSearchEngine(callback.search).search(query)
        searchResults[tabId] = results

        val firstMatch = results.firstOrNull() as? HexSearchHit
        if (firstMatch != null) {
            callback.goToOffset(firstMatch.offset)
        }

        val matchCount = results.size
        currentSearchState = currentSearchState.copy(
            matchCount = matchCount,
            currentIndex = if (matchCount > 0) 0 else -1
        )

        return matchCount
    }

    /**
     * 跳转到下一个匹配
     */
    fun findNext() {
        if (currentSearchState.matchCount <= 0) return
        val newIndex = (currentSearchState.currentIndex + 1) % currentSearchState.matchCount
        currentSearchState = currentSearchState.copy(currentIndex = newIndex)
    }

    /**
     * 跳转到上一个匹配
     */
    fun findPrevious() {
        if (currentSearchState.matchCount <= 0) return
        val newIndex = if (currentSearchState.currentIndex <= 0) {
            currentSearchState.matchCount - 1
        } else {
            currentSearchState.currentIndex - 1
        }
        currentSearchState = currentSearchState.copy(currentIndex = newIndex)
    }

    /**
     * 跳转到通用代码查看器中的当前匹配
     */
    fun goToMatchInCodeViewer(tabId: String) {
        val index = currentSearchState.currentIndex
        if (index < 0) return

        val callback = codeViewerCallbacks[tabId] ?: return
        val result = searchResults[tabId]?.getOrNull(index) as? CodeSearchHit ?: return
        callback.goToMatch(result)
    }

    /**
     * 跳转到 Hex 查看器中的当前匹配
     */
    fun goToMatchInHexViewer(tabId: String) {
        val index = currentSearchState.currentIndex
        if (index < 0) return

        val callback = hexViewerCallbacks[tabId] ?: return
        val result = searchResults[tabId]?.getOrNull(index) as? HexSearchHit ?: return
        callback.goToOffset(result.offset)
    }

    private fun clearSearchForTab(tabId: String) {
        searchResults.remove(tabId)
    }

    private fun buildSearchOptions(): SearchOptions = SearchOptions(
        caseSensitive = currentSearchState.caseSensitive,
        useRegex = currentSearchState.useRegex,
        wholeWord = currentSearchState.wholeWord
    )

    // ========== 回调注册 ==========

    fun hasCodeViewerCallback(tabId: String): Boolean = codeViewerCallbacks[tabId] != null

    fun registerCodeViewerCallback(tabId: String, callback: CodeViewerCallback) {
        codeViewerCallbacks[tabId] = callback
    }

    fun unregisterCodeViewerCallback(tabId: String) {
        codeViewerCallbacks.remove(tabId)
    }

    fun registerHexViewerCallback(tabId: String, callback: HexViewerCallback) {
        hexViewerCallbacks[tabId] = callback
    }

    fun unregisterHexViewerCallback(tabId: String) {
        hexViewerCallbacks.remove(tabId)
    }

    /**
     * 清理指定 Tab 的所有搜索相关资源
     */
    fun cleanupForTab(tabId: String) {
        clearSearchForTab(tabId)
        codeViewerCallbacks.remove(tabId)
        hexViewerCallbacks.remove(tabId)
    }

    /**
     * 清理所有资源
     */
    fun release() {
        searchResults.clear()
        codeViewerCallbacks.clear()
        hexViewerCallbacks.clear()
    }
}

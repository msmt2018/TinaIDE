package com.wuxianggujun.tinaide.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.search.ProjectSearchEngine
import com.wuxianggujun.tinaide.search.ProjectSearchOptions
import com.wuxianggujun.tinaide.search.ProjectSearchResult
import com.wuxianggujun.tinaide.search.history.SearchHistoryEntry
import com.wuxianggujun.tinaide.search.history.SearchHistoryManager
import com.wuxianggujun.tinaide.search.replace.BatchReplaceManager
import com.wuxianggujun.tinaide.search.replace.BatchReplaceResult
import com.wuxianggujun.tinaide.search.replace.ReplaceOptions
import com.wuxianggujun.tinaide.search.replace.ReplaceProgress
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 全局搜索 ViewModel
 */
class GlobalSearchViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "GlobalSearchViewModel"
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ProjectSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ProjectSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _caseSensitive = MutableStateFlow(false)
    val caseSensitive: StateFlow<Boolean> = _caseSensitive.asStateFlow()

    private val _useRegex = MutableStateFlow(false)
    val useRegex: StateFlow<Boolean> = _useRegex.asStateFlow()

    private val _wholeWord = MutableStateFlow(false)
    val wholeWord: StateFlow<Boolean> = _wholeWord.asStateFlow()

    private val _resultCount = MutableStateFlow(0)
    val resultCount: StateFlow<Int> = _resultCount.asStateFlow()

    private val _fileCount = MutableStateFlow(0)
    val fileCount: StateFlow<Int> = _fileCount.asStateFlow()

    // 替换相关状态
    private val _replacementText = MutableStateFlow("")
    val replacementText: StateFlow<String> = _replacementText.asStateFlow()

    private val _isReplaceMode = MutableStateFlow(false)
    val isReplaceMode: StateFlow<Boolean> = _isReplaceMode.asStateFlow()

    private val _selectedResults = MutableStateFlow<Set<String>>(emptySet())
    val selectedResults: StateFlow<Set<String>> = _selectedResults.asStateFlow()

    // 替换执行相关状态
    private val _replaceProgress = MutableStateFlow(ReplaceProgress(0, 0))
    val replaceProgress: StateFlow<ReplaceProgress> = _replaceProgress.asStateFlow()

    private val _isReplacing = MutableStateFlow(false)
    val isReplacing: StateFlow<Boolean> = _isReplacing.asStateFlow()

    private val _replaceResult = MutableStateFlow<BatchReplaceResult?>(null)
    val replaceResult: StateFlow<BatchReplaceResult?> = _replaceResult.asStateFlow()

    private var searchJob: Job? = null
    private var projectPath: String? = null
    private val batchReplaceManager = BatchReplaceManager()
    private val searchHistoryManager = SearchHistoryManager(application)

    // 搜索历史状态
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = searchHistoryManager.history
    val searchFavorites: StateFlow<List<SearchHistoryEntry>> = searchHistoryManager.favorites

    /**
     * 设置项目路径
     */
    fun setProjectPath(path: String) {
        projectPath = path
    }

    /**
     * 更新搜索查询（带防抖）
     */
    fun updateQuery(query: String) {
        _searchQuery.value = query

        // 取消之前的搜索
        searchJob?.cancel()

        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            _resultCount.value = 0
            _fileCount.value = 0
            _isSearching.value = false
            return
        }

        // 防抖搜索
        searchJob = viewModelScope.launch {
            delay(300) // 300ms 防抖
            performSearch(query)
        }
    }

    /**
     * 立即执行搜索
     */
    fun searchNow() {
        val query = _searchQuery.value
        if (query.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(query)
        }
    }

    /**
     * 切换大小写敏感
     */
    fun toggleCaseSensitive() {
        _caseSensitive.value = !_caseSensitive.value
        if (_searchQuery.value.isNotEmpty()) {
            searchNow()
        }
    }

    /**
     * 切换正则表达式
     */
    fun toggleRegex() {
        _useRegex.value = !_useRegex.value
        if (_searchQuery.value.isNotEmpty()) {
            searchNow()
        }
    }

    /**
     * 切换全词匹配
     */
    fun toggleWholeWord() {
        _wholeWord.value = !_wholeWord.value
        if (_searchQuery.value.isNotEmpty()) {
            searchNow()
        }
    }

    /**
     * 切换替换模式
     */
    fun toggleReplaceMode() {
        _isReplaceMode.value = !_isReplaceMode.value
        if (!_isReplaceMode.value) {
            _replacementText.value = ""
        }
    }

    /**
     * 更新替换文本
     */
    fun updateReplacementText(text: String) {
        _replacementText.value = text
    }

    /**
     * 切换结果选择状态
     */
    fun toggleResultSelection(resultKey: String) {
        val current = _selectedResults.value.toMutableSet()
        if (current.contains(resultKey)) {
            current.remove(resultKey)
        } else {
            current.add(resultKey)
        }
        _selectedResults.value = current
    }

    /**
     * 全选所有结果
     */
    fun selectAllResults() {
        _selectedResults.value = _searchResults.value.map { it.uniqueKey }.toSet()
    }

    /**
     * 取消全选
     */
    fun deselectAllResults() {
        _selectedResults.value = emptySet()
    }

    /**
     * 获取选中的结果数量
     */
    fun getSelectedCount(): Int = _selectedResults.value.size

    /**
     * 检查结果是否被选中
     */
    fun isResultSelected(resultKey: String): Boolean = _selectedResults.value.contains(resultKey)

    /**
     * 清除搜索
     */
    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _resultCount.value = 0
        _fileCount.value = 0
        _isSearching.value = false
        _selectedResults.value = emptySet()
        _replacementText.value = ""
    }

    private suspend fun performSearch(query: String) {
        val path = projectPath ?: return

        _isSearching.value = true
        _searchResults.value = emptyList()
        _selectedResults.value = emptySet()

        try {
            val engine = ProjectSearchEngine(path)
            val options = ProjectSearchOptions(
                caseSensitive = _caseSensitive.value,
                useRegex = _useRegex.value,
                wholeWord = _wholeWord.value
            )

            val results = mutableListOf<ProjectSearchResult>()

            // 使用 Flow 收集结果，支持流式更新
            engine.searchFlow(query, options).collect { result ->
                results.add(result)
                // 每 50 个结果更新一次 UI
                if (results.size % 50 == 0) {
                    _searchResults.value = results.toList()
                    updateCounts(results)
                }
            }

            _searchResults.value = results
            updateCounts(results)
            // 搜索完成后自动全选所有结果
            _selectedResults.value = results.map { it.uniqueKey }.toSet()
            // 搜索完成后保存到历史
            if (results.isNotEmpty()) {
                saveToHistory()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Global search failed: query=%s, projectPath=%s", query, path)
        } finally {
            _isSearching.value = false
        }
    }

    private fun updateCounts(results: List<ProjectSearchResult>) {
        _resultCount.value = results.size
        _fileCount.value = results.map { it.file }.distinct().size
    }

    /**
     * 获取相对路径
     */
    fun getRelativePath(file: File): String {
        val path = projectPath ?: return file.name
        return file.absolutePath.removePrefix(path).removePrefix(File.separator)
    }

    /**
     * 获取选中的搜索结果
     */
    fun getSelectedSearchResults(): List<ProjectSearchResult> {
        val selected = _selectedResults.value
        return _searchResults.value.filter { selected.contains(it.uniqueKey) }
    }

    /**
     * 执行批量替换
     */
    fun executeReplace(onComplete: (BatchReplaceResult) -> Unit = {}) {
        val query = _searchQuery.value
        val replacement = _replacementText.value
        if (query.isEmpty()) return

        val selectedResults = getSelectedSearchResults()
        if (selectedResults.isEmpty()) return

        viewModelScope.launch {
            _isReplacing.value = true
            _replaceResult.value = null

            try {
                val options = ReplaceOptions(
                    caseSensitive = _caseSensitive.value,
                    useRegex = _useRegex.value,
                    wholeWord = _wholeWord.value
                )

                // 生成预览
                val preview = batchReplaceManager.generateBatchPreview(
                    results = selectedResults,
                    searchQuery = query,
                    replacement = replacement,
                    options = options
                )

                // 收集进度更新
                val progressJob = launch {
                    batchReplaceManager.progress.collect { progress ->
                        _replaceProgress.value = progress
                    }
                }

                // 执行替换
                val result = batchReplaceManager.executeBatch(
                    previews = preview.previews,
                    createBackups = true
                )

                progressJob.cancel()
                _replaceResult.value = result
                onComplete(result)

                // 替换成功后重新搜索以更新结果
                if (result.isSuccess || result.hasPartialSuccess) {
                    searchNow()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(
                    e,
                    "Batch replace failed: query=%s, selected=%s",
                    query,
                    selectedResults.size
                )
            } finally {
                _isReplacing.value = false
            }
        }
    }

    /**
     * 撤销上一次替换
     */
    fun undoReplace(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = batchReplaceManager.undoLastBatch()
            onComplete(success)
            if (success) {
                searchNow()
            }
        }
    }

    /**
     * 检查是否可以撤销
     */
    fun canUndoReplace(): Boolean = batchReplaceManager.canUndo()

    /**
     * 清除替换结果状态
     */
    fun clearReplaceResult() {
        _replaceResult.value = null
    }

    // ========== 搜索历史相关方法 ==========

    /**
     * 保存当前搜索到历史
     */
    fun saveToHistory() {
        val query = _searchQuery.value
        if (query.isBlank()) return

        searchHistoryManager.addEntry(
            query = query,
            replacement = _replacementText.value.takeIf { it.isNotBlank() },
            caseSensitive = _caseSensitive.value,
            useRegex = _useRegex.value,
            wholeWord = _wholeWord.value
        )
    }

    /**
     * 从历史记录恢复搜索
     */
    fun restoreFromHistory(entry: SearchHistoryEntry) {
        _searchQuery.value = entry.query
        _caseSensitive.value = entry.options.caseSensitive
        _useRegex.value = entry.options.useRegex
        _wholeWord.value = entry.options.wholeWord
        entry.replacement?.let {
            _replacementText.value = it
            _isReplaceMode.value = true
        }
        searchNow()
    }

    /**
     * 切换历史记录收藏状态
     */
    fun toggleHistoryFavorite(entryId: Long) {
        searchHistoryManager.toggleFavorite(entryId)
    }

    /**
     * 删除历史记录
     */
    fun deleteHistoryEntry(entryId: Long) {
        searchHistoryManager.deleteEntry(entryId)
    }

    /**
     * 清除非收藏历史
     */
    fun clearNonFavoriteHistory() {
        searchHistoryManager.clearNonFavorites()
    }
}

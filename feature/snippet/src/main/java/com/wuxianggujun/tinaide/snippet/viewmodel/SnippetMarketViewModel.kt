package com.wuxianggujun.tinaide.snippet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.network.ApiResult
import com.wuxianggujun.tinaide.snippet.api.CopyResponse
import com.wuxianggujun.tinaide.snippet.api.FavoriteResponse
import com.wuxianggujun.tinaide.snippet.api.RateResponse
import com.wuxianggujun.tinaide.snippet.repository.SnippetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 代码片段市场 ViewModel
 *
 * 负责：片段列表浏览、搜索、详情查看、收藏、评分、复制、创建
 */
class SnippetMarketViewModel(
    private val snippetRepository: SnippetRepository
) : ViewModel() {

    private val _listState = MutableStateFlow(SnippetListState())
    val listState: StateFlow<SnippetListState> = _listState.asStateFlow()

    private val _detailState = MutableStateFlow(SnippetDetailState())
    val detailState: StateFlow<SnippetDetailState> = _detailState.asStateFlow()

    init {
        loadSnippets()
    }

    // ── 列表 ──

    fun loadSnippets(
        search: String? = _listState.value.searchQuery,
        currentUserId: String? = null,
        onlyMine: Boolean = _listState.value.onlyMine
    ) {
        viewModelScope.launch {
            _listState.update {
                it.copy(isLoading = true, error = null, searchQuery = search, onlyMine = onlyMine)
            }

            if (onlyMine && currentUserId.isNullOrBlank()) {
                _listState.update {
                    it.copy(isLoading = false, error = Strings.snippet_publish_unavailable.str())
                }
                return@launch
            }

            val result = snippetRepository.listSnippets(
                page = 1,
                limit = 20,
                search = search,
                authorId = currentUserId.takeIf { onlyMine },
                includeDrafts = if (onlyMine) true else null
            )

            when (result) {
                is ApiResult.Success -> _listState.update {
                    it.copy(isLoading = false, snippets = result.data.snippets, error = null)
                }
                is ApiResult.Error -> _listState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                is ApiResult.NetworkError -> _listState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    // ── 详情 ──

    fun loadSnippetDetail(id: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true, error = null, snippet = null) }

            when (val result = snippetRepository.getSnippetDetail(id)) {
                is ApiResult.Success -> _detailState.update {
                    it.copy(isLoading = false, snippet = result.data, error = null)
                }
                is ApiResult.Error -> _detailState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                is ApiResult.NetworkError -> _detailState.update {
                    it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun clearSnippetDetail() {
        _detailState.update { SnippetDetailState() }
    }

    // ── 收藏 ──

    fun toggleSnippetFavorite() {
        val current = _detailState.value.snippet ?: return
        viewModelScope.launch {
            val result = if (current.isFavorited) {
                snippetRepository.unfavorite(current.id)
            } else {
                snippetRepository.favorite(current.id)
            }

            when (result) {
                is ApiResult.Success -> applyFavoriteResult(result.data)
                is ApiResult.Error -> _detailState.update { it.copy(error = result.message) }
                is ApiResult.NetworkError -> _detailState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun applyFavoriteResult(data: FavoriteResponse) {
        _detailState.update { state ->
            val snippet = state.snippet ?: return@update state
            state.copy(
                snippet = snippet.copy(
                    isFavorited = data.isFavorited,
                    favoriteCount = data.favoriteCount
                ),
                error = null
            )
        }
    }

    // ── 评分 ──

    fun rateSnippet(rating: Int) {
        val current = _detailState.value.snippet ?: return
        viewModelScope.launch {
            when (val result = snippetRepository.rate(current.id, rating)) {
                is ApiResult.Success -> applyRateResult(result.data)
                is ApiResult.Error -> _detailState.update { it.copy(error = result.message) }
                is ApiResult.NetworkError -> _detailState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun applyRateResult(data: RateResponse) {
        _detailState.update { state ->
            val snippet = state.snippet ?: return@update state
            state.copy(
                snippet = snippet.copy(
                    myRating = data.myRating,
                    ratingAvg = data.ratingAvg,
                    ratingCount = data.ratingCount
                ),
                error = null
            )
        }
    }

    // ── 复制 ──

    fun recordSnippetCopy() {
        val current = _detailState.value.snippet ?: return
        viewModelScope.launch {
            when (val result = snippetRepository.recordCopy(current.id)) {
                is ApiResult.Success -> applyCopyResult(result.data)
                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    private fun applyCopyResult(data: CopyResponse) {
        _detailState.update { state ->
            val snippet = state.snippet ?: return@update state
            state.copy(snippet = snippet.copy(copyCount = data.copyCount))
        }
    }

    // ── 创建 ──

    fun createSnippet(
        title: String,
        description: String?,
        language: String,
        codeContent: String,
        isDraft: Boolean,
        onDone: (success: Boolean, message: String?) -> Unit
    ) {
        viewModelScope.launch {
            val t = title.trim()
            val lang = language.trim()
            val code = codeContent
            if (t.length < 3) {
                onDone(false, Strings.market_error_title_min_length.str())
                return@launch
            }
            if (lang.isEmpty()) {
                onDone(false, Strings.market_error_language_empty.str())
                return@launch
            }
            if (code.trim().isEmpty()) {
                onDone(false, Strings.market_error_code_empty.str())
                return@launch
            }

            when (
                val result = snippetRepository.createSnippet(
                    title = t,
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    codeContent = code,
                    language = lang,
                    isDraft = isDraft
                )
            ) {
                is ApiResult.Success -> {
                    loadSnippets()
                    onDone(true, null)
                }
                is ApiResult.Error -> onDone(false, result.message)
                is ApiResult.NetworkError -> onDone(false, result.message)
            }
        }
    }
}

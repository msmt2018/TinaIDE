package com.wuxianggujun.tinaide.ui.compose.screens.main.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.user.DownloadHistoryItem
import com.wuxianggujun.tinaide.core.user.DownloadItemType
import com.wuxianggujun.tinaide.core.user.UserContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 下载历史界面状态
 */
data class DownloadHistoryUiState(
    val downloads: List<DownloadHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    val filterType: DownloadItemType? = null
)

/**
 * 下载历史界面 ViewModel
 */
class DownloadHistoryViewModel(
    private val repository: UserContentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadHistoryUiState())
    val uiState: StateFlow<DownloadHistoryUiState> = _uiState.asStateFlow()

    init {
        // 延迟加载，避免初始化时崩溃
        viewModelScope.launch {
            try {
                loadDownloadHistory()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: Strings.profile_load_failed.str()
                )
            }
        }
    }

    fun loadDownloadHistory(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = DownloadHistoryUiState(
                    isLoading = true,
                    filterType = _uiState.value.filterType
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            val page = if (refresh) 1 else _uiState.value.currentPage
            val filterType = _uiState.value.filterType

            repository.getDownloadHistory(page = page, pageSize = 20).fold(
                onSuccess = { response ->
                    // 在客户端进行类型过滤
                    val filteredItems = if (filterType != null) {
                        response.items.filter { it.itemType == filterType.name.lowercase() }
                    } else {
                        response.items
                    }

                    val newDownloads = if (refresh) {
                        filteredItems
                    } else {
                        _uiState.value.downloads + filteredItems
                    }

                    val hasMore = page * response.pageSize < response.total

                    _uiState.value = _uiState.value.copy(
                        downloads = newDownloads,
                        isLoading = false,
                        hasMore = hasMore,
                        currentPage = page
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: Strings.profile_load_failed.str()
                    )
                }
            )
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        _uiState.value = _uiState.value.copy(currentPage = _uiState.value.currentPage + 1)
        loadDownloadHistory()
    }

    fun setFilter(type: DownloadItemType?) {
        _uiState.value = _uiState.value.copy(filterType = type, currentPage = 1)
        loadDownloadHistory(refresh = true)
    }

    fun refresh() {
        loadDownloadHistory(refresh = true)
    }
}

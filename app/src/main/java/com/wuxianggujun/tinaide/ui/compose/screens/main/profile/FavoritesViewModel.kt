package com.wuxianggujun.tinaide.ui.compose.screens.main.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import com.wuxianggujun.tinaide.core.user.FavoritePlugin
import com.wuxianggujun.tinaide.core.user.UserContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 收藏界面状态
 */
data class FavoritesUiState(
    val favorites: List<FavoritePlugin> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val syncError: String? = null,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
)

/**
 * 收藏界面 ViewModel
 */
class FavoritesViewModel(
    private val repository: UserContentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        // 首次加载时先从服务器同步，再加载本地数据
        viewModelScope.launch {
            try {
                // 先尝试从服务器同步（静默同步，不显示加载状态）
                repository.syncFavoritesFromServer()
                // 然后加载本地数据
                loadFavorites()
            } catch (e: Exception) {
                // 同步失败也继续加载本地数据
                loadFavorites()
            }
        }
    }

    fun loadFavorites(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) {
                _uiState.value = FavoritesUiState(isLoading = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            val page = if (refresh) 1 else _uiState.value.currentPage

            repository.getFavorites(page = page, pageSize = 20).fold(
                onSuccess = { response ->
                    val newFavorites = if (refresh) {
                        response.plugins
                    } else {
                        _uiState.value.favorites + response.plugins
                    }

                    val hasMore = page * response.pageSize < response.total

                    _uiState.value = FavoritesUiState(
                        favorites = newFavorites,
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
        loadFavorites()
    }

    fun removeFavorite(pluginId: String) {
        viewModelScope.launch {
            repository.removeFavorite(pluginId).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        favorites = _uiState.value.favorites.filter { it.pluginId != pluginId }
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: Strings.market_unfavorite_failed.str()
                    )
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // 先从服务器同步
            syncFromServer()
            // 再加载本地数据
            loadFavorites(refresh = true)
        }
    }

    /**
     * 从服务器同步收藏
     */
    fun syncFromServer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)

            repository.syncFavoritesFromServer().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                    // 同步成功后重新加载本地数据
                    loadFavorites(refresh = true)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncError = error.message ?: Strings.favorites_sync_failed.str()
                    )
                }
            )
        }
    }

    fun clearSyncError() {
        _uiState.value = _uiState.value.copy(syncError = null)
    }
}

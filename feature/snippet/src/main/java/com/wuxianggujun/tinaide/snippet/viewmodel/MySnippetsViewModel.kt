package com.wuxianggujun.tinaide.snippet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 我的代码片段 ViewModel
 *
 * 负责：加载当前用户发布的代码片段列表、创建新片段
 */
class MySnippetsViewModel : ViewModel() {
    private val _state = MutableStateFlow(MySnippetsState())
    val state: StateFlow<MySnippetsState> = _state.asStateFlow()

    fun loadMySnippets() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = false,
                    snippets = emptyList(),
                    error = Strings.snippet_publish_unavailable.str()
                )
            }
        }
    }

    fun createSnippet(
        title: String,
        description: String?,
        language: String,
        codeContent: String,
        isDraft: Boolean,
        onComplete: (success: Boolean, message: String?) -> Unit
    ) {
        viewModelScope.launch {
            onComplete(false, Strings.snippet_publish_unavailable.str())
        }
    }
}

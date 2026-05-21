package com.wuxianggujun.tinaide.snippet.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnippetUiStateTest {

    @Test
    fun uiStates_shouldExposeEmptyIdleDefaults() {
        val list = SnippetListState()
        val detail = SnippetDetailState()
        val mine = MySnippetsState()

        assertThat(list.isLoading).isFalse()
        assertThat(list.snippets).isEmpty()
        assertThat(list.searchQuery).isNull()
        assertThat(list.onlyMine).isFalse()
        assertThat(detail.snippet).isNull()
        assertThat(mine.error).isNull()
    }
}

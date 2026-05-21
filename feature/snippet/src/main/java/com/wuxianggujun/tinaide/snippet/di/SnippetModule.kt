package com.wuxianggujun.tinaide.snippet.di

import com.wuxianggujun.tinaide.snippet.repository.SnippetRepository
import com.wuxianggujun.tinaide.snippet.viewmodel.SnippetMarketViewModel
import com.wuxianggujun.tinaide.snippet.viewmodel.MySnippetsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val snippetModule = module {
    single { SnippetRepository(get()) }
    viewModel { SnippetMarketViewModel(get()) }
    viewModel { MySnippetsViewModel() }
}

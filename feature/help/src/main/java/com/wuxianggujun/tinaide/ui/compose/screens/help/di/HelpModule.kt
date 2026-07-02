package com.wuxianggujun.tinaide.ui.compose.screens.help.di

import com.wuxianggujun.tinaide.ui.compose.screens.help.HelpViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val helpModule = module {
    viewModel { HelpViewModel(androidApplication()) }
}

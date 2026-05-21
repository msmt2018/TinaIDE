package com.wuxianggujun.tinaide.output.di

import com.wuxianggujun.tinaide.output.IOutputManager
import com.wuxianggujun.tinaide.output.OutputManager
import org.koin.dsl.module

val outputModule = module {
    single<IOutputManager> { OutputManager(get()) }
}

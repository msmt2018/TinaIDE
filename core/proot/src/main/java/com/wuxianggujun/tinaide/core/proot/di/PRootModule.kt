package com.wuxianggujun.tinaide.core.proot.di

import com.wuxianggujun.tinaide.core.proot.InstallLogManager
import org.koin.dsl.module

val prootModule = module {
    single { InstallLogManager(get()) }
}

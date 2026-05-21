package com.wuxianggujun.tinaide.core.config.di

import com.wuxianggujun.tinaide.core.config.ConfigManager
import com.wuxianggujun.tinaide.core.config.IConfigManager
import org.koin.dsl.module

val configModule = module {
    single<IConfigManager> { ConfigManager(get()).also { it.onCreate() } }
}

package com.wuxianggujun.tinaide.ai.di

import com.wuxianggujun.tinaide.ai.channel.AiChannelApiKeyStore
import com.wuxianggujun.tinaide.ai.channel.AiChannelRepository
import com.wuxianggujun.tinaide.ai.config.AiPreferences
import com.wuxianggujun.tinaide.ai.repository.ConversationRepository
import com.wuxianggujun.tinaide.ai.settings.AiSettingsBridgeImpl
import com.wuxianggujun.tinaide.ai.tools.ToolInitializer
import com.wuxianggujun.tinaide.ai.viewmodel.AiChatViewModel
import com.wuxianggujun.tinaide.core.config.ai.AiChannelProvider
import com.wuxianggujun.tinaide.core.config.ai.AiConfigProvider
import com.wuxianggujun.tinaide.core.config.ai.AiSettingsBridge
import com.wuxianggujun.tinaide.database.user.UserContentDatabase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aiModule = module {
    single { AiPreferences(context = get()) }
    single<AiConfigProvider> { get<AiPreferences>() }

    single { AiChannelApiKeyStore(get()) }
    single { get<UserContentDatabase>().aiChannelDao() }
    single { AiChannelRepository(dao = get(), apiKeyStore = get()) }
    single<AiChannelProvider> { get<AiChannelRepository>() }

    single<AiSettingsBridge> {
        AiSettingsBridgeImpl(
            context = get(),
            aiPreferences = get(),
            channelRepository = get(),
        )
    }
    single { ConversationRepository(get(), get()) }

    // AiChatViewModel 使用 V3 注册模式，不再需要在创建时传入 toolExecutionContext
    // 使用方需要在获取 ViewModel 后调用 initializeProjectContext() 等方法注册回调
    viewModel {
        AiChatViewModel(
            context = get(),
            aiPreferences = get(),
            channelRepository = get(),
            conversationRepository = get()
        )
    }

    // 初始化工具系统（立即初始化）
    single(createdAtStart = true) {
        val aiPreferences = get<AiPreferences>()
        ToolInitializer.apply {
            registerBuiltInTools()
            // 加载保存的工具启用状态
            val savedStates = aiPreferences.loadToolEnabledStates()
            if (savedStates.isNotEmpty()) {
                com.wuxianggujun.tinaide.ai.tools.ToolRegistry.setToolEnabledStates(savedStates)
            }
        }
    }
}

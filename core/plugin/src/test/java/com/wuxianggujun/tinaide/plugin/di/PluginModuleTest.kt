package com.wuxianggujun.tinaide.plugin.di

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.file.Project
import com.wuxianggujun.tinaide.plugin.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class PluginModuleTest {

    @After
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun pluginModule_shouldReusePluginManagerSingleton() {
        val context = RuntimeEnvironment.getApplication() as Application
        val singleton = PluginManager.getInstance(context)

        startKoin {
            modules(
                module {
                    single<Context> { context }
                    single<IProjectContext> {
                        object : IProjectContext {
                            override fun getCurrentProject(): Project? = null

                            override val currentProjectFlow = MutableStateFlow<Project?>(null)
                        }
                    }
                },
                pluginModule
            )
        }

        val injected = GlobalContext.get().get<PluginManager>()

        assertThat(injected).isSameInstanceAs(singleton)
    }
}

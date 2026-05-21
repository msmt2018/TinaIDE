package com.wuxianggujun.tinaide.plugin

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class PluginLogManagerTest {

    private lateinit var logManager: PluginLogManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Application
        logManager = PluginLogManager.getInstance(context)
        logManager.clearAll()
    }

    @After
    fun tearDown() {
        logManager.clearAll()
    }

    @Test
    fun info_withHostSource_shouldRecordHostEntry() {
        logManager.info(PluginHostLogSources.PluginManager, "singleton reused")

        val entry = logManager.getAllLogs().single()

        assertThat(entry.pluginId).isEqualTo(PluginHostLogSources.PluginManager.id)
        assertThat(entry.pluginName).isEqualTo(PluginHostLogSources.PluginManager.name)
        assertThat(entry.level).isEqualTo(PluginLogLevel.INFO)
        assertThat(entry.message).isEqualTo("singleton reused")
    }
}

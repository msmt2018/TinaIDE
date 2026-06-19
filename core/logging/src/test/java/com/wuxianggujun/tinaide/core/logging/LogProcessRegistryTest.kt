package com.wuxianggujun.tinaide.core.logging

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogProcessRegistryTest {
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LogProcessRegistry.clear(context)
    }

    @After
    fun tearDown() {
        LogProcessRegistry.clear(context)
    }

    @Test
    fun `recordProcess stores recent process record`() {
        LogProcessRegistry.recordProcess(
            context = context,
            pid = 1234,
            processName = "com.wuxianggujun.tinaide:sdl",
            nowMillis = 10_000L
        )

        val records = LogProcessRegistry.loadRecentRecords(
            context = context,
            nowMillis = 11_000L,
            maxAgeMillis = 5_000L
        )

        assertThat(records).containsExactly(
            LogProcessRecord(
                pid = 1234,
                processName = "com.wuxianggujun.tinaide:sdl",
                recordedAtMillis = 10_000L
            )
        )
    }

    @Test
    fun `loadRecentRecords drops stale and malformed records`() {
        LogProcessRegistry.recordProcess(
            context = context,
            pid = 1234,
            processName = "com.wuxianggujun.tinaide:sdl",
            nowMillis = 1_000L
        )
        LogProcessRegistry.recordProcess(
            context = context,
            pid = 5678,
            processName = "com.wuxianggujun.tinaide",
            nowMillis = 10_000L
        )

        val registryDir = File(context.filesDir, "log_processes").apply { mkdirs() }
        File(registryDir, "process_bad.tsv").writeText("not-a-record", Charsets.UTF_8)

        val records = LogProcessRegistry.loadRecentRecords(
            context = context,
            nowMillis = 10_000L,
            maxAgeMillis = 5_000L
        )

        assertThat(records.map { it.pid }).containsExactly(5678)
        assertThat(File(registryDir, "process_bad.tsv").exists()).isFalse()
    }
}

package com.wuxianggujun.tinaide.core.crash

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
@OptIn(ExperimentalCoroutinesApi::class)
class CrashLogAutoUploaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun uploadFromCrashScreen_shouldForceScheduleBeforeImmediateUpload() = runTest {
        setAutoUploadEnabled(true)
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val closeable = CrashLogAutoUploader.replaceActionsForTesting(
            testActions(events, immediateUploadResult = false, dispatcher)
        )

        try {
            CrashLogAutoUploader.uploadFromCrashScreen(context, this)
            advanceUntilIdle()
        } finally {
            closeable.close()
        }

        assertThat(events).containsExactly("schedule", "upload").inOrder()
    }

    @Test
    fun uploadFromCrashScreen_shouldKeepFallbackJobWhenImmediateUploadNeedsRetry() = runTest {
        setAutoUploadEnabled(true)
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val closeable = CrashLogAutoUploader.replaceActionsForTesting(
            testActions(events, immediateUploadResult = true, dispatcher)
        )

        try {
            CrashLogAutoUploader.uploadFromCrashScreen(context, this)
            advanceUntilIdle()
        } finally {
            closeable.close()
        }

        assertThat(events).containsExactly("schedule", "upload", "scheduleIfNeeded").inOrder()
    }

    @Test
    fun uploadFromCrashScreen_shouldNotifyCompletionAfterImmediateUpload() = runTest {
        setAutoUploadEnabled(true)
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val closeable = CrashLogAutoUploader.replaceActionsForTesting(
            testActions(events, immediateUploadResult = false, dispatcher)
        )

        try {
            CrashLogAutoUploader.uploadFromCrashScreen(context, this) {
                events += "complete"
            }
            advanceUntilIdle()
        } finally {
            closeable.close()
        }

        assertThat(events).containsExactly("schedule", "upload", "complete").inOrder()
    }

    @Test
    fun uploadOnStartup_shouldScheduleIfNeededBeforeImmediateUpload() = runTest {
        setAutoUploadEnabled(true)
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val closeable = CrashLogAutoUploader.replaceActionsForTesting(
            testActions(events, immediateUploadResult = false, dispatcher)
        )

        try {
            CrashLogAutoUploader.uploadOnStartup(context, this)
            advanceUntilIdle()
        } finally {
            closeable.close()
        }

        assertThat(events).containsExactly("scheduleIfNeeded", "upload").inOrder()
    }

    @Test
    fun autoUploadDisabled_shouldNotScheduleOrUpload() = runTest {
        setAutoUploadEnabled(false)
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val closeable = CrashLogAutoUploader.replaceActionsForTesting(
            testActions(events, immediateUploadResult = false, dispatcher)
        )

        try {
            CrashLogAutoUploader.uploadFromCrashScreen(context, this)
            advanceUntilIdle()
        } finally {
            closeable.close()
        }

        assertThat(events).isEmpty()
    }

    private fun testActions(
        events: MutableList<String>,
        immediateUploadResult: Boolean,
        uploadDispatcher: CoroutineDispatcher,
    ): CrashLogAutoUploadActions = CrashLogAutoUploadActions(
        scheduleIfNeeded = { events += "scheduleIfNeeded" },
        schedule = { events += "schedule" },
        uploadPending = {
            events += "upload"
            immediateUploadResult
        },
        uploadDispatcher = uploadDispatcher,
    )

    private fun setAutoUploadEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("logs.crash.auto_upload", enabled)
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "tinaide_config"
    }
}

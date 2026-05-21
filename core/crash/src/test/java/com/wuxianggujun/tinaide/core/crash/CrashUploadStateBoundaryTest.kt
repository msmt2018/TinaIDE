package com.wuxianggujun.tinaide.core.crash

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
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
class CrashUploadStateBoundaryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        context.getSharedPreferences("tinaide_config", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun blankUploadNames_shouldNotCreateTerminalUploadRecords() {
        assertThat(CrashUploadState.markUploadAttemptStarted(context, " ", 100L)).isFalse()
        assertThat(CrashUploadState.markUploadFailed(context, "", 100L, "failed")).isFalse()
        assertThat(CrashUploadState.markUploadSkipped(context, "\t", "skipped")).isFalse()
        assertThat(CrashUploadState.markUploaded(context, "\n", 100L)).isFalse()

        val snapshot = CrashUploadState.getLastUploadSnapshot(context)
        assertThat(snapshot.status).isEqualTo(CrashUploadState.Status.NONE)
        assertThat(snapshot.attemptCount).isEqualTo(0)
        assertThat(CrashUploadState.isUploaded(context, "")).isFalse()
        assertThat(CrashUploadState.isUploadSkipped(context, " ")).isFalse()
    }

    @Test
    fun queuedBlankName_shouldNormalizeSnapshotButRemainUnuploaded() {
        assertThat(CrashUploadState.markUploadQueued(context, "  ", "queued")).isTrue()

        val snapshot = CrashUploadState.getLastUploadSnapshot(context)
        assertThat(snapshot.status).isEqualTo(CrashUploadState.Status.QUEUED)
        assertThat(snapshot.fileName).isEqualTo("<unknown>")
        assertThat(snapshot.reason).isEqualTo("queued")
        assertThat(snapshot.attemptCount).isEqualTo(0)
        assertThat(CrashUploadState.isUploaded(context, "<unknown>")).isFalse()
    }

    @Test
    fun longReason_shouldBeTruncatedInSnapshot() {
        val reason = "x".repeat(260)

        CrashUploadState.markUploadFailed(context, "tombstone.log", 10L, reason)

        val snapshot = CrashUploadState.getLastUploadSnapshot(context)
        assertThat(snapshot.status).isEqualTo(CrashUploadState.Status.FAILED)
        assertThat(snapshot.reason).hasLength(240)
        assertThat(snapshot.reason).isEqualTo("x".repeat(240))
    }
}

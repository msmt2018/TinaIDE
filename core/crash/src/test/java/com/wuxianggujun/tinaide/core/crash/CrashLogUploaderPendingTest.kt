package com.wuxianggujun.tinaide.core.crash

import android.app.Application
import android.content.Context
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
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class CrashLogUploaderPendingTest {
    private lateinit var context: Context
    private lateinit var tombstoneDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("logs.crash.auto_upload", true)
            .commit()

        tombstoneDir = NativeCrashHandler.getTombstoneDir(context)
        tombstoneDir.deleteRecursively()
        tombstoneDir.mkdirs()
    }

    @After
    fun tearDown() {
        tombstoneDir.deleteRecursively()
    }

    @Test
    fun hasPendingUploadableTombstone_shouldFindNewHostCrash() {
        writeTombstone("tombstone-host.log", ">>> ${context.packageName} <<<")

        assertThat(CrashLogUploader.hasPendingUploadableTombstone(context)).isTrue()
    }

    @Test
    fun hasPendingUploadableTombstone_shouldIgnoreAlreadyUploadedCrash() {
        val tombstone = writeTombstone("tombstone-uploaded.log", ">>> ${context.packageName} <<<")

        CrashUploadState.markUploaded(context, tombstone.name, tombstone.lastModified())

        assertThat(CrashLogUploader.hasPendingUploadableTombstone(context)).isFalse()
    }

    @Test
    fun hasPendingUploadableTombstone_shouldSkipUserRuntimeCrash() {
        val tombstone = writeTombstone("tombstone-sdl.log", ">>> ${context.packageName}:sdl <<<")

        assertThat(CrashLogUploader.hasPendingUploadableTombstone(context)).isFalse()
        assertThat(CrashUploadState.isUploadSkipped(context, tombstone.name)).isTrue()
    }

    private fun writeTombstone(fileName: String, processLine: String): File {
        val file = File(tombstoneDir, fileName)
        file.writeText(
            """
            Tombstone maker: 'xCrash 3.1.0'
            pid: 123, tid: 456, name: DefaultDispatch  $processLine
            signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
            backtrace:
                #00 pc 0000000000021c90  /data/app/lib/arm64/liblua54.so
            """.trimIndent(),
            Charsets.UTF_8,
        )
        file.setLastModified(1_700_000_000_000L)
        return file
    }

    companion object {
        private const val PREFS_NAME = "tinaide_config"
    }
}

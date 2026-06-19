package com.wuxianggujun.tinaide.ui.runtime

import androidx.annotation.Keep
import timber.log.Timber

@Keep
object NativeStdStreamRedirect {
    private const val TAG = "NativeStdStreamRedirect"
    private const val LIBRARY_NAME = "tina_log_redirect"
    private const val OUTPUT_LOG_TAG = "TINA_USER_OUTPUT"

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var started = false

    fun start(): Boolean {
        if (started) return true
        if (!loadLibrary()) return false

        return runCatching {
            nativeStart(OUTPUT_LOG_TAG)
        }.onSuccess { success ->
            started = success
            if (success) {
                Timber.tag(TAG).d("Native stdout/stderr redirect started")
            } else {
                Timber.tag(TAG).w("Native stdout/stderr redirect start returned false")
            }
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Failed to start native stdout/stderr redirect")
        }.getOrDefault(false)
    }

    fun stop() {
        if (!started || !libraryLoaded) return

        runCatching {
            nativeStop()
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Failed to stop native stdout/stderr redirect")
        }
        started = false
    }

    private fun loadLibrary(): Boolean {
        if (libraryLoaded) return true

        return runCatching {
            System.loadLibrary(LIBRARY_NAME)
        }.onSuccess {
            libraryLoaded = true
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Failed to load %s", LIBRARY_NAME)
        }.isSuccess
    }

    private external fun nativeStart(tag: String): Boolean

    private external fun nativeStop()
}

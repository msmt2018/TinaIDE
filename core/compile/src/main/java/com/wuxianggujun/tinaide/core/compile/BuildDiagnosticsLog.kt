package com.wuxianggujun.tinaide.core.compile

import com.wuxianggujun.tinaide.core.config.Prefs
import timber.log.Timber

/**
 * Developer-only build diagnostics.
 *
 * Kept behind Prefs.devDiagnosticsEnabled + Prefs.devBuildDiagnosticsLogEnabled so normal
 * compile/run paths do not pay for verbose string construction.
 */
object BuildDiagnosticsLog {
    private const val TAG = "BuildDiagnostics"

    val enabled: Boolean
        get() = runCatching {
            Prefs.devDiagnosticsEnabled && Prefs.devBuildDiagnosticsLogEnabled
        }.getOrDefault(false)

    fun d(message: () -> String) {
        if (!enabled) return
        Timber.tag(TAG).d(message())
    }

    fun i(message: () -> String) {
        if (!enabled) return
        Timber.tag(TAG).i(message())
    }

    fun w(message: () -> String) {
        if (!enabled) return
        Timber.tag(TAG).w(message())
    }

    fun w(throwable: Throwable, message: () -> String) {
        if (!enabled) return
        Timber.tag(TAG).w(throwable, message())
    }
}

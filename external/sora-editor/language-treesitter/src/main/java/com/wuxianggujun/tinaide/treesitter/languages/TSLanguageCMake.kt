package com.wuxianggujun.tinaide.treesitter.languages

import com.wuxianggujun.tinaide.treesitter.TSLanguage
import java.util.concurrent.atomic.AtomicBoolean

object TSLanguageCMake {
    private const val NAME = "cmake"
    private val loaded = AtomicBoolean(false)
    @Volatile private var instance: TSLanguage? = null

    init { ensureLoaded() }

    private fun ensureLoaded() {
        if (loaded.compareAndSet(false, true)) {
            try { System.loadLibrary("native_compiler") }
            catch (_: UnsatisfiedLinkError) { loaded.set(false) }
        }
    }

    @JvmStatic
    fun getInstance(): TSLanguage = instance ?: synchronized(this) {
        instance ?: TSLanguage.create(NAME, nativeLanguage()).also { instance = it }
    }

    @JvmStatic private external fun nativeLanguage(): Long
}

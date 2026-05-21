package com.wuxianggujun.tinaide.ui.gui

import timber.log.Timber

/**
 * GUI 运行时桥接：负责加载用户构建产物（.so）并调用统一渲染入口。
 */
object GuiRuntimeBridge {
    private const val TAG = "GuiRuntimeBridge"

    @Volatile
    private var nativeLoaded = false

    private fun ensureNativeLoaded(): String? {
        if (nativeLoaded) return null
        return runCatching {
            System.loadLibrary("tina_gui_runtime")
            nativeLoaded = true
            null
        }.getOrElse { throwable ->
            Timber.tag(TAG).e(throwable, "Failed to load tina_gui_runtime")
            throwable.message ?: "Failed to load tina_gui_runtime"
        }
    }

    fun loadLibrary(path: String): String? {
        val loadError = ensureNativeLoaded()
        if (loadError != null) return loadError
        return nativeLoadLibrary(path)
    }

    fun unloadLibrary() {
        if (!nativeLoaded) return
        nativeUnloadLibrary()
    }

    fun renderArgb32(width: Int, height: Int, pixels: IntArray): Boolean {
        if (!nativeLoaded) return false
        return nativeRenderArgb32(width, height, pixels)
    }

    fun lastError(): String? {
        if (!nativeLoaded) return null
        return nativeGetLastError()
    }

    /**
     * 向用户程序发送触摸事件。
     *
     * @param action    0 = DOWN, 1 = UP, 2 = MOVE
     * @param x         触摸点 x 坐标（相对于渲染区域，像素）
     * @param y         触摸点 y 坐标
     * @param pointerId 触摸指针 ID（多点触控）
     */
    fun sendTouchEvent(action: Int, x: Float, y: Float, pointerId: Int = 0) {
        if (!nativeLoaded) return
        nativeSendTouchEvent(action, x, y, pointerId)
    }

    /**
     * 向用户程序发送按键事件。
     *
     * @param keycode Android KeyEvent.KEYCODE_* 值
     * @param action  0 = DOWN, 1 = UP
     */
    fun sendKeyEvent(keycode: Int, action: Int) {
        if (!nativeLoaded) return
        nativeSendKeyEvent(keycode, action)
    }

    private external fun nativeLoadLibrary(path: String): String?
    private external fun nativeUnloadLibrary()
    private external fun nativeRenderArgb32(width: Int, height: Int, pixels: IntArray): Boolean
    private external fun nativeSendTouchEvent(action: Int, x: Float, y: Float, pointerId: Int)
    private external fun nativeSendKeyEvent(keycode: Int, action: Int)
    private external fun nativeGetLastError(): String?
}

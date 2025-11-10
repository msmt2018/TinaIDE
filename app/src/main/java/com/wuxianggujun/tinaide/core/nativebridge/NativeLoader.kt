package com.wuxianggujun.tinaide.core.nativebridge

import android.util.Log

object NativeLoader {
    @Volatile
    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        // libc++_shared 先行（若已由依赖自动加载，重复无害）
        try {
            System.loadLibrary("c++_shared")
        } catch (_: Throwable) {
            // 忽略，可能已由其他库加载
        }
        // 先尝试加载 LLVM 主库（libLLVM-17.so），再加载 clang-cpp，提升兼容性
        try {
            System.loadLibrary("LLVM-17")
        } catch (_: Throwable) {
            // 忽略，若由 clang-cpp 的 DT_NEEDED 自动加载也可
        }
        try {
            System.loadLibrary("clang-cpp")
            Log.i("NativeLoader", "Loaded clang-cpp successfully")
        } catch (t: Throwable) {
            Log.w("NativeLoader", "Failed to load clang-cpp: ${t.message}")
        }
        try {
            System.loadLibrary("native_compiler")
            loaded = true
            Log.i("NativeLoader", "Loaded native_compiler successfully")
        } catch (t: Throwable) {
            Log.w("NativeLoader", "Failed to load native_compiler: ${t.message}")
        }
    }

    fun isLoaded(): Boolean = loaded
}


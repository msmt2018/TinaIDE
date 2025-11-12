package com.wuxianggujun.tinaide.core.nativebridge

import android.util.Log
import com.wuxianggujun.tinaide.TinaApplication

object NativeLoader {
    @Volatile
    private var loaded = false

    private fun preloadLibcxxFromSysrootIfAvailable() {
        try {
            val ctx = TinaApplication.instance
            val base = java.io.File(ctx.filesDir, "sysroot")
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
            val triple = when {
                abi.contains("arm64", true) -> "aarch64-linux-android"
                abi.contains("x86_64", true) -> "x86_64-linux-android"
                else -> "aarch64-linux-android"
            }
            val candidates = listOf(
                java.io.File(base, "usr/lib/$triple/libc++_shared.so"),
                java.io.File(base, "usr/lib/$triple/26/libc++_shared.so")
            )
            for (f in candidates) {
                if (f.exists()) {
                    try {
                        System.load(f.absolutePath)
                        Log.i("NativeLoader", "Preloaded libc++_shared from ${f.absolutePath}")
                        return
                    } catch (_: Throwable) {
                        // try next candidate
                    }
                }
            }
        } catch (_: Throwable) {
            // ignore
        }
        // 不再回退到 jniLibs：统一从 sysroot 加载，缺失则让上层安装流程报错
    }

    fun loadIfNeeded() {
        if (loaded) return
        // 先预加载 libc++_shared（仅 sysroot），以满足 LLVM/Clang 的依赖
        preloadLibcxxFromSysrootIfAvailable()
        // 加载 LLVM 主库（仅 sysroot runtime），再加载 clang-cpp
        var llvmLoaded = false
        try {
            val ctx = TinaApplication.instance
            val base = java.io.File(ctx.filesDir, "sysroot")
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
            val triple = when {
                abi.contains("arm64", true) -> "aarch64-linux-android"
                abi.contains("x86_64", true) -> "x86_64-linux-android"
                else -> "aarch64-linux-android"
            }
            val llvmPath = java.io.File(base, "usr/lib/$triple/runtime/libLLVM-17.so")
            if (llvmPath.exists()) { System.load(llvmPath.absolutePath); llvmLoaded = true }
        } catch (_: Throwable) { }
        try {
            val ctx = TinaApplication.instance
            val base = java.io.File(ctx.filesDir, "sysroot")
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
            val triple = when {
                abi.contains("arm64", true) -> "aarch64-linux-android"
                abi.contains("x86_64", true) -> "x86_64-linux-android"
                else -> "aarch64-linux-android"
            }
            val clangPath = java.io.File(base, "usr/lib/$triple/runtime/libclang-cpp.so")
            if (clangPath.exists()) {
                System.load(clangPath.absolutePath)
                Log.i("NativeLoader", "Loaded clang-cpp from sysroot runtime")
            } else {
                throw UnsatisfiedLinkError("clang-cpp not found in sysroot runtime: ${clangPath.absolutePath}")
            }
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


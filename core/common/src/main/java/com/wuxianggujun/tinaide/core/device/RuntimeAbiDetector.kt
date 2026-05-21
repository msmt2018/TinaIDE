package com.wuxianggujun.tinaide.core.device

import android.content.Context
import android.os.Build

/**
 * 获取“当前进程正在使用的 ABI”。
 *
 * 背景：在部分 x86_64 模拟器/设备上，系统可能同时声明支持 x86_64 与 arm64-v8a（native bridge/翻译层）。
 * 此时仅看 Build.SUPPORTED_ABIS 的第一个值会导致误判：
 * - 应用实际运行的是 arm64 变体（nativeLibraryDir/lib/arm64），但 SUPPORTED_ABIS[0] 仍为 x86_64
 * - 从而选择/校验 rootfs 时出现“架构不匹配”假阳性
 */
object RuntimeAbiDetector {

    /**
     * 返回 Android ABI：目前仅用到 `arm64-v8a` 与 `x86_64`。
     */
    fun currentAndroidAbi(context: Context): String {
        val libDir = context.applicationInfo?.nativeLibraryDir.orEmpty()
        parseAbiFromNativeLibraryDir(libDir)?.let { return it }

        val supported = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return normalizeAndroidAbi(supported)
    }

    /**
     * 返回 Ubuntu apt/ports 使用的 arch（`arm64` / `amd64`）。
     */
    fun currentUbuntuArch(context: Context): String {
        return when (currentAndroidAbi(context)) {
            "x86_64" -> "amd64"
            else -> "arm64"
        }
    }

    private fun parseAbiFromNativeLibraryDir(nativeLibraryDir: String): String? {
        val lower = nativeLibraryDir.lowercase()
        return when {
            lower.contains("x86_64") -> "x86_64"
            lower.contains("arm64") || lower.contains("aarch64") -> "arm64-v8a"
            else -> null
        }
    }

    private fun normalizeAndroidAbi(abi: String): String {
        val lower = abi.lowercase()
        return when {
            lower.contains("x86_64") -> "x86_64"
            lower.contains("arm64") || lower.contains("aarch64") -> "arm64-v8a"
            else -> "arm64-v8a"
        }
    }
}


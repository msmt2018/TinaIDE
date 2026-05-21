package com.wuxianggujun.tinaide.core.util

import java.io.File

/**
 * Android 系统动态链接器路径解析。
 *
 * 说明：
 * - 新版 Android 将 linker 放在 runtime APEX 下：/apex/com.android.runtime/bin/linker{,64}
 * - 同时通常会在 /system/bin 下提供兼容入口：/system/bin/linker{,64}
 *
 * 这里统一做“按存在性”探测，避免不同设备/ROM 路径差异导致启动失败。
 */
object AndroidSystemLinker {

    fun resolve64BitPreferred(): String {
        val candidates = listOf(
            "/system/bin/linker64",
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker",
            "/apex/com.android.runtime/bin/linker",
        )
        return candidates.firstOrNull { File(it).isFile } ?: "/system/bin/linker64"
    }
}

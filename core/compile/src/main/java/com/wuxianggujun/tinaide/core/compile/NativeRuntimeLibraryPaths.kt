package com.wuxianggujun.tinaide.core.compile

import android.content.Context
import com.wuxianggujun.tinaide.core.ndk.AndroidSysrootManager
import java.io.File

internal object NativeRuntimeLibraryPaths {
    const val CXX_SHARED_LIBRARY_NAME: String = "libc++_shared.so"

    fun activeSysrootRuntimeDirs(context: Context): List<File> {
        val arch = AndroidSysrootManager.Companion.Arch.current()
        return sysrootRuntimeDirs(
            context = context,
            sysrootProfileId = null,
            arch = arch,
        )
    }

    fun sysrootRuntimeDirs(
        context: Context,
        sysrootProfileId: String?,
        arch: AndroidSysrootManager.Companion.Arch = AndroidSysrootManager.Companion.Arch.current(),
    ): List<File> {
        val manager = AndroidSysrootManager(context.applicationContext)
        val normalizedProfileId = sysrootProfileId?.trim()?.takeIf { it.isNotEmpty() }
        val sysrootDir = manager.getSysrootDir(arch, normalizedProfileId)
        val runtimeDir = File(sysrootDir, "usr/lib/${arch.triple}")
        val cxxRuntime = File(runtimeDir, CXX_SHARED_LIBRARY_NAME)
        return if (cxxRuntime.isFile) listOf(runtimeDir) else emptyList()
    }
}

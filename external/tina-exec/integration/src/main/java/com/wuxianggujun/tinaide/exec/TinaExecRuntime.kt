package com.wuxianggujun.tinaide.exec

import android.content.Context
import java.io.File

enum class TinaExecPreloadMode {
    DIRECT,
    LINKER,
}

enum class TinaExecSystemLinkerMode(val envValue: String) {
    DISABLE("disable"),
    ENABLE("enable"),
    FORCE("force"),
}

/**
 * TinaIDE 内部封装的 exec preload 运行时。
 *
 * 这里只负责：
 * 1. 解析打包进 APK 的 preload so 路径
 * 2. 注入运行时环境变量
 * 3. 为现有执行链路提供按需接入点
 */
object TinaExecRuntime {

    private const val DIRECT_LIBRARY_FILE_NAME = "libtina_exec_direct_ld_preload.so"
    private const val LINKER_LIBRARY_FILE_NAME = "libtina_exec_linker_ld_preload.so"
    const val ENV_APP_DATA_DIR = "TINA_APP__DATA_DIR"
    const val ENV_APP_LEGACY_DATA_DIR = "TINA_APP__LEGACY_DATA_DIR"
    const val ENV_ROOTFS = "TINA_ROOTFS"
    const val ENV_PREFIX = "TINA_PREFIX"
    const val ENV_LOG_LEVEL = "TINA_EXEC__LOG_LEVEL"
    const val ENV_SYSTEM_LINKER_EXEC_MODE = "TINA_EXEC__SYSTEM_LINKER_EXEC__MODE"
    const val ENV_PROC_SELF_EXE = "TINA_EXEC__PROC_SELF_EXE"

    fun resolveLibraryPath(
        context: Context,
        mode: TinaExecPreloadMode,
    ): String? {
        val fileName = when (mode) {
            TinaExecPreloadMode.DIRECT -> DIRECT_LIBRARY_FILE_NAME
            TinaExecPreloadMode.LINKER -> LINKER_LIBRARY_FILE_NAME
        }
        return resolveBinaryPath(context, fileName)
    }

    fun applyLdPreload(
        environment: MutableMap<String, String>,
        context: Context,
        mode: TinaExecPreloadMode,
        systemLinkerMode: TinaExecSystemLinkerMode? = null,
        logLevel: Int? = null,
    ): Boolean {
        val libraryPath = resolveLibraryPath(context, mode) ?: return false

        populateBaseEnvironment(environment, context)
        environment["LD_PRELOAD"] = mergeLdPreload(libraryPath, environment["LD_PRELOAD"])

        if (systemLinkerMode != null) {
            environment[ENV_SYSTEM_LINKER_EXEC_MODE] = systemLinkerMode.envValue
        }
        if (logLevel != null) {
            environment[ENV_LOG_LEVEL] = logLevel.toString()
        }
        return true
    }

    fun recommendedMode(preferLinker64: Boolean): TinaExecPreloadMode {
        return if (preferLinker64) {
            TinaExecPreloadMode.LINKER
        } else {
            TinaExecPreloadMode.DIRECT
        }
    }

    private fun populateBaseEnvironment(
        environment: MutableMap<String, String>,
        context: Context,
    ) {
        val dataDir = context.dataDir.absolutePath
        val legacyDataDir = "/data/data/${context.packageName}"
        val rootfsDir = context.filesDir.absolutePath
        val prefixDir = File(context.filesDir, "usr").absolutePath

        environment.putIfAbsent(ENV_APP_DATA_DIR, dataDir)
        environment.putIfAbsent(ENV_APP_LEGACY_DATA_DIR, legacyDataDir)
        environment.putIfAbsent(ENV_ROOTFS, rootfsDir)
        environment.putIfAbsent(ENV_PREFIX, prefixDir)
    }

    private fun resolveBinaryPath(
        context: Context,
        fileName: String,
    ): String? {
        val candidate = File(context.applicationInfo.nativeLibraryDir, fileName)
        return candidate.takeIf { it.isFile }?.absolutePath
    }

    private fun mergeLdPreload(
        libraryPath: String,
        existingValue: String?,
    ): String {
        val current = existingValue?.trim().orEmpty()
        if (current.isEmpty()) return libraryPath

        val entries = current.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (entries.contains(libraryPath)) {
            current
        } else {
            "$libraryPath $current"
        }
    }
}

package com.wuxianggujun.tinaide.core.nativebridge

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.LinkedHashSet
import java.util.Locale
import java.util.zip.ZipInputStream

object SysrootInstaller {
    private const val TAG = "SysrootInstaller"

    /**
     * Ensure <files>/sysroot is present by copying from assets/sysroot on first run.
     * Idempotent and minimal (no versioning yet – YAGNI).
     */
    fun ensureInstalled(context: Context): File {
        val dst = File(context.filesDir, "sysroot")
        val cSentinel = File(dst, "usr/include/stdio.h")
        val cppSentinel = File(dst, "usr/include/c++/v1/__ios/fpos.h")
        val clangResSentinel = File(dst, "lib/clang/17/include/stdarg.h")
        val needInstall = !(cSentinel.exists() && cppSentinel.exists() && clangResSentinel.exists())
        return if (needInstall) forceReinstall(context) else dst
    }

    /**
     * 强制重新安装 sysroot（删除旧的并重新解压）
     * 用于更新 APK 后刷新 sysroot 内容
     */
    fun forceReinstall(context: Context): File {
        val dst = File(context.filesDir, "sysroot")
        try {
            dst.deleteRecursively()
        } catch (_: Throwable) {
        }
        val archive = try {
            openSysrootArchive(context)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to find sysroot archive in assets: ${t.message}")
            throw IllegalStateException("sysroot archive missing or invalid", t)
        }
        Log.i(
            TAG,
            "Installing sysroot using assets/${archive.assetName} (abis=${Build.SUPPORTED_ABIS?.joinToString()})"
        )
        try {
            archive.stream.use { input ->
                extractZip(input, dst)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to unpack assets/${archive.assetName}: ${t.message}")
            throw IllegalStateException("sysroot archive corrupted (${archive.assetName})", t)
        }
        try {
            fixExecPermissions(dst)
        } catch (_: Throwable) {
        }
        Log.i(TAG, "sysroot installed/refreshed at ${dst.absolutePath}")
        return dst
    }

    private fun fixExecPermissions(root: File) {
        val binDir = File(root, "usr/bin")
        if (binDir.isDirectory) {
            binDir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    try {
                        f.setExecutable(true, false)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    private fun extractZip(zipStream: InputStream, dstDir: File) {
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = sanitizeZipEntry(entry.name)
                if (name.isNotEmpty()) {
                    val outPath = File(dstDir, name)
                    if (entry.isDirectory) {
                        outPath.mkdirs()
                    } else {
                        outPath.parentFile?.mkdirs()
                        FileOutputStream(outPath).use { out ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zis.read(buf)
                                if (read <= 0) break
                                out.write(buf, 0, read)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun sanitizeZipEntry(name: String): String {
        if (name.isEmpty()) return ""
        var n = name.replace('\\', '/')
        if (n.startsWith("/")) n = n.substring(1)
        if (n.contains("..")) return ""
        return n
    }

    private fun openSysrootArchive(context: Context): ArchiveHandle {
        val candidates = LinkedHashSet<String>()
        AbiResolver.prioritizedAbis(context.applicationInfo.nativeLibraryDir).forEach { abi ->
            val normalized = abi.trim()
            if (normalized.isNotEmpty()) {
                candidates += "sysroot-$normalized.zip"
                val lower = normalized.lowercase(Locale.US)
                if (lower != normalized) {
                    candidates += "sysroot-$lower.zip"
                }
            }
        }
        candidates += "sysroot.zip"

        var lastError: IOException? = null
        val assets = context.assets
        for (assetName in candidates) {
            try {
                val stream = assets.open(assetName)
                return ArchiveHandle(assetName, stream)
            } catch (ioe: IOException) {
                lastError = ioe
            }
        }
        throw IllegalStateException(
            "No sysroot archive found (looked for ${candidates.joinToString()})",
            lastError
        )
    }

    private data class ArchiveHandle(val assetName: String, val stream: InputStream)
}

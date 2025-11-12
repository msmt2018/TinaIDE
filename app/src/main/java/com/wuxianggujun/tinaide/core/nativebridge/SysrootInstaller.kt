package com.wuxianggujun.tinaide.core.nativebridge

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        if (!needInstall) return dst
        // Remove stale/incomplete sysroot then copy from assets
        try { dst.deleteRecursively() } catch (_: Throwable) {}
        // 仅从压缩包安装（assets/sysroot.zip）；不再支持目录回退，减小 APK 体积
        try {
            context.assets.open("sysroot.zip").use { input ->
                extractZip(input, dst)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to install sysroot from assets/sysroot.zip: ${t.message}")
            throw IllegalStateException("sysroot.zip missing or invalid", t)
        }
        // 修正可执行权限：usr/bin 下的工具（如 cmake/ninja）需要 +x
        try { fixExecPermissions(dst) } catch (_: Throwable) { }
        Log.i(TAG, "sysroot installed/refreshed at ${dst.absolutePath}")
        return dst
    }

    private fun fixExecPermissions(root: File) {
        val binDir = File(root, "usr/bin")
        if (binDir.isDirectory) {
            binDir.listFiles()?.forEach { f ->
                if (f.isFile) { try { f.setExecutable(true, false) } catch (_: Throwable) {} }
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
        // 防止路径穿越，忽略非法条目
        if (name.isEmpty()) return ""
        var n = name.replace('\\', '/')
        if (n.startsWith("/")) n = n.substring(1)
        if (n.contains("..")) return ""
        return n
    }
}

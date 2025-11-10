package com.wuxianggujun.tinaide.core.nativebridge

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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
        copyAssetsDir(context.assets, "sysroot", dst)
        Log.i(TAG, "sysroot installed/refreshed at ${dst.absolutePath}")
        return dst
    }

    private fun copyAssetsDir(assets: AssetManager, assetDir: String, dstDir: File) {
        if (!dstDir.exists()) dstDir.mkdirs()
        val entries = try { assets.list(assetDir) ?: emptyArray() } catch (_: Throwable) { emptyArray() }
        for (name in entries) {
            val assetPath = if (assetDir.isEmpty()) name else "$assetDir/$name"
            val children = try { assets.list(assetPath) ?: emptyArray() } catch (_: Throwable) { emptyArray() }
            val out = File(dstDir, name)
            if (children.isEmpty()) {
                // file
                assets.open(assetPath).use { input ->
                    writeFile(out, input)
                }
            } else {
                // dir
                copyAssetsDir(assets, assetPath, out)
            }
        }
    }

    private fun writeFile(outFile: File, input: InputStream) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { output ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
            }
        }
    }
}

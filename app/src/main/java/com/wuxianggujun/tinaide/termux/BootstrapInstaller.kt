package com.wuxianggujun.tinaide.termux

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BootstrapInstaller {
    data class Result(val installed: Boolean, val message: String?)

    private fun abiToArch(): String? {
        val abis = Build.SUPPORTED_ABIS?.toList() ?: listOf(Build.CPU_ABI)
        return when {
            abis.any { it.equals("arm64-v8a", true) } -> "aarch64"
            abis.any { it.equals("armeabi-v7a", true) } -> "arm"
            abis.any { it.equals("x86_64", true) } -> "x86_64"
            abis.any { it.equals("x86", true) } -> "i686"
            else -> null
        }
    }

    private fun openBootstrapAsset(ctx: Context, arch: String): InputStream? {
        val am = ctx.assets
        val candidates = listOf(
            "bootstrap/$arch/bootstrap-$arch.zip",
            "bootstrap/$arch/$arch.zip",
            "bootstrap/$arch.zip"
        )
        for (p in candidates) {
            try {
                return am.open(p)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun installIfNeeded(ctx: Context): Result {
        val filesDir = ctx.filesDir
        val prefix = File(filesDir, "usr")
        val home = File(filesDir, "home")
        if (File(prefix, "bin/login").exists()) {
            if (!home.exists()) home.mkdirs()
            return Result(true, "already installed")
        }

        val arch = abiToArch() ?: return Result(false, "unsupported ABI")
        val input = openBootstrapAsset(ctx, arch) ?: return Result(false, "missing bootstrap for $arch in assets/bootstrap/")

        // Extract zip into filesDir (bootstrap usually contains usr/...)
        return try {
            unzipToDir(input, filesDir)
            if (!home.exists()) home.mkdirs()
            fixExecBits(prefix)
            Result(true, "installed for $arch")
        } catch (e: Exception) {
            Result(false, e.message)
        }
    }

    private fun unzipToDir(input: InputStream, destDir: File) {
        ZipInputStream(input).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(64 * 1024)
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        var r = zis.read(buffer)
                        while (r > 0) {
                            fos.write(buffer, 0, r)
                            r = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun fixExecBits(prefix: File) {
        val bins = listOf(
            File(prefix, "bin"),
            File(prefix, "libexec"),
            File(prefix, "lib/apt/methods")
        )
        bins.filter { it.exists() }.forEach { dir ->
            dir.walkTopDown().forEach { f ->
                if (f.isFile) f.setExecutable(true, false)
            }
        }
    }

    fun buildEnv(ctx: Context): Array<String> {
        val filesDir = ctx.filesDir
        val prefix = File(filesDir, "usr").absolutePath
        val home = File(filesDir, "home").absolutePath
        val path = "$prefix/bin:/system/bin:/system/xbin"
        val ld = "$prefix/lib"
        return arrayOf(
            "TERM=xterm-256color",
            "HOME=$home",
            "PREFIX=$prefix",
            "PATH=$path",
            "LD_LIBRARY_PATH=$ld",
            "LANG=en_US.UTF-8"
        )
    }

    fun resolveShell(ctx: Context): String {
        val prefix = File(ctx.filesDir, "usr").absolutePath
        val login = File(prefix, "bin/login")
        val bash = File(prefix, "bin/bash")
        return when {
            login.exists() -> login.absolutePath
            bash.exists() -> bash.absolutePath
            else -> "/system/bin/sh"
        }
    }
}


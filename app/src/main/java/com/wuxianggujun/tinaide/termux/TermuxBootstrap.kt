package com.wuxianggujun.tinaide.termux

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object TermuxBootstrap {
    private const val TAG = "TermuxBootstrap"

    data class Result(
        val installed: Boolean,
        val message: String?,
        val arch: String? = null,
        val prefixPath: String? = null
    )

    internal fun abiToArch(): String? {
        val abis = Build.SUPPORTED_ABIS?.toList() ?: listOf(Build.CPU_ABI)
        android.util.Log.d(TAG, "Supported ABIs: ${abis.joinToString()}")
        for (abi in abis) {
            when (abi.lowercase()) {
                "arm64-v8a" -> return "aarch64"
                "armeabi-v7a" -> return "arm"
                "x86_64" -> return "x86_64"
                "x86" -> return "i686"
            }
        }
        return null
    }

    private fun openBootstrapAsset(ctx: Context, arch: String): InputStream? {
        val am = ctx.assets
        val candidates = listOf(
            "bootstrap/$arch/bootstrap-$arch.zip",
            "bootstrap/$arch/$arch.zip",
            "bootstrap/$arch.zip"
        )
        for (p in candidates) {
            try { return am.open(p) } catch (_: Exception) {}
        }
        return null
    }

    fun installIfNeeded(ctx: Context, forceReinstall: Boolean = false): Result {
        val filesDir = ctx.filesDir
        val prefix = File(filesDir, "usr")
        val home = File(filesDir, "home")

        val loginBin = File(prefix, "bin/login")
        val bashBin = File(prefix, "bin/bash")
        val shBin = File(prefix, "bin/sh")
        if (!forceReinstall && (loginBin.exists() || bashBin.exists() || shBin.exists())) {
            if (!home.exists()) home.mkdirs()
            return Result(true, "Termux environment ready", abiToArch(), prefix.absolutePath)
        }

        if (forceReinstall && prefix.exists()) {
            try { prefix.deleteRecursively() } catch (_: Exception) {}
        }

        val arch = abiToArch() ?: return Result(false, "不支持的设备架构", null, null)
        val input = openBootstrapAsset(ctx, arch)
            ?: return Result(false, "缺少 Termux bootstrap 包\n请将 bootstrap-$arch.zip 放到 assets/bootstrap/$arch/ 目录", arch, null)

        return try {
            if (!prefix.exists()) prefix.mkdirs()
            unzipToDir(input, prefix)
            if (!home.exists()) home.mkdirs()
            fixExecBits(prefix)
            patchShebangs(prefix)
            ensureLibrarySymlinks(prefix)
            ensureShFallback(prefix)
            Result(true, "Termux 环境安装成功 ($arch)", arch, prefix.absolutePath)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to install bootstrap", e)
            Result(false, "安装失败: ${e.message}", arch, null)
        }
    }

    fun verifyEnvironment(ctx: Context): Boolean {
        val prefix = File(ctx.filesDir, "usr")
        if (!prefix.exists()) return false
        val required = listOf("bin", "lib", "etc", "var")
        val ok = required.all { File(prefix, it).exists() }
        if (!ok) return false
        return listOf("bin/sh", "bin/bash", "bin/login").any { File(prefix, it).exists() }
    }

    internal fun unzipToDir(input: InputStream, destDir: File) {
        ZipInputStream(input).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(64 * 1024)
            var topLevel: String? = null
            var index = 0
            while (entry != null) {
                index++
                var name = entry.name
                if (index == 1 && name.contains('/')) topLevel = name.substringBefore('/') + "/"
                if (topLevel != null && name.startsWith(topLevel!!)) {
                    name = name.substring(topLevel!!.length)
                    if (name.isEmpty()) { zis.closeEntry(); entry = zis.nextEntry; continue }
                }
                if (name.contains("..")) { zis.closeEntry(); entry = zis.nextEntry; continue }
                val outFile = File(destDir, name)
                if (entry.isDirectory) outFile.mkdirs() else {
                    outFile.parentFile?.mkdirs()
                    try {
                        FileOutputStream(outFile).use { fos ->
                            var r = zis.read(buffer)
                            while (r > 0) { fos.write(buffer, 0, r); r = zis.read(buffer) }
                        }
                    } catch (_: java.io.FileNotFoundException) {}
                }
                zis.closeEntry(); entry = zis.nextEntry
            }
        }
    }

    internal fun fixExecBits(prefix: File) {
        listOf(File(prefix, "bin"), File(prefix, "libexec"), File(prefix, "lib/apt/methods"))
            .filter { it.exists() }
            .forEach { dir -> dir.walkTopDown().forEach { f -> if (f.isFile) f.setExecutable(true, false) } }
    }

    internal fun patchShebangs(prefix: File) {
        val binDir = File(prefix, "bin")
        if (!binDir.exists()) return
        val termuxPrefix = "/data/data/com.termux/files/usr"
        val currentPrefix = prefix.absolutePath
        binDir.listFiles()?.forEach { file ->
            if (!file.isFile || !file.canRead()) return@forEach
            val ins = try { file.inputStream() } catch (_: Exception) { return@forEach }
            ins.buffered().use { buf ->
                buf.mark(256)
                val header = ByteArray(256)
                val n = buf.read(header)
                if (n <= 2) return@use
                val head = String(header, 0, n)
                if (!head.startsWith("#!") || !head.contains(termuxPrefix)) return@use
                buf.reset()
                val content = buf.readBytes().toString(Charsets.UTF_8)
                val firstEnd = content.indexOf("\n").let { if (it == -1) content.length else it }
                val first = content.substring(0, firstEnd)
                val parts = first.removePrefix("#!").trim().split(" ")
                if (parts.isEmpty()) return@use
                val interp = parts[0]
                val rest = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""
                val newInterp = interp.replace(termuxPrefix, currentPrefix)
                val newShebang = if (rest.isEmpty()) "#!$newInterp" else "#!$newInterp $rest"
                val newContent = newShebang + content.substring(firstEnd)
                try { file.writeText(newContent); file.setExecutable(true, false) } catch (_: Exception) {}
            }
        }
    }

    internal fun ensureLibrarySymlinks(prefix: File) {
        val libDir = File(prefix, "lib")
        if (!libDir.exists()) return
        val files = libDir.listFiles() ?: return
        val soRegex = Regex("^(lib.+)\\.so\\.(\\d+)(\\..*)?")
        for (f in files) {
            val m = soRegex.matchEntire(f.name) ?: continue
            val base = m.groupValues[1]
            val major = m.groupValues[2]
            val majorLink = File(libDir, "$base.so.$major")
            val noVerLink = File(libDir, "$base.so")
            try { if (!majorLink.exists()) Os.symlink(f.name, majorLink.absolutePath) } catch (_: Exception) {}
            try { if (!noVerLink.exists()) Os.symlink(f.name, noVerLink.absolutePath) } catch (_: Exception) {}
        }
    }

    internal fun ensureShFallback(prefix: File) {
        val binDir = File(prefix, "bin")
        if (!binDir.exists()) return
        val sh = File(binDir, "sh")
        val bash = File(binDir, "bash")
        var needWrap = false
        if (!sh.exists()) {
            needWrap = true
        } else {
            val canon = try { sh.canonicalPath } catch (_: Exception) { sh.absolutePath }
            if (canon.endsWith("/bin/bash") && !hasBashDependencies(prefix)) {
                needWrap = true
            }
        }
        if (needWrap) {
            try {
                val content = """
#!/system/bin/sh
exec /system/bin/sh "${'$'}@"
""".trimIndent()
                sh.writeText(content)
                sh.setExecutable(true, false)
            } catch (_: Exception) {}
        }
    }

    fun buildEnv(ctx: Context): Array<String> {
        val filesDir = ctx.filesDir
        val prefix = File(filesDir, "usr").absolutePath
        val home = File(filesDir, "home").apply { if (!exists()) mkdirs() }.absolutePath
        val tmp = File(filesDir, "tmp").apply { if (!exists()) mkdirs() }.absolutePath
        val path = "$prefix/bin:$prefix/bin/applets:/system/bin:/system/xbin"
        val ld = "$prefix/lib"
        val envList = mutableListOf(
            "TERM=xterm-256color",
            "HOME=$home",
            "PREFIX=$prefix",
            "TMPDIR=$tmp",
            "PATH=$path",
            "LD_LIBRARY_PATH=$ld",
            "LANG=en_US.UTF-8",
            "COLORTERM=truecolor"
        )
        val termuxExec = File(prefix, "lib/libtermux-exec.so")
        if (termuxExec.exists()) envList += "LD_PRELOAD=$ld/libtermux-exec.so"
        return envList.toTypedArray()
    }

    private fun hasBashDependencies(prefix: File): Boolean {
        val libDir = File(prefix, "lib")
        if (!libDir.exists() || !libDir.isDirectory) return false
        val names = libDir.list()?.toList() ?: emptyList()
        val hasReadline = names.any { it.startsWith("libreadline.so.8") || it.startsWith("libreadline.so.") }
        val hasNcurses = names.any { it.startsWith("libncursesw.so.6") || it.startsWith("libncursesw.so.") }
        return hasReadline && hasNcurses
    }

    fun resolveShell(ctx: Context): String? {
        val prefix = File(ctx.filesDir, "usr")
        val binDir = File(prefix, "bin")
        val login = File(binDir, "login")
        val bash = File(binDir, "bash")
        val sh = File(binDir, "sh")
        val systemSh = File("/system/bin/sh")

        // 1) 优先 bash（依赖齐全）
        if (bash.exists() && bash.canExecute() && hasBashDependencies(prefix)) {
            android.util.Log.d(TAG, "Using Termux bash shell: ${bash.absolutePath}")
            return bash.absolutePath
        }
        // 2) 其次 Termux 自带 sh（且不是指向 bash）
        if (sh.exists() && sh.canExecute()) {
            val canon = try { sh.canonicalPath } catch (_: Exception) { sh.absolutePath }
            if (!canon.endsWith("/bin/bash")) {
                android.util.Log.d(TAG, "Using Termux sh shell: ${sh.absolutePath}")
                return sh.absolutePath
            }
        }
        // 3) 再使用系统 sh
        if (systemSh.exists() && systemSh.canExecute()) {
            android.util.Log.d(TAG, "Using system sh shell: ${systemSh.absolutePath}")
            return systemSh.absolutePath
        }
        // 4) 最后 login
        if (login.exists() && login.canExecute()) {
            android.util.Log.d(TAG, "Using Termux login shell: ${login.absolutePath}")
            return login.absolutePath
        }
        android.util.Log.e(TAG, "No usable shell found in ${binDir.absolutePath} or /system/bin/sh")
        return null
    }
}


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
            // 先从版本号推导补齐一轮，再按 SYMLINKS.txt 精确恢复
            ensureLibrarySymlinks(prefix)
            restoreSymlinksFromFile(prefix)
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

    /**
     * 解析 $PREFIX/SYMLINKS.txt 并按声明创建符号链接。
     * 该文件来自官方 bootstrap，内容形如：
     *   libbz2.so.1.0.8 -> /lib/libbz2.so.1.0
     * 或使用 Unicode 箭头/其他分隔符。这里做兼容解析。
     */
    internal fun restoreSymlinksFromFile(prefix: File) {
        val file = File(prefix, "SYMLINKS.txt")
        if (!file.exists()) return
        val lines = try { file.readLines(Charsets.UTF_8) } catch (_: Exception) { return }
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            // 尝试多种分隔符
            var src: String? = null
            var dst: String? = null
            val arrowIdx = line.indexOf("->")
            val uniArrowIdx = if (arrowIdx < 0) line.indexOf('→') else -1
            if (arrowIdx >= 0) {
                src = line.substring(0, arrowIdx).trim()
                dst = line.substring(arrowIdx + 2).trim()
            } else if (uniArrowIdx >= 0) {
                src = line.substring(0, uniArrowIdx).trim()
                dst = line.substring(uniArrowIdx + 1).trim()
            } else {
                // 退化：以“/lib/”作为目标起点
                val libPos = line.indexOf("/lib/")
                if (libPos > 0) {
                    src = line.substring(0, libPos).trim().trimEnd('?', '�')
                    dst = line.substring(libPos).trim()
                }
            }
            if (src.isNullOrEmpty() || dst.isNullOrEmpty()) continue

            // 计算源与目标的实际文件路径
            val linkRel = dst.removePrefix("/")
            val linkFile = File(prefix, linkRel)

            // 源可能是相对名（如 libbz2.so.1.0.8），也可能是带路径的相对/绝对
            var srcRel = src.removePrefix("/")
            var srcFile = File(prefix, if (srcRel.contains("/")) srcRel else "lib/${srcRel}")
            if (!srcFile.exists() && !srcRel.startsWith("lib/")) {
                srcFile = File(prefix, "lib/${srcRel}")
            }
            if (!srcFile.exists()) continue

            // 创建链接
            try {
                linkFile.parentFile?.mkdirs()
                if (!linkFile.exists()) {
                    Os.symlink(srcFile.absolutePath, linkFile.absolutePath)
                    android.util.Log.d(TAG, "SYMLINK: ${linkFile.name} -> ${srcFile.name}")
                }
            } catch (_: Exception) { }
        }
    }

    internal fun ensureLibrarySymlinks(prefix: File) {
        val libDir = File(prefix, "lib")
        if (!libDir.exists()) return
        val files = libDir.listFiles() ?: return
        for (f in files) {
            val name = f.name
            if (!name.startsWith("lib")) continue
            val soIdx = name.indexOf(".so")
            if (soIdx <= 0) continue
            val base = name.substring(0, soIdx)
            val tail = name.substring(soIdx + 3) // after ".so"
            val noVerLink = File(libDir, "$base.so")
            try { if (!noVerLink.exists()) Os.symlink(name, noVerLink.absolutePath) } catch (_: Exception) {}
            val verTail = if (tail.startsWith(".")) tail.substring(1) else ""
            if (verTail.isEmpty()) continue
            val parts = verTail.split('.')
            if (parts.any { it.isEmpty() || it.any { ch -> !ch.isDigit() } }) continue
            var acc = ""
            for ((i, part) in parts.withIndex()) {
                acc = if (i == 0) part else "$acc.$part"
                val link = File(libDir, "$base.so.$acc")
                try { if (!link.exists()) Os.symlink(name, link.absolutePath) } catch (_: Exception) {}
            }
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
        val prefixDir = File(filesDir, "usr")
        // 运行前再次从 SYMLINKS.txt 恢复（兜底），避免 apt 初次运行缺别名
        restoreSymlinksFromFile(prefixDir)
        ensureLibrarySymlinks(prefixDir)

        val prefix = prefixDir.absolutePath
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


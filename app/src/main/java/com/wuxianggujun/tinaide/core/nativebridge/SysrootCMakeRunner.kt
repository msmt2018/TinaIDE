package com.wuxianggujun.tinaide.core.nativebridge

import android.util.Log
import com.wuxianggujun.tinaide.TinaApplication
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Experimental: Probe and (later) run CMake/Ninja from the packaged sysroot.
 * Current scope: only probe executable presence and `--version` to verify exec permission.
 * Does not alter existing in-process LLVM path.
 */
object SysrootCMakeRunner {
    data class ToolProbe(val name: String, val path: String, val exists: Boolean, val executable: Boolean, val rc: Int?, val out: String, val err: String)

    private fun filesDir(): File = TinaApplication.instance.filesDir

    private fun sysrootDir(): File = File(filesDir(), "sysroot")

    private fun tripleForAbi(): String {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abi.contains("arm64", true) -> "aarch64-linux-android"
            abi.contains("x86_64", true) -> "x86_64-linux-android"
            else -> "aarch64-linux-android"
        }
    }

    private fun toolPath(name: String): File = File(sysrootDir(), "usr/bin/$name")

    private fun runCommand(absPath: File, args: List<String>): Triple<Int, String, String> {
        val pb = ProcessBuilder(listOf(absPath.absolutePath) + args)
        // Ensure no surprises in PATH: we call absolute paths; still pass a minimal PATH including usr/bin
        val env = pb.environment()
        val usrBin = File(sysrootDir(), "usr/bin").absolutePath
        env["PATH"] = usrBin + File.pathSeparator + (env["PATH"] ?: "")
        pb.redirectErrorStream(false)
        val proc = pb.start()
        proc.waitFor(4, TimeUnit.SECONDS)
        val rc = try { proc.exitValue() } catch (_: IllegalThreadStateException) { -9999 }
        val out = proc.inputStream.bufferedReader().readText()
        val err = proc.errorStream.bufferedReader().readText()
        return Triple(rc, out, err)
    }

    /**
     * Probe cmake/ninja availability and version. Returns a multi-line report for UI logging.
     */
    fun probe(): String {
        val sb = StringBuilder()
        val triple = tripleForAbi()
        val root = sysrootDir()
        sb.appendLine("== Sysroot CMake/Ninja Probe ==")
        sb.appendLine("sysroot: ${root.absolutePath}")
        sb.appendLine("triple: $triple")

        fun probeOne(name: String): ToolProbe {
            val p = toolPath(name)
            val exists = p.exists()
            if (exists) p.setExecutable(true)
            val executable = exists && p.canExecute()
            var rc: Int? = null
            var out = ""
            var err = ""
            if (executable) {
                val (c, o, e) = runCommand(p, listOf("--version"))
                rc = c; out = o.trim(); err = e.trim()
            }
            return ToolProbe(name, p.absolutePath, exists, executable, rc, out, err)
        }

        val tools = listOf("cmake", "ninja").map { probeOne(it) }
        for (t in tools) {
            sb.appendLine("- ${t.name}: path=${t.path}")
            sb.appendLine("  exists=${t.exists} executable=${t.executable} rc=${t.rc}")
            if (t.out.isNotEmpty()) sb.appendLine("  out: ${t.out}")
            if (t.err.isNotEmpty()) sb.appendLine("  err: ${t.err}")
        }

        val report = sb.toString()
        Log.i("SysrootCMakeRunner", report)
        return report
    }
}


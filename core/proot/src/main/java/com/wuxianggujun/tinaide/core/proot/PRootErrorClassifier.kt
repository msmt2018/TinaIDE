package com.wuxianggujun.tinaide.core.proot

/**
 * PRoot 运行时错误分类器。
 *
 * 目标：将“PRoot 未能正确启动/运行”的错误与“纯网络错误”区分开，避免误导用户换源。
 */
object PRootErrorClassifier {
    fun looksLikePRootRuntimeError(output: String): Boolean {
        if (output.isBlank()) return false
        val lower = output.lowercase()

        // Native 崩溃（部分 ROM/内核/加速路径下可能出现）
        // 典型日志：proot info: vpid 1: terminated with signal 11
        if (lower.contains("terminated with signal")) return true
        if (lower.contains("sigsegv") || lower.contains("signal 11")) return true

        // 典型症状：tracee 侧 fork()/execve()/clone() 失败（ENOSYS）
        if (lower.contains("function not implemented") &&
            (lower.contains("fork") || lower.contains("execve") || lower.contains("clone") || lower.contains("posix_spawn"))
        ) return true
        if (lower.contains("en osys") || lower.contains("enosys")) return true

        // init-proot.sh 诊断
        if (lower.contains("unable to determine a working proot launch mode")) return true

        // 动态链接/执行错误
        val cannotLink = lower.contains("cannot link executable")
        val execFormat = lower.contains("exec format error")
        val notExecutableElf = lower.contains("not executable") && lower.contains("elf")
        val permissionDenied = lower.contains("permission denied") &&
            (output.contains("proot") || output.contains("libproot.so"))
        val linkerHelp = lower.contains("helper program for dynamic executables") ||
            (lower.contains("this is /system/bin/linker"))

        // 典型特征：proot ELF 被 sh 当脚本解析（会出现 proot[1]、syntax error 等）
        val hasShellParsedElf =
            (lower.contains("syntax error") || lower.contains("not found")) &&
                (output.contains("/libproot.so") ||
                    output.contains("/libproot-loader.so") ||
                    output.contains("/proot/proot") ||
                    output.contains("proot[") ||
                    output.contains("libproot-loader.so["))

        return hasShellParsedElf || cannotLink || execFormat || notExecutableElf || permissionDenied || linkerHelp
    }
}

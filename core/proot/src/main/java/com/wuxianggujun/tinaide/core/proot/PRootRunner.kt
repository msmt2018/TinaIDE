package com.wuxianggujun.tinaide.core.proot

import java.io.File

/**
 * PRoot 程序运行器
 *
 * 通过 PRoot 运行编译后的 Linux 程序。
 * 支持标准 I/O、命令行参数、环境变量等。
 *
 * @param prootManager PRoot 进程管理器
 */
class PRootRunner(
    private val prootManager: PRootManager
) {
    companion object {
        // 有超时需求的内部命令可显式传入；用户程序运行默认不限制时长
        private const val DEFAULT_TIMEOUT = 30_000L
    }

    /**
     * 运行程序
     *
     * @param executablePath 可执行文件路径（相对于 rootfs）
     * @param args 命令行参数
     * @param stdin 标准输入内容
     * @param workDir 工作目录
     * @param env 环境变量
     * @param timeout 超时时间（毫秒）；为 null 表示不限制
     * @return 运行结果
     */
    suspend fun run(
        executablePath: String,
        args: List<String> = emptyList(),
        stdin: String? = null,
        workDir: String? = null,
        env: Map<String, String> = emptyMap(),
        timeout: Long? = null
    ): RunResult {
        val safeTimeout = timeout?.coerceAtLeast(1000)

        val command = buildList {
            add(executablePath)
            addAll(args)
        }

        val effectiveWorkDir = workDir ?: File(executablePath).parent ?: "/workspace"

        return prootManager.execute(
            command = command,
            workDir = effectiveWorkDir,
            env = env,
            timeout = safeTimeout,
            stdin = stdin
        )
    }

    /**
     * 交互式运行程序
     *
     * @param executablePath 可执行文件路径
     * @param args 命令行参数
     * @param workDir 工作目录
     * @return 交互式进程接口
     */
    fun runInteractive(
        executablePath: String,
        args: List<String> = emptyList(),
        workDir: String? = null
    ): InteractiveProcess {
        val command = buildList {
            add(executablePath)
            addAll(args)
        }

        val effectiveWorkDir = workDir ?: "/workspace"

        return prootManager.startInteractive(
            command = command,
            workDir = effectiveWorkDir
        )
    }

    /**
     * 运行 shell 命令
     *
     * @param shellCommand shell 命令字符串
     * @param workDir 工作目录
     * @param timeout 超时时间
     * @return 运行结果
     */
    suspend fun runShell(
        shellCommand: String,
        workDir: String = "/workspace",
        timeout: Long = DEFAULT_TIMEOUT
    ): RunResult {
        return prootManager.execute(
            command = listOf("/bin/sh", "-c", shellCommand),
            workDir = workDir,
            timeout = timeout
        )
    }

    /**
     * 检查程序是否存在
     *
     * @param executablePath 可执行文件路径
     * @return 是否存在
     */
    suspend fun exists(executablePath: String): Boolean {
        val result = prootManager.execute(
            command = listOf("/bin/test", "-f", executablePath),
            workDir = "/",
            timeout = 5000
        )
        return result.exitCode == 0
    }

    /**
     * 检查程序是否可执行
     *
     * @param executablePath 可执行文件路径
     * @return 是否可执行
     */
    suspend fun isExecutable(executablePath: String): Boolean {
        val result = prootManager.execute(
            command = listOf("/bin/test", "-x", executablePath),
            workDir = "/",
            timeout = 5000
        )
        return result.exitCode == 0
    }

    /**
     * 设置文件可执行权限
     *
     * @param filePath 文件路径
     * @return 是否成功
     */
    suspend fun makeExecutable(filePath: String): Boolean {
        val result = prootManager.execute(
            command = listOf("/bin/chmod", "+x", filePath),
            workDir = "/",
            timeout = 5000
        )
        return result.exitCode == 0
    }
}

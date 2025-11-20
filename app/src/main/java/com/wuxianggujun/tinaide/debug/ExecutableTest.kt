package com.wuxianggujun.tinaide.debug

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 测试工具链是否可执行
 */
object ExecutableTest {
    private const val TAG = "ExecutableTest"
    
    /**
     * 测试文件是否可执行
     */
    fun testExecutable(context: Context) {
        val sysroot = File(context.filesDir, "sysroot")
        val clangPath = File(sysroot, "usr/bin/clang-cpp")
        
        Log.i(TAG, "=== 可执行性测试 ===")
        Log.i(TAG, "Clang 路径: ${clangPath.absolutePath}")
        Log.i(TAG, "文件存在: ${clangPath.exists()}")
        Log.i(TAG, "可读: ${clangPath.canRead()}")
        Log.i(TAG, "可执行: ${clangPath.canExecute()}")
        
        // 检查挂载参数
        try {
            val mountInfo = File("/proc/mounts").readText()
            val dataMount = mountInfo.lines().find { it.contains("/data") }
            Log.i(TAG, "Data 分区挂载信息: $dataMount")
            
            if (dataMount?.contains("noexec") == true) {
                Log.e(TAG, "❌ Data 分区被挂载为 noexec！")
            } else {
                Log.i(TAG, "✅ Data 分区没有 noexec 限制")
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法读取挂载信息", e)
        }
        
        // 尝试执行
        testExec(clangPath)
    }
    
    /**
     * 尝试执行文件
     */
    private fun testExec(file: File) {
        try {
            Log.i(TAG, "尝试执行: ${file.absolutePath}")
            val process = ProcessBuilder(file.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            Log.i(TAG, "✅ 执行成功！")
            Log.i(TAG, "退出码: $exitCode")
            Log.i(TAG, "输出: $output")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 执行失败: ${e.message}", e)
            
            // 分析错误类型
            when {
                e.message?.contains("Permission denied") == true -> {
                    Log.e(TAG, "原因: 权限被拒绝（可能是 SELinux 或 noexec）")
                }
                e.message?.contains("No such file") == true -> {
                    Log.e(TAG, "原因: 文件不存在或依赖缺失")
                }
                e.message?.contains("Text file busy") == true -> {
                    Log.e(TAG, "原因: 文件正在被使用")
                }
                else -> {
                    Log.e(TAG, "原因: 未知错误")
                }
            }
        }
    }
    
    /**
     * 测试 Native 层 exec
     */
    external fun testNativeExec(path: String): String
}

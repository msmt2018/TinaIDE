package com.wuxianggujun.tinaide.core.lsp

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 混合传输测试 - 集成控制通道和共享内存
 */
object HybridTransportTest {

    private const val TAG = "HybridTransportTest"
    private const val SOCKET_FILE_NAME = "tinaide_hybrid_test.sock"

    init {
        System.loadLibrary("native_compiler")
    }

    /**
     * 运行混合传输集成测试
     *
     * @param socketPath 可读写目录下的 Unix Socket 路径
     */
    external fun runIntegrationTest(socketPath: String): Boolean

    /**
     * 运行所有测试并返回结果
     */
    fun runAllTests(context: Context): TestResult {
        Log.i(TAG, "========== 开始混合传输测试 ==========")

        val socketPath = File(context.cacheDir, SOCKET_FILE_NAME).absolutePath
        Log.i(TAG, "控制通道 Socket 路径: $socketPath")

        val results = mutableListOf<Pair<String, Boolean>>()

        Log.i(TAG, "--- 混合传输集成测试 ---")
        val integrationResult = try {
            runIntegrationTest(socketPath)
        } catch (e: Exception) {
            Log.e(TAG, "集成测试异常", e)
            false
        }
        results.add("混合传输集成" to integrationResult)
        Log.i(TAG, "结果: ${if (integrationResult) "✅ 通过" else "❌ 失败"}")

        val passedCount = results.count { it.second }
        val totalCount = results.size

        Log.i(TAG, "========== 测试完成 ==========")
        Log.i(TAG, "通过: $passedCount / $totalCount")

        return TestResult(
            tests = results,
            passedCount = passedCount,
            totalCount = totalCount
        )
    }

    data class TestResult(
        val tests: List<Pair<String, Boolean>>,
        val passedCount: Int,
        val totalCount: Int
    ) {
        val allPassed: Boolean get() = passedCount == totalCount
        val passRate: Double get() = if (totalCount > 0) passedCount.toDouble() / totalCount else 0.0

        fun printSummary() {
            println("========== 混合传输测试报告 ==========")
            tests.forEach { (name, passed) ->
                println("${if (passed) "✅" else "❌"} $name")
            }
            println("通过率: ${(passRate * 100).toInt()}% ($passedCount / $totalCount)")
            println("========================================")
        }
    }
}

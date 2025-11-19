package com.wuxianggujun.tinaide.core.nativebridge

import android.util.Log

/**
 * Ninja 工具的 JNI 包装器
 * 
 * 通过加载 libninja_runner.so 来执行 Ninja 构建，绕过 Android SELinux 限制
 * 
 * libninja_runner.so 导出的接口：
 * - extern "C" int ninja_run(int argc, char** argv)
 */
object NinjaRunner {
    private const val TAG = "NinjaRunner"
    private var loaded = false
    
    /**
     * 加载 libninja_runner.so
     */
    fun loadIfNeeded() {
        if (loaded) return
        try {
            System.loadLibrary("ninja_runner")
            loaded = true
            Log.i(TAG, "libninja_runner.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libninja_runner.so", e)
            throw RuntimeException("Ninja runner library not found", e)
        }
    }
    
    /**
     * 检查是否可用
     */
    fun isAvailable(): Boolean {
        return try {
            loadIfNeeded()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 执行 Ninja 命令
     * 
     * 注意：由于 libninja_runner.so 直接调用 ninja 的 main()，
     * 我们需要通过 JNI 桥接来调用 ninja_run()
     * 
     * @param workingDir 工作目录
     * @param args Ninja 参数（包括 "ninja" 作为 argv[0]）
     * @return 退出码
     */
    external fun runNinja(workingDir: String, args: Array<String>): Int
}

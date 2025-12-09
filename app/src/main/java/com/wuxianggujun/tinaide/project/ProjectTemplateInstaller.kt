package com.wuxianggujun.tinaide.project

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ProjectTemplateInstaller {
    private const val TAG = "ProjectTemplate"

    /**
     * 创建 C++ 单文件项目（不使用构建系统）
     */
    fun installCppSingleFile(destDir: File, projectName: String): Boolean {
        return try {
            generateCppSingleFile(destDir, projectName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Generate single file template failed", e)
            false
        }
    }

    /**
     * 生成 C++ 单文件项目
     */
    private fun generateCppSingleFile(destDir: File, projectName: String) {
        // 创建一个简单的 main.cpp
        val mainCpp = """
            #include <iostream>
            
            int main() {
                std::cout << "Hello, $projectName!" << std::endl;
                return 0;
            }
        """.trimIndent()
        File(destDir, "main.cpp").writeText(mainCpp)
    }

}

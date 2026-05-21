package com.wuxianggujun.tinaide.ai.integration

import java.io.File

/**
 * 路径工具类
 *
 * 用于在 AI 工具中处理文件路径
 */
object PathUtils {
    /**
     * 将绝对路径转换为相对于项目根目录的相对路径
     *
     * @param absolutePath 绝对路径
     * @param projectRoot 项目根目录
     * @return 相对路径，如果无法转换则返回原路径
     */
    fun toRelativePath(absolutePath: String, projectRoot: String): String = try {
        // 先规范化路径，移除 file:// 前缀
        val normalizedPath = normalizeFilePath(absolutePath)
        val absFile = File(normalizedPath).canonicalFile
        val rootFile = File(projectRoot).canonicalFile

        if (absFile.startsWith(rootFile)) {
            absFile.relativeTo(rootFile).path
        } else {
            normalizedPath
        }
    } catch (e: Exception) {
        normalizeFilePath(absolutePath)
    }

    /**
     * 将相对路径转换为绝对路径
     *
     * @param relativePath 相对路径
     * @param projectRoot 项目根目录
     * @return 绝对路径
     */
    fun toAbsolutePath(relativePath: String, projectRoot: String): String = try {
        // 先规范化路径，移除 file:// 前缀
        val normalizedPath = normalizeFilePath(relativePath)
        val file = File(normalizedPath)
        if (file.isAbsolute) {
            file.canonicalPath
        } else {
            File(projectRoot, normalizedPath).canonicalPath
        }
    } catch (e: Exception) {
        normalizeFilePath(relativePath)
    }

    /**
     * 规范化文件路径，移除 file:// 前缀
     *
     * @param path 原始路径
     * @return 规范化后的路径
     */
    fun normalizeFilePath(path: String): String = when {
        path.startsWith("file://") -> path.substring(7)
        path.startsWith("file:") -> path.substring(5)
        else -> path
    }

    /**
     * 批量转换为相对路径
     */
    fun toRelativePaths(absolutePaths: List<String>, projectRoot: String): List<String> = absolutePaths.map { toRelativePath(it, projectRoot) }
}

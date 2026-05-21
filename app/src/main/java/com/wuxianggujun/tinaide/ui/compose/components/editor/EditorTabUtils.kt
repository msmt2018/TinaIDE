package com.wuxianggujun.tinaide.ui.compose.components.editor

import java.io.File

/**
 * 编辑器标签页工具函数
 */

/**
 * 计算标签页的区分路径
 *
 * 当存在同名文件时，计算最短的能区分它们的父目录路径
 * 例如：
 * - /a/b/c/iostream 和 /a/d/e/iostream -> "c" 和 "e"
 * - /a/b/iostream 和 /a/c/iostream -> "b" 和 "c"
 * - /a/b/c/iostream（唯一）-> null（不需要区分）
 */
fun calculateDisambiguationPaths(tabs: List<EditorTabState>): Map<String, String?> {
    val result = mutableMapOf<String, String?>()

    // 按文件名分组
    val groupedByName = tabs.groupBy { it.file.name }

    for ((_, tabsWithSameName) in groupedByName) {
        if (tabsWithSameName.size == 1) {
            // 只有一个文件，不需要区分
            result[tabsWithSameName[0].id] = null
        } else {
            // 多个同名文件，需要计算区分路径
            val paths = tabsWithSameName.map { tab ->
                tab.id to getPathSegments(tab.file)
            }

            // 从最后一个目录开始，逐级向上查找能区分的路径
            val disambiguationPaths = findMinimalDisambiguationPaths(paths)
            result.putAll(disambiguationPaths)
        }
    }

    return result
}

/**
 * 获取文件的路径段列表（从文件名向上）
 */
private fun getPathSegments(file: File): List<String> {
    val segments = mutableListOf<String>()
    var parent = file.parentFile
    while (parent != null) {
        segments.add(parent.name)
        parent = parent.parentFile
    }
    return segments
}

/**
 * 找到能区分所有路径的最短路径段
 */
private fun findMinimalDisambiguationPaths(paths: List<Pair<String, List<String>>>): Map<String, String> {
    val result = mutableMapOf<String, String>()

    // 从第一级父目录开始尝试
    var level = 0
    val maxLevel = paths.maxOfOrNull { it.second.size } ?: 0

    while (level < maxLevel) {
        // 获取当前级别的路径段
        val currentLevelPaths = paths.map { (id, segments) ->
            val pathPart = if (level < segments.size) {
                // 构建从当前级别到第一级的路径
                segments.take(level + 1).reversed().joinToString("/")
            } else {
                // 路径不够长，使用完整路径
                segments.reversed().joinToString("/")
            }
            id to pathPart
        }

        // 检查是否所有路径都唯一
        val pathCounts = currentLevelPaths.groupBy { it.second }
        val allUnique = pathCounts.all { it.value.size == 1 }

        if (allUnique) {
            // 找到了能区分的路径
            currentLevelPaths.forEach { (id, path) ->
                result[id] = path
            }
            break
        }

        level++
    }

    // 如果遍历完所有级别仍无法区分，使用完整路径
    if (result.isEmpty()) {
        paths.forEach { (id, segments) ->
            result[id] = segments.reversed().joinToString("/")
        }
    }

    return result
}

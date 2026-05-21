package com.wuxianggujun.tinaide.editor.navigation

/**
 * #include 指令解析器
 * 
 * 解析 C/C++ 源文件中的 #include 指令，提取头文件路径信息
 */
object IncludeParser {
    
    /**
     * 匹配 #include <header.h> 或 #include "header.h"
     * 
     * 支持的格式：
     * - #include <stdio.h>
     * - #include "myheader.h"
     * - #include <sys/types.h>
     * - #include "subdir/header.h"
     * - #  include <header.h>  (带空格)
     * - 行首有空格的情况
     */
    private val INCLUDE_REGEX = Regex(
        """^\s*#\s*include\s*([<"])([^>"]+)[>"]"""
    )
    
    /**
     * 解析 #include 行
     * 
     * @param lineText 行文本
     * @return IncludeInfo 或 null（如果不是 #include 行）
     */
    fun parse(lineText: String): IncludeInfo? {
        val match = INCLUDE_REGEX.find(lineText) ?: return null
        
        val delimiter = match.groupValues[1]
        val path = match.groupValues[2]
        val isSystemHeader = delimiter == "<"
        
        // 计算路径在行中的位置（用于判断点击位置）
        // 找到路径字符串在原始行中的位置
        val pathStart = findPathStartIndex(lineText, path, delimiter)
        val pathEnd = pathStart + path.length
        
        return IncludeInfo(
            path = path,
            isSystemHeader = isSystemHeader,
            pathStartColumn = pathStart,
            pathEndColumn = pathEnd
        )
    }
    
    /**
     * 检查给定列是否在头文件路径范围内
     * 
     * @param column 列号（0-based）
     * @param includeInfo 解析后的 include 信息
     * @return 如果列在路径范围内返回 true
     */
    fun isColumnInPath(column: Int, includeInfo: IncludeInfo): Boolean {
        return column >= includeInfo.pathStartColumn && column < includeInfo.pathEndColumn
    }
    
    /**
     * 查找路径在行中的起始索引
     */
    private fun findPathStartIndex(lineText: String, path: String, delimiter: String): Int {
        // 找到 delimiter（< 或 "）的位置，路径紧跟其后
        val delimiterIndex = if (delimiter == "<") {
            lineText.indexOf('<')
        } else {
            lineText.indexOf('"')
        }
        
        return if (delimiterIndex >= 0) {
            delimiterIndex + 1
        } else {
            // 回退方案：直接搜索路径
            lineText.indexOf(path).coerceAtLeast(0)
        }
    }
}
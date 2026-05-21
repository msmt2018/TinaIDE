package com.wuxianggujun.tinaide.editor.navigation

/**
 * #include 指令信息
 * 
 * 包含解析后的头文件路径和位置信息
 */
data class IncludeInfo(
    /**
     * 头文件路径，如 "stdio.h" 或 "myheader.h" 或 "subdir/header.h"
     */
    val path: String,
    
    /**
     * 是否是系统头文件
     * true: <header.h> 形式
     * false: "header.h" 形式
     */
    val isSystemHeader: Boolean,
    
    /**
     * 路径在行中的起始列（0-based）
     */
    val pathStartColumn: Int,
    
    /**
     * 路径在行中的结束列（0-based，不包含）
     */
    val pathEndColumn: Int
)
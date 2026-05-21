package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * TinaIDE 语义化颜色定义
 *
 * 用于状态指示、日志级别、Git 状态等场景
 * 这些颜色在浅色/深色主题下保持一致，因为它们是语义化的状态颜色
 *
 * 设计原则：
 * - 成功/添加：绿色系
 * - 错误/删除：红色系
 * - 警告/修改：橙色/黄色系
 * - 信息/调试：蓝色系
 * - 中性/未知：灰色系
 */
object TinaSemanticColors {

    // ============ 通用状态颜色 ============

    /** 成功状态 - 绿色 */
    val success: Color = Color(0xFF4CAF50)

    /** 成功状态（亮色）- 亮绿色 */
    val successBright: Color = Color(0xFF00E676)

    /** 错误状态 - 红色 */
    val error: Color = Color(0xFFF44336)

    /** 错误状态（亮色）- 亮红色 */
    val errorBright: Color = Color(0xFFFF1744)

    /** 警告状态 - 橙色 */
    val warning: Color = Color(0xFFFF9800)

    /** 警告状态（黄色）- 用于忙碌等状态 */
    val warningYellow: Color = Color(0xFFFFC107)

    /** 信息状态 - 蓝色 */
    val info: Color = Color(0xFF2196F3)

    /** 中性状态 - 灰色 */
    val neutral: Color = Color(0xFF9E9E9E)

    /** 中性状态（深色）- 深灰色 */
    val neutralDark: Color = Color(0xFF757575)

    // ============ 日志级别颜色 ============

    object Log {
        /** VERBOSE 级别 - 灰色 */
        val verbose: Color = Color(0xFF9E9E9E)

        /** DEBUG 级别 - 蓝色 */
        val debug: Color = Color(0xFF2196F3)

        /** INFO 级别 - 绿色 */
        val info: Color = Color(0xFF4CAF50)

        /** WARN 级别 - 橙色 */
        val warn: Color = Color(0xFFFF9800)

        /** ERROR 级别 - 红色 */
        val error: Color = Color(0xFFF44336)

        /** SUCCESS 级别 - 亮绿色 */
        val success: Color = Color(0xFF00E676)

        /** FAIL 级别 - 亮红色 */
        val fail: Color = Color(0xFFFF1744)
    }

    // ============ Git 状态颜色 ============

    object Git {
        /** 已修改 - 黄色 */
        val modified: Color = Color(0xFFE2A832)

        /** 已添加 - 绿色 */
        val added: Color = Color(0xFF4CAF50)

        /** 已删除 - 红色 */
        val deleted: Color = Color(0xFFF44336)

        /** 已重命名 - 蓝色 */
        val renamed: Color = Color(0xFF2196F3)

        /** 已复制 - 紫色 */
        val copied: Color = Color(0xFF9C27B0)

        /** 未跟踪 - 灰色 */
        val untracked: Color = Color(0xFF9E9E9E)

        /** 已忽略 - 深灰色 */
        val ignored: Color = Color(0xFF757575)
    }

    // ============ 编辑器/LSP 状态颜色 ============

    object Editor {
        /** 就绪状态 - 绿色 */
        val ready: Color = Color(0xFF4CAF50)

        /** 连接中状态 - 蓝色 */
        val connecting: Color = Color(0xFF2196F3)

        /** 忙碌状态 - 黄色 */
        val busy: Color = Color(0xFFFFC107)

        /** 无 LSP 状态 - 灰色 */
        val noLsp: Color = Color(0xFF9E9E9E)

        /** 错误状态 - 红色 */
        val error: Color = Color(0xFFF44336)
    }

    // ============ 调试状态颜色 ============

    object Debug {
        /** 暂停状态（可继续）- 绿色 */
        val paused: Color = Color(0xFF4CAF50)

        /** 运行中状态 - 橙色 */
        val running: Color = Color(0xFFFF9800)

        /** 断点颜色 - 红色 */
        val breakpoint: Color = Color(0xFFE53935)

        /** 断点未验证颜色 - 半透明红色 */
        val breakpointUnverified: Color = Color(0x80E53935)
    }

    // ============ 诊断严重性颜色 ============

    object Diagnostic {
        /** 错误 - 使用 MaterialTheme.colorScheme.error */
        @Composable
        fun error(): Color = MaterialTheme.colorScheme.error

        /** 警告 - 橙色 */
        val warning: Color = Color(0xFFFF9800)

        /** 信息 - 使用 MaterialTheme.colorScheme.primary */
        @Composable
        fun info(): Color = MaterialTheme.colorScheme.primary

        /** 提示 - 使用 MaterialTheme.colorScheme.onSurfaceVariant */
        @Composable
        fun hint(): Color = MaterialTheme.colorScheme.onSurfaceVariant
    }

    // ============ 项目列表/UI 装饰颜色 ============

    object Project {
        /** 最近更新标记 - 绿色 */
        val recentUpdate: Color = Color(0xFF34A853)

        /** Logo 背景 - 蓝色 */
        val logoBg: Color = Color(0xFF4285F4)

        /** 未读红点 - 红色 */
        val unreadDot: Color = Color(0xFFEA4335)

        /** 快捷操作卡片 - 蓝色背景 */
        val quickActionBlueBg: Color = Color(0xFFE3F2FD)
        val quickActionBlueIcon: Color = Color(0xFF4285F4)

        /** 快捷操作卡片 - 绿色背景 */
        val quickActionGreenBg: Color = Color(0xFFE8F5E9)
        val quickActionGreenIcon: Color = Color(0xFF34A853)

        /** 提示卡片 - 橙色系 */
        val tipCardBg: Color = Color(0xFFFFF3E0)
        val tipCardIcon: Color = Color(0xFFFFA726)
        val tipCardText: Color = Color(0xFF795548)

        /** FAB 提示文字 - 蓝色 */
        val fabHint: Color = Color(0xFF4285F4)
    }

    // ============ 语言/工具标签颜色 ============

    object Language {
        /** Git 标签 */
        val gitBg: Color = Color(0xFFE8F5E9)
        val gitText: Color = Color(0xFF34A853)

        /** CMake 标签 */
        val cmakeBg: Color = Color(0xFFE3F2FD)
        val cmakeText: Color = Color(0xFF1976D2)

        /** Makefile 标签 */
        val makefileBg: Color = Color(0xFFFFF3E0)
        val makefileText: Color = Color(0xFFE65100)

        /** C/C++ 标签 */
        val cppBg: Color = Color(0xFFE8EAF6)
        val cppText: Color = Color(0xFF3F51B5)

        /** Java 标签 */
        val javaBg: Color = Color(0xFFFFF8E1)
        val javaText: Color = Color(0xFFFF8F00)

        /** Kotlin 标签 */
        val kotlinBg: Color = Color(0xFFF3E5F5)
        val kotlinText: Color = Color(0xFF7B1FA2)

        /** Python 标签 */
        val pythonBg: Color = Color(0xFFE3F2FD)
        val pythonText: Color = Color(0xFF1565C0)

        /** Rust 标签 */
        val rustBg: Color = Color(0xFFFFEBEE)
        val rustText: Color = Color(0xFFB71C1C)

        /** Go 标签 */
        val goBg: Color = Color(0xFFE0F7FA)
        val goText: Color = Color(0xFF00838F)

        /** JavaScript 标签 */
        val jsBg: Color = Color(0xFFFFFDE7)
        val jsText: Color = Color(0xFFF9A825)

        /** TypeScript 标签 */
        val tsBg: Color = Color(0xFFE3F2FD)
        val tsText: Color = Color(0xFF1976D2)

        /** Shell 标签 */
        val shellBg: Color = Color(0xFFECEFF1)
        val shellText: Color = Color(0xFF455A64)
    }
}

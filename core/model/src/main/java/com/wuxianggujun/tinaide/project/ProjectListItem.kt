package com.wuxianggujun.tinaide.project

import java.io.File

enum class ProjectSourceLocation {
    PUBLIC,
    PRIVATE,
}

data class ProjectListItem(
    val dir: File,
    val displayName: String,
    val id: String? = null,
    val lastOpenedAt: Long? = null,
    /** 构建系统类型（用于显示标签） */
    val buildSystem: ProjectBuildSystem? = null,
    /** 主要编程语言（用于显示语言标签） */
    val primaryLanguage: ProjectLanguage? = null,
    /** 源码所在目录类型 */
    val sourceLocation: ProjectSourceLocation? = null
)

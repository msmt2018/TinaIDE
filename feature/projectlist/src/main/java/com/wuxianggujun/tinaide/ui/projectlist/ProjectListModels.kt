package com.wuxianggujun.tinaide.ui.projectlist

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.project.ProjectLanguage
import com.wuxianggujun.tinaide.project.ProjectSourceLocation

/**
 * 项目标签 —— 用于在项目卡片上展示项目的关键特征
 *
 * 分为三类：
 * - 来源标签：GIT（有 .git 目录时显示）
 * - 构建系统标签：CMAKE / MAKEFILE / PLUGIN
 * - 语言标签：C_CPP / JAVA / KOTLIN / PYTHON / RUST / GO / JAVASCRIPT / TYPESCRIPT / SHELL
 */
enum class ProjectTag(private val displayNameResId: Int) {
    /** 公有源码目录 */
    PUBLIC_SOURCE(Strings.tag_public_source),
    /** 私有源码目录 */
    PRIVATE_SOURCE(Strings.tag_private_source),
    /** Git 仓库 */
    GIT(Strings.tag_git),
    /** CMake 项目 */
    CMAKE(Strings.tag_cmake),
    /** Makefile 项目 */
    MAKEFILE(Strings.tag_makefile),
    /** TinaIDE 插件项目 */
    PLUGIN(Strings.tag_plugin),
    /** C/C++ */
    C_CPP(Strings.tag_c_cpp),
    /** Java */
    JAVA(Strings.tag_java),
    /** Kotlin */
    KOTLIN(Strings.tag_kotlin),
    /** Python */
    PYTHON(Strings.tag_python),
    /** Rust */
    RUST(Strings.tag_rust),
    /** Go */
    GO(Strings.tag_go),
    /** JavaScript */
    JAVASCRIPT(Strings.tag_javascript),
    /** TypeScript */
    TYPESCRIPT(Strings.tag_typescript),
    /** Shell */
    SHELL(Strings.tag_shell);

    fun getDisplayName(context: Context): String = context.getString(displayNameResId)

    companion object {
        fun fromSourceLocation(sourceLocation: ProjectSourceLocation?): ProjectTag? {
            return when (sourceLocation) {
                ProjectSourceLocation.PUBLIC -> PUBLIC_SOURCE
                ProjectSourceLocation.PRIVATE -> PRIVATE_SOURCE
                null -> null
            }
        }

        /**
         * 从 ProjectLanguage 转换为对应的语言标签
         */
        fun fromLanguage(language: ProjectLanguage): ProjectTag? {
            return when (language) {
                ProjectLanguage.C, ProjectLanguage.CPP -> C_CPP
                ProjectLanguage.JAVA -> JAVA
                ProjectLanguage.KOTLIN -> KOTLIN
                ProjectLanguage.PYTHON -> PYTHON
                ProjectLanguage.RUST -> RUST
                ProjectLanguage.GO -> GO
                ProjectLanguage.JAVASCRIPT -> JAVASCRIPT
                ProjectLanguage.TYPESCRIPT -> TYPESCRIPT
                ProjectLanguage.SHELL -> SHELL
                ProjectLanguage.MIXED, ProjectLanguage.UNKNOWN -> null
            }
        }
    }
}

/**
 * 项目操作类型
 */
enum class ProjectAction {
    OPEN,       // 打开项目
    RENAME,     // 重命名
    EXPORT,     // 导出项目
    SETTINGS,   // 项目设置（不打开项目，仅编辑其专属配置）
    INFO,       // 项目信息
    DELETE      // 删除项目
}

/**
 * 公告类型枚举
 */
enum class AnnouncementType {
    /** 新版本发布 */
    NEW_RELEASE,
    /** 普通公告 */
    INFO,
    /** 重要通知 */
    IMPORTANT,
    /** 警告 */
    WARNING
}

data class AnnouncementReward(
    val quotaAmount: Long,
    val quotaExpiresAtMillis: Long? = null,
    val claimed: Boolean = false,
    val canClaim: Boolean = false,
    val claimedAtMillis: Long? = null,
)

/**
 * 公告数据模型
 */
data class Announcement(
    val id: String,
    val type: AnnouncementType,
    val title: String,
    val bodyContent: String? = null,
    val content: String? = null,
    val actionText: String? = null,
    val actionUrl: String? = null,
    val isPopup: Boolean = false,
    val dismissible: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long? = null,
    val receivedAtMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val readAtMillis: Long? = null,
    val reward: AnnouncementReward? = null,
)


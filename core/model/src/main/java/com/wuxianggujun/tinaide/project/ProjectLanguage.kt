package com.wuxianggujun.tinaide.project

/**
 * 项目主要编程语言
 *
 * 注意：这个枚举的名称会被序列化到 JSON 中，不要随意修改
 */
enum class ProjectLanguage {
    /** C 语言 */
    C,

    /** C++ 语言 */
    CPP,

    /** Java 语言 */
    JAVA,

    /** Kotlin 语言 */
    KOTLIN,

    /** Python 语言 */
    PYTHON,

    /** Rust 语言 */
    RUST,

    /** Go 语言 */
    GO,

    /** JavaScript 语言 */
    JAVASCRIPT,

    /** TypeScript 语言 */
    TYPESCRIPT,

    /** Shell 脚本 */
    SHELL,

    /** 混合语言（多种语言混合项目） */
    MIXED,

    /** 未知语言 */
    UNKNOWN;

    companion object {
        /**
         * 从字符串解析语言类型
         */
        fun fromString(value: String?): ProjectLanguage {
            if (value == null) return UNKNOWN
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

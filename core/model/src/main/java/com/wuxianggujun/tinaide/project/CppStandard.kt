package com.wuxianggujun.tinaide.project

/**
 * C++ 语言标准版本枚举
 *
 * 用于项目创建时选择 C++ 标准，影响：
 * - CMakeLists.txt 中的 CMAKE_CXX_STANDARD
 * - compile_commands.json 中的 -std=c++XX 标志
 *
 * 注意：枚举名称会被序列化到 JSON，不要随意修改
 */
enum class CppStandard(
    /** 编译器标志值，如 "c++17" */
    val flag: String,
    /** CMake 变量值，如 "17" */
    val cmakeValue: String
) {
    CPP_11("c++11", "11"),
    CPP_14("c++14", "14"),
    CPP_17("c++17", "17"),
    CPP_20("c++20", "20"),
    CPP_23("c++23", "23");

    companion object {
        /** 默认 C++ 标准 */
        val DEFAULT = CPP_17

        /**
         * 从字符串解析 C++ 标准
         *
         * @param value 枚举名称（如 "CPP_17"）或 CMake 值（如 "17"）
         * @return 对应的 CppStandard，未找到时返回 DEFAULT
         */
        fun fromString(value: String?): CppStandard {
            if (value == null) return DEFAULT
            val normalized = value.trim()
            if (normalized.isEmpty()) return DEFAULT
            return entries.find {
                it.name.equals(normalized, ignoreCase = true) ||
                    it.cmakeValue == normalized ||
                    it.flag.equals(normalized, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}

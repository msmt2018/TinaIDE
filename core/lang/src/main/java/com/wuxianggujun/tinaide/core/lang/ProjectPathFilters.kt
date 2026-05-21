package com.wuxianggujun.tinaide.core.lang

/**
 * 项目路径过滤策略。
 *
 * 这里统一维护大项目里最常见的高噪声目录和同步排除模式，
 * 避免 watcher、搜索、同步各自维护一套不一致的规则。
 */
object ProjectPathFilters {
    val NOISY_DIRECTORY_NAMES: Set<String> = setOf(
        ".git",
        ".gradle",
        ".idea",
        ".vscode",
        ".svn",
        ".hg",
        ".cxx",
        ".externalnativebuild",
        ".kotlin",
        ".cache",
        ".mypy_cache",
        ".pytest_cache",
        "build",
        "out",
        "obj",
        "node_modules",
        "vendor",
        "__pycache__"
    )

    val NOISY_DIRECTORY_PREFIXES: Set<String> = setOf(
        "cmake-build-"
    )

    val SEARCH_ONLY_DIRECTORY_NAMES: Set<String> = setOf(
        "external"
    )

    val SYNC_IGNORE_PATTERNS: List<String> = buildList {
        NOISY_DIRECTORY_NAMES.forEach { add("$it/") }
        NOISY_DIRECTORY_PREFIXES.forEach { add("${it}*/") }
        add("bin/")
        add("*.iml")
        add("*.pyc")
        add("*.tmp")
        add("*.temp")
        add("*.swp")
        add("*.bak")
        add("*~")
        add("*.so")
        add("*.a")
        add("*.o")
        add("*.obj")
        add("*.exe")
        add("*.dll")
        add("*.dylib")
        add("*.zip")
        add("*.tar")
        add("*.gz")
        add("*.rar")
        add("*.7z")
        add("*.apk")
        add("*.aar")
        add("*.jar")
    }

    fun isNoisyDirectoryName(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized in NOISY_DIRECTORY_NAMES ||
            NOISY_DIRECTORY_PREFIXES.any(normalized::startsWith)
    }

    fun shouldSkipSearchDirectory(name: String): Boolean {
        val normalized = name.lowercase()
        return (normalized.startsWith(".") && normalized.length > 1) ||
            normalized in SEARCH_ONLY_DIRECTORY_NAMES ||
            isNoisyDirectoryName(normalized)
    }
}

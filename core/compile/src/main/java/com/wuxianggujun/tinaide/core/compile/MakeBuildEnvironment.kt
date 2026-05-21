package com.wuxianggujun.tinaide.core.compile

import com.wuxianggujun.tinaide.core.packages.InstalledPackagePathResolver

/**
 * Make 项目的附加环境变量组装器。
 *
 * 设计原则：
 * 1. 不再通过 `make` 命令行去改写 `CPPFLAGS/CFLAGS/CXXFLAGS/LDFLAGS/LDLIBS`
 * 2. 只通过环境变量补充 IDE 侧信息，避免命令行变量优先级覆盖用户 Makefile
 * 3. 用户的 Makefile 仍然是 Make 项目的唯一构建事实来源
 */
internal object MakeBuildEnvironment {

    fun build(
        packagePaths: InstalledPackagePathResolver.PackagePaths,
        nativeCFlags: String = "",
        nativeCppFlags: String = "",
        nativeLdFlags: String = "",
        nativeLdLibs: String = "",
        extraLibraryDirs: List<String> = emptyList(),
        pathMapper: (String) -> String = { it }
    ): Map<String, String> {
        val env = linkedMapOf<String, String>()

        putPathIfNotBlank(
            env,
            "CPATH",
            packagePaths.includeDirs.map { pathMapper(it.absolutePath) }
        )
        putPathIfNotBlank(
            env,
            "LIBRARY_PATH",
            buildList {
                addAll(extraLibraryDirs.map(pathMapper))
                addAll(packagePaths.libDirs.map { pathMapper(it.absolutePath) })
            }
        )
        putPathIfNotBlank(
            env,
            "PKG_CONFIG_PATH",
            packagePaths.pkgConfigDirs.map { pathMapper(it.absolutePath) }
        )
        putPathIfNotBlank(
            env,
            "LD_LIBRARY_PATH",
            packagePaths.runtimeLibDirs.map { pathMapper(it.absolutePath) }
        )

        putEnvIfNotBlank(env, "CFLAGS", nativeCFlags)
        putEnvIfNotBlank(env, "CXXFLAGS", nativeCppFlags)
        putEnvIfNotBlank(env, "LDFLAGS", nativeLdFlags)
        putEnvIfNotBlank(env, "LDLIBS", nativeLdLibs)

        return env
    }

    internal fun normalizeFlagValue(value: String): String {
        if (value.isBlank()) return ""
        return value.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .trim()
    }

    private fun putEnvIfNotBlank(
        env: MutableMap<String, String>,
        key: String,
        value: String
    ) {
        val normalized = normalizeFlagValue(value)
        if (normalized.isNotBlank()) {
            env[key] = normalized
        }
    }

    private fun putPathIfNotBlank(
        env: MutableMap<String, String>,
        key: String,
        paths: List<String>
    ) {
        val normalized = paths.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (normalized.isNotEmpty()) {
            env[key] = normalized.joinToString(":")
        }
    }
}

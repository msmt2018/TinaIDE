package com.wuxianggujun.tinaide.core.compile.cmake

/**
 * 统一处理 Ninja 文件里的 cmake 路径替换。
 *
 * 目标：
 * 1. 覆盖 /data/user/0 与 /data/data 路径别名
 * 2. shim 模式下替换所有 raw cmake 路径
 * 3. linker64 兜底模式下避免重复包装（幂等）
 */
internal object NinjaCmakePathPatcher {

    private const val DATA_USER_PREFIX = "/data/user/0/"
    private const val DATA_DATA_PREFIX = "/data/data/"

    fun expandPathVariants(path: String): Set<String> {
        if (path.isBlank()) return emptySet()

        val normalized = path.replace('\\', '/')
        return buildSet {
            add(path)
            add(normalized)

            when {
                normalized.startsWith(DATA_USER_PREFIX) -> {
                    add(normalized.replaceFirst(DATA_USER_PREFIX, DATA_DATA_PREFIX))
                }

                normalized.startsWith(DATA_DATA_PREFIX) -> {
                    add(normalized.replaceFirst(DATA_DATA_PREFIX, DATA_USER_PREFIX))
                }
            }
        }
    }

    fun patchContent(
        original: String,
        cmakePaths: Set<String>,
        cmakeShimCommand: String?,
        linker64: String,
        shimScriptPaths: Set<String> = emptySet(),
        binaryCommandMappings: Map<String, String> = emptyMap()
    ): String {
        if (original.isEmpty()) return original
        if (cmakePaths.isEmpty() && shimScriptPaths.isEmpty() && binaryCommandMappings.isEmpty()) {
            return original
        }

        val orderedPaths = cmakePaths
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }

        var patched = original
        orderedPaths.forEach { cmakePath ->
            patched = if (cmakeShimCommand != null) {
                patched.replace(cmakePath, cmakeShimCommand)
            } else {
                replaceUnwrappedPathWithLinker64(patched, cmakePath, linker64)
            }
        }

        val orderedBinaryMappings = binaryCommandMappings.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .sortedByDescending { it.key.length }
        orderedBinaryMappings.forEach { (binaryPath, commandString) ->
            patched = replaceUnwrappedPathWithCommand(
                content = patched,
                path = binaryPath,
                command = commandString,
                linker64 = linker64
            )
        }

        val orderedShimPaths = shimScriptPaths
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
        orderedShimPaths.forEach { shimPath ->
            patched = replaceUnwrappedPathWithShell(patched, shimPath)
        }

        return patched
    }

    private fun replaceUnwrappedPathWithLinker64(
        content: String,
        cmakePath: String,
        linker64: String
    ): String {
        if (cmakePath !in content) return content

        val escapedPath = Regex.escape(cmakePath)
        val escapedLinker = Regex.escape(linker64)
        val pattern = Regex("(?<!$escapedLinker\\s)$escapedPath")
        return content.replace(pattern) { match ->
            "$linker64 ${match.value}"
        }
    }

    private fun replaceUnwrappedPathWithShell(
        content: String,
        scriptPath: String
    ): String {
        if (scriptPath !in content) return content

        val escapedPath = Regex.escape(scriptPath)
        val escapedShell = Regex.escape("/system/bin/sh")
        val pattern = Regex("(?<!$escapedShell\\s)$escapedPath")
        return content.replace(pattern) { match ->
            "/system/bin/sh ${match.value}"
        }
    }

    private fun replaceUnwrappedPathWithCommand(
        content: String,
        path: String,
        command: String,
        linker64: String
    ): String {
        if (path !in content) return content

        val escapedPath = Regex.escape(path)
        val escapedShell = Regex.escape("/system/bin/sh")
        val escapedLinker = Regex.escape(linker64)
        val pattern = Regex("(?<!$escapedShell\\s)(?<!$escapedLinker\\s)$escapedPath")
        return content.replace(pattern, command)
    }
}

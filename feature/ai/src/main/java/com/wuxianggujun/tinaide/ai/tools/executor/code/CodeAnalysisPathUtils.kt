package com.wuxianggujun.tinaide.ai.tools.executor.code

import java.io.File

internal fun resolveProjectPath(projectRoot: String, path: String): File? = runCatching {
    val root = File(projectRoot).canonicalFile
    val rawTarget = File(path)
    val target = if (rawTarget.isAbsolute) {
        rawTarget.canonicalFile
    } else {
        File(root, path).canonicalFile
    }
    target.takeIf { candidate ->
        val rootPath = root.toPath()
        val candidatePath = candidate.toPath()
        candidatePath == rootPath || candidatePath.startsWith(rootPath)
    }
}.getOrNull()

internal fun toProjectRelativePath(projectRoot: String, absolutePath: String): String = runCatching {
    val root = File(projectRoot).canonicalFile
    val target = File(absolutePath).canonicalFile
    val rootPath = root.toPath()
    val targetPath = target.toPath()

    when {
        targetPath == rootPath -> "."
        targetPath.startsWith(rootPath) -> target.relativeTo(root).path
        else -> absolutePath
    }
}.getOrElse { absolutePath }

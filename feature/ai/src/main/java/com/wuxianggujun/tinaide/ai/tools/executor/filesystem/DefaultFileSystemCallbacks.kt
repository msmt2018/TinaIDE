package com.wuxianggujun.tinaide.ai.tools.executor.filesystem

import com.wuxianggujun.tinaide.ai.tools.rethrowIfCancellation
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 默认的文件系统回调实现
 * 使用标准的 Java File API 进行文件操作
 *
 * 注意：这是一个简单的默认实现，不包含编辑器同步功能
 * 实际使用时应该在 app 模块中创建自定义实现
 */
class DefaultFileSystemCallbacks(
    private val projectRoot: String? = null
) : FileSystemCallbacks {

    override fun readFile(path: String): FileReadResult {
        val file = resolvePath(path)

        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $path")
        }
        if (!file.isFile) {
            throw IllegalArgumentException("Path is not a file: $path")
        }

        val content = file.readText()
        return FileReadResult(
            content = content,
            path = toRelativePath(file.absolutePath),
            size = file.length(),
            lines = content.lines().size
        )
    }

    override fun writeFile(request: FileWriteRequest): FileWriteResult {
        val file = resolvePath(request.path)

        if (file.exists() && !request.overwrite) {
            throw IllegalStateException("File already exists: ${toRelativePath(file.absolutePath)}")
        }

        if (request.createDirs) {
            file.parentFile?.mkdirs()
        }

        val existed = file.exists()
        file.writeText(request.content)

        return FileWriteResult(
            path = toRelativePath(file.absolutePath),
            size = file.length(),
            created = !existed
        )
    }

    override fun listFiles(request: ListFilesRequest): ListFilesResult {
        val dir = resolvePath(request.path)

        if (!dir.exists()) {
            throw IllegalArgumentException("Directory not found: ${request.path}")
        }
        if (!dir.isDirectory) {
            throw IllegalArgumentException("Path is not a directory: ${request.path}")
        }

        val files = if (request.recursive) {
            dir.walkTopDown()
                .maxDepth(request.maxDepth)
                .filter { it.isFile }
        } else {
            dir.listFiles()?.asSequence()?.filter { it.isFile } ?: emptySequence()
        }

        val filteredFiles = files
            .filter { request.includeHidden || !it.isHidden }
            .filter { file ->
                if (request.pattern.isNullOrBlank()) {
                    true
                } else {
                    val regex = request.pattern.replace("*", ".*").toRegex()
                    regex.matches(file.name)
                }
            }

        val fileEntries = filteredFiles.map { file ->
            FileEntry(
                name = file.name,
                path = toRelativePath(file.absolutePath),
                relativePath = file.relativeTo(dir).path,
                isDirectory = file.isDirectory,
                size = file.length(),
                lastModified = file.lastModified()
            )
        }.toList()

        return ListFilesResult(
            files = fileEntries,
            totalCount = fileEntries.size,
            path = toRelativePath(dir.absolutePath)
        )
    }

    override fun deleteFile(request: DeleteFileRequest): FileOperationResult {
        val file = resolvePath(request.path)

        if (!file.exists()) {
            return FileOperationResult(
                success = false,
                message = "File not found: ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        }

        if (file.isDirectory && !request.recursive) {
            return FileOperationResult(
                success = false,
                message = "Recursive flag required to delete directory: ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        }

        return try {
            val deleted = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }

            if (!deleted) {
                return FileOperationResult(
                    success = false,
                    message = "Failed to delete: ${toRelativePath(file.absolutePath)}",
                    path = toRelativePath(file.absolutePath)
                )
            }

            FileOperationResult(
                success = true,
                message = "Deleted: ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to delete: ${e.message}",
                path = toRelativePath(file.absolutePath)
            )
        }
    }

    override fun createDirectory(request: CreateDirectoryRequest): FileOperationResult {
        val dir = resolvePath(request.path)

        val success = if (request.createParents) {
            dir.mkdirs()
        } else {
            dir.mkdir()
        }

        return if (success || dir.exists()) {
            FileOperationResult(
                success = true,
                message = "Directory created: ${toRelativePath(dir.absolutePath)}",
                path = toRelativePath(dir.absolutePath)
            )
        } else {
            FileOperationResult(
                success = false,
                message = "Failed to create directory",
                path = toRelativePath(dir.absolutePath)
            )
        }
    }

    override fun moveFile(request: MoveFileRequest): FileOperationResult {
        val sourceFile = resolvePath(request.source)
        val destFile = resolvePath(request.destination)

        if (!sourceFile.exists()) {
            return FileOperationResult(
                success = false,
                message = "Source file not found: ${request.source}"
            )
        }

        if (destFile.exists() && !request.overwrite) {
            return FileOperationResult(
                success = false,
                message = "Destination already exists: ${request.destination}"
            )
        }

        return try {
            val copyOption = if (request.overwrite) {
                StandardCopyOption.REPLACE_EXISTING
            } else {
                StandardCopyOption.ATOMIC_MOVE
            }
            Files.move(sourceFile.toPath(), destFile.toPath(), copyOption)

            FileOperationResult(
                success = true,
                message = "Moved: ${toRelativePath(sourceFile.absolutePath)} -> ${toRelativePath(destFile.absolutePath)}",
                path = toRelativePath(destFile.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to move: ${e.message}"
            )
        }
    }

    override fun copyFile(request: CopyFileRequest): FileOperationResult {
        val sourceFile = resolvePath(request.source)
        val destFile = resolvePath(request.destination)

        if (!sourceFile.exists()) {
            return FileOperationResult(
                success = false,
                message = "Source file not found: ${request.source}"
            )
        }

        if (destFile.exists() && !request.overwrite) {
            return FileOperationResult(
                success = false,
                message = "Destination already exists: ${request.destination}"
            )
        }

        return try {
            if (sourceFile.isDirectory) {
                sourceFile.copyRecursively(destFile, request.overwrite)
            } else {
                val copyOption = if (request.overwrite) {
                    StandardCopyOption.REPLACE_EXISTING
                } else {
                    StandardCopyOption.COPY_ATTRIBUTES
                }
                Files.copy(sourceFile.toPath(), destFile.toPath(), copyOption)
            }

            FileOperationResult(
                success = true,
                message = "Copied: ${toRelativePath(sourceFile.absolutePath)} -> ${toRelativePath(destFile.absolutePath)}",
                path = toRelativePath(destFile.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to copy: ${e.message}"
            )
        }
    }

    override fun getFileInfo(path: String): FileInfoResult {
        val file = resolvePath(path)

        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $path")
        }

        return FileInfoResult(
            path = toRelativePath(file.absolutePath),
            name = file.name,
            isFile = file.isFile,
            isDirectory = file.isDirectory,
            size = file.length(),
            lastModified = file.lastModified(),
            canRead = file.canRead(),
            canWrite = file.canWrite(),
            canExecute = file.canExecute(),
            isHidden = file.isHidden
        )
    }

    override fun replaceText(request: ReplaceTextRequest): FileOperationResult {
        val file = resolvePath(request.path)

        if (!file.exists()) {
            return FileOperationResult(
                success = false,
                message = "File not found: ${request.path}",
                path = toRelativePath(file.absolutePath)
            )
        }

        return try {
            var content = file.readText()

            // 规范化行结束符：将所有 CRLF 转换为 LF
            content = content.replace("\r\n", "\n")

            // 规范化要替换的文本
            var oldText = request.oldText.replace("\r\n", "\n")
            var newText = request.newText.replace("\r\n", "\n")

            // 执行替换
            val newContent = if (request.replaceAll) {
                content.replace(oldText, newText)
            } else {
                content.replaceFirst(oldText, newText)
            }

            if (content == newContent) {
                return FileOperationResult(
                    success = false,
                    message = "Text not found: ${request.oldText}",
                    path = toRelativePath(file.absolutePath)
                )
            }

            file.writeText(newContent)

            FileOperationResult(
                success = true,
                message = "Text replaced in ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to replace text: ${e.message}",
                path = toRelativePath(file.absolutePath)
            )
        }
    }

    override fun replaceLine(request: ReplaceLineRequest): FileOperationResult {
        val file = resolvePath(request.path)

        if (!file.exists()) {
            return FileOperationResult(
                success = false,
                message = "File not found: ${request.path}",
                path = toRelativePath(file.absolutePath)
            )
        }

        return try {
            val content = file.readText()

            // 检测原始行结束符
            val lineEnding = when {
                content.contains("\r\n") -> "\r\n"
                content.contains("\r") -> "\r"
                else -> "\n"
            }

            val lines = content.split(lineEnding).toMutableList()

            if (request.lineNumber < 1 || request.lineNumber > lines.size) {
                return FileOperationResult(
                    success = false,
                    message = "Invalid line number: ${request.lineNumber} (file has ${lines.size} lines)",
                    path = toRelativePath(file.absolutePath)
                )
            }

            lines[request.lineNumber - 1] = request.newContent
            file.writeText(lines.joinToString(lineEnding))

            FileOperationResult(
                success = true,
                message = "Replaced line ${request.lineNumber} in ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to replace line: ${e.message}",
                path = toRelativePath(file.absolutePath)
            )
        }
    }

    override fun insertLine(request: InsertLineRequest): FileOperationResult {
        val file = resolvePath(request.path)

        if (!file.exists()) {
            return FileOperationResult(
                success = false,
                message = "File not found: ${request.path}",
                path = toRelativePath(file.absolutePath)
            )
        }

        return try {
            val content = file.readText()

            // 检测原始行结束符
            val lineEnding = when {
                content.contains("\r\n") -> "\r\n"
                content.contains("\r") -> "\r"
                else -> "\n"
            }

            val lines = content.split(lineEnding).toMutableList()

            if (request.lineNumber < 1 || request.lineNumber > lines.size + 1) {
                return FileOperationResult(
                    success = false,
                    message = "Invalid line number: ${request.lineNumber} (file has ${lines.size} lines)",
                    path = toRelativePath(file.absolutePath)
                )
            }

            val insertIndex = when (request.position) {
                InsertPosition.BEFORE -> request.lineNumber - 1
                InsertPosition.AFTER -> request.lineNumber
            }
            lines.add(insertIndex, request.content)
            file.writeText(lines.joinToString(lineEnding))

            FileOperationResult(
                success = true,
                message = "Inserted line ${request.position.name.lowercase()} line ${request.lineNumber} in ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to insert line: ${e.message}",
                path = toRelativePath(file.absolutePath)
            )
        }
    }

    override fun deleteLines(request: DeleteLinesRequest): FileOperationResult {
        val file = resolvePath(request.path)

        if (!file.exists()) {
            return FileOperationResult(
                success = false,
                message = "File not found: ${request.path}",
                path = toRelativePath(file.absolutePath)
            )
        }

        return try {
            val content = file.readText()

            // 检测原始行结束符
            val lineEnding = when {
                content.contains("\r\n") -> "\r\n"
                content.contains("\r") -> "\r"
                else -> "\n"
            }

            val lines = content.split(lineEnding).toMutableList()

            if (request.startLine < 1 || request.startLine > lines.size) {
                return FileOperationResult(
                    success = false,
                    message = "Invalid start line: ${request.startLine} (file has ${lines.size} lines)",
                    path = toRelativePath(file.absolutePath)
                )
            }

            if (request.endLine < request.startLine || request.endLine > lines.size) {
                return FileOperationResult(
                    success = false,
                    message = "Invalid end line: ${request.endLine} (file has ${lines.size} lines)",
                    path = toRelativePath(file.absolutePath)
                )
            }

            val deleteCount = request.endLine - request.startLine + 1
            repeat(deleteCount) {
                lines.removeAt(request.startLine - 1)
            }
            file.writeText(lines.joinToString(lineEnding))

            val lineRange = if (request.startLine == request.endLine) {
                "line ${request.startLine}"
            } else {
                "lines ${request.startLine}-${request.endLine}"
            }

            FileOperationResult(
                success = true,
                message = "Deleted $lineRange in ${toRelativePath(file.absolutePath)}",
                path = toRelativePath(file.absolutePath)
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            FileOperationResult(
                success = false,
                message = "Failed to delete lines: ${e.message}",
                path = toRelativePath(file.absolutePath)
            )
        }
    }

    override fun resolvePath(path: String): File {
        val rootPath = projectRoot ?: return resolvePathWithoutProjectRoot(path)
        val root = File(rootPath).canonicalFile
        val file = File(path)
        val target = if (file.isAbsolute) {
            file.canonicalFile
        } else {
            File(root, path).canonicalFile
        }
        val rootCanonicalPath = root.toPath()
        val targetCanonicalPath = target.toPath()
        if (targetCanonicalPath != rootCanonicalPath && !targetCanonicalPath.startsWith(rootCanonicalPath)) {
            throw IllegalArgumentException("Path is outside project root: $path")
        }
        return target
    }

    override fun toRelativePath(absolutePath: String): String {
        val rootPath = projectRoot ?: return absolutePath
        return runCatching {
            val root = File(rootPath).canonicalFile
            val target = File(absolutePath).canonicalFile
            val rootCanonicalPath = root.toPath()
            val targetCanonicalPath = target.toPath()
            when {
                targetCanonicalPath == rootCanonicalPath -> "."
                targetCanonicalPath.startsWith(rootCanonicalPath) -> target.relativeTo(root).path
                else -> absolutePath
            }
        }.getOrElse {
            absolutePath
        }
    }

    private fun resolvePathWithoutProjectRoot(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(".", path)
    }
}

package com.wuxianggujun.tinaide.ai.tools.executor.filesystem

import java.io.File

/**
 * 文件系统工具执行器的回调接口
 */
interface FileSystemCallbacks {
    /**
     * 读取文件内容
     */
    fun readFile(path: String): FileReadResult

    /**
     * 写入文件内容
     */
    fun writeFile(request: FileWriteRequest): FileWriteResult

    /**
     * 列出目录内容
     */
    fun listFiles(request: ListFilesRequest): ListFilesResult

    /**
     * 删除文件或目录
     */
    fun deleteFile(request: DeleteFileRequest): FileOperationResult

    /**
     * 创建目录
     */
    fun createDirectory(request: CreateDirectoryRequest): FileOperationResult

    /**
     * 移动/重命名文件
     */
    fun moveFile(request: MoveFileRequest): FileOperationResult

    /**
     * 复制文件
     */
    fun copyFile(request: CopyFileRequest): FileOperationResult

    /**
     * 获取文件信息
     */
    fun getFileInfo(path: String): FileInfoResult

    /**
     * 替换文件中的文本
     */
    fun replaceText(request: ReplaceTextRequest): FileOperationResult

    /**
     * 替换指定行的内容
     */
    fun replaceLine(request: ReplaceLineRequest): FileOperationResult

    /**
     * 在指定行插入内容
     */
    fun insertLine(request: InsertLineRequest): FileOperationResult

    /**
     * 删除指定行或行范围
     */
    fun deleteLines(request: DeleteLinesRequest): FileOperationResult

    /**
     * 解析文件路径（相对路径转绝对路径）
     */
    fun resolvePath(path: String): File

    fun toRelativePath(absolutePath: String): String
}

/**
 * 文件读取结果
 */
data class FileReadResult(
    val content: String,
    val path: String,
    val size: Long,
    val lines: Int,
    val encoding: String = "UTF-8"
)

/**
 * 文件写入请求
 */
data class FileWriteRequest(
    val path: String,
    val content: String,
    val createDirs: Boolean = true,
    val overwrite: Boolean = true
)

/**
 * 文件写入结果
 */
data class FileWriteResult(
    val path: String,
    val size: Long,
    val created: Boolean
)

/**
 * 列出文件请求
 */
data class ListFilesRequest(
    val path: String = ".",
    val recursive: Boolean = false,
    val pattern: String? = null,
    val includeHidden: Boolean = false,
    val maxDepth: Int = Int.MAX_VALUE
)

/**
 * 列出文件结果
 */
data class ListFilesResult(
    val files: List<FileEntry>,
    val totalCount: Int,
    val path: String
)

/**
 * 文件条目
 */
data class FileEntry(
    val name: String,
    val path: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * 删除文件请求
 */
data class DeleteFileRequest(
    val path: String,
    val recursive: Boolean = false
)

/**
 * 创建目录请求
 */
data class CreateDirectoryRequest(
    val path: String,
    val createParents: Boolean = true
)

/**
 * 移动文件请求
 */
data class MoveFileRequest(
    val source: String,
    val destination: String,
    val overwrite: Boolean = false
)

/**
 * 复制文件请求
 */
data class CopyFileRequest(
    val source: String,
    val destination: String,
    val overwrite: Boolean = false
)

/**
 * 文件操作结果
 */
data class FileOperationResult(
    val success: Boolean,
    val message: String,
    val path: String? = null
)

/**
 * 文件信息结果
 */
data class FileInfoResult(
    val path: String,
    val name: String,
    val isFile: Boolean,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canExecute: Boolean,
    val isHidden: Boolean
)

/**
 * 替换文本请求
 */
data class ReplaceTextRequest(
    val path: String,
    val oldText: String,
    val newText: String,
    val replaceAll: Boolean = false
)

/**
 * 替换行请求
 */
data class ReplaceLineRequest(
    val path: String,
    val lineNumber: Int,
    val newContent: String
)

/**
 * 插入行请求
 */
data class InsertLineRequest(
    val path: String,
    val lineNumber: Int,
    val content: String,
    val position: InsertPosition = InsertPosition.AFTER
)

/**
 * 插入位置
 */
enum class InsertPosition {
    BEFORE,
    AFTER
}

/**
 * 删除行请求
 */
data class DeleteLinesRequest(
    val path: String,
    val startLine: Int,
    val endLine: Int = startLine
)

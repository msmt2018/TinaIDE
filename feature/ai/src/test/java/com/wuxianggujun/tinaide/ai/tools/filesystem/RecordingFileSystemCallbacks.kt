package com.wuxianggujun.tinaide.ai.tools.filesystem

import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.CopyFileRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.CreateDirectoryRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.DeleteFileRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.DeleteLinesRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileEntry
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileInfoResult
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileOperationResult
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileReadResult
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileSystemCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileWriteRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.FileWriteResult
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.InsertLineRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.ListFilesRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.ListFilesResult
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.MoveFileRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.ReplaceLineRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.ReplaceTextRequest
import java.io.File

internal class RecordingFileSystemCallbacks(
    private val readResult: FileReadResult = FileReadResult(
        content = "hello\nworld",
        path = "src/main.cpp",
        size = 11,
        lines = 2
    ),
    private val writeResult: FileWriteResult = FileWriteResult(
        path = "src/main.cpp",
        size = 11,
        created = true
    ),
    private val listResult: ListFilesResult = ListFilesResult(
        files = listOf(
            FileEntry(
                name = "main.cpp",
                path = "src/main.cpp",
                relativePath = "src/main.cpp",
                isDirectory = false,
                size = 11,
                lastModified = 1000L
            )
        ),
        totalCount = 1,
        path = "src"
    ),
    private val fileInfoResult: FileInfoResult = FileInfoResult(
        path = "src/main.cpp",
        name = "main.cpp",
        isFile = true,
        isDirectory = false,
        size = 11,
        lastModified = 1000L,
        canRead = true,
        canWrite = true,
        canExecute = false,
        isHidden = false
    ),
    private val operationResult: FileOperationResult = FileOperationResult(
        success = true,
        message = "operation ok",
        path = "src/main.cpp"
    ),
    private val operationError: RuntimeException? = null
) : FileSystemCallbacks {
    var lastReadPath: String? = null
        private set
    var lastWriteRequest: FileWriteRequest? = null
        private set
    var lastListFilesRequest: ListFilesRequest? = null
        private set
    var lastDeleteFileRequest: DeleteFileRequest? = null
        private set
    var lastCreateDirectoryRequest: CreateDirectoryRequest? = null
        private set
    var lastMoveFileRequest: MoveFileRequest? = null
        private set
    var lastCopyFileRequest: CopyFileRequest? = null
        private set
    var lastFileInfoPath: String? = null
        private set
    var lastReplaceTextRequest: ReplaceTextRequest? = null
        private set
    var lastReplaceLineRequest: ReplaceLineRequest? = null
        private set
    var lastInsertLineRequest: InsertLineRequest? = null
        private set
    var lastDeleteLinesRequest: DeleteLinesRequest? = null
        private set

    override fun readFile(path: String): FileReadResult {
        lastReadPath = path
        return readResult.copy(path = path)
    }

    override fun writeFile(request: FileWriteRequest): FileWriteResult {
        lastWriteRequest = request
        return writeResult.copy(path = request.path, size = request.content.length.toLong())
    }

    override fun listFiles(request: ListFilesRequest): ListFilesResult {
        lastListFilesRequest = request
        return listResult.copy(path = request.path)
    }

    override fun deleteFile(request: DeleteFileRequest): FileOperationResult {
        operationError?.let { throw it }
        lastDeleteFileRequest = request
        return operationResult.copy(path = request.path)
    }

    override fun createDirectory(request: CreateDirectoryRequest): FileOperationResult {
        lastCreateDirectoryRequest = request
        return operationResult.copy(path = request.path)
    }

    override fun moveFile(request: MoveFileRequest): FileOperationResult {
        lastMoveFileRequest = request
        return operationResult.copy(path = request.destination)
    }

    override fun copyFile(request: CopyFileRequest): FileOperationResult {
        lastCopyFileRequest = request
        return operationResult.copy(path = request.destination)
    }

    override fun getFileInfo(path: String): FileInfoResult {
        lastFileInfoPath = path
        return fileInfoResult.copy(path = path)
    }

    override fun replaceText(request: ReplaceTextRequest): FileOperationResult {
        lastReplaceTextRequest = request
        return operationResult.copy(path = request.path)
    }

    override fun replaceLine(request: ReplaceLineRequest): FileOperationResult {
        lastReplaceLineRequest = request
        return operationResult.copy(path = request.path)
    }

    override fun insertLine(request: InsertLineRequest): FileOperationResult {
        operationError?.let { throw it }
        lastInsertLineRequest = request
        return operationResult.copy(path = request.path)
    }

    override fun deleteLines(request: DeleteLinesRequest): FileOperationResult {
        operationError?.let { throw it }
        lastDeleteLinesRequest = request
        return operationResult.copy(path = request.path)
    }

    override fun resolvePath(path: String): File = File(path)

    override fun toRelativePath(absolutePath: String): String = absolutePath
}

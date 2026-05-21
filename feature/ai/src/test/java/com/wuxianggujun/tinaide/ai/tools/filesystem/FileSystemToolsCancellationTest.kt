package com.wuxianggujun.tinaide.ai.tools.filesystem

import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.assertCancellationRethrown
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.CopyFileRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.CreateDirectoryRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.DeleteFileRequest
import com.wuxianggujun.tinaide.ai.tools.executor.filesystem.DeleteLinesRequest
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
import com.wuxianggujun.tinaide.ai.tools.toolCall
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FileSystemToolsCancellationTest {

    @Test
    fun `file system tools rethrow cancellation exception`(): Unit = runBlocking {
        val context = ToolExecutionContext(additionalData = mapOf("fileSystemCallbacks" to cancellingFileSystemCallbacks()))

        assertCancellationRethrown {
            ReadFileTool.execute(toolCall(ReadFileTool.name, """{"path":"a.cpp"}"""), context)
        }
        assertCancellationRethrown {
            WriteFileTool.execute(toolCall(WriteFileTool.name, """{"path":"a.cpp","content":"x"}"""), context)
        }
        assertCancellationRethrown {
            ListFilesTool.execute(toolCall(ListFilesTool.name), context)
        }
        assertCancellationRethrown {
            DeleteFileTool.execute(toolCall(DeleteFileTool.name, """{"path":"a.cpp"}"""), context)
        }
        assertCancellationRethrown {
            CreateDirectoryTool.execute(toolCall(CreateDirectoryTool.name, """{"path":"generated"}"""), context)
        }
        assertCancellationRethrown {
            MoveFileTool.execute(toolCall(MoveFileTool.name, """{"source":"a.cpp","destination":"b.cpp"}"""), context)
        }
        assertCancellationRethrown {
            CopyFileTool.execute(toolCall(CopyFileTool.name, """{"source":"a.cpp","destination":"b.cpp"}"""), context)
        }
        assertCancellationRethrown {
            GetFileInfoTool.execute(toolCall(GetFileInfoTool.name, """{"path":"a.cpp"}"""), context)
        }
        assertCancellationRethrown {
            ReplaceTextTool.execute(
                toolCall(ReplaceTextTool.name, """{"path":"a.cpp","old_text":"a","new_text":"b"}"""),
                context
            )
        }
        assertCancellationRethrown {
            ReplaceLineTool.execute(
                toolCall(ReplaceLineTool.name, """{"path":"a.cpp","line_number":1,"new_content":"b"}"""),
                context
            )
        }
        assertCancellationRethrown {
            InsertLineTool.execute(
                toolCall(InsertLineTool.name, """{"path":"a.cpp","line_number":1,"content":"b"}"""),
                context
            )
        }
        assertCancellationRethrown {
            DeleteLinesTool.execute(toolCall(DeleteLinesTool.name, """{"path":"a.cpp","start_line":1}"""), context)
        }
    }

    private fun cancellingFileSystemCallbacks(): FileSystemCallbacks = object : FileSystemCallbacks {
        override fun readFile(path: String): FileReadResult = throw CancellationException("cancelled")

        override fun writeFile(request: FileWriteRequest): FileWriteResult = throw CancellationException("cancelled")

        override fun listFiles(request: ListFilesRequest): ListFilesResult = throw CancellationException("cancelled")

        override fun deleteFile(request: DeleteFileRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun createDirectory(request: CreateDirectoryRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun moveFile(request: MoveFileRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun copyFile(request: CopyFileRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun getFileInfo(path: String): FileInfoResult = throw CancellationException("cancelled")

        override fun replaceText(request: ReplaceTextRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun replaceLine(request: ReplaceLineRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun insertLine(request: InsertLineRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun deleteLines(request: DeleteLinesRequest): FileOperationResult = throw CancellationException("cancelled")

        override fun resolvePath(path: String): File = throw CancellationException("cancelled")

        override fun toRelativePath(absolutePath: String): String = throw CancellationException("cancelled")
    }
}

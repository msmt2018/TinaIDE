package com.wuxianggujun.tinaide.ai.tools.executor.filesystem

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.assertFailsWith
import org.junit.Test

class DefaultFileSystemCallbacksTest {

    @Test
    fun `write read list and info use project relative paths`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)

        val write = callbacks.writeFile(
            FileWriteRequest(path = "src/main.cpp", content = "int main() {}", createDirs = true)
        )
        val read = callbacks.readFile("src/main.cpp")
        val list = callbacks.listFiles(ListFilesRequest(path = "src", pattern = "*.cpp"))
        val info = callbacks.getFileInfo("src/main.cpp")

        assertThat(write.created).isTrue()
        assertThat(write.path).isEqualTo("src${File.separator}main.cpp")
        assertThat(read.content).isEqualTo("int main() {}")
        assertThat(list.files.map { it.name }).containsExactly("main.cpp")
        assertThat(info.isFile).isTrue()
        assertThat(info.name).isEqualTo("main.cpp")
    }

    @Test
    fun `copy move create and delete return operation results`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.createDirectory(CreateDirectoryRequest("src"))
        callbacks.writeFile(FileWriteRequest(path = "src/a.cpp", content = "a"))

        val copy = callbacks.copyFile(CopyFileRequest("src/a.cpp", "src/b.cpp", overwrite = false))
        val move = callbacks.moveFile(MoveFileRequest("src/b.cpp", "src/c.cpp", overwrite = true))
        val delete = callbacks.deleteFile(DeleteFileRequest("src/c.cpp"))

        assertThat(copy.success).isTrue()
        assertThat(move.success).isTrue()
        assertThat(delete.success).isTrue()
        assertThat(File(root, "src/a.cpp").isFile).isTrue()
        assertThat(File(root, "src/b.cpp").exists()).isFalse()
        assertThat(File(root, "src/c.cpp").exists()).isFalse()
    }

    @Test
    fun `text replacement and line edits mutate file content`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "notes.txt", content = "one\ntwo\nthree"))

        assertThat(callbacks.replaceText(ReplaceTextRequest("notes.txt", "two", "2")).success).isTrue()
        assertThat(callbacks.replaceLine(ReplaceLineRequest("notes.txt", 1, "ONE")).success).isTrue()
        assertThat(callbacks.insertLine(InsertLineRequest("notes.txt", 2, "inserted", InsertPosition.AFTER)).success).isTrue()
        assertThat(callbacks.deleteLines(DeleteLinesRequest("notes.txt", 4)).success).isTrue()

        assertThat(File(root, "notes.txt").readText()).isEqualTo("ONE\n2\ninserted")
    }

    @Test
    fun `default file operations report invalid paths and line ranges`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "notes.txt", content = "one\ntwo"))

        assertThat(callbacks.deleteFile(DeleteFileRequest("missing.txt")).success).isFalse()
        assertThat(callbacks.copyFile(CopyFileRequest("missing.txt", "copy.txt")).success).isFalse()
        assertThat(callbacks.replaceText(ReplaceTextRequest("notes.txt", "absent", "x")).success).isFalse()
        assertThat(callbacks.replaceLine(ReplaceLineRequest("notes.txt", 9, "x")).success).isFalse()
        assertThat(callbacks.insertLine(InsertLineRequest("notes.txt", 9, "x")).success).isFalse()
        assertThat(callbacks.deleteLines(DeleteLinesRequest("notes.txt", 2, 9)).success).isFalse()
    }

    @Test
    fun `write file respects overwrite flag and reports existing file updates`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "notes.txt", content = "first"))

        val error = assertFailsWith<IllegalStateException> {
            callbacks.writeFile(FileWriteRequest(path = "notes.txt", content = "blocked", overwrite = false))
        }
        val update = callbacks.writeFile(FileWriteRequest(path = "notes.txt", content = "second", overwrite = true))

        assertThat(error.message).contains("File already exists")
        assertThat(update.created).isFalse()
        assertThat(File(root, "notes.txt").readText()).isEqualTo("second")
    }

    @Test
    fun `file operations reject paths outside project root`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        val outsideDir = File("${root.absolutePath}-outside").apply { mkdirs() }
        try {
            val outsideFile = File(outsideDir, "secret.txt").apply { writeText("secret") }

            assertFailsWith<IllegalArgumentException> {
                callbacks.readFile(outsideFile.absolutePath)
            }
            assertFailsWith<IllegalArgumentException> {
                callbacks.writeFile(FileWriteRequest(path = outsideFile.absolutePath, content = "changed", overwrite = true))
            }
            assertThat(callbacks.toRelativePath(outsideFile.absolutePath)).isEqualTo(outsideFile.absolutePath)
            assertThat(outsideFile.readText()).isEqualTo("secret")
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `delete non empty directory requires recursive flag`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "nested/file.txt", content = "content"))

        val nonRecursive = callbacks.deleteFile(DeleteFileRequest("nested", recursive = false))

        assertThat(nonRecursive.success).isFalse()
        assertThat(File(root, "nested").exists()).isTrue()

        val recursive = callbacks.deleteFile(DeleteFileRequest("nested", recursive = true))

        assertThat(recursive.success).isTrue()
        assertThat(File(root, "nested").exists()).isFalse()
    }

    @Test
    fun `create directory without parents only succeeds when parent exists`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)

        val missingParent = callbacks.createDirectory(CreateDirectoryRequest("missing/child", createParents = false))
        callbacks.createDirectory(CreateDirectoryRequest("existing", createParents = true))
        val existingParent = callbacks.createDirectory(CreateDirectoryRequest("existing/child", createParents = false))
        val existingAgain = callbacks.createDirectory(CreateDirectoryRequest("existing/child", createParents = false))

        assertThat(missingParent.success).isFalse()
        assertThat(existingParent.success).isTrue()
        assertThat(existingAgain.success).isTrue()
        assertThat(File(root, "existing/child").isDirectory).isTrue()
    }

    @Test
    fun `write without parent creation fails when parent is missing`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)

        assertFailsWith<java.io.FileNotFoundException> {
            callbacks.writeFile(
                FileWriteRequest(
                    path = "missing/file.txt",
                    content = "content",
                    createDirs = false,
                )
            )
        }
    }

    @Test
    fun `copy and move protect existing destination unless overwrite is true`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "source.txt", content = "source"))
        callbacks.writeFile(FileWriteRequest(path = "dest.txt", content = "dest"))

        val blockedCopy = callbacks.copyFile(CopyFileRequest("source.txt", "dest.txt", overwrite = false))
        val overwriteCopy = callbacks.copyFile(CopyFileRequest("source.txt", "dest.txt", overwrite = true))
        callbacks.writeFile(FileWriteRequest(path = "move-source.txt", content = "move"))
        callbacks.writeFile(FileWriteRequest(path = "move-dest.txt", content = "old"))
        val blockedMove = callbacks.moveFile(MoveFileRequest("move-source.txt", "move-dest.txt", overwrite = false))
        val overwriteMove = callbacks.moveFile(MoveFileRequest("move-source.txt", "move-dest.txt", overwrite = true))

        assertThat(blockedCopy.success).isFalse()
        assertThat(overwriteCopy.success).isTrue()
        assertThat(File(root, "dest.txt").readText()).isEqualTo("source")
        assertThat(blockedMove.success).isFalse()
        assertThat(overwriteMove.success).isTrue()
        assertThat(File(root, "move-dest.txt").readText()).isEqualTo("move")
    }

    @Test
    fun `move succeeds without overwrite when destination is new`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "move-source.txt", content = "move"))

        val moved = callbacks.moveFile(
            MoveFileRequest(
                source = "move-source.txt",
                destination = "new-dest.txt",
                overwrite = false,
            )
        )

        assertThat(moved.success).isTrue()
        assertThat(moved.path).isEqualTo("new-dest.txt")
        assertThat(File(root, "move-source.txt").exists()).isFalse()
        assertThat(File(root, "new-dest.txt").readText()).isEqualTo("move")
    }

    @Test
    fun `list files supports recursion max depth and blank pattern`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "a.txt", content = "a"))
        callbacks.writeFile(FileWriteRequest(path = "nested/b.txt", content = "b"))
        callbacks.writeFile(FileWriteRequest(path = "nested/deeper/c.txt", content = "c"))

        val shallow = callbacks.listFiles(ListFilesRequest(path = ".", recursive = true, maxDepth = 2, pattern = ""))
        val deep = callbacks.listFiles(ListFilesRequest(path = ".", recursive = true, maxDepth = 4, pattern = "*.txt"))

        assertThat(shallow.files.map { it.relativePath }).contains("a.txt")
        assertThat(shallow.files.map { it.relativePath }).doesNotContain("nested${File.separator}deeper${File.separator}c.txt")
        assertThat(deep.files.map { it.name }).containsAtLeast("a.txt", "b.txt", "c.txt")
    }

    @Test
    fun `list files filters hidden entries unless explicitly included`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "visible.txt", content = "visible"))
        val hidden = File(root, ".hidden.txt").apply {
            writeText("hidden")
            makeHiddenIfSupported()
        }

        val visibleOnly = callbacks.listFiles(ListFilesRequest(path = ".", includeHidden = false))
        val includeHidden = callbacks.listFiles(ListFilesRequest(path = ".", includeHidden = true))

        assertThat(visibleOnly.files.map { it.name }).contains("visible.txt")
        if (hidden.isHidden) {
            assertThat(visibleOnly.files.map { it.name }).doesNotContain(".hidden.txt")
        }
        assertThat(includeHidden.files.map { it.name }).contains(".hidden.txt")
    }

    @Test
    fun `replace text can replace all normalized crlf occurrences`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        File(root, "notes.txt").writeText("one\r\ntwo\r\ntwo")

        val result = callbacks.replaceText(ReplaceTextRequest("notes.txt", "two", "2", replaceAll = true))

        assertThat(result.success).isTrue()
        assertThat(File(root, "notes.txt").readText()).isEqualTo("one\n2\n2")
    }

    @Test
    fun `line edits support before insertion cr line endings and invalid end ranges`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        File(root, "classic.txt").writeText("one\rtwo\rthree")

        val replace = callbacks.replaceLine(ReplaceLineRequest("classic.txt", 2, "TWO"))
        val insert = callbacks.insertLine(InsertLineRequest("classic.txt", 1, "zero", InsertPosition.BEFORE))
        val deleteInvalid = callbacks.deleteLines(DeleteLinesRequest("classic.txt", 3, 2))
        val deleteRange = callbacks.deleteLines(DeleteLinesRequest("classic.txt", 2, 3))

        assertThat(replace.success).isTrue()
        assertThat(insert.success).isTrue()
        assertThat(deleteInvalid.success).isFalse()
        assertThat(deleteRange.success).isTrue()
        assertThat(File(root, "classic.txt").readText()).isEqualTo("zero\rthree")
    }

    @Test
    fun `line edits reject line numbers below one`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "notes.txt", content = "one\ntwo"))

        val replaceLine = callbacks.replaceLine(ReplaceLineRequest("notes.txt", 0, "x"))
        val insertLine = callbacks.insertLine(InsertLineRequest("notes.txt", 0, "x"))
        val deleteLines = callbacks.deleteLines(DeleteLinesRequest("notes.txt", 0))

        assertThat(replaceLine.success).isFalse()
        assertThat(replaceLine.message).contains("Invalid line number: 0")
        assertThat(insertLine.success).isFalse()
        assertThat(insertLine.message).contains("Invalid line number: 0")
        assertThat(deleteLines.success).isFalse()
        assertThat(deleteLines.message).contains("Invalid start line: 0")
    }

    @Test
    fun `read list and info reject missing files and wrong path types`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "file.txt", content = "content"))
        callbacks.createDirectory(CreateDirectoryRequest("dir"))

        assertFailsWith<IllegalArgumentException> {
            callbacks.readFile("missing.txt")
        }
        assertFailsWith<IllegalArgumentException> {
            callbacks.readFile("dir")
        }
        assertFailsWith<IllegalArgumentException> {
            callbacks.listFiles(ListFilesRequest(path = "missing"))
        }
        assertFailsWith<IllegalArgumentException> {
            callbacks.listFiles(ListFilesRequest(path = "file.txt"))
        }
        assertFailsWith<IllegalArgumentException> {
            callbacks.getFileInfo("missing.txt")
        }
    }

    @Test
    fun `file info supports directories and project root relative path`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.createDirectory(CreateDirectoryRequest("dir"))

        val rootInfo = callbacks.getFileInfo(".")
        val dirInfo = callbacks.getFileInfo("dir")

        assertThat(rootInfo.path).isEqualTo(".")
        assertThat(rootInfo.isDirectory).isTrue()
        assertThat(rootInfo.isFile).isFalse()
        assertThat(dirInfo.path).isEqualTo("dir")
        assertThat(dirInfo.isDirectory).isTrue()
        assertThat(dirInfo.isFile).isFalse()
    }

    @Test
    fun `relative path returns original input when canonicalization fails`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        val invalidPath = "\u0000"

        val relativePath = callbacks.toRelativePath(invalidPath)

        assertThat(relativePath).isEqualTo(invalidPath)
    }

    @Test
    fun `operations report missing sources failed destinations and directory copies`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.writeFile(FileWriteRequest(path = "source.txt", content = "source"))
        callbacks.writeFile(FileWriteRequest(path = "dir/file.txt", content = "nested"))

        val missingMove = callbacks.moveFile(MoveFileRequest("missing.txt", "dest.txt"))
        val failedMove = callbacks.moveFile(MoveFileRequest("source.txt", "missing-parent/dest.txt"))
        val missingCopy = callbacks.copyFile(CopyFileRequest("missing.txt", "copy.txt"))
        val failedCopy = callbacks.copyFile(CopyFileRequest("source.txt", "missing-parent/copy.txt"))
        val directoryCopy = callbacks.copyFile(CopyFileRequest("dir", "dir-copy"))

        assertThat(missingMove.success).isFalse()
        assertThat(failedMove.success).isFalse()
        assertThat(missingCopy.success).isFalse()
        assertThat(failedCopy.success).isFalse()
        assertThat(directoryCopy.success).isTrue()
        assertThat(File(root, "dir-copy/file.txt").readText()).isEqualTo("nested")
    }

    @Test
    fun `text and line edits report missing files and invalid start lines`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        File(root, "windows.txt").writeText("one\r\ntwo\r\nthree")

        val missingReplaceText = callbacks.replaceText(ReplaceTextRequest("missing.txt", "a", "b"))
        val missingReplaceLine = callbacks.replaceLine(ReplaceLineRequest("missing.txt", 1, "x"))
        val missingInsertLine = callbacks.insertLine(InsertLineRequest("missing.txt", 1, "x"))
        val missingDeleteLines = callbacks.deleteLines(DeleteLinesRequest("missing.txt", 1))
        val invalidStart = callbacks.deleteLines(DeleteLinesRequest("windows.txt", 9))
        val replaceLine = callbacks.replaceLine(ReplaceLineRequest("windows.txt", 2, "TWO"))
        val insertAfter = callbacks.insertLine(InsertLineRequest("windows.txt", 3, "after", InsertPosition.AFTER))

        assertThat(missingReplaceText.success).isFalse()
        assertThat(missingReplaceLine.success).isFalse()
        assertThat(missingInsertLine.success).isFalse()
        assertThat(missingDeleteLines.success).isFalse()
        assertThat(invalidStart.success).isFalse()
        assertThat(replaceLine.success).isTrue()
        assertThat(insertAfter.success).isTrue()
        assertThat(File(root, "windows.txt").readText()).isEqualTo("one\r\nTWO\r\nthree\r\nafter")
    }

    @Test
    fun `text and line edits report read failures for directory paths`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks(root.absolutePath)
        callbacks.createDirectory(CreateDirectoryRequest("dir"))

        val replaceText = callbacks.replaceText(ReplaceTextRequest("dir", "old", "new"))
        val replaceLine = callbacks.replaceLine(ReplaceLineRequest("dir", 1, "new"))
        val insertLine = callbacks.insertLine(InsertLineRequest("dir", 1, "new"))
        val deleteLines = callbacks.deleteLines(DeleteLinesRequest("dir", 1))

        assertThat(replaceText.success).isFalse()
        assertThat(replaceText.message).contains("Failed to replace text")
        assertThat(replaceLine.success).isFalse()
        assertThat(replaceLine.message).contains("Failed to replace line")
        assertThat(insertLine.success).isFalse()
        assertThat(insertLine.message).contains("Failed to insert line")
        assertThat(deleteLines.success).isFalse()
        assertThat(deleteLines.message).contains("Failed to delete lines")
    }

    @Test
    fun `callbacks without project root resolve absolute paths without sandboxing`() = withTempProject { root ->
        val callbacks = DefaultFileSystemCallbacks()
        val file = File(root, "absolute.txt")

        val write = callbacks.writeFile(
            FileWriteRequest(path = file.absolutePath, content = "content", createDirs = false)
        )
        val read = callbacks.readFile(file.absolutePath)
        val resolvedRelative = callbacks.resolvePath("relative.txt")

        assertThat(write.path).isEqualTo(file.absolutePath)
        assertThat(read.path).isEqualTo(file.absolutePath)
        assertThat(callbacks.toRelativePath(file.absolutePath)).isEqualTo(file.absolutePath)
        assertThat(resolvedRelative.path).contains("relative.txt")
    }

    private inline fun withTempProject(block: (File) -> Unit) {
        val root = createTempDirectory(prefix = "tina-ai-fs-callbacks-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun File.makeHiddenIfSupported() {
        runCatching {
            Files.setAttribute(toPath(), "dos:hidden", true)
        }
    }
}

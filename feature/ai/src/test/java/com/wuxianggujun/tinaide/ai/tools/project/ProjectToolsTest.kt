package com.wuxianggujun.tinaide.ai.tools.project

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.installToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.resetToolTestAppStrings
import com.wuxianggujun.tinaide.ai.tools.success
import com.wuxianggujun.tinaide.ai.tools.toolCall
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class ProjectToolsTest {
    @Before
    fun setUpStrings() {
        installToolTestAppStrings()
    }

    @After
    fun tearDownStrings() {
        resetToolTestAppStrings()
    }

    @Test
    fun `get project structure returns local tree and metadata`(): Unit = runBlocking {
        withSampleProject { root ->
            val result = GetProjectStructureTool.execute(
                toolCall(GetProjectStructureTool.name, """{"path":".","max_depth":2,"show_files":true}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            )

            val success = result.success()
            assertThat(success.content).contains("Project Structure: .")
            assertThat(success.content).contains("src/")
            assertThat(success.content).contains("Main.kt")
            assertThat(success.content).doesNotContain("ignored.kt")
            assertThat(success.metadata).containsExactly(
                "path",
                ".",
                "maxDepth",
                2,
                "showFiles",
                true
            )
        }
    }

    @Test
    fun `get project structure supports directory-only view and depth clamp`(): Unit = runBlocking {
        withRichProject { root ->
            val result = GetProjectStructureTool.execute(
                toolCall(GetProjectStructureTool.name, """{"path":".","max_depth":99,"show_files":false}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            )

            val success = result.success()
            assertThat(success.content).contains("src/")
            assertThat(success.content).contains("nested/")
            assertThat(success.content).doesNotContain("Main.kt")
            assertThat(success.content).doesNotContain("ignored.py")
            assertThat(success.metadata).containsEntry("maxDepth", 10)
            assertThat(success.metadata).containsEntry("showFiles", false)
        }
    }

    @Test
    fun `get project structure honors depth limit and formats large files`(): Unit = runBlocking {
        withTempProject { root ->
            val srcDir = File(root, "src").apply { mkdirs() }
            val nestedDir = File(srcDir, "nested").apply { mkdirs() }
            File(nestedDir, "Deep.kt").writeText("fun deep() = Unit")
            File(root, "large.bin").writeBytes(ByteArray(2 * 1024 * 1024))

            val result = GetProjectStructureTool.execute(
                toolCall(GetProjectStructureTool.name, """{"path":".","max_depth":1,"show_files":true}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            ).success()

            assertThat(result.content).contains("src/")
            assertThat(result.content).contains("large.bin (2 MB)")
            assertThat(result.content).doesNotContain("nested/")
            assertThat(result.content).doesNotContain("Deep.kt")
        }
    }

    @Test
    fun `find file supports wildcard search and metadata`(): Unit = runBlocking {
        withSampleProject { root ->
            val result = FindFileTool.execute(
                toolCall(FindFileTool.name, """{"name":"*.kt","path":"src","case_sensitive":true,"max_results":5}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            )

            val success = result.success()
            assertThat(success.content).contains("src/Main.kt")
            assertThat(success.metadata).containsExactly(
                "totalCount",
                1,
                "truncated",
                false,
                "pattern",
                "*.kt"
            )
        }
    }

    @Test
    fun `find file supports case-insensitive search no results and truncation`(): Unit = runBlocking {
        withRichProject { root ->
            val context = ToolExecutionContext(projectRoot = root.absolutePath)

            val insensitive = FindFileTool.execute(
                toolCall(FindFileTool.name, """{"name":"main.kt","path":"src","case_sensitive":false,"max_results":5}"""),
                context
            ).success()
            val empty = FindFileTool.execute(
                toolCall(FindFileTool.name, """{"name":"*.swift","path":"src"}"""),
                context
            ).success()
            val limited = FindFileTool.execute(
                toolCall(FindFileTool.name, """{"name":"*.kt","path":".","max_results":1}"""),
                context
            ).success()

            assertThat(insensitive.content).contains("src/Main.KT")
            assertThat(empty.metadata).containsEntry("totalCount", 0)
            assertThat(empty.content).contains("No files found matching the pattern.")
            assertThat(limited.metadata).containsEntry("totalCount", 1)
            assertThat(limited.metadata).containsEntry("truncated", true)
            assertThat(limited.content).contains("Results limited to 1 files")
        }
    }

    @Test
    fun `find file reports missing project root and formats kb mb matches`(): Unit = runBlocking {
        assertThat(
            FindFileTool.execute(
                toolCall(FindFileTool.name, """{"name":"*.kt"}"""),
                ToolExecutionContext()
            )
        ).isEqualTo(ToolExecutionResult.Error("Project root not set. Please initialize project context first."))

        withTempProject { root ->
            File(root, "small.kt").writeText("fun small() = Unit")
            File(root, "medium.kt").writeBytes(ByteArray(2048))
            File(root, "large.kt").writeBytes(ByteArray(2 * 1024 * 1024))

            val result = FindFileTool.execute(
                toolCall(FindFileTool.name, """{"name":"*.kt","path":".","max_results":10}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            ).success()

            assertThat(result.content).contains("small.kt")
            assertThat(result.content).contains("medium.kt (2 KB)")
            assertThat(result.content).contains("large.kt (2 MB)")
            assertThat(result.metadata["totalCount"]).isEqualTo(3)
        }
    }

    @Test
    fun `count code lines reports code file statistics`(): Unit = runBlocking {
        withSampleProject { root ->
            val result = CountCodeLinesTool.execute(
                toolCall(CountCodeLinesTool.name, """{"path":"src"}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            )

            val success = result.success()
            assertThat(success.content).contains("Code Statistics for: src")
            assertThat(success.content).contains(".kt")
            assertThat(success.metadata).containsEntry("fileCount", 1)
            assertThat(success.metadata).containsEntry("path", "src")
        }
    }

    @Test
    fun `count code lines handles comments blank lines multiple extensions and empty directories`(): Unit = runBlocking {
        withRichProject { root ->
            val context = ToolExecutionContext(projectRoot = root.absolutePath)

            val stats = CountCodeLinesTool.execute(
                toolCall(CountCodeLinesTool.name, """{"path":"src"}"""),
                context
            ).success()
            val empty = CountCodeLinesTool.execute(
                toolCall(CountCodeLinesTool.name, """{"path":"empty"}"""),
                context
            ).success()

            assertThat(stats.content).contains(".kt")
            assertThat(stats.content).contains(".py")
            assertThat(stats.content).contains(".sh")
            assertThat(stats.content).contains("Comments:")
            assertThat(stats.content).contains("Blank:")
            assertThat(stats.metadata["fileCount"]).isEqualTo(4)
            assertThat(empty.metadata["fileCount"]).isEqualTo(0)
            assertThat(empty.content).contains("No code files found.")
        }
    }

    @Test
    fun `count code lines reports missing root and zero-line code file percentages`(): Unit = runBlocking {
        assertThat(
            CountCodeLinesTool.execute(
                toolCall(CountCodeLinesTool.name),
                ToolExecutionContext()
            )
        ).isEqualTo(ToolExecutionResult.Error("Project root not set. Please initialize project context first."))

        withTempProject { root ->
            File(root, "empty.kt").writeText("")

            val result = CountCodeLinesTool.execute(
                toolCall(CountCodeLinesTool.name, """{"path":"."}"""),
                ToolExecutionContext(projectRoot = root.absolutePath)
            ).success()

            assertThat(result.metadata["fileCount"]).isEqualTo(1)
            assertThat(result.metadata["totalLines"]).isEqualTo(0)
            assertThat(result.content).contains(".kt (0%)")
        }
    }

    @Test
    fun `project tools return clear errors for missing context and invalid input`(): Unit = runBlocking {
        assertThat(GetProjectStructureTool.execute(toolCall(GetProjectStructureTool.name), ToolExecutionContext()))
            .isEqualTo(ToolExecutionResult.Error("Project root not set. Please initialize project context first."))
        assertThat(FindFileTool.execute(toolCall(FindFileTool.name, """{"name":" "}"""), ToolExecutionContext(projectRoot = ".")))
            .isEqualTo(ToolExecutionResult.Error("File name pattern is required"))
        assertThat(CountCodeLinesTool.execute(toolCall(CountCodeLinesTool.name, """{"path":"definitely-missing-ai-test-dir"}"""), ToolExecutionContext(projectRoot = ".")))
            .isEqualTo(ToolExecutionResult.Error("Directory not found: definitely-missing-ai-test-dir"))
    }

    @Test
    fun `project tools reject paths outside project root`(): Unit = runBlocking {
        withSampleProject { root ->
            val context = ToolExecutionContext(projectRoot = root.absolutePath)

            assertThat(
                GetProjectStructureTool.execute(
                    toolCall(GetProjectStructureTool.name, """{"path":".."}"""),
                    context
                )
            ).isEqualTo(ToolExecutionResult.Error("Path .. is not in allowed range"))
            assertThat(
                FindFileTool.execute(
                    toolCall(FindFileTool.name, """{"name":"*.kt","path":".."}"""),
                    context
                )
            ).isEqualTo(ToolExecutionResult.Error("Path .. is not in allowed range"))
            assertThat(
                CountCodeLinesTool.execute(
                    toolCall(CountCodeLinesTool.name, """{"path":".."}"""),
                    context
                )
            ).isEqualTo(ToolExecutionResult.Error("Path .. is not in allowed range"))
        }
    }

    @Test
    fun `project tools reject files where directory is expected`(): Unit = runBlocking {
        withSampleProject { root ->
            val context = ToolExecutionContext(projectRoot = root.absolutePath)

            assertThat(
                GetProjectStructureTool.execute(
                    toolCall(GetProjectStructureTool.name, """{"path":"src/Main.kt"}"""),
                    context
                )
            ).isEqualTo(ToolExecutionResult.Error("Path is not a directory: src/Main.kt"))
            assertThat(
                FindFileTool.execute(
                    toolCall(FindFileTool.name, """{"name":"Main.kt","path":"src/Main.kt"}"""),
                    context
                )
            ).isEqualTo(ToolExecutionResult.Error("Path is not a directory: src/Main.kt"))
            assertThat(
                CountCodeLinesTool.execute(
                    toolCall(CountCodeLinesTool.name, """{"path":"src/Main.kt"}"""),
                    context
                )
            ).isEqualTo(ToolExecutionResult.Error("Path is not a directory: src/Main.kt"))
        }
    }

    @Test
    fun `project tools report missing directories consistently`(): Unit = runBlocking {
        withSampleProject { root ->
            val context = ToolExecutionContext(projectRoot = root.absolutePath)

            assertThat(
                GetProjectStructureTool.execute(
                    toolCall(GetProjectStructureTool.name, """{"path":"missing"}"""),
                    context
                )
            ).isInstanceOf(ToolExecutionResult.Error::class.java)
            assertThat(
                FindFileTool.execute(
                    toolCall(FindFileTool.name, """{"name":"*.kt","path":"missing"}"""),
                    context
                )
            ).isInstanceOf(ToolExecutionResult.Error::class.java)
        }
    }

    private inline fun withTempProject(
        prefix: String = "tina-ai-project-tools-custom-",
        block: (File) -> Unit
    ) {
        val root = createTempDirectory(prefix = prefix).toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private inline fun withSampleProject(block: (File) -> Unit) {
        val root = createTempDirectory(prefix = "tina-ai-project-tools-").toFile()
        try {
            val srcDir = File(root, "src").apply { mkdirs() }
            File(srcDir, "Main.kt").writeText(
                """
                fun main() {
                    println("hello")
                }
                """.trimIndent()
            )
            val buildDir = File(root, "build").apply { mkdirs() }
            File(buildDir, "ignored.kt").writeText("fun ignored() = Unit")

            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private inline fun withRichProject(block: (File) -> Unit) {
        val root = createTempDirectory(prefix = "tina-ai-project-tools-rich-").toFile()
        try {
            val srcDir = File(root, "src").apply { mkdirs() }
            val nestedDir = File(srcDir, "nested").apply { mkdirs() }
            File(srcDir, "Main.KT").writeText(
                """
                /* block
                   comment */
                fun main() {
                    // line comment

                    println("hello")
                }
                """.trimIndent()
            )
            File(srcDir, "helper.kt").writeText("fun helper() = Unit")
            File(nestedDir, "script.py").writeText(
                """
                # comment
                print("hello")
                """.trimIndent()
            )
            File(nestedDir, "run.sh").writeText(
                """
                #!/bin/sh
                echo hello
                """.trimIndent()
            )
            File(root, "empty").mkdirs()
            File(root, "build").apply { mkdirs() }
            File(root, "build/ignored.py").writeText("print('ignored')")

            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}

package com.wuxianggujun.tinaide.ai.tools.executor.code

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Before
import org.junit.Test

class EnhancedCodeAnalysisCallbacksTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory(prefix = "enhanced-code-analysis-").toFile()
        File(tempDir, "Main.kt").writeText(
            """
            fun main() {
                println("HelloSearch")
            }
            """.trimIndent()
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `search falls back to default implementation when ripgrep is unavailable`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = File(tempDir, "missing-rg").absolutePath,
        )

        val result = callbacks.searchCode(
            CodeSearchRequest(query = "hellosearch", caseSensitive = false)
        )

        assertThat(result.matches).hasSize(1)
        assertThat(result.matches.single().filePath).isEqualTo("Main.kt")
        assertThat(result.matches.single().lineContent).contains("HelloSearch")
    }

    @Test
    fun `search falls back to default implementation when ripgrep version exits non zero`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = createFailingVersionRipgrep().absolutePath,
        )

        val result = callbacks.searchCode(
            CodeSearchRequest(query = "hellosearch", caseSensitive = false)
        )

        assertThat(result.matches).hasSize(1)
        assertThat(result.matches.single().filePath).isEqualTo("Main.kt")
        assertThat(result.matches.single().lineContent).contains("HelloSearch")
    }

    @Test
    fun `find references falls back to default implementation when ripgrep is unavailable`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = File(tempDir, "missing-rg").absolutePath,
        )

        val result = callbacks.findReferences(ReferenceSearchRequest(symbolName = "HelloSearch"))

        assertThat(result.references).hasSize(1)
        assertThat(result.references.single().filePath).isEqualTo("Main.kt")
        assertThat(result.references.single().lineContent).contains("HelloSearch")
    }

    @Test
    fun `search and references use ripgrep when available`() {
        val fakeRg = createFakeRipgrep()
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = fakeRg.absolutePath,
        )

        val search = callbacks.searchCode(
            CodeSearchRequest(
                query = "HelloSearch",
                maxResults = 1,
            )
        )
        val references = callbacks.findReferences(
            ReferenceSearchRequest(symbolName = "HelloSearch")
        )

        assertThat(search.matches).hasSize(1)
        assertThat(search.matches.single().filePath).isEqualTo("Main.kt")
        assertThat(search.matches.single().matchStart).isEqualTo(4)
        assertThat(search.truncated).isTrue()
        assertThat(references.references).hasSize(2)
        assertThat(references.references.first().columnNumber).isEqualTo(4)
        assertThat(references.references.first().isDefinition).isFalse()
    }

    @Test
    fun `find references passes explicit path to ripgrep search`() {
        File(tempDir, "src").mkdirs()
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = createFakeRipgrep().absolutePath,
        )

        val references = callbacks.findReferences(
            ReferenceSearchRequest(symbolName = "HelloSearch", filePath = "src")
        )

        assertThat(references.references).hasSize(2)
        assertThat(references.references.first().lineContent).contains("HelloSearch")
    }

    @Test
    fun `search falls back when ripgrep disappears after availability check`() {
        val fakeRg = createFakeRipgrep()
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = fakeRg.absolutePath,
        )

        assertThat(callbacks.searchCode(CodeSearchRequest(query = "HelloSearch")).matches).isNotEmpty()
        assertThat(fakeRg.delete()).isTrue()

        val fallback = callbacks.searchCode(
            CodeSearchRequest(query = "hellosearch", caseSensitive = false)
        )

        assertThat(fallback.matches).hasSize(1)
        assertThat(fallback.matches.single().filePath).isEqualTo("Main.kt")
    }

    @Test
    fun `find references falls back when ripgrep disappears after availability check`() {
        val fakeRg = createFakeRipgrep()
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = fakeRg.absolutePath,
        )

        assertThat(callbacks.searchCode(CodeSearchRequest(query = "HelloSearch")).matches).isNotEmpty()
        assertThat(fakeRg.delete()).isTrue()

        val references = callbacks.findReferences(ReferenceSearchRequest(symbolName = "HelloSearch"))

        assertThat(references.references).hasSize(1)
        assertThat(references.references.single().filePath).isEqualTo("Main.kt")
    }

    @Test
    fun `symbol and outline queries delegate to default implementation`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = File(tempDir, "missing-rg").absolutePath,
        )

        val symbols = callbacks.findSymbol(SymbolSearchRequest(symbolName = "main"))
        val outline = callbacks.getCodeOutline("Main.kt")

        assertThat(symbols.symbols).isNotNull()
        assertThat(outline.filePath).isEqualTo("Main.kt")
        assertThat(outline.items).isNotNull()
    }

    @Test
    fun `ripgrep command respects case regex glob and max result options`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")
        val command = callbacks.invokePrivate<List<String>>(
            "buildRipgrepCommand",
            CodeSearchRequest(
                query = "Hello",
                path = "src",
                filePattern = "*.kt",
                caseSensitive = false,
                isRegex = false,
                maxResults = 7,
            )
        )

        assertThat(command).containsAtLeast(
            "rg-custom",
            "--line-number",
            "--column",
            "--with-filename",
            "--color=never",
            "--ignore-case",
            "--fixed-strings",
            "--glob",
            "*.kt",
            "--max-count",
            "7",
            "Hello",
            "src",
        ).inOrder()
    }

    @Test
    fun `ripgrep command uses default binary and omits optional filters`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath)
        val command = callbacks.invokePrivate<List<String>>(
            "buildRipgrepCommand",
            CodeSearchRequest(
                query = "Hello",
                path = ".",
                caseSensitive = true,
                isRegex = true,
                maxResults = 3,
            )
        )

        assertThat(command).containsExactly(
            "rg",
            "--line-number",
            "--column",
            "--with-filename",
            "--color=never",
            "--max-count",
            "3",
            "Hello",
            ".",
        ).inOrder()
    }

    @Test
    fun `ripgrep line parser preserves content containing colons`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")

        val match = callbacks.invokePrivate<CodeMatch>("parseRipgrepLine", "Main.kt:7:5:val url = \"a:b\"")

        assertThat(match.filePath).isEqualTo("Main.kt")
        assertThat(match.lineNumber).isEqualTo(7)
        assertThat(match.matchStart).isEqualTo(4)
        assertThat(match.lineContent).isEqualTo("val url = \"a:b\"")
    }

    @Test
    fun `ripgrep line parser and search skip malformed lines`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            rgPath = createMalformedOutputRipgrep().absolutePath,
        )

        val missingParts = callbacks.invokePrivate<CodeMatch?>("parseRipgrepLine", "not-enough-parts")
        val invalidNumber = callbacks.invokePrivate<CodeMatch?>("parseRipgrepLine", "Main.kt:not-number:5:bad")
        val search = callbacks.searchCode(CodeSearchRequest(query = "HelloSearch", maxResults = 10))

        assertThat(missingParts).isNull()
        assertThat(invalidNumber).isNull()
        assertThat(search.matches).hasSize(1)
        assertThat(search.matches.single().filePath).isEqualTo("Main.kt")
        assertThat(search.matches.single().lineNumber).isEqualTo(4)
        assertThat(search.matches.single().lineContent).contains("valid hit")
        assertThat(search.totalCount).isEqualTo(1)
        assertThat(search.truncated).isFalse()
    }

    @Test
    fun `relative path conversion rejects false project root prefixes`() {
        val siblingDir = File("${tempDir.absolutePath}-sibling").apply { mkdirs() }
        try {
            val siblingFile = File(siblingDir, "Outside.kt").apply { writeText("class Outside") }
            val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")

            val relativePath = callbacks.invokePrivate<String>("toRelativePath", siblingFile.absolutePath)

            assertThat(relativePath).isEqualTo(siblingFile.absolutePath)
        } finally {
            siblingDir.deleteRecursively()
        }
    }

    @Test
    fun `ripgrep search returns empty for missing project path`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")

        val result = callbacks.invokePrivate<CodeSearchResult>(
            "searchWithRipgrep",
            CodeSearchRequest(query = "Missing", path = "missing-dir")
        )

        assertThat(result.matches).isEmpty()
        assertThat(result.totalCount).isEqualTo(0)
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `ripgrep search rejects outside project paths before process launch`() {
        val siblingDir = File("${tempDir.absolutePath}-outside").apply { mkdirs() }
        try {
            File(siblingDir, "Outside.kt").writeText("class Outside")
            val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")

            val result = callbacks.invokePrivate<CodeSearchResult>(
                "searchWithRipgrep",
                CodeSearchRequest(query = "Outside", path = siblingDir.absolutePath)
            )

            assertThat(result.matches).isEmpty()
            assertThat(result.totalCount).isEqualTo(0)
            assertThat(result.truncated).isFalse()
        } finally {
            siblingDir.deleteRecursively()
        }
    }

    @Test
    fun `ripgrep path formatter returns relative child path`() {
        val srcDir = File(tempDir, "src").apply { mkdirs() }
        val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")

        val searchPath = callbacks.invokePrivate<String>("toRipgrepSearchPath", srcDir)

        assertThat(searchPath).isEqualTo("src")
    }

    @Test
    fun `ripgrep path formatter uses dot for project root`() {
        val callbacks = EnhancedCodeAnalysisCallbacks(projectRoot = tempDir.absolutePath, rgPath = "rg-custom")

        val searchPath = callbacks.invokePrivate<String>("toRipgrepSearchPath", tempDir)

        assertThat(searchPath).isEqualTo(".")
    }

    private fun createFakeRipgrep(): File {
        val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
        val fakeRg = File(tempDir, if (isWindows) "fake-rg.cmd" else "fake-rg.sh")
        val script = if (isWindows) {
            """
            @echo off
            if "%~1"=="--version" (
              echo ripgrep 14.0.0
              exit /b 0
            )
            echo Main.kt:2:5:val hit = HelloSearch
            echo Main.kt:3:1:HelloSearch again
            exit /b 0
            """.trimIndent()
        } else {
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
              echo "ripgrep 14.0.0"
              exit 0
            fi
            printf '%s\n' 'Main.kt:2:5:val hit = HelloSearch'
            printf '%s\n' 'Main.kt:3:1:HelloSearch again'
            """.trimIndent()
        }
        fakeRg.writeText(script)
        fakeRg.setExecutable(true)
        return fakeRg
    }

    private fun createFailingVersionRipgrep(): File {
        val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
        val fakeRg = File(tempDir, if (isWindows) "failing-version-rg.cmd" else "failing-version-rg.sh")
        val script = if (isWindows) {
            """
            @echo off
            if "%~1"=="--version" (
              echo broken ripgrep
              exit /b 2
            )
            exit /b 0
            """.trimIndent()
        } else {
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
              echo "broken ripgrep"
              exit 2
            fi
            exit 0
            """.trimIndent()
        }
        fakeRg.writeText(script)
        fakeRg.setExecutable(true)
        return fakeRg
    }

    private fun createMalformedOutputRipgrep(): File {
        val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
        val fakeRg = File(tempDir, if (isWindows) "malformed-rg.cmd" else "malformed-rg.sh")
        val script = if (isWindows) {
            """
            @echo off
            if "%~1"=="--version" (
              echo ripgrep 14.0.0
              exit /b 0
            )
            echo not-enough-parts
            echo Main.kt:not-number:5:bad hit
            echo Main.kt:4:2:valid hit
            exit /b 0
            """.trimIndent()
        } else {
            """
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
              echo "ripgrep 14.0.0"
              exit 0
            fi
            printf '%s\n' 'not-enough-parts'
            printf '%s\n' 'Main.kt:not-number:5:bad hit'
            printf '%s\n' 'Main.kt:4:2:valid hit'
            """.trimIndent()
        }
        fakeRg.writeText(script)
        fakeRg.setExecutable(true)
        return fakeRg
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> EnhancedCodeAnalysisCallbacks.invokePrivate(name: String, argument: Any): T {
        val method = javaClass.getDeclaredMethod(name, argument::class.java)
        method.isAccessible = true
        return method.invoke(this, argument) as T
    }
}

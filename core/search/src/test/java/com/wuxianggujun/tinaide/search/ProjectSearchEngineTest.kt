package com.wuxianggujun.tinaide.search

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ProjectSearchEngine 单元测试
 *
 * 使用临时目录模拟项目结构，验证跨文件搜索逻辑。
 */
class ProjectSearchEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var engine: ProjectSearchEngine

    @Before
    fun setUp() {
        engine = ProjectSearchEngine(tempFolder.root.absolutePath)
    }

    // ==================== 基本搜索 ====================

    @Test
    fun `search finds text in single file`() = runTest {
        tempFolder.newFile("main.c").writeText("int main() { return 0; }")

        val results = engine.search("main")
        assertThat(results).hasSize(1)
        assertThat(results[0].file.name).isEqualTo("main.c")
        assertThat(results[0].lineNumber).isEqualTo(1)
    }

    @Test
    fun `search finds text across multiple files`() = runTest {
        tempFolder.newFile("a.cpp").writeText("void hello() {}")
        tempFolder.newFile("b.cpp").writeText("void hello_world() {}")

        val results = engine.search("hello")
        assertThat(results).hasSize(2)
    }

    @Test
    fun `search returns empty for no matches`() = runTest {
        tempFolder.newFile("main.c").writeText("int main() { return 0; }")

        val results = engine.search("nonexistent")
        assertThat(results).isEmpty()
    }

    @Test
    fun `search returns empty for empty query`() = runTest {
        tempFolder.newFile("main.c").writeText("int main() {}")

        val results = engine.search("")
        assertThat(results).isEmpty()
    }

    @Test
    fun `search returns empty for non-existent directory`() = runTest {
        val engine = ProjectSearchEngine("/non/existent/path")
        val results = engine.search("test")
        assertThat(results).isEmpty()
    }

    // ==================== 大小写敏感 ====================

    @Test
    fun `search is case-insensitive by default`() = runTest {
        tempFolder.newFile("test.c").writeText("Hello World")

        val results = engine.search("hello")
        assertThat(results).hasSize(1)
    }

    @Test
    fun `search respects case-sensitive option`() = runTest {
        tempFolder.newFile("test.c").writeText("Hello World")

        val results = engine.search("hello", ProjectSearchOptions(caseSensitive = true))
        assertThat(results).isEmpty()
    }

    // ==================== 正则搜索 ====================

    @Test
    fun `regex search finds pattern`() = runTest {
        tempFolder.newFile("test.cpp").writeText("int count = 42;\nfloat value = 3.14;")

        val results = engine.search("\\d+\\.\\d+", ProjectSearchOptions(useRegex = true))
        assertThat(results).hasSize(1)
        assertThat(results[0].lineNumber).isEqualTo(2)
    }

    @Test
    fun `invalid regex returns empty results`() = runTest {
        tempFolder.newFile("test.c").writeText("test content")

        val results = engine.search("[invalid", ProjectSearchOptions(useRegex = true))
        assertThat(results).isEmpty()
    }

    // ==================== 全词匹配 ====================

    @Test
    fun `whole word match excludes partial matches`() = runTest {
        tempFolder.newFile("test.c").writeText("int counter = 0;\nint count = 1;")

        val results = engine.search("count", ProjectSearchOptions(wholeWord = true))
        assertThat(results).hasSize(1)
        assertThat(results[0].lineNumber).isEqualTo(2)
    }

    // ==================== 目录跳过 ====================

    @Test
    fun `search skips build directory`() = runTest {
        val buildDir = tempFolder.newFolder("build")
        buildDir.resolve("output.c").writeText("int found = 1;")
        tempFolder.newFile("main.c").writeText("int found = 1;")

        val results = engine.search("found")
        assertThat(results).hasSize(1)
        assertThat(results[0].file.name).isEqualTo("main.c")
    }

    @Test
    fun `search skips git directory`() = runTest {
        val gitDir = tempFolder.newFolder(".git")
        gitDir.resolve("config").writeText("searchable text")
        tempFolder.newFile("main.c").writeText("searchable text")

        val results = engine.search("searchable")
        assertThat(results).hasSize(1)
    }

    // ==================== 二进制文件跳过 ====================

    @Test
    fun `search skips binary files`() = runTest {
        tempFolder.newFile("image.png").writeText("fake png with searchable")
        tempFolder.newFile("main.c").writeText("searchable code")

        val results = engine.search("searchable")
        assertThat(results).hasSize(1)
        assertThat(results[0].file.name).isEqualTo("main.c")
    }

    // ==================== 文件扩展名过滤 ====================

    @Test
    fun `search filters by file extensions`() = runTest {
        tempFolder.newFile("main.c").writeText("hello")
        tempFolder.newFile("main.cpp").writeText("hello")
        tempFolder.newFile("main.py").writeText("hello")

        val results = engine.search("hello", ProjectSearchOptions(fileExtensions = setOf("c")))
        assertThat(results).hasSize(1)
        assertThat(results[0].file.name).isEqualTo("main.c")
    }

    // ==================== maxResults 限制 ====================

    @Test
    fun `search respects maxResults limit`() = runTest {
        tempFolder.newFile("test.c").writeText("match\nmatch\nmatch\nmatch\nmatch")

        val results = engine.search("match", ProjectSearchOptions(maxResults = 3))
        assertThat(results).hasSize(3)
    }

    // ==================== 上下文行 ====================

    @Test
    fun `search includes context lines when requested`() = runTest {
        tempFolder.newFile("test.c").writeText("line1\nline2\ntarget\nline4\nline5")

        val results = engine.search("target", ProjectSearchOptions(contextLines = 1))
        assertThat(results).hasSize(1)
        assertThat(results[0].contextBefore).containsExactly("line2")
        assertThat(results[0].contextAfter).containsExactly("line4")
    }

    // ==================== 多匹配同一行 ====================

    @Test
    fun `search finds multiple matches on same line`() = runTest {
        tempFolder.newFile("test.c").writeText("int a = 1; int b = 2;")

        val results = engine.search("int")
        assertThat(results).hasSize(2)
    }

    // ==================== groupByFile ====================

    @Test
    fun `groupByFile groups results correctly`() = runTest {
        tempFolder.newFile("a.c").writeText("hello\nhello")
        tempFolder.newFile("b.c").writeText("hello")

        val results = engine.search("hello")
        val grouped = engine.groupByFile(results)
        assertThat(grouped).hasSize(2)
    }

    // ==================== 文件大小限制 ====================

    @Test
    fun `search skips files exceeding maxFileSize`() = runTest {
        val largeContent = "x".repeat(2 * 1024 * 1024) // 2MB
        tempFolder.newFile("large.c").writeText(largeContent)
        tempFolder.newFile("small.c").writeText("findme")

        val results = engine.search("findme", ProjectSearchOptions(maxFileSize = 1024 * 1024))
        assertThat(results).hasSize(1)
        assertThat(results[0].file.name).isEqualTo("small.c")
    }
}

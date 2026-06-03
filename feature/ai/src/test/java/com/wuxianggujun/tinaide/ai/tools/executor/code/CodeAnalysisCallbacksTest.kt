package com.wuxianggujun.tinaide.ai.tools.executor.code

import com.wuxianggujun.tinaide.core.symbol.FuzzySymbolMatch
import com.wuxianggujun.tinaide.core.symbol.IProjectSymbolIndexService
import com.wuxianggujun.tinaide.core.symbol.SymbolIndexStatus
import com.wuxianggujun.tinaide.core.symbol.SymbolInfo
import com.wuxianggujun.tinaide.core.symbol.SymbolKind
import com.wuxianggujun.tinaide.core.symbol.SymbolLocation
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * CodeAnalysisCallbacks 测试
 */
class CodeAnalysisCallbacksTest {

    private lateinit var tempDir: File
    private lateinit var callbacks: CodeAnalysisCallbacks

    @Before
    fun setup() {
        tempDir = createTempDirectory(prefix = "code-analysis-test-").toFile()

        // 创建测试文件
        createTestFiles()

        // 创建回调实例
        callbacks = DefaultCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            symbolIndexService = null // 测试时不使用符号索引
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun createTestFiles() {
        // 创建 Kotlin 测试文件
        File(tempDir, "TestClass.kt").writeText(
            """
            package com.example.test

            class TestClass {
                fun testMethod() {
                    println("Hello World")
                }

                val testProperty = "test"
            }

            fun topLevelFunction() {
                val localVar = 42
            }
            """.trimIndent()
        )

        // 创建 Java 测试文件
        File(tempDir, "JavaClass.java").writeText(
            """
            package com.example.test;

            public class JavaClass {
                private String name;

                public void setName(String name) {
                    this.name = name;
                }

                public String getName() {
                    return name;
                }
            }
            """.trimIndent()
        )

        // 创建子目录
        val subDir = File(tempDir, "subdir")
        subDir.mkdir()

        File(subDir, "SubClass.kt").writeText(
            """
            package com.example.test.sub

            class SubClass {
                companion object {
                    const val CONSTANT = "constant"
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun testSearchCode_simpleText() {
        val request = CodeSearchRequest(
            query = "Hello World",
            path = ".",
            caseSensitive = false,
            isRegex = false,
            maxResults = 50
        )

        val result = callbacks.searchCode(request)

        assertTrue(result.matches.isNotEmpty(), "Should find matches")
        assertTrue(
            result.matches.any { it.lineContent.contains("Hello World") },
            "Should contain 'Hello World'"
        )
    }

    @Test
    fun testSearchCode_regex() {
        val request = CodeSearchRequest(
            query = "fun\\s+\\w+\\(\\)",
            path = ".",
            caseSensitive = true,
            isRegex = true,
            maxResults = 50
        )

        val result = callbacks.searchCode(request)

        assertTrue(result.matches.isNotEmpty(), "Should find function declarations")
    }

    @Test
    fun testSearchCode_filePattern() {
        val request = CodeSearchRequest(
            query = "class",
            path = ".",
            filePattern = "*.kt",
            caseSensitive = false,
            isRegex = false,
            maxResults = 50
        )

        val result = callbacks.searchCode(request)

        assertTrue(result.matches.isNotEmpty(), "Should find matches in Kotlin files")
        assertTrue(
            result.matches.all { it.filePath.endsWith(".kt") },
            "All matches should be from .kt files"
        )
    }

    @Test
    fun testSearchCode_maxResults() {
        val request = CodeSearchRequest(
            query = "\\w+",
            path = ".",
            caseSensitive = false,
            isRegex = true,
            maxResults = 5
        )

        val result = callbacks.searchCode(request)

        assertTrue(result.matches.size <= 5, "Should respect max results limit")
    }

    @Test
    fun testSearchCode_caseSensitive() {
        // "testMethod" 只以小写形式存在于 TestClass.kt
        val requestLower = CodeSearchRequest(
            query = "testMethod",
            path = ".",
            caseSensitive = true,
            isRegex = false,
            maxResults = 50
        )
        val resultLower = callbacks.searchCode(requestLower)
        assertTrue(resultLower.matches.isNotEmpty(), "Should find 'testMethod' with case sensitive search")

        val requestUpper = CodeSearchRequest(
            query = "TESTMETHOD",
            path = ".",
            caseSensitive = true,
            isRegex = false,
            maxResults = 50
        )
        val resultUpper = callbacks.searchCode(requestUpper)
        assertTrue(
            resultUpper.matches.isEmpty(),
            "Case sensitive search for 'TESTMETHOD' should find nothing"
        )
    }

    @Test
    fun testFindReferences() {
        val request = ReferenceSearchRequest(
            symbolName = "name",
            filePath = null,
            lineNumber = null
        )

        val result = callbacks.findReferences(request)

        assertTrue(result.references.isNotEmpty(), "Should find references to 'name'")
    }

    @Test
    fun testGetCodeOutline() {
        val testFile = File(tempDir, "TestClass.kt")

        val result = callbacks.getCodeOutline(testFile.absolutePath)

        assertNotNull(result, "Should return outline result")
        // getCodeOutline 返回相对于 projectRoot 的路径
        assertEquals("TestClass.kt", result.filePath)
        assertEquals("kotlin", result.language)
    }

    @Test
    fun testGetCodeOutline_nonExistentFile() {
        val nonExistentFile = File(tempDir, "NonExistent.kt")

        try {
            callbacks.getCodeOutline(nonExistentFile.absolutePath)
            throw AssertionError("Should throw exception for non-existent file")
        } catch (e: IllegalArgumentException) {
            // Expected
            assertTrue(e.message?.contains("not found") == true)
        }
    }

    @Test
    fun testSearchCode_invalidPathAndRegexReturnEmpty() {
        val missingPath = callbacks.searchCode(
            CodeSearchRequest(
                query = "anything",
                path = "missing-dir",
                maxResults = 10
            )
        )
        val filePath = callbacks.searchCode(
            CodeSearchRequest(
                query = "package",
                path = "TestClass.kt",
                maxResults = 10
            )
        )
        val invalidRegex = callbacks.searchCode(
            CodeSearchRequest(
                query = "[",
                isRegex = true,
                maxResults = 10
            )
        )

        assertTrue(missingPath.matches.isEmpty(), "Missing path should return no matches")
        assertEquals(0, missingPath.totalCount)
        assertEquals(false, missingPath.truncated)
        assertTrue(filePath.matches.isEmpty(), "File path should not be treated as a search root directory")
        assertTrue(invalidRegex.matches.isEmpty(), "Invalid regex should return no matches")
    }

    @Test
    fun testSearchCode_rejectsPathOutsideProjectRoot() {
        val outsideDir = createTempDirectory(prefix = "code-analysis-outside-").toFile()
        try {
            File(outsideDir, "Outside.kt").writeText("class OutsideSecret")

            val result = callbacks.searchCode(
                CodeSearchRequest(
                    query = "OutsideSecret",
                    path = outsideDir.absolutePath,
                    maxResults = 10
                )
            )

            assertTrue(result.matches.isEmpty(), "Outside project path should not be searched")
            assertEquals(0, result.totalCount)
            assertEquals(false, result.truncated)
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun testSearchCode_rejectsSiblingDirectoryWithSharedPrefix() {
        val siblingDir = File(tempDir.parentFile, "${tempDir.name}-outside").apply { mkdirs() }
        try {
            File(siblingDir, "Outside.kt").writeText("class OutsidePrefixSecret")

            val result = callbacks.searchCode(
                CodeSearchRequest(
                    query = "OutsidePrefixSecret",
                    path = siblingDir.absolutePath,
                    maxResults = 10
                )
            )

            assertTrue(result.matches.isEmpty(), "Sibling directory with shared prefix should not be searched")
            assertEquals(0, result.totalCount)
            assertEquals(false, result.truncated)
        } finally {
            siblingDir.deleteRecursively()
        }
    }

    @Test
    fun testGetCodeOutline_rejectsPathOutsideProjectRoot() {
        val outsideDir = createTempDirectory(prefix = "code-outline-outside-").toFile()
        try {
            val outsideFile = File(outsideDir, "Outside.kt").apply { writeText("class OutsideOutline") }

            try {
                callbacks.getCodeOutline(outsideFile.absolutePath)
                throw AssertionError("Should throw exception for outside project file")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("not found") == true)
            }
        } finally {
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun testSearchCode_ignoresBuildDirectoryAndInvalidFilePatternFallsBack() {
        val buildDir = File(tempDir, "build").apply { mkdir() }
        File(buildDir, "Ignored.kt").writeText("class IgnoredBuildClass")

        val ignored = callbacks.searchCode(
            CodeSearchRequest(
                query = "IgnoredBuildClass",
                path = ".",
                maxResults = 10
            )
        )
        val invalidPattern = callbacks.searchCode(
            CodeSearchRequest(
                query = "JavaClass",
                path = ".",
                filePattern = "[",
                maxResults = 10
            )
        )

        assertTrue(ignored.matches.isEmpty(), "Ignored build directory should not be searched")
        assertTrue(
            invalidPattern.matches.any { it.filePath == "JavaClass.java" },
            "Invalid file pattern should fall back to searching all files"
        )
    }

    @Test
    fun testSearchCode_skipsOversizedFiles() {
        File(tempDir, "Large.kt").writeText("LargeNeedle\n" + "x".repeat(1024 * 1024))

        val result = callbacks.searchCode(
            CodeSearchRequest(
                query = "LargeNeedle",
                path = ".",
                maxResults = 10
            )
        )

        assertTrue(result.matches.isEmpty(), "Files larger than the search limit should be skipped")
    }

    @Test
    fun testFindSymbol_withoutIndexReturnsEmpty() {
        val result = callbacks.findSymbol(SymbolSearchRequest(symbolName = "TestClass"))

        assertTrue(result.symbols.isEmpty(), "Missing symbol index should return no symbols")
    }

    @Test
    fun testFindSymbol_filtersTypesAndMapsSymbolMetadata() {
        val sourceFile = File(tempDir, "Symbols.kt").apply { writeText("class TargetClass") }
        val callbacksWithSymbols = DefaultCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            symbolIndexService = FakeSymbolIndexService(
                listOf(
                    symbol("TargetClass", SymbolKind.CLASS, sourceFile, line = 3, signature = "class TargetClass"),
                    symbol("TargetMethod", SymbolKind.METHOD, sourceFile, line = 8, signature = null),
                    symbol("TargetField", SymbolKind.FIELD, sourceFile, line = 12),
                    symbol("TargetInterface", SymbolKind.INTERFACE, sourceFile, line = 16),
                    symbol("TargetEnum", SymbolKind.ENUM, sourceFile, line = 20),
                    symbol("TargetConstant", SymbolKind.CONSTANT, sourceFile, line = 24),
                    symbol("TargetNamespace", SymbolKind.NAMESPACE, sourceFile, line = 28)
                )
            )
        )

        val classSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.CLASS)
        )
        val functionSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.FUNCTION)
        )
        val variableSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.VARIABLE)
        )
        val interfaceSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.INTERFACE)
        )
        val enumSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.ENUM)
        )
        val constantSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.CONSTANT)
        )
        val anySymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.ANY)
        )

        assertEquals(listOf("TargetClass"), classSymbols.symbols.map { it.name })
        assertEquals("Symbols.kt", classSymbols.symbols.single().filePath)
        assertEquals(3, classSymbols.symbols.single().lineNumber)
        assertEquals("class TargetClass", classSymbols.symbols.single().signature)
        assertEquals("documentation for TargetClass", classSymbols.symbols.single().documentation)
        assertEquals(listOf(SymbolType.FUNCTION), functionSymbols.symbols.map { it.type })
        assertEquals(listOf(SymbolType.VARIABLE), variableSymbols.symbols.map { it.type })
        assertEquals(listOf(SymbolType.INTERFACE), interfaceSymbols.symbols.map { it.type })
        assertEquals(listOf(SymbolType.ENUM), enumSymbols.symbols.map { it.type })
        assertEquals(listOf(SymbolType.CONSTANT), constantSymbols.symbols.map { it.type })
        assertTrue(anySymbols.symbols.any { it.name == "TargetNamespace" && it.type == SymbolType.ANY })
    }

    @Test
    fun testFindSymbol_mapsFunctionVariablePropertyAndMissingLocations() {
        val sourceFile = File(tempDir, "MoreSymbols.kt").apply { writeText("fun target() = Unit") }
        val callbacksWithSymbols = DefaultCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            symbolIndexService = FakeSymbolIndexService(
                listOf(
                    symbol("TargetFunction", SymbolKind.FUNCTION, sourceFile, line = 1),
                    symbol("TargetVariable", SymbolKind.VARIABLE, sourceFile, line = 2),
                    symbol("TargetProperty", SymbolKind.PROPERTY, sourceFile, line = 3),
                    symbolWithoutLocation("TargetString", SymbolKind.STRING, sourceFile)
                )
            )
        )

        val functionSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.FUNCTION)
        )
        val variableSymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.VARIABLE)
        )
        val anySymbols = callbacksWithSymbols.findSymbol(
            SymbolSearchRequest(symbolName = "Target", symbolType = SymbolType.ANY)
        )

        assertEquals(listOf("TargetFunction"), functionSymbols.symbols.map { it.name })
        assertEquals(listOf("TargetVariable", "TargetProperty"), variableSymbols.symbols.map { it.name })
        val stringSymbol = anySymbols.symbols.single { it.name == "TargetString" }
        assertEquals(0, stringSymbol.lineNumber)
        assertEquals(0, stringSymbol.columnNumber)
        assertEquals("detail for TargetString", stringSymbol.signature)
    }

    @Test
    fun testFindReferences_filtersByFileAndLine() {
        val testFile = File(tempDir, "TestClass.kt")

        val byFile = callbacks.findReferences(
            ReferenceSearchRequest(symbolName = "name", filePath = File(tempDir, "JavaClass.java").absolutePath)
        )
        val byLine = callbacks.findReferences(
            ReferenceSearchRequest(symbolName = "testMethod", filePath = testFile.absolutePath, lineNumber = 4)
        )
        val byLineFromDifferentFile = callbacks.findReferences(
            ReferenceSearchRequest(
                symbolName = "testMethod",
                filePath = File(tempDir, "JavaClass.java").absolutePath,
                lineNumber = 4
            )
        )

        assertTrue(byFile.references.isNotEmpty(), "Should keep Java references when filtering by file")
        assertTrue(byFile.references.all { it.filePath == "JavaClass.java" })
        assertTrue(
            byLine.references.any { it.filePath == "TestClass.kt" || it.lineNumber == 4 },
            "Combined file and line filter should keep matching references"
        )
        assertTrue(
            byLineFromDifferentFile.references.any { it.filePath == "TestClass.kt" && it.lineNumber == 4 },
            "Line filter should keep references when the file filter does not match"
        )
    }

    @Test
    fun testGetCodeOutline_detectsKnownLanguagesWithoutIndex() {
        val samples = mapOf(
            "sample.java" to "java",
            "sample.cpp" to "cpp",
            "sample.cc" to "cpp",
            "sample.cxx" to "cpp",
            "sample.c++" to "cpp",
            "sample.c" to "c",
            "sample.h" to "c/c++ header",
            "sample.hpp" to "c/c++ header",
            "sample.hxx" to "c/c++ header",
            "sample.py" to "python",
            "sample.js" to "javascript",
            "sample.ts" to "typescript",
            "sample.rs" to "rust",
            "sample.go" to "go",
            "sample.xml" to "xml",
            "sample.json" to "json",
            "sample.yaml" to "yaml",
            "sample.yml" to "yaml",
            "sample.md" to "markdown",
            "sample.unknown" to "unknown"
        )

        samples.forEach { (fileName, language) ->
            val file = File(tempDir, fileName).apply { writeText("content") }
            assertEquals(language, callbacks.getCodeOutline(file.absolutePath).language, fileName)
        }
    }

    @Test
    fun testGetCodeOutline_mapsIndexedSymbolsToOutlineKinds() {
        val sourceFile = File(tempDir, "Outline.kt").apply { writeText("class Outline") }
        val indexedKinds = listOf(
            SymbolKind.CLASS,
            SymbolKind.STRUCT,
            SymbolKind.ENUM,
            SymbolKind.INTERFACE,
            SymbolKind.FUNCTION,
            SymbolKind.METHOD,
            SymbolKind.FIELD,
            SymbolKind.PROPERTY,
            SymbolKind.VARIABLE,
            SymbolKind.CONSTANT,
            SymbolKind.NAMESPACE,
            SymbolKind.MODULE,
            SymbolKind.STRING
        )
        val callbacksWithSymbols = DefaultCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            symbolIndexService = FakeSymbolIndexService(
                indexedKinds.mapIndexed { index, kind ->
                    symbol("${kind.name.lowercase()}Symbol", kind, sourceFile, line = index + 1)
                }
            )
        )

        val outline = callbacksWithSymbols.getCodeOutline(sourceFile.absolutePath)

        assertEquals("Outline.kt", outline.filePath)
        assertEquals("kotlin", outline.language)
        assertEquals(
            listOf(
                OutlineItemKind.CLASS,
                OutlineItemKind.STRUCT,
                OutlineItemKind.ENUM,
                OutlineItemKind.INTERFACE,
                OutlineItemKind.FUNCTION,
                OutlineItemKind.METHOD,
                OutlineItemKind.FIELD,
                OutlineItemKind.PROPERTY,
                OutlineItemKind.VARIABLE,
                OutlineItemKind.CONSTANT,
                OutlineItemKind.NAMESPACE,
                OutlineItemKind.MODULE,
                OutlineItemKind.OBJECT
            ),
            outline.items.map { it.kind }
        )
        assertEquals(1, outline.items.first().range.startLine)
    }

    @Test
    fun testGetCodeOutline_usesDefaultsForMissingLocationAndSignature() {
        val sourceFile = File(tempDir, "FallbackOutline.kt").apply { writeText("fun fallback() = Unit") }
        val callbacksWithSymbols = DefaultCodeAnalysisCallbacks(
            projectRoot = tempDir.absolutePath,
            symbolIndexService = FakeSymbolIndexService(
                listOf(symbolWithoutLocation("FallbackFunction", SymbolKind.FUNCTION, sourceFile))
            )
        )

        val outline = callbacksWithSymbols.getCodeOutline(sourceFile.absolutePath)

        val item = outline.items.single()
        assertEquals(OutlineItemKind.FUNCTION, item.kind)
        assertEquals(0, item.range.startLine)
        assertEquals(0, item.range.startColumn)
        assertEquals(0, item.range.endLine)
        assertEquals(0, item.range.endColumn)
        assertEquals("detail for FallbackFunction", item.detail)
    }

    private fun symbol(
        name: String,
        kind: SymbolKind,
        file: File,
        line: Int,
        signature: String? = "signature for $name"
    ): SymbolInfo = SymbolInfo(
        name = name,
        kind = kind,
        detail = "detail for $name",
        filePath = file.absolutePath,
        location = SymbolLocation(
            startLine = line,
            startColumn = 1,
            endLine = line,
            endColumn = 10
        ),
        signature = signature,
        documentation = "documentation for $name"
    )

    private fun symbolWithoutLocation(
        name: String,
        kind: SymbolKind,
        file: File
    ): SymbolInfo = SymbolInfo(
        name = name,
        kind = kind,
        detail = "detail for $name",
        filePath = file.absolutePath,
        location = null,
        signature = null,
        documentation = null
    )

    private class FakeSymbolIndexService(
        private val symbols: List<SymbolInfo>
    ) : IProjectSymbolIndexService {
        override val status: StateFlow<SymbolIndexStatus> = MutableStateFlow(SymbolIndexStatus())

        override fun onProjectOpened(projectRoot: File) = Unit
        override fun onProjectClosed() = Unit
        override fun onFileSaved(file: File, content: String) = Unit
        override fun queryGlobals(prefix: String, limit: Int): List<SymbolInfo> = symbols
            .filter { prefix.isBlank() || it.name.startsWith(prefix) }
            .take(limit)
        override fun queryGlobalsFuzzy(pattern: String, limit: Int): List<FuzzySymbolMatch> = emptyList()
        override fun clearCache() = Unit
    }
}

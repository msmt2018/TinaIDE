package com.wuxianggujun.tinaide.ai.integration

import com.wuxianggujun.tinaide.ai.tools.executor.code.*
import com.wuxianggujun.tinaide.core.lang.ProjectPathFilters
import com.wuxianggujun.tinaide.core.symbol.IProjectSymbolIndexService
import com.wuxianggujun.tinaide.ui.compose.state.editor.resolveCodeAnalysisLanguageLabel
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * 代码分析回调实现
 * 基于 Tree-sitter 符号索引和文件搜索
 */
class CodeAnalysisCallbacksImpl(
    private val projectRoot: String,
    private val symbolIndexService: IProjectSymbolIndexService? = null
) : CodeAnalysisCallbacks {

    companion object {
        private const val MAX_SEARCH_RESULTS = 1000
        private const val MAX_FILE_SIZE = 1 * 1024 * 1024 // 1MB
    }

    override fun searchCode(request: CodeSearchRequest): CodeSearchResult {
        val matches = mutableListOf<CodeMatch>()
        val rootDir = File(projectRoot, request.path)

        if (!rootDir.exists() || !rootDir.isDirectory) {
            return CodeSearchResult(
                matches = emptyList(),
                totalCount = 0,
                truncated = false
            )
        }

        val pattern = if (request.isRegex) {
            try {
                val flags = if (request.caseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(request.query, flags)
            } catch (e: Exception) {
                return CodeSearchResult(
                    matches = emptyList(),
                    totalCount = 0,
                    truncated = false
                )
            }
        } else {
            null
        }

        val filePattern = request.filePattern?.let {
            try {
                val regex = it.replace("*", ".*").replace("?", ".")
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
            } catch (e: Exception) {
                null
            }
        }
        val queryLower = if (pattern == null && !request.caseSensitive) {
            request.query.lowercase(Locale.ROOT)
        } else {
            null
        }

        rootDir.walkTopDown()
            .onEnter { dir -> dir == rootDir || !shouldSkipSearchDirectory(dir.name) }
            .filter { it.isFile }
            .filter { file ->
                filePattern?.matcher(file.name)?.matches() ?: true
            }
            .filter { it.length() <= MAX_FILE_SIZE }
            .forEach { file ->
                if (matches.size >= MAX_SEARCH_RESULTS) {
                    return@forEach
                }

                try {
                    file.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (matches.size >= request.maxResults) {
                                return@forEachIndexed
                            }

                            val matchFound = if (pattern != null) {
                                pattern.matcher(line).find()
                            } else {
                                if (request.caseSensitive) {
                                    line.contains(request.query)
                                } else {
                                    line.lowercase(Locale.ROOT).contains(queryLower.orEmpty())
                                }
                            }

                            if (matchFound) {
                                val matchStart = if (pattern != null) {
                                    val matcher = pattern.matcher(line)
                                    if (matcher.find()) matcher.start() else 0
                                } else {
                                    if (request.caseSensitive) {
                                        line.indexOf(request.query)
                                    } else {
                                        // 对于 case-insensitive 搜索，在原始 line 中查找匹配起点
                                        val lowerLine = line.lowercase(Locale.ROOT)
                                        lowerLine.indexOf(queryLower.orEmpty())
                                    }
                                }

                                val matchEnd = if (pattern != null) {
                                    val matcher = pattern.matcher(line)
                                    if (matcher.find()) matcher.end() else line.length
                                } else {
                                    if (matchStart >= 0) {
                                        matchStart + request.query.length
                                    } else {
                                        -1
                                    }
                                }

                                if (matchStart >= 0 && matchEnd > matchStart) {
                                    matches.add(
                                        CodeMatch(
                                            filePath = toRelativePath(file.absolutePath),
                                            lineNumber = index + 1,
                                            lineContent = line,
                                            matchStart = matchStart.coerceAtLeast(0),
                                            matchEnd = matchEnd.coerceAtMost(line.length)
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略无法读取的文件
                }
            }

        return CodeSearchResult(
            matches = matches,
            totalCount = matches.size,
            truncated = matches.size >= request.maxResults
        )
    }

    override fun findSymbol(request: SymbolSearchRequest): SymbolSearchResult {
        val service = symbolIndexService
            ?: return SymbolSearchResult(symbols = emptyList())

        val symbols = service.queryGlobals(request.symbolName, 100)

        val filtered = if (request.symbolType != SymbolType.ANY) {
            symbols.filter { symbol ->
                matchesSymbolType(symbol.kind, request.symbolType)
            }
        } else {
            symbols
        }

        val definitions = filtered.map { symbol ->
            SymbolDefinition(
                name = symbol.name,
                type = convertSymbolKind(symbol.kind),
                filePath = toRelativePath(symbol.filePath),
                lineNumber = symbol.location?.startLine ?: 0,
                columnNumber = symbol.location?.startColumn ?: 0,
                signature = symbol.signature ?: symbol.detail,
                documentation = symbol.documentation
            )
        }

        return SymbolSearchResult(symbols = definitions)
    }

    override fun findReferences(request: ReferenceSearchRequest): ReferenceSearchResult {
        val searchRequest = CodeSearchRequest(
            query = "\\b${Pattern.quote(request.symbolName)}\\b",
            path = ".",
            caseSensitive = true,
            isRegex = true,
            maxResults = 500
        )

        val searchResult = searchCode(searchRequest)

        val references = searchResult.matches.map { match ->
            SymbolReference(
                filePath = match.filePath,
                lineNumber = match.lineNumber,
                columnNumber = match.matchStart,
                lineContent = match.lineContent,
                isDefinition = false
            )
        }

        val filtered = when {
            request.filePath != null && request.lineNumber != null -> {
                val requestFilePath = request.filePath
                val requestLineNumber = request.lineNumber
                val relativeRequestPath = requestFilePath?.let { toRelativePath(it) }
                references.filter {
                    it.filePath == relativeRequestPath || it.lineNumber == requestLineNumber
                }
            }
            request.filePath != null -> {
                val requestFilePath = request.filePath
                val relativeRequestPath = requestFilePath?.let { toRelativePath(it) }
                references.filter { it.filePath == relativeRequestPath }
            }
            else -> references
        }

        return ReferenceSearchResult(references = filtered)
    }

    private fun shouldSkipSearchDirectory(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return normalized == ".tinaide" ||
            normalized == "dist" ||
            normalized == "cmake-build-debug" ||
            normalized == "cmake-build-release" ||
            ProjectPathFilters.isNoisyDirectoryName(normalized)
    }

    override fun getCodeOutline(filePath: String): CodeOutlineResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        val service = symbolIndexService
        if (service == null) {
            return CodeOutlineResult(
                filePath = toRelativePath(filePath),
                language = detectLanguage(file),
                items = emptyList()
            )
        }

        val allSymbols = service.queryGlobals("", 10000)
        val fileSymbols = allSymbols.filter { it.filePath == file.absolutePath }

        val sortedSymbols = fileSymbols.sortedBy { it.location?.startLine ?: 0 }

        val items = sortedSymbols.map { symbol ->
            OutlineItem(
                name = symbol.name,
                kind = convertToOutlineKind(symbol.kind),
                range = OutlineRange(
                    startLine = symbol.location?.startLine ?: 0,
                    startColumn = symbol.location?.startColumn ?: 0,
                    endLine = symbol.location?.endLine ?: 0,
                    endColumn = symbol.location?.endColumn ?: 0
                ),
                children = emptyList(),
                detail = symbol.signature ?: symbol.detail
            )
        }

        return CodeOutlineResult(
            filePath = toRelativePath(filePath),
            language = detectLanguage(file),
            items = items
        )
    }

    private fun detectLanguage(file: File): String = file.resolveCodeAnalysisLanguageLabel()

    private fun matchesSymbolType(
        kind: com.wuxianggujun.tinaide.core.symbol.SymbolKind,
        type: SymbolType
    ): Boolean = when (type) {
        SymbolType.CLASS -> kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.CLASS
        SymbolType.FUNCTION ->
            kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.FUNCTION ||
                kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.METHOD
        SymbolType.VARIABLE ->
            kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.VARIABLE ||
                kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.FIELD ||
                kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.PROPERTY
        SymbolType.INTERFACE -> kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.INTERFACE
        SymbolType.ENUM -> kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.ENUM
        SymbolType.CONSTANT -> kind == com.wuxianggujun.tinaide.core.symbol.SymbolKind.CONSTANT
        SymbolType.ANY -> true
    }

    private fun convertSymbolKind(kind: com.wuxianggujun.tinaide.core.symbol.SymbolKind): SymbolType = when (kind) {
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.CLASS -> SymbolType.CLASS
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.FUNCTION,
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.METHOD -> SymbolType.FUNCTION
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.VARIABLE,
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.FIELD,
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.PROPERTY -> SymbolType.VARIABLE
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.INTERFACE -> SymbolType.INTERFACE
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.ENUM -> SymbolType.ENUM
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.CONSTANT -> SymbolType.CONSTANT
        else -> SymbolType.ANY
    }

    private fun convertToOutlineKind(kind: com.wuxianggujun.tinaide.core.symbol.SymbolKind): OutlineItemKind = when (kind) {
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.CLASS -> OutlineItemKind.CLASS
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.STRUCT -> OutlineItemKind.STRUCT
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.ENUM -> OutlineItemKind.ENUM
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.INTERFACE -> OutlineItemKind.INTERFACE
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.FUNCTION -> OutlineItemKind.FUNCTION
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.METHOD -> OutlineItemKind.METHOD
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.FIELD -> OutlineItemKind.FIELD
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.PROPERTY -> OutlineItemKind.PROPERTY
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.VARIABLE -> OutlineItemKind.VARIABLE
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.CONSTANT -> OutlineItemKind.CONSTANT
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.NAMESPACE -> OutlineItemKind.NAMESPACE
        com.wuxianggujun.tinaide.core.symbol.SymbolKind.MODULE -> OutlineItemKind.MODULE
        else -> OutlineItemKind.OBJECT
    }

    private fun toRelativePath(absolutePath: String): String = PathUtils.toRelativePath(absolutePath, projectRoot)
}

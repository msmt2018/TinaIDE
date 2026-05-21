package com.wuxianggujun.tinaide.ai.tools.executor.code

import com.wuxianggujun.tinaide.core.symbol.IProjectSymbolIndexService
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * 增强的代码分析回调实现
 * 优先使用 ripgrep (rg) 进行代码搜索，回退到默认实现
 */
class EnhancedCodeAnalysisCallbacks(
    private val projectRoot: String,
    private val symbolIndexService: IProjectSymbolIndexService? = null,
    private val rgPath: String? = null
) : CodeAnalysisCallbacks {

    companion object {
        private const val TAG = "EnhancedCodeAnalysis"
        private const val RG_TIMEOUT_SECONDS = 30L
        private const val MAX_RG_RESULTS = 10000
    }

    private val defaultImpl = DefaultCodeAnalysisCallbacks(projectRoot, symbolIndexService)
    private val hasRipgrep: Boolean by lazy {
        checkRipgrepAvailable()
    }

    override fun searchCode(request: CodeSearchRequest): CodeSearchResult {
        // 如果 ripgrep 可用，使用它进行搜索
        if (hasRipgrep) {
            try {
                return searchWithRipgrep(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Ripgrep search failed, falling back to default")
            }
        }

        // 回退到默认实现
        return defaultImpl.searchCode(request)
    }

    override fun findSymbol(request: SymbolSearchRequest): SymbolSearchResult = defaultImpl.findSymbol(request)

    override fun findReferences(request: ReferenceSearchRequest): ReferenceSearchResult {
        // 使用 ripgrep 进行更快的引用查找
        if (hasRipgrep) {
            try {
                val searchRequest = CodeSearchRequest(
                    query = "\\b${request.symbolName}\\b",
                    path = request.filePath ?: ".",
                    caseSensitive = true,
                    isRegex = true,
                    maxResults = 500
                )
                val searchResult = searchWithRipgrep(searchRequest)

                val references = searchResult.matches.map { match ->
                    SymbolReference(
                        filePath = match.filePath,
                        lineNumber = match.lineNumber,
                        columnNumber = match.matchStart,
                        lineContent = match.lineContent,
                        isDefinition = false
                    )
                }

                return ReferenceSearchResult(references = references)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Ripgrep reference search failed, falling back to default")
            }
        }

        return defaultImpl.findReferences(request)
    }

    override fun getCodeOutline(filePath: String): CodeOutlineResult = defaultImpl.getCodeOutline(filePath)

    /**
     * 使用 ripgrep 进行代码搜索
     */
    private fun searchWithRipgrep(request: CodeSearchRequest): CodeSearchResult {
        val searchPath = resolveProjectPath(projectRoot, request.path)
        if (searchPath == null || !searchPath.exists()) {
            return CodeSearchResult(
                matches = emptyList(),
                totalCount = 0,
                truncated = false
            )
        }

        val rgCommand = buildRipgrepCommand(request.copy(path = toRipgrepSearchPath(searchPath)))
        val process = ProcessBuilder(rgCommand)
            .directory(File(projectRoot))
            .redirectErrorStream(true)
            .start()

        val matches = mutableListOf<CodeMatch>()
        var totalCount = 0

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null && matches.size < request.maxResults) {
                line?.let { parsedLine ->
                    parseRipgrepLine(parsedLine)?.let { match ->
                        matches.add(match)
                        totalCount++
                    }
                }
            }

            // 等待进程完成
            val completed = process.waitFor(RG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                Timber.tag(TAG).w("Ripgrep search timed out after $RG_TIMEOUT_SECONDS seconds")
            }
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }

        return CodeSearchResult(
            matches = matches,
            totalCount = totalCount,
            truncated = matches.size >= request.maxResults
        )
    }

    /**
     * 构建 ripgrep 命令
     */
    private fun buildRipgrepCommand(request: CodeSearchRequest): List<String> {
        val command = mutableListOf<String>()

        // ripgrep 可执行文件
        command.add(rgPath ?: "rg")

        // 行号
        command.add("--line-number")

        // 列号
        command.add("--column")

        // 不显示文件名前缀（我们会解析完整输出）
        command.add("--with-filename")

        // 不使用颜色
        command.add("--color=never")

        // 大小写敏感
        if (!request.caseSensitive) {
            command.add("--ignore-case")
        }

        // 正则表达式模式
        if (!request.isRegex) {
            command.add("--fixed-strings")
        }

        // 文件类型过滤
        request.filePattern?.let { pattern ->
            command.add("--glob")
            command.add(pattern)
        }

        // 最大结果数
        command.add("--max-count")
        command.add(request.maxResults.toString())

        // 搜索模式
        command.add(request.query)

        // 搜索路径
        command.add(request.path)

        return command
    }

    /**
     * 解析 ripgrep 输出行
     * 格式: file:line:column:content
     */
    private fun parseRipgrepLine(line: String): CodeMatch? {
        val parts = line.split(":", limit = 4)
        if (parts.size < 4) return null

        return try {
            val filePath = File(projectRoot, parts[0]).absolutePath
            val lineNumber = parts[1].toInt()
            val columnNumber = parts[2].toInt()
            val content = parts[3]

            CodeMatch(
                filePath = toRelativePath(filePath),
                lineNumber = lineNumber,
                lineContent = content,
                matchStart = columnNumber - 1, // ripgrep 列号从 1 开始
                matchEnd = columnNumber - 1 + content.length
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse ripgrep line: $line")
            null
        }
    }

    /**
     * 检查 ripgrep 是否可用
     */
    private fun checkRipgrepAvailable(): Boolean {
        return try {
            val command = listOf(rgPath ?: "rg", "--version")
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return false
            }

            val exitCode = process.exitValue()
            if (exitCode == 0) {
                Timber.tag(TAG).i("Ripgrep is available")
                true
            } else {
                Timber.tag(TAG).w("Ripgrep check failed with exit code: $exitCode")
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).d("Ripgrep not available: ${e.message}")
            false
        }
    }

    private fun toRelativePath(absolutePath: String): String = toProjectRelativePath(projectRoot, absolutePath)

    private fun toRipgrepSearchPath(searchPath: File): String {
        val relativePath = toProjectRelativePath(projectRoot, searchPath.absolutePath)
        return relativePath.ifBlank { "." }
    }
}

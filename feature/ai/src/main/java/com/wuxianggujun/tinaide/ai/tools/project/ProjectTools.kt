package com.wuxianggujun.tinaide.ai.tools.project

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolParameterParser
import com.wuxianggujun.tinaide.ai.tools.appendLocalizedToolLine
import com.wuxianggujun.tinaide.ai.tools.localizedToolText
import com.wuxianggujun.tinaide.ai.tools.rethrowIfCancellation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import java.io.File
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 获取项目结构工具
 * 快速了解项目的目录结构和组织方式
 */
object GetProjectStructureTool : AiTool {
    override val name = "get_project_structure"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_project_structure,
            "Get the directory structure of the project. Shows folders and files in a tree format with configurable depth. Useful for understanding project organization, locating source files, headers, and build configurations. Automatically excludes build artifacts and common temporary directories."
        )
    override val category = ToolCategory.CODE_ANALYSIS
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_directory_path_to_analyze_relative_to_project,
                                "The directory path to analyze relative to project root (default: '.' for root)"
                            )
                        )
                        put("default", ".")
                    }
                )
                put(
                    "max_depth",
                    buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_maximum_depth_to_traverse_default_3_max_10,
                                "Maximum depth to traverse (default: 3, max: 10)"
                            )
                        )
                        put("default", 3)
                        put("minimum", 1)
                        put("maximum", 10)
                    }
                )
                put(
                    "show_files",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_whether_to_show_files_or_only_directories_default,
                                "Whether to show files or only directories (default: true)"
                            )
                        )
                        put("default", true)
                    }
                )
            }
        )
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val args = ToolParameterParser.parseArguments(toolCall)
        val path = ToolParameterParser.getStringParameter(args, "path", ".")
        val maxDepth = ToolParameterParser.getIntParameter(args, "max_depth", 3).coerceIn(1, 10)
        val showFiles = ToolParameterParser.getBooleanParameter(args, "show_files", true)

        val projectRoot = context.projectRoot ?: return ToolExecutionResult.Error(Strings.ai_tool_error_project_root_missing.str())
        val targetDir = resolveProjectPath(projectRoot, path)
            ?: return ToolExecutionResult.Error(Strings.exception_path_not_allowed.str(path))

        if (!targetDir.exists()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_directory_not_found.str(path))
        }

        if (!targetDir.isDirectory) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_path_not_directory.str(path))
        }

        // 排除常见的构建和临时目录
        val excludePatterns = listOf(
            "build", ".git", ".gradle", "node_modules", ".idea",
            "out", ".cache", "cmake-build-", "CMakeFiles", ".vscode"
        )

        return try {
            val structure = buildDirectoryTree(targetDir, projectRoot, maxDepth, showFiles, excludePatterns)

            val metadata = mapOf(
                "path" to path,
                "maxDepth" to maxDepth,
                "showFiles" to showFiles
            )

            val content = buildString {
                appendLocalizedToolLine(Strings.ai_tool_output_project_structure, "Project Structure: %1\$s", path)
                appendLine()
                append(structure)
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_project_structure.str(e.message))
        }
    }

    private fun buildDirectoryTree(
        dir: File,
        projectRoot: String,
        maxDepth: Int,
        showFiles: Boolean,
        excludePatterns: List<String>,
        currentDepth: Int = 0,
        prefix: String = ""
    ): String {
        if (currentDepth >= maxDepth) return ""

        val result = StringBuilder()
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return ""

        files.forEachIndexed { index, file ->
            // 跳过排除的模式
            if (excludePatterns.any { pattern ->
                    file.name.contains(pattern, ignoreCase = true) ||
                        file.name.startsWith(pattern, ignoreCase = true)
                }
            ) {
                return@forEachIndexed
            }

            val isLast = index == files.size - 1
            val connector = if (isLast) "└── " else "├── "
            val childPrefix = if (isLast) "    " else "│   "

            if (file.isDirectory) {
                result.append("$prefix$connector${file.name}/\n")
                result.append(
                    buildDirectoryTree(
                        file,
                        projectRoot,
                        maxDepth,
                        showFiles,
                        excludePatterns,
                        currentDepth + 1,
                        prefix + childPrefix
                    )
                )
            } else if (showFiles) {
                val size = formatFileSize(file.length())
                result.append("$prefix$connector${file.name} ($size)\n")
            }
        }

        return result.toString()
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

private fun resolveProjectPath(projectRoot: String, path: String): File? = runCatching {
    val root = File(projectRoot).canonicalFile
    val rawTarget = File(path)
    val target = if (rawTarget.isAbsolute) {
        rawTarget.canonicalFile
    } else {
        File(root, path).canonicalFile
    }
    target.takeIf { candidate ->
        val rootPath = root.toPath()
        val candidatePath = candidate.toPath()
        candidatePath == rootPath || candidatePath.startsWith(rootPath)
    }
}.getOrNull()

/**
 * 查找文件工具
 * 按名称或模式快速查找文件
 */
object FindFileTool : AiTool {
    override val name = "find_file"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_find_file,
            "Find files by name or pattern in the project. Supports wildcards (* and ?) and case-insensitive search. Returns file paths relative to project root. Useful for locating source files, headers, or configuration files. Faster than list_files for finding specific files."
        )
    override val category = ToolCategory.FILE_SYSTEM
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "name",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_file_name_or_pattern_to_search_for_supports,
                                "File name or pattern to search for (supports * and ? wildcards, e.g., '*.cpp', 'main.*', 'test_*.h')"
                            )
                        )
                    }
                )
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_directory_to_search_in_relative_to_project_root,
                                "Directory to search in relative to project root (default: '.' for entire project)"
                            )
                        )
                        put("default", ".")
                    }
                )
                put(
                    "case_sensitive",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_whether_the_search_is_case_sensitive_default_false,
                                "Whether the search is case sensitive (default: false)"
                            )
                        )
                        put("default", false)
                    }
                )
                put(
                    "max_results",
                    buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_maximum_number_of_results_to_return_default_50,
                                "Maximum number of results to return (default: 50)"
                            )
                        )
                        put("default", 50)
                        put("minimum", 1)
                        put("maximum", 200)
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("name"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val args = ToolParameterParser.parseArguments(toolCall)
        val name = ToolParameterParser.getStringParameter(args, "name")
        val path = ToolParameterParser.getStringParameter(args, "path", ".")
        val caseSensitive = ToolParameterParser.getBooleanParameter(args, "case_sensitive", false)
        val maxResults = ToolParameterParser.getIntParameter(args, "max_results", 50).coerceIn(1, 200)

        if (name.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_file_name_pattern_required.str())
        }

        val projectRoot = context.projectRoot ?: return ToolExecutionResult.Error(Strings.ai_tool_error_project_root_missing.str())
        val searchDir = resolveProjectPath(projectRoot, path)
            ?: return ToolExecutionResult.Error(Strings.exception_path_not_allowed.str(path))

        if (!searchDir.exists()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_directory_not_found.str(path))
        }

        if (!searchDir.isDirectory) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_path_not_directory.str(path))
        }

        return try {
            // 转换通配符为正则表达式
            val regexPattern = name
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")

            val pattern = regexPattern.toRegex(
                if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
            )

            val results = mutableListOf<File>()
            findFilesRecursive(searchDir, pattern, results, maxResults)

            val metadata = mapOf(
                "totalCount" to results.size,
                "truncated" to (results.size >= maxResults),
                "pattern" to name
            )

            val content = buildString {
                appendLocalizedToolLine(
                    Strings.ai_tool_output_found_files_matching,
                    "Found %1\$s file(s) matching '%2\$s':",
                    results.size,
                    name
                )
                if (results.size >= maxResults) {
                    appendLocalizedToolLine(Strings.ai_tool_output_results_limited, "(Results limited to %1\$s files)", maxResults)
                }
                appendLine()

                if (results.isEmpty()) {
                    appendLocalizedToolLine(Strings.ai_tool_output_no_files_matching, "No files found matching the pattern.")
                } else {
                    results.forEach { file ->
                        val relativePath = file.relativeTo(File(projectRoot)).path.replace("\\", "/")
                        val size = formatFileSize(file.length())
                        appendLine("  $relativePath ($size)")
                    }
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_find_files.str(e.message))
        }
    }

    private fun findFilesRecursive(dir: File, pattern: Regex, results: MutableList<File>, maxResults: Int) {
        if (results.size >= maxResults) return

        dir.listFiles()?.forEach { file ->
            if (results.size >= maxResults) return

            if (file.isDirectory) {
                // 跳过常见的排除目录
                if (file.name !in listOf("build", ".git", ".gradle", "node_modules", ".idea", "out", ".cache", "CMakeFiles")) {
                    findFilesRecursive(file, pattern, results, maxResults)
                }
            } else if (pattern.matches(file.name)) {
                results.add(file)
            }
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

/**
 * 统计代码行数工具
 * 统计项目或指定目录的代码行数
 */
object CountCodeLinesTool : AiTool {
    override val name = "count_code_lines"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_count_code_lines,
            "Count lines of code in the project or specific directory. Breaks down by file type (C, C++, H, CMake, etc.) and shows total lines, code lines, comment lines, and blank lines. Useful for project metrics, understanding codebase size, and tracking code complexity."
        )
    override val category = ToolCategory.CODE_ANALYSIS
    override val isDangerous = false

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_directory_to_analyze_relative_to_project_root_default,
                                "Directory to analyze relative to project root (default: '.' for entire project)"
                            )
                        )
                        put("default", ".")
                    }
                )
            }
        )
        put("required", buildJsonArray {})
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val args = ToolParameterParser.parseArguments(toolCall)
        val path = ToolParameterParser.getStringParameter(args, "path", ".")

        val projectRoot = context.projectRoot ?: return ToolExecutionResult.Error(Strings.ai_tool_error_project_root_missing.str())
        val targetDir = resolveProjectPath(projectRoot, path)
            ?: return ToolExecutionResult.Error(Strings.exception_path_not_allowed.str(path))

        if (!targetDir.exists()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_directory_not_found.str(path))
        }

        if (!targetDir.isDirectory) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_path_not_directory.str(path))
        }

        return try {
            val stats = mutableMapOf<String, CodeStats>()
            countLinesRecursive(targetDir, stats)

            val totalStats = stats.values.fold(CodeStats()) { acc, stat ->
                CodeStats(
                    totalLines = acc.totalLines + stat.totalLines,
                    codeLines = acc.codeLines + stat.codeLines,
                    commentLines = acc.commentLines + stat.commentLines,
                    blankLines = acc.blankLines + stat.blankLines,
                    fileCount = acc.fileCount + stat.fileCount
                )
            }

            val metadata = mapOf(
                "totalLines" to totalStats.totalLines,
                "codeLines" to totalStats.codeLines,
                "fileCount" to totalStats.fileCount,
                "path" to path
            )

            val content = buildString {
                appendLocalizedToolLine(Strings.ai_tool_output_code_statistics_for, "Code Statistics for: %1\$s", path)
                appendLine("=".repeat(50))
                appendLine()
                appendLocalizedToolLine(Strings.ai_tool_output_total_summary, "Total Summary:")
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_files_count, "Files: %1\$s", totalStats.fileCount)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_total_lines_count, "Total Lines: %1\$s", totalStats.totalLines)
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_code_lines_count, "Code Lines: %1\$s", totalStats.codeLines)
                append("  ")
                appendLocalizedToolLine(
                    Strings.ai_tool_output_comment_lines_count,
                    "Comment Lines: %1\$s",
                    totalStats.commentLines
                )
                append("  ")
                appendLocalizedToolLine(Strings.ai_tool_output_blank_lines_count, "Blank Lines: %1\$s", totalStats.blankLines)
                appendLine()
                appendLocalizedToolLine(Strings.ai_tool_output_breakdown_by_file_type, "Breakdown by File Type:")
                appendLine("-".repeat(50))

                if (stats.isEmpty()) {
                    appendLocalizedToolLine(Strings.ai_tool_output_no_code_files_found, "No code files found.")
                } else {
                    stats.entries.sortedByDescending { it.value.totalLines }.forEach { (ext, stat) ->
                        val percentage = if (totalStats.totalLines > 0) {
                            (stat.totalLines * 100.0 / totalStats.totalLines).toInt()
                        } else {
                            0
                        }

                        appendLine()
                        append("  ")
                        appendLocalizedToolLine(
                            Strings.ai_tool_output_file_type_percentage,
                            ".%1\$s (%2\$s%%)",
                            ext,
                            percentage
                        )
                        append("    ")
                        appendLocalizedToolLine(Strings.ai_tool_output_files_count, "Files: %1\$s", stat.fileCount)
                        append("    ")
                        appendLocalizedToolLine(Strings.ai_tool_output_lines_count, "Lines: %1\$s", stat.totalLines)
                        append("    ")
                        appendLocalizedToolLine(Strings.ai_tool_output_code_count, "Code: %1\$s", stat.codeLines)
                        append("    ")
                        appendLocalizedToolLine(Strings.ai_tool_output_comments_count, "Comments: %1\$s", stat.commentLines)
                        append("    ")
                        appendLocalizedToolLine(Strings.ai_tool_output_blank_count, "Blank: %1\$s", stat.blankLines)
                    }
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_count_code_lines.str(e.message))
        }
    }

    private fun countLinesRecursive(dir: File, stats: MutableMap<String, CodeStats>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // 跳过构建和临时目录
                if (file.name !in listOf("build", ".git", ".gradle", "node_modules", ".idea", "out", ".cache", "CMakeFiles")) {
                    countLinesRecursive(file, stats)
                }
            } else {
                val extension = file.extension.lowercase()
                // 支持 C/C++ 项目常见文件类型
                if (extension in listOf("c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "cmake", "txt", "md", "sh", "py", "java", "kt")) {
                    val lines = file.readLines()
                    val stat = analyzeLines(lines, extension)

                    val current = stats.getOrDefault(extension, CodeStats())
                    stats[extension] = CodeStats(
                        totalLines = current.totalLines + stat.totalLines,
                        codeLines = current.codeLines + stat.codeLines,
                        commentLines = current.commentLines + stat.commentLines,
                        blankLines = current.blankLines + stat.blankLines,
                        fileCount = current.fileCount + 1
                    )
                }
            }
        }
    }

    private fun analyzeLines(lines: List<String>, extension: String): CodeStats {
        var codeLines = 0
        var commentLines = 0
        var blankLines = 0
        var inBlockComment = false

        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> blankLines++
                // C/C++ 风格注释
                extension in listOf("c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "java", "kt") && (trimmed.startsWith("/*") || inBlockComment) -> {
                    commentLines++
                    inBlockComment = !trimmed.endsWith("*/")
                }
                extension in listOf("c", "cpp", "cc", "cxx", "h", "hpp", "hxx", "java", "kt") && trimmed.startsWith("//") -> commentLines++
                // Shell/Python 风格注释
                extension in listOf("sh", "py", "cmake") && trimmed.startsWith("#") -> commentLines++
                else -> codeLines++
            }
        }

        return CodeStats(
            totalLines = lines.size,
            codeLines = codeLines,
            commentLines = commentLines,
            blankLines = blankLines,
            fileCount = 0
        )
    }

    private data class CodeStats(
        val totalLines: Int = 0,
        val codeLines: Int = 0,
        val commentLines: Int = 0,
        val blankLines: Int = 0,
        val fileCount: Int = 0
    )
}

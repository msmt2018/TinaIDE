package com.wuxianggujun.tinaide.ai.tools.code

import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.tools.AiTool
import com.wuxianggujun.tinaide.ai.tools.ToolCategory
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionContext
import com.wuxianggujun.tinaide.ai.tools.ToolExecutionResult
import com.wuxianggujun.tinaide.ai.tools.ToolParameterParser
import com.wuxianggujun.tinaide.ai.tools.appendLocalizedToolLine
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeAnalysisCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.code.CodeSearchRequest
import com.wuxianggujun.tinaide.ai.tools.localizedToolText
import com.wuxianggujun.tinaide.ai.tools.rethrowIfCancellation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 搜索代码工具
 * 在项目中搜索代码模式，支持正则表达式、文件过滤和大小写敏感选项
 */
object SearchCodeTool : AiTool {
    override val name = "search_code"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_search_code,
            "Search for code patterns across the project. Supports regex patterns, file filtering (*.kt, *.java), case sensitivity, and result limiting. Use this to find specific code snippets, function calls, or patterns in the codebase."
        )
    override val category = ToolCategory.CODE_ANALYSIS

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "query",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_search_query_text_or_regex_pattern,
                                "The search query (text or regex pattern)"
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
                                Strings.ai_tool_param_desc_the_directory_path_to_search_in_default_project,
                                "The directory path to search in (default: project root)"
                            )
                        )
                        put("default", ".")
                    }
                )
                put(
                    "file_pattern",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_file_name_pattern_to_filter_e_g_kt,
                                "File name pattern to filter (e.g., '*.kt', '*.java')"
                            )
                        )
                    }
                )
                put(
                    "case_sensitive",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_whether_the_search_is_case_sensitive,
                                "Whether the search is case sensitive"
                            )
                        )
                        put("default", false)
                    }
                )
                put(
                    "regex",
                    buildJsonObject {
                        put("type", "boolean")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_whether_to_treat_query_as_regex_pattern,
                                "Whether to treat query as regex pattern"
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
                                Strings.ai_tool_param_desc_maximum_number_of_results_to_return,
                                "Maximum number of results to return"
                            )
                        )
                        put("default", 50)
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("query"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["codeAnalysisCallbacks"] as? CodeAnalysisCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_code_analysis_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val query = ToolParameterParser.getStringParameter(args, "query")

        if (query.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_search_query_required.str())
        }

        val request = CodeSearchRequest(
            query = query,
            path = ToolParameterParser.getStringParameter(args, "path", "."),
            filePattern = args["file_pattern"],
            caseSensitive = ToolParameterParser.getBooleanParameter(args, "case_sensitive", false),
            isRegex = ToolParameterParser.getBooleanParameter(args, "regex", false),
            maxResults = ToolParameterParser.getIntParameter(args, "max_results", 50)
        )

        return try {
            val result = callbacks.searchCode(request)

            val metadata = mapOf(
                "totalCount" to result.totalCount,
                "matchCount" to result.matches.size,
                "truncated" to result.truncated
            )

            val content = buildString {
                appendLocalizedToolLine(
                    Strings.ai_tool_output_found_matches,
                    "Found %1\$s matches%2\$s:",
                    result.totalCount,
                    if (result.truncated) {
                        localizedToolText(
                            Strings.ai_tool_output_showing_first_suffix,
                            " (showing first %1\$s)",
                            result.matches.size
                        )
                    } else {
                        ""
                    }
                )
                appendLine()

                result.matches.groupBy { it.filePath }.forEach { (file, matches) ->
                    appendLine("$file:")
                    matches.forEach { match ->
                        append("  ")
                        appendLocalizedToolLine(
                            Strings.ai_tool_output_line_content,
                            "Line %1\$s: %2\$s",
                            match.lineNumber,
                            match.lineContent.trim()
                        )
                    }
                    appendLine()
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_search_code.str(e.message))
        }
    }
}

/**
 * 查找符号定义工具
 * 快速定位类、函数、变量等符号的定义位置，包括完整签名和文档
 */
object FindSymbolTool : AiTool {
    override val name = "find_symbol"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_find_symbol,
            "Locate the definition of a code symbol (class, function, variable, interface, enum, constant). Returns file path, line number, signature, and documentation. Useful for understanding where a symbol is defined and its structure."
        )
    override val category = ToolCategory.CODE_ANALYSIS

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "symbol",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_symbol_name_to_find,
                                "The symbol name to find"
                            )
                        )
                    }
                )
                put(
                    "type",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_symbol_type_class_function_variable_etc,
                                "The symbol type (class, function, variable, etc.)"
                            )
                        )
                        put(
                            "enum",
                            buildJsonArray {
                                add(JsonPrimitive("class"))
                                add(JsonPrimitive("function"))
                                add(JsonPrimitive("variable"))
                                add(JsonPrimitive("interface"))
                                add(JsonPrimitive("enum"))
                                add(JsonPrimitive("constant"))
                                add(JsonPrimitive("any"))
                            }
                        )
                        put("default", "any")
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("symbol"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["codeAnalysisCallbacks"] as? CodeAnalysisCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_code_analysis_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val symbolName = ToolParameterParser.getStringParameter(args, "symbol")

        if (symbolName.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_symbol_name_required.str())
        }

        val symbolTypeStr = ToolParameterParser.getStringParameter(args, "type", "any")
        val symbolType = try {
            com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolType.valueOf(symbolTypeStr.uppercase())
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolType.ANY
        }

        val request = com.wuxianggujun.tinaide.ai.tools.executor.code.SymbolSearchRequest(
            symbolName = symbolName,
            symbolType = symbolType
        )

        return try {
            val result = callbacks.findSymbol(request)

            if (result.symbols.isEmpty()) {
                return ToolExecutionResult.Success(Strings.ai_tool_success_no_symbols_found.str(symbolName))
            }

            val metadata = mapOf(
                "symbolCount" to result.symbols.size
            )

            val content = buildString {
                appendLocalizedToolLine(Strings.ai_tool_output_found_symbols, "Found %1\$s symbol(s):", result.symbols.size)
                appendLine()

                result.symbols.forEach { symbol ->
                    appendLine("${symbol.type.name}: ${symbol.name}")
                    append("  ")
                    appendLocalizedToolLine(
                        Strings.ai_tool_output_location,
                        "Location: %1\$s",
                        "${symbol.filePath}:${symbol.lineNumber}:${symbol.columnNumber}"
                    )
                    append("  ")
                    appendLocalizedToolLine(Strings.ai_tool_output_signature, "Signature: %1\$s", symbol.signature)
                    symbol.documentation?.let {
                        append("  ")
                        appendLocalizedToolLine(Strings.ai_tool_output_documentation, "Documentation: %1\$s", it)
                    }
                    appendLine()
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_find_symbol.str(e.message))
        }
    }
}

/**
 * 查找符号引用工具
 * 查找项目中所有使用指定符号的位置，区分定义和引用
 */
object FindReferencesTool : AiTool {
    override val name = "find_references"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_find_references,
            "Find all locations where a symbol is referenced or used in the project. Distinguishes between definitions and usages. Helpful for impact analysis, refactoring, and understanding code dependencies."
        )
    override val category = ToolCategory.CODE_ANALYSIS

    override fun getParameters(): JsonElement = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "symbol",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_symbol_name_to_find_references_for,
                                "The symbol name to find references for"
                            )
                        )
                    }
                )
                put(
                    "file",
                    buildJsonObject {
                        put("type", "string")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_file_containing_the_symbol_optional,
                                "The file containing the symbol (optional)"
                            )
                        )
                    }
                )
                put(
                    "line",
                    buildJsonObject {
                        put("type", "integer")
                        put(
                            "description",
                            localizedToolText(
                                Strings.ai_tool_param_desc_the_line_number_of_the_symbol_optional,
                                "The line number of the symbol (optional)"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("symbol"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["codeAnalysisCallbacks"] as? CodeAnalysisCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_code_analysis_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val symbolName = ToolParameterParser.getStringParameter(args, "symbol")

        if (symbolName.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_symbol_name_required.str())
        }

        val request = com.wuxianggujun.tinaide.ai.tools.executor.code.ReferenceSearchRequest(
            symbolName = symbolName,
            filePath = args["file"],
            lineNumber = args["line"]?.toIntOrNull()
        )

        return try {
            val result = callbacks.findReferences(request)

            if (result.references.isEmpty()) {
                return ToolExecutionResult.Success(Strings.ai_tool_success_no_references_found.str(symbolName))
            }

            val metadata = mapOf(
                "referenceCount" to result.references.size,
                "definitionCount" to result.references.count { it.isDefinition }
            )

            val content = buildString {
                appendLocalizedToolLine(
                    Strings.ai_tool_output_found_references,
                    "Found %1\$s reference(s):",
                    result.references.size
                )
                appendLine()

                result.references.groupBy { it.filePath }.forEach { (file, refs) ->
                    appendLine("$file:")
                    refs.forEach { ref ->
                        val prefix = if (ref.isDefinition) "[DEF]" else "[REF]"
                        append("  ")
                        appendLocalizedToolLine(
                            Strings.ai_tool_output_reference_line_content,
                            "%1\$s Line %2\$s:%3\$s: %4\$s",
                            prefix,
                            ref.lineNumber,
                            ref.columnNumber,
                            ref.lineContent.trim()
                        )
                    }
                    appendLine()
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_find_references.str(e.message))
        }
    }
}

/**
 * 获取代码大纲工具
 * 提取文件的结构化大纲，展示类、方法、属性等的层次关系
 */
object GetCodeOutlineTool : AiTool {
    override val name = "get_code_outline"
    override val description: String
        get() = localizedToolText(
            Strings.ai_tool_desc_get_code_outline,
            "Extract the structural outline of a file showing classes, methods, properties, and their hierarchical relationships. Provides a high-level overview of file organization without reading full content."
        )
    override val category = ToolCategory.CODE_ANALYSIS

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
                                Strings.ai_tool_param_desc_the_file_path_to_get_outline_for,
                                "The file path to get outline for"
                            )
                        )
                    }
                )
            }
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("path"))
            }
        )
    }

    override suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult {
        val callbacks = context.additionalData["codeAnalysisCallbacks"] as? CodeAnalysisCallbacks
            ?: return ToolExecutionResult.Error(Strings.ai_tool_error_code_analysis_callbacks_unavailable.str())

        val args = ToolParameterParser.parseArguments(toolCall)
        val path = ToolParameterParser.getStringParameter(args, "path")

        if (path.isBlank()) {
            return ToolExecutionResult.Error(Strings.ai_tool_error_file_path_required.str())
        }

        return try {
            val result = callbacks.getCodeOutline(path)

            val metadata = mapOf(
                "filePath" to result.filePath,
                "language" to result.language,
                "itemCount" to result.items.size
            )

            fun formatOutlineItem(item: com.wuxianggujun.tinaide.ai.tools.executor.code.OutlineItem, indent: String = ""): String = buildString {
                val range = "${item.range.startLine}:${item.range.startColumn}-${item.range.endLine}:${item.range.endColumn}"
                append("$indent${item.kind.name}: ${item.name}")
                item.detail?.let { append(" - $it") }
                appendLine(" [$range]")

                item.children.forEach { child ->
                    append(formatOutlineItem(child, "$indent  "))
                }
            }

            val content = buildString {
                appendLocalizedToolLine(
                    Strings.ai_tool_output_code_outline_for,
                    "Code outline for %1\$s (%2\$s):",
                    result.filePath,
                    result.language
                )
                appendLine()

                result.items.forEach { item ->
                    append(formatOutlineItem(item))
                }
            }

            ToolExecutionResult.Success(content, metadata)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            ToolExecutionResult.Error(Strings.ai_tool_error_failed_get_code_outline.str(e.message))
        }
    }
}

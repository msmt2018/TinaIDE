package com.wuxianggujun.tinaide.ai.tools.executor.code

/**
 * 代码分析工具执行器的回调接口
 */
interface CodeAnalysisCallbacks {
    /**
     * 搜索代码
     */
    fun searchCode(request: CodeSearchRequest): CodeSearchResult

    /**
     * 查找符号定义
     */
    fun findSymbol(request: SymbolSearchRequest): SymbolSearchResult

    /**
     * 查找符号引用
     */
    fun findReferences(request: ReferenceSearchRequest): ReferenceSearchResult

    /**
     * 获取代码大纲
     */
    fun getCodeOutline(filePath: String): CodeOutlineResult
}

/**
 * 代码搜索请求
 */
data class CodeSearchRequest(
    val query: String,
    val path: String = ".",
    val filePattern: String? = null,
    val caseSensitive: Boolean = false,
    val isRegex: Boolean = false,
    val maxResults: Int = 50
)

/**
 * 代码搜索结果
 */
data class CodeSearchResult(
    val matches: List<CodeMatch>,
    val totalCount: Int,
    val truncated: Boolean = false
)

/**
 * 代码匹配项
 */
data class CodeMatch(
    val filePath: String,
    val lineNumber: Int,
    val lineContent: String,
    val matchStart: Int,
    val matchEnd: Int,
    val context: String? = null
)

/**
 * 符号搜索请求
 */
data class SymbolSearchRequest(
    val symbolName: String,
    val symbolType: SymbolType = SymbolType.ANY
)

/**
 * 符号类型
 */
enum class SymbolType {
    CLASS,
    FUNCTION,
    VARIABLE,
    INTERFACE,
    ENUM,
    CONSTANT,
    ANY
}

/**
 * 符号搜索结果
 */
data class SymbolSearchResult(
    val symbols: List<SymbolDefinition>
)

/**
 * 符号定义
 */
data class SymbolDefinition(
    val name: String,
    val type: SymbolType,
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val signature: String,
    val documentation: String? = null
)

/**
 * 引用搜索请求
 */
data class ReferenceSearchRequest(
    val symbolName: String,
    val filePath: String? = null,
    val lineNumber: Int? = null
)

/**
 * 引用搜索结果
 */
data class ReferenceSearchResult(
    val references: List<SymbolReference>
)

/**
 * 符号引用
 */
data class SymbolReference(
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val lineContent: String,
    val isDefinition: Boolean = false
)

/**
 * 代码大纲结果
 */
data class CodeOutlineResult(
    val filePath: String,
    val language: String,
    val items: List<OutlineItem>
)

/**
 * 大纲项
 */
data class OutlineItem(
    val name: String,
    val kind: OutlineItemKind,
    val range: OutlineRange,
    val children: List<OutlineItem> = emptyList(),
    val detail: String? = null
)

/**
 * 大纲项类型
 */
enum class OutlineItemKind {
    FILE,
    MODULE,
    NAMESPACE,
    PACKAGE,
    CLASS,
    METHOD,
    PROPERTY,
    FIELD,
    CONSTRUCTOR,
    ENUM,
    INTERFACE,
    FUNCTION,
    VARIABLE,
    CONSTANT,
    STRING,
    NUMBER,
    BOOLEAN,
    ARRAY,
    OBJECT,
    KEY,
    NULL,
    ENUM_MEMBER,
    STRUCT,
    EVENT,
    OPERATOR,
    TYPE_PARAMETER
}

/**
 * 大纲范围
 */
data class OutlineRange(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int
)

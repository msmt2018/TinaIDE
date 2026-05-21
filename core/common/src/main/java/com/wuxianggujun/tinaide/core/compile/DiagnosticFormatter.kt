package com.wuxianggujun.tinaide.core.compile

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.Color

/**
 * 编译诊断信息格式化工具
 *
 * **功能**:
 * - 解析编译器输出的诊断信息
 * - 支持多种编译器输出格式（GCC、Clang、MSVC）
 * - 提供富文本格式化（带颜色标记）
 * - 支持错误分类和统计
 *
 * **支持的诊断格式**:
 * - GCC/Clang: `file:line:column: severity: message`
 * - MSVC: `file(line,column): severity code: message`
 *
 * **使用示例**:
 * ```kotlin
 * val formatter = DiagnosticFormatter()
 * 
 * // 解析编译器输出
 * val diagnostics = formatter.parse(compilerOutput)
 * 
 * // 格式化为富文本
 * val formattedText = formatter.formatRichText(diagnostics)
 * textView.text = formattedText
 * ```
 */
class DiagnosticFormatter {
    
    companion object {
        // 诊断严重性颜色
        val COLOR_ERROR = Color.parseColor("#F44336")      // 红色
        val COLOR_WARNING = Color.parseColor("#FF9800")    // 橙色
        val COLOR_NOTE = Color.parseColor("#2196F3")       // 蓝色
        val COLOR_FILE = Color.parseColor("#9C27B0")       // 紫色
        val COLOR_LINE_NUMBER = Color.parseColor("#4CAF50") // 绿色
        
        // GCC/Clang 格式正则表达式
        private val GCC_CLANG_PATTERN = Regex(
            """^(.+?):(\d+):(\d+):\s*(error|warning|note|fatal error):\s*(.+)$"""
        )
        
        // MSVC 格式正则表达式
        private val MSVC_PATTERN = Regex(
            """^(.+?)\((\d+),(\d+)\):\s*(error|warning)\s+(\w+):\s*(.+)$"""
        )
        
        // 链接器错误格式
        private val LINKER_PATTERN = Regex(
            """^(.+?):\s*(undefined reference to|multiple definition of)\s*[`'](.+?)[`']"""
        )
    }
    
    /**
     * 解析的诊断信息
     */
    data class ParsedDiagnostic(
        val file: String,
        val line: Int,
        val column: Int,
        val severity: Severity,
        val message: String,
        val code: String? = null  // MSVC 错误码
    ) {
        enum class Severity {
            ERROR,
            WARNING,
            NOTE,
            FATAL_ERROR
        }
        
        /**
         * 获取简短的文件名
         */
        val shortFileName: String
            get() = file.substringAfterLast('/')
        
        /**
         * 格式化为单行文本
         */
        fun toSingleLine(): String {
            val severityStr = when (severity) {
                Severity.ERROR -> "error"
                Severity.WARNING -> "warning"
                Severity.NOTE -> "note"
                Severity.FATAL_ERROR -> "fatal error"
            }
            return "$shortFileName:$line:$column: $severityStr: $message"
        }
    }
    
    /**
     * 诊断统计信息
     */
    data class DiagnosticSummary(
        val errorCount: Int,
        val warningCount: Int,
        val noteCount: Int
    ) {
        val totalCount: Int get() = errorCount + warningCount + noteCount
        val hasErrors: Boolean get() = errorCount > 0
        
        override fun toString(): String {
            val parts = mutableListOf<String>()
            if (errorCount > 0) parts.add("$errorCount errors")
            if (warningCount > 0) parts.add("$warningCount warnings")
            if (noteCount > 0) parts.add("$noteCount notes")
            return if (parts.isEmpty()) "No diagnostics" else parts.joinToString(", ")
        }
    }
    
    /**
     * 解析编译器输出
     *
     * @param output 编译器输出文本
     * @return 解析后的诊断信息列表
     */
    fun parse(output: String): List<ParsedDiagnostic> {
        val diagnostics = mutableListOf<ParsedDiagnostic>()
        
        for (line in output.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            // 尝试 GCC/Clang 格式
            GCC_CLANG_PATTERN.find(trimmedLine)?.let { match ->
                val severity = when (match.groupValues[4]) {
                    "error" -> ParsedDiagnostic.Severity.ERROR
                    "warning" -> ParsedDiagnostic.Severity.WARNING
                    "note" -> ParsedDiagnostic.Severity.NOTE
                    "fatal error" -> ParsedDiagnostic.Severity.FATAL_ERROR
                    else -> ParsedDiagnostic.Severity.NOTE
                }
                diagnostics.add(
                    ParsedDiagnostic(
                        file = match.groupValues[1],
                        line = match.groupValues[2].toIntOrNull() ?: 0,
                        column = match.groupValues[3].toIntOrNull() ?: 0,
                        severity = severity,
                        message = match.groupValues[5]
                    )
                )
                return@let
            }
            
            // 尝试 MSVC 格式
            MSVC_PATTERN.find(trimmedLine)?.let { match ->
                val severity = when (match.groupValues[4]) {
                    "error" -> ParsedDiagnostic.Severity.ERROR
                    "warning" -> ParsedDiagnostic.Severity.WARNING
                    else -> ParsedDiagnostic.Severity.NOTE
                }
                diagnostics.add(
                    ParsedDiagnostic(
                        file = match.groupValues[1],
                        line = match.groupValues[2].toIntOrNull() ?: 0,
                        column = match.groupValues[3].toIntOrNull() ?: 0,
                        severity = severity,
                        message = match.groupValues[6],
                        code = match.groupValues[5]
                    )
                )
                return@let
            }
            
            // 尝试链接器错误格式
            LINKER_PATTERN.find(trimmedLine)?.let { match ->
                diagnostics.add(
                    ParsedDiagnostic(
                        file = match.groupValues[1],
                        line = 0,
                        column = 0,
                        severity = ParsedDiagnostic.Severity.ERROR,
                        message = "${match.groupValues[2]} '${match.groupValues[3]}'"
                    )
                )
            }
        }
        
        return diagnostics
    }
    
    /**
     * 生成诊断统计
     */
    fun summarize(diagnostics: List<ParsedDiagnostic>): DiagnosticSummary {
        var errors = 0
        var warnings = 0
        var notes = 0
        
        for (diag in diagnostics) {
            when (diag.severity) {
                ParsedDiagnostic.Severity.ERROR, ParsedDiagnostic.Severity.FATAL_ERROR -> errors++
                ParsedDiagnostic.Severity.WARNING -> warnings++
                ParsedDiagnostic.Severity.NOTE -> notes++
            }
        }
        
        return DiagnosticSummary(errors, warnings, notes)
    }
    
    /**
     * 格式化为富文本（带颜色）
     *
     * @param diagnostics 诊断信息列表
     * @return 带颜色的 SpannableStringBuilder
     */
    fun formatRichText(diagnostics: List<ParsedDiagnostic>): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        
        for ((index, diag) in diagnostics.withIndex()) {
            if (index > 0) builder.append("\n")
            
            // 文件名
            val fileStart = builder.length
            builder.append(diag.shortFileName)
            builder.setSpan(
                ForegroundColorSpan(COLOR_FILE),
                fileStart,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // 行号和列号
            builder.append(":")
            val lineStart = builder.length
            builder.append("${diag.line}:${diag.column}")
            builder.setSpan(
                ForegroundColorSpan(COLOR_LINE_NUMBER),
                lineStart,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // 严重性
            builder.append(": ")
            val severityStart = builder.length
            val severityText = when (diag.severity) {
                ParsedDiagnostic.Severity.ERROR -> "error"
                ParsedDiagnostic.Severity.FATAL_ERROR -> "fatal error"
                ParsedDiagnostic.Severity.WARNING -> "warning"
                ParsedDiagnostic.Severity.NOTE -> "note"
            }
            builder.append(severityText)
            val severityColor = when (diag.severity) {
                ParsedDiagnostic.Severity.ERROR, ParsedDiagnostic.Severity.FATAL_ERROR -> COLOR_ERROR
                ParsedDiagnostic.Severity.WARNING -> COLOR_WARNING
                ParsedDiagnostic.Severity.NOTE -> COLOR_NOTE
            }
            builder.setSpan(
                ForegroundColorSpan(severityColor),
                severityStart,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // 消息
            builder.append(": ")
            builder.append(diag.message)
        }
        
        return builder
    }
    
    /**
     * 格式化为纯文本
     */
    fun formatPlainText(diagnostics: List<ParsedDiagnostic>): String {
        return diagnostics.joinToString("\n") { it.toSingleLine() }
    }
    
    /**
     * 按文件分组诊断信息
     */
    fun groupByFile(diagnostics: List<ParsedDiagnostic>): Map<String, List<ParsedDiagnostic>> {
        return diagnostics.groupBy { it.file }
    }
    
    /**
     * 按严重性排序（错误优先）
     */
    fun sortBySeverity(diagnostics: List<ParsedDiagnostic>): List<ParsedDiagnostic> {
        return diagnostics.sortedBy { diag ->
            when (diag.severity) {
                ParsedDiagnostic.Severity.FATAL_ERROR -> 0
                ParsedDiagnostic.Severity.ERROR -> 1
                ParsedDiagnostic.Severity.WARNING -> 2
                ParsedDiagnostic.Severity.NOTE -> 3
            }
        }
    }
    
    /**
     * 过滤特定严重性的诊断信息
     */
    fun filterBySeverity(
        diagnostics: List<ParsedDiagnostic>,
        vararg severities: ParsedDiagnostic.Severity
    ): List<ParsedDiagnostic> {
        val severitySet = severities.toSet()
        return diagnostics.filter { it.severity in severitySet }
    }
    
    /**
     * 获取唯一的错误（去除重复的 note）
     */
    fun getUniqueErrors(diagnostics: List<ParsedDiagnostic>): List<ParsedDiagnostic> {
        return diagnostics.filter { it.severity != ParsedDiagnostic.Severity.NOTE }
            .distinctBy { "${it.file}:${it.line}:${it.column}" }
    }
}

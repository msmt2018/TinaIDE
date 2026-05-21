package com.wuxianggujun.tinaide.diff

import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str

/**
 * 文件对比数据模型
 * 
 * 用于通用的文件对比功能，支持：
 * - 两个文件对比
 * - 同一文件的不同版本对比
 * - Git 工作区与暂存区对比
 */

/**
 * 文件对比结果
 */
data class FileDiff(
    val leftFile: DiffFile,
    val rightFile: DiffFile,
    val hunks: List<DiffHunk>,
    val stats: DiffStats
)

/**
 * 对比文件信息
 */
data class DiffFile(
    val path: String,
    val name: String = path.substringAfterLast("/"),
    val label: String = name,  // 显示标签，如 "原始文件" 或 "修改后"
    val content: String = "",
    val encoding: String = "UTF-8"
)

/**
 * Diff 块（Hunk）
 */
data class DiffHunk(
    val leftStart: Int,      // 左侧起始行号
    val leftCount: Int,      // 左侧行数
    val rightStart: Int,     // 右侧起始行号
    val rightCount: Int,     // 右侧行数
    val lines: List<DiffLine>
)

/**
 * Diff 行
 */
data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val leftLineNumber: Int?,   // 左侧行号（删除/上下文行有值）
    val rightLineNumber: Int?   // 右侧行号（新增/上下文行有值）
) {
    companion object {
        fun context(content: String, leftLine: Int, rightLine: Int) = DiffLine(
            type = DiffLineType.CONTEXT,
            content = content,
            leftLineNumber = leftLine,
            rightLineNumber = rightLine
        )
        
        fun added(content: String, rightLine: Int) = DiffLine(
            type = DiffLineType.ADDED,
            content = content,
            leftLineNumber = null,
            rightLineNumber = rightLine
        )
        
        fun removed(content: String, leftLine: Int) = DiffLine(
            type = DiffLineType.REMOVED,
            content = content,
            leftLineNumber = leftLine,
            rightLineNumber = null
        )
    }
}

/**
 * Diff 行类型
 */
enum class DiffLineType {
    CONTEXT,   // 上下文（未修改）
    ADDED,     // 新增
    REMOVED    // 删除
}

/**
 * Diff 统计信息
 */
data class DiffStats(
    val additions: Int,      // 新增行数
    val deletions: Int,      // 删除行数
    val changes: Int         // 修改块数
) {
    val total: Int get() = additions + deletions
    
    companion object {
        val EMPTY = DiffStats(0, 0, 0)
        
        fun fromHunks(hunks: List<DiffHunk>): DiffStats {
            var additions = 0
            var deletions = 0
            hunks.forEach { hunk ->
                hunk.lines.forEach { line ->
                    when (line.type) {
                        DiffLineType.ADDED -> additions++
                        DiffLineType.REMOVED -> deletions++
                        DiffLineType.CONTEXT -> { /* 不计数 */ }
                    }
                }
            }
            return DiffStats(additions, deletions, hunks.size)
        }
    }
}

/**
 * 对比视图模式
 */
enum class DiffViewMode {
    UNIFIED,      // 统一视图（上下排列）
    SIDE_BY_SIDE  // 并排视图（左右排列）
}

/**
 * 对比请求
 */
sealed class DiffRequest {
    /**
     * 两个文件对比
     */
    data class TwoFiles(
        val leftPath: String,
        val rightPath: String,
        val leftLabel: String = Strings.diff_label_original.str(),
        val rightLabel: String = Strings.diff_label_modified.str()
    ) : DiffRequest()
    
    /**
     * 文件与文本对比
     */
    data class FileWithText(
        val filePath: String,
        val text: String,
        val fileLabel: String = Strings.diff_label_file.str(),
        val textLabel: String = Strings.diff_label_editor.str()
    ) : DiffRequest()
    
    /**
     * 两段文本对比
     */
    data class TwoTexts(
        val leftText: String,
        val rightText: String,
        val leftLabel: String = Strings.diff_label_left.str(),
        val rightLabel: String = Strings.diff_label_right.str()
    ) : DiffRequest()
    
    /**
     * Git 工作区对比
     */
    data class GitWorkingTree(
        val filePath: String,
        val staged: Boolean = false  // true: 暂存区 vs HEAD, false: 工作区 vs 暂存区
    ) : DiffRequest()
}

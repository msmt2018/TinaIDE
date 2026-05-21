package com.wuxianggujun.tinaide.diff

import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diff 引擎
 * 
 * 使用 Myers 差分算法计算两个文本之间的差异
 */
class DiffEngine(
    private val contextLines: Int = 3  // 上下文行数
) {
    
    /**
     * 计算两个文件的差异
     */
    suspend fun diff(leftPath: String, rightPath: String): FileDiff = withContext(Dispatchers.IO) {
        val leftFile = File(leftPath)
        val rightFile = File(rightPath)
        
        val leftContent = if (leftFile.exists()) leftFile.readText() else ""
        val rightContent = if (rightFile.exists()) rightFile.readText() else ""
        
        diff(
            leftContent = leftContent,
            rightContent = rightContent,
            leftFile = DiffFile(
                path = leftPath,
                name = leftFile.name,
                label = Strings.diff_label_original.str(),
                content = leftContent
            ),
            rightFile = DiffFile(
                path = rightPath,
                name = rightFile.name,
                label = Strings.diff_label_modified.str(),
                content = rightContent
            )
        )
    }
    
    /**
     * 计算两段文本的差异
     */
    fun diff(
        leftContent: String,
        rightContent: String,
        leftFile: DiffFile = DiffFile(path = "", label = Strings.diff_label_left.str(), content = leftContent),
        rightFile: DiffFile = DiffFile(path = "", label = Strings.diff_label_right.str(), content = rightContent)
    ): FileDiff {
        val leftLines = leftContent.lines()
        val rightLines = rightContent.lines()
        
        val hunks = computeHunks(leftLines, rightLines)
        val stats = DiffStats.fromHunks(hunks)
        
        return FileDiff(
            leftFile = leftFile,
            rightFile = rightFile,
            hunks = hunks,
            stats = stats
        )
    }
    
    /**
     * 计算差异块
     */
    private fun computeHunks(leftLines: List<String>, rightLines: List<String>): List<DiffHunk> {
        // 使用 LCS（最长公共子序列）算法计算差异
        val lcs = computeLCS(leftLines, rightLines)
        val diffOps = buildDiffOperations(leftLines, rightLines, lcs)
        
        return groupIntoHunks(diffOps, leftLines, rightLines)
    }
    
    /**
     * 计算最长公共子序列
     */
    private fun computeLCS(left: List<String>, right: List<String>): List<Pair<Int, Int>> {
        val m = left.size
        val n = right.size
        
        // DP 表
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 1..m) {
            for (j in 1..n) {
                if (left[i - 1] == right[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        // 回溯找出 LCS
        val lcs = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n
        
        while (i > 0 && j > 0) {
            when {
                left[i - 1] == right[j - 1] -> {
                    lcs.add(0, Pair(i - 1, j - 1))
                    i--
                    j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }
        
        return lcs
    }
    
    /**
     * 构建差异操作列表
     */
    private fun buildDiffOperations(
        left: List<String>,
        right: List<String>,
        lcs: List<Pair<Int, Int>>
    ): List<DiffOp> {
        val ops = mutableListOf<DiffOp>()
        
        var leftIdx = 0
        var rightIdx = 0
        var lcsIdx = 0
        
        while (leftIdx < left.size || rightIdx < right.size) {
            if (lcsIdx < lcs.size) {
                val (lcsLeft, lcsRight) = lcs[lcsIdx]
                
                // 处理删除的行
                while (leftIdx < lcsLeft) {
                    ops.add(DiffOp.Delete(leftIdx, left[leftIdx]))
                    leftIdx++
                }
                
                // 处理新增的行
                while (rightIdx < lcsRight) {
                    ops.add(DiffOp.Insert(rightIdx, right[rightIdx]))
                    rightIdx++
                }
                
                // 处理相同的行
                ops.add(DiffOp.Equal(leftIdx, rightIdx, left[leftIdx]))
                leftIdx++
                rightIdx++
                lcsIdx++
            } else {
                // 处理剩余的删除行
                while (leftIdx < left.size) {
                    ops.add(DiffOp.Delete(leftIdx, left[leftIdx]))
                    leftIdx++
                }
                
                // 处理剩余的新增行
                while (rightIdx < right.size) {
                    ops.add(DiffOp.Insert(rightIdx, right[rightIdx]))
                    rightIdx++
                }
            }
        }
        
        return ops
    }
    
    /**
     * 将差异操作分组为 Hunk
     */
    private fun groupIntoHunks(
        ops: List<DiffOp>,
        leftLines: List<String>,
        rightLines: List<String>
    ): List<DiffHunk> {
        if (ops.isEmpty()) return emptyList()
        
        val hunks = mutableListOf<DiffHunk>()
        var currentHunkOps = mutableListOf<DiffOp>()
        var lastChangeIdx = -1
        
        ops.forEachIndexed { idx, op ->
            val isChange = op !is DiffOp.Equal
            
            if (isChange) {
                // 如果距离上一个变更太远，先保存当前 hunk
                if (lastChangeIdx >= 0 && idx - lastChangeIdx > contextLines * 2) {
                    if (currentHunkOps.isNotEmpty()) {
                        hunks.add(createHunk(currentHunkOps))
                        currentHunkOps = mutableListOf()
                    }
                }
                
                // 添加前置上下文
                if (currentHunkOps.isEmpty()) {
                    val contextStart = maxOf(0, idx - contextLines)
                    for (i in contextStart until idx) {
                        if (i < ops.size && ops[i] is DiffOp.Equal) {
                            currentHunkOps.add(ops[i])
                        }
                    }
                }
                
                currentHunkOps.add(op)
                lastChangeIdx = idx
            } else {
                // 上下文行
                if (lastChangeIdx >= 0 && idx - lastChangeIdx <= contextLines) {
                    currentHunkOps.add(op)
                }
            }
        }
        
        // 保存最后一个 hunk
        if (currentHunkOps.isNotEmpty()) {
            hunks.add(createHunk(currentHunkOps))
        }
        
        return hunks
    }
    
    /**
     * 从操作列表创建 Hunk
     */
    private fun createHunk(ops: List<DiffOp>): DiffHunk {
        val lines = mutableListOf<DiffLine>()
        
        var leftStart = Int.MAX_VALUE
        var rightStart = Int.MAX_VALUE
        var leftCount = 0
        var rightCount = 0
        
        ops.forEach { op ->
            when (op) {
                is DiffOp.Equal -> {
                    leftStart = minOf(leftStart, op.leftIdx + 1)
                    rightStart = minOf(rightStart, op.rightIdx + 1)
                    leftCount++
                    rightCount++
                    lines.add(DiffLine.context(op.content, op.leftIdx + 1, op.rightIdx + 1))
                }
                is DiffOp.Delete -> {
                    leftStart = minOf(leftStart, op.leftIdx + 1)
                    leftCount++
                    lines.add(DiffLine.removed(op.content, op.leftIdx + 1))
                }
                is DiffOp.Insert -> {
                    rightStart = minOf(rightStart, op.rightIdx + 1)
                    rightCount++
                    lines.add(DiffLine.added(op.content, op.rightIdx + 1))
                }
            }
        }
        
        return DiffHunk(
            leftStart = if (leftStart == Int.MAX_VALUE) 1 else leftStart,
            leftCount = leftCount,
            rightStart = if (rightStart == Int.MAX_VALUE) 1 else rightStart,
            rightCount = rightCount,
            lines = lines
        )
    }
    
    /**
     * 差异操作
     */
    private sealed class DiffOp {
        data class Equal(val leftIdx: Int, val rightIdx: Int, val content: String) : DiffOp()
        data class Delete(val leftIdx: Int, val content: String) : DiffOp()
        data class Insert(val rightIdx: Int, val content: String) : DiffOp()
    }
    
    companion object {
        val DEFAULT = DiffEngine()
    }
}

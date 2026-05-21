package com.wuxianggujun.tinaide.ui

import com.wuxianggujun.tinaide.core.git.FileStatus
import com.wuxianggujun.tinaide.core.git.GitStatus
import com.wuxianggujun.tinaide.ui.compose.components.FileGitStatus

/**
 * Git 状态辅助类
 *
 * 职责：
 * - 构建 Git 状态映射（用于文件树显示）
 * - 提供 Git 状态相关的工具方法
 */
object GitStatusHelper {

    /**
     * 构建 Git 状态映射（用于文件树显示）
     *
     * @param gitStatus Git 状态对象
     * @return 文件路径到 Git 状态的映射
     */
    fun buildGitStatusMap(gitStatus: GitStatus): Map<String, FileGitStatus> {
        if (!gitStatus.isRepository) return emptyMap()

        val expectedSize = gitStatus.staged.size + gitStatus.unstaged.size + gitStatus.untracked.size
        val map = HashMap<String, FileGitStatus>(expectedSize.coerceAtLeast(16))

        // 暂存区文件
        gitStatus.staged.forEach { fileStatus ->
            map[fileStatus.path] = FileGitStatus(
                status = fileStatus.status,
                isStaged = true
            )
        }

        // 工作区文件（未暂存的更改）
        gitStatus.unstaged.forEach { fileStatus ->
            // 如果文件同时在暂存区和工作区，优先显示工作区状态
            map[fileStatus.path] = FileGitStatus(
                status = fileStatus.status,
                isStaged = false
            )
        }

        // 未跟踪文件
        gitStatus.untracked.forEach { path ->
            map[path] = FileGitStatus(
                status = FileStatus.UNTRACKED,
                isStaged = false
            )
        }

        return map
    }

    /**
     * 获取文件的 Git 状态
     *
     * @param gitStatus Git 状态对象
     * @param relativePath 相对于项目根目录的文件路径
     * @return 文件的 Git 状态，如果文件不在 Git 跟踪中则返回 null
     */
    fun getFileStatus(gitStatus: GitStatus, relativePath: String): FileGitStatus? {
        if (!gitStatus.isRepository) return null

        // 检查暂存区
        gitStatus.staged.find { it.path == relativePath }?.let { fileStatus ->
            return FileGitStatus(status = fileStatus.status, isStaged = true)
        }

        // 检查工作区
        gitStatus.unstaged.find { it.path == relativePath }?.let { fileStatus ->
            return FileGitStatus(status = fileStatus.status, isStaged = false)
        }

        // 检查未跟踪
        if (relativePath in gitStatus.untracked) {
            return FileGitStatus(status = FileStatus.UNTRACKED, isStaged = false)
        }

        return null
    }

    /**
     * 判断文件是否有未提交的更改
     */
    fun hasUncommittedChanges(gitStatus: GitStatus, relativePath: String): Boolean {
        if (!gitStatus.isRepository) return false

        return gitStatus.staged.any { it.path == relativePath } ||
            gitStatus.unstaged.any { it.path == relativePath } ||
            relativePath in gitStatus.untracked
    }

    /**
     * 获取有更改的文件数量
     */
    fun getChangedFilesCount(gitStatus: GitStatus): Int {
        if (!gitStatus.isRepository) return 0

        val allPaths = mutableSetOf<String>()
        gitStatus.staged.mapTo(allPaths) { it.path }
        gitStatus.unstaged.mapTo(allPaths) { it.path }
        allPaths.addAll(gitStatus.untracked)

        return allPaths.size
    }

    /**
     * 获取暂存区文件数量
     */
    fun getStagedFilesCount(gitStatus: GitStatus): Int = gitStatus.staged.size

    /**
     * 获取未暂存更改的文件数量
     */
    fun getUnstagedFilesCount(gitStatus: GitStatus): Int = gitStatus.unstaged.size + gitStatus.untracked.size
}

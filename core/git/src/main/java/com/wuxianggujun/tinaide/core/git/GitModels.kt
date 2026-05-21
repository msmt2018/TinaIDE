package com.wuxianggujun.tinaide.core.git

/**
 * Git 文件状态
 */
enum class FileStatus(val symbol: String) {
    MODIFIED("M"),
    ADDED("A"),
    DELETED("D"),
    RENAMED("R"),
    COPIED("C"),
    UNTRACKED("U"),  // 改为 U 更直观
    IGNORED("!")
}

/**
 * Git 文件状态项
 */
data class GitFileStatus(
    val path: String,
    val status: FileStatus,
    val oldPath: String? = null  // 用于重命名
)

/**
 * Git 仓库状态
 */
data class GitStatus(
    val isRepository: Boolean,
    val branch: String? = null,
    val staged: List<GitFileStatus> = emptyList(),
    val unstaged: List<GitFileStatus> = emptyList(),
    val untracked: List<String> = emptyList(),
    val hasChanges: Boolean = false
) {
    companion object {
        val NOT_A_REPOSITORY = GitStatus(isRepository = false)
    }
}

/**
 * Git 操作结果
 */
sealed class GitResult<out T> {
    data class Success<T>(val data: T) : GitResult<T>()
    data class Error(val message: String) : GitResult<Nothing>()
}

/**
 * Git Diff 信息
 */
data class GitDiff(
    val filePath: String,
    val hunks: List<DiffHunk>
)

data class DiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val lines: List<DiffLine>
)

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
)

enum class DiffLineType {
    CONTEXT,  // 未修改的行
    ADDED,    // 新增的行
    REMOVED   // 删除的行
}

/**
 * Git 提交信息
 */
data class GitCommit(
    val hash: String,           // 完整 hash
    val shortHash: String,      // 短 hash
    val author: String,         // 作者
    val authorEmail: String,    // 作者邮箱
    val date: String,           // 提交日期
    val message: String,        // 提交信息（第一行）
    val fullMessage: String     // 完整提交信息
)

/**
 * Git 分支信息
 */
data class GitBranch(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean = false
)

/**
 * Git 远程仓库信息
 */
data class GitRemote(
    val name: String,        // 如 "origin"
    val fetchUrl: String,    // fetch URL
    val pushUrl: String      // push URL（可能与 fetch 不同）
)

/**
 * Git HTTPS 凭据（用户名 + Token/密码）
 */
data class GitCredential(
    val protocol: String,  // "https"
    val host: String,      // "github.com"
    val username: String,
    val password: String   // Token 或密码
)

/**
 * 当前 Git 冲突处理模式
 */
enum class GitConflictKind {
    NONE,
    MERGE,
    REBASE
}

/**
 * Git 远程操作进度（预留：用于解析 fetch/pull/push 输出）
 */
sealed class GitProgress {
    data object Indeterminate : GitProgress()
    data class Counting(val current: Int, val total: Int) : GitProgress()
    data class Compressing(val current: Int, val total: Int) : GitProgress()
    data class Receiving(val current: Int, val total: Int) : GitProgress()
    data class Resolving(val current: Int, val total: Int) : GitProgress()
}

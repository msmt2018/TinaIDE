package com.wuxianggujun.tinaide.ui.compose.state.git

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wuxianggujun.tinaide.core.git.GitCommit

/**
 * 合并 Git 相关对话框的显示状态，减少 MainScreen 中的散落状态变量。
 */
@Stable
internal class GitDialogState {
    var showCommitDialog by mutableStateOf(false)
    var showRemoteDialog by mutableStateOf(false)
    var showSyncDialog by mutableStateOf(false)
    var showMergeConflictDialog by mutableStateOf(false)
    var showCommitDetailDialog by mutableStateOf(false)
    var selectedCommitForDetail by mutableStateOf<GitCommit?>(null)

    fun openCommitDetail(commit: GitCommit) {
        selectedCommitForDetail = commit
        showCommitDetailDialog = true
    }

    fun closeCommitDetail() {
        showCommitDetailDialog = false
        selectedCommitForDetail = null
    }
}

@Composable
internal fun rememberGitDialogState(): GitDialogState = remember { GitDialogState() }

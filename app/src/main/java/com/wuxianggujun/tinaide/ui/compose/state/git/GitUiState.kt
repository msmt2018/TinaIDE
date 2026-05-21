package com.wuxianggujun.tinaide.ui.compose.state.git

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuxianggujun.tinaide.core.git.GitBranch
import com.wuxianggujun.tinaide.core.git.GitCommit
import com.wuxianggujun.tinaide.core.git.GitConflictKind
import com.wuxianggujun.tinaide.core.git.GitRemote
import com.wuxianggujun.tinaide.core.git.GitStatus
import com.wuxianggujun.tinaide.ui.GitViewModel

internal data class GitUiState(
    val status: GitStatus,
    val isLoading: Boolean,
    val commitHistory: List<GitCommit>,
    val branches: List<GitBranch>,
    val isLoadingHistory: Boolean,
    val commitError: String?,
    val isCommitting: Boolean,
    val diffContent: String?,
    val diffFilePath: String?,
    val diffIsStaged: Boolean,
    val isLoadingDiff: Boolean,
    val diffError: String?,
    val remotes: List<GitRemote>,
    val isLoadingRemotes: Boolean,
    val remoteError: String?,
    val syncOutput: String?,
    val isSyncing: Boolean,
    val syncError: String?,
    val mergeConflicts: List<String>,
    val conflictKind: GitConflictKind,
    val isResolvingConflicts: Boolean,
    val conflictError: String?,
    val sshPassphraseRequest: GitViewModel.SshPassphraseRequest?,
)

@Composable
internal fun rememberGitUiState(viewModel: GitViewModel): GitUiState {
    val status = viewModel.status.collectAsStateWithLifecycle().value
    val isLoading = viewModel.isLoading.collectAsStateWithLifecycle().value
    val commitHistory = viewModel.commitHistory.collectAsStateWithLifecycle().value
    val branches = viewModel.branches.collectAsStateWithLifecycle().value
    val isLoadingHistory = viewModel.isLoadingHistory.collectAsStateWithLifecycle().value
    val commitError = viewModel.commitError.collectAsStateWithLifecycle().value
    val isCommitting = viewModel.isCommitting.collectAsStateWithLifecycle().value

    val diffContent = viewModel.diffContent.collectAsStateWithLifecycle().value
    val diffFilePath = viewModel.diffFilePath.collectAsStateWithLifecycle().value
    val diffIsStaged = viewModel.diffIsStaged.collectAsStateWithLifecycle().value
    val isLoadingDiff = viewModel.isLoadingDiff.collectAsStateWithLifecycle().value
    val diffError = viewModel.diffError.collectAsStateWithLifecycle().value

    val remotes = viewModel.remotes.collectAsStateWithLifecycle().value
    val isLoadingRemotes = viewModel.isLoadingRemotes.collectAsStateWithLifecycle().value
    val remoteError = viewModel.remoteError.collectAsStateWithLifecycle().value

    val syncOutput = viewModel.syncOutput.collectAsStateWithLifecycle().value
    val isSyncing = viewModel.isSyncing.collectAsStateWithLifecycle().value
    val syncError = viewModel.syncError.collectAsStateWithLifecycle().value

    val mergeConflicts = viewModel.mergeConflicts.collectAsStateWithLifecycle().value
    val conflictKind = viewModel.conflictKind.collectAsStateWithLifecycle().value
    val isResolvingConflicts = viewModel.isResolvingConflicts.collectAsStateWithLifecycle().value
    val conflictError = viewModel.conflictError.collectAsStateWithLifecycle().value

    val sshPassphraseRequest = viewModel.sshPassphraseRequest.collectAsStateWithLifecycle().value

    return GitUiState(
        status = status,
        isLoading = isLoading,
        commitHistory = commitHistory,
        branches = branches,
        isLoadingHistory = isLoadingHistory,
        commitError = commitError,
        isCommitting = isCommitting,
        diffContent = diffContent,
        diffFilePath = diffFilePath,
        diffIsStaged = diffIsStaged,
        isLoadingDiff = isLoadingDiff,
        diffError = diffError,
        remotes = remotes,
        isLoadingRemotes = isLoadingRemotes,
        remoteError = remoteError,
        syncOutput = syncOutput,
        isSyncing = isSyncing,
        syncError = syncError,
        mergeConflicts = mergeConflicts,
        conflictKind = conflictKind,
        isResolvingConflicts = isResolvingConflicts,
        conflictError = conflictError,
        sshPassphraseRequest = sshPassphraseRequest,
    )
}

package com.wuxianggujun.tinaide.ui.compose.screens.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.git.GitConflictKind
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.extensions.toastSuccess
import com.wuxianggujun.tinaide.ui.GitViewModel
import com.wuxianggujun.tinaide.ui.compose.components.GitCommitDetailDialog
import com.wuxianggujun.tinaide.ui.compose.components.GitCommitDialog
import com.wuxianggujun.tinaide.ui.compose.components.GitDiffDialog
import com.wuxianggujun.tinaide.ui.compose.components.GitMergeConflictDialog
import com.wuxianggujun.tinaide.ui.compose.components.GitRemoteDialog
import com.wuxianggujun.tinaide.ui.compose.components.GitSyncDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogMessageCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.state.git.GitDialogState
import com.wuxianggujun.tinaide.ui.compose.state.git.GitUiState
import kotlinx.coroutines.delay

@Composable
internal fun MainActivityGitDialogs(
    gitUiState: GitUiState,
    gitViewModel: GitViewModel,
    dialogState: GitDialogState,
    onOpenConflictFile: (String) -> Unit,
) {
    val context = LocalContext.current

    if (dialogState.showCommitDialog) {
        GitCommitDialog(
            stagedCount = gitUiState.status.staged.size,
            isLoading = gitUiState.isCommitting,
            error = gitUiState.commitError,
            onCommit = { message ->
                gitViewModel.commit(message) {
                    dialogState.showCommitDialog = false
                    context.toastSuccess(Strings.toast_commit_success.strOr(context))
                }
            },
            onDismiss = {
                dialogState.showCommitDialog = false
                gitViewModel.clearCommitError()
            }
        )
    }

    // Git Diff 对话框
    if (gitUiState.diffFilePath != null) {
        GitDiffDialog(
            filePath = gitUiState.diffFilePath!!,
            diff = null,
            rawDiff = gitUiState.diffContent,
            isLoading = gitUiState.isLoadingDiff,
            error = gitUiState.diffError,
            isStaged = gitUiState.diffIsStaged,
            onDismiss = { gitViewModel.clearDiff() }
        )
    }

    // Git 远程仓库对话框
    if (dialogState.showRemoteDialog) {
        GitRemoteDialog(
            remotes = gitUiState.remotes,
            isLoading = gitUiState.isLoadingRemotes,
            error = gitUiState.remoteError,
            onRefresh = { gitViewModel.loadRemotes() },
            onClearError = { gitViewModel.clearRemoteError() },
            onAddRemote = { name, url, onSuccess -> gitViewModel.addRemote(name, url, onSuccess) },
            onEditRemoteUrl = { name, url, onSuccess ->
                gitViewModel.setRemoteUrl(name, url, onSuccess)
            },
            onRemoveRemote = { name, onSuccess -> gitViewModel.removeRemote(name, onSuccess) },
            onDismiss = {
                dialogState.showRemoteDialog = false
                gitViewModel.clearRemoteError()
            }
        )
    }

    // Git 同步对话框
    if (dialogState.showSyncDialog) {
        GitSyncDialog(
            currentBranch = gitUiState.status.branch,
            branches = gitUiState.branches,
            remotes = gitUiState.remotes,
            isSyncing = gitUiState.isSyncing,
            output = gitUiState.syncOutput,
            error = gitUiState.syncError,
            conflicts = gitUiState.mergeConflicts,
            conflictKind = gitUiState.conflictKind,
            onOpenConflicts = { dialogState.showMergeConflictDialog = true },
            onFetch = { remote, branch, prune ->
                gitViewModel.fetch(remote = remote, branch = branch, prune = prune)
            },
            onPull = { remote, branch, rebase ->
                gitViewModel.pull(remote = remote, branch = branch, rebase = rebase)
            },
            onPush = { remote, branch, setUpstream, force, tags ->
                gitViewModel.push(
                    remote = remote,
                    branch = branch,
                    setUpstream = setUpstream,
                    force = force,
                    tags = tags
                )
            },
            onDismiss = { dialogState.showSyncDialog = false },
            onClear = { gitViewModel.clearSyncState() }
        )
    }

    // SSH 私钥口令对话框（用于自动解锁并重试 Git 远程操作）
    run {
        val sshReq = gitUiState.sshPassphraseRequest
        var passphrase by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(sshReq?.keyName, sshReq?.host) {
            if (sshReq != null) {
                passphrase = ""
                delay(100)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }

        if (sshReq != null) {
            val host = sshReq.host ?: "-"
            TinaAlertDialog(
                onDismissRequest = {
                    keyboardController?.hide()
                    gitViewModel.dismissSshPassphraseRequest()
                },
                title = {
                    TinaDialogTitleText(stringResource(Strings.git_ssh_passphrase_title))
                },
                text = {
                    TinaDialogContentColumn {
                        TinaDialogMessageCard(
                            message = stringResource(
                                Strings.git_ssh_passphrase_message,
                                sshReq.keyName,
                                host
                            )
                        )
                        TinaDialogCard(
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it },
                                    label = { Text(stringResource(Strings.git_ssh_passphrase_label)) },
                                    placeholder = { Text(stringResource(Strings.git_ssh_passphrase_placeholder)) },
                                    enabled = !gitUiState.isSyncing,
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (!gitUiState.isSyncing && passphrase.isNotBlank()) {
                                                keyboardController?.hide()
                                                gitViewModel.submitSshPassphrase(passphrase)
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                )
                                sshReq.error?.let { msg ->
                                    TinaDialogMessageCard(
                                        message = msg,
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f),
                                        textColor = MaterialTheme.colorScheme.onErrorContainer,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TinaPrimaryButton(
                        text = stringResource(Strings.btn_ok),
                        onClick = {
                            keyboardController?.hide()
                            gitViewModel.submitSshPassphrase(passphrase)
                        },
                        enabled = !gitUiState.isSyncing && passphrase.isNotBlank()
                    )
                },
                dismissButton = {
                    TinaTextButton(
                        text = stringResource(Strings.btn_cancel),
                        onClick = {
                            keyboardController?.hide()
                            gitViewModel.dismissSshPassphraseRequest()
                        },
                        enabled = !gitUiState.isSyncing
                    )
                }
            )
        }
    }

    if (
        dialogState.showMergeConflictDialog &&
        (gitUiState.mergeConflicts.isNotEmpty() || gitUiState.conflictKind != GitConflictKind.NONE)
    ) {
        GitMergeConflictDialog(
            conflictKind = gitUiState.conflictKind,
            conflicts = gitUiState.mergeConflicts,
            isBusy = gitUiState.isResolvingConflicts,
            error = gitUiState.conflictError,
            onOpenFile = onOpenConflictFile,
            onUseOurs = { file -> gitViewModel.acceptOurs(file) },
            onUseTheirs = { file -> gitViewModel.acceptTheirs(file) },
            onContinue = { gitViewModel.continueAfterResolve { dialogState.showMergeConflictDialog = false } },
            onSkipRebase = { gitViewModel.rebaseSkip() },
            onAbort = { gitViewModel.abortMergeOrRebase { dialogState.showMergeConflictDialog = false } },
            onMarkResolved = { files -> gitViewModel.markConflictsResolved(files) },
            onDismiss = { dialogState.showMergeConflictDialog = false }
        )
    }

    // Git 提交详情对话框
    val commitForDetail = dialogState.selectedCommitForDetail
    if (dialogState.showCommitDetailDialog && commitForDetail != null) {
        GitCommitDetailDialog(
            commit = commitForDetail,
            onDismiss = { dialogState.closeCommitDetail() }
        )
    }
}

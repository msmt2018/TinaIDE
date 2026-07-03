package com.wuxianggujun.tinaide.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleCoroutineScope
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.editor.IEditorManager
import com.wuxianggujun.tinaide.editor.session.SaveReason
import com.wuxianggujun.tinaide.editor.session.SaveResult
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.settings.SettingsActivity
import com.wuxianggujun.tinaide.ui.compose.components.BottomPanelTab
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity 的工作区宿主动作委托。
 *
 * 只承接设置、终端、收藏、文件保存、Git 分支切换与相关 Toast 编排，
 * 让 MainActivity 保留装配和系统边界。
 */
class MainActivityWorkspaceActionsDelegate(
    private val context: Context,
    private val activityStarter: (Intent) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val projectContext: IProjectContext,
    private val editorManager: IEditorManager,
    private val gitViewModel: GitViewModel,
    private val bottomPanelViewModel: BottomPanelViewModel,
    private val bottomPanelController: BottomPanelController,
    private val onToastSuccess: (String) -> Unit,
    private val onToastError: (String) -> Unit,
    private val onToastInfo: (String) -> Unit,
) {
    fun openSettings() {
        SettingsActivity.start(context)
    }

    fun openBookmarksPanel() {
        bottomPanelViewModel.setSelectedTab(BottomPanelTab.BOOKMARKS)
        lifecycleScope.launch { bottomPanelController.expandToDefault() }
    }

    fun openProjectTerminal() {
        val intent = Intent(context, TerminalActivity::class.java).apply {
            projectContext.getCurrentProject()?.rootPath?.let { rootPath ->
                putExtra(TerminalActivity.EXTRA_WORK_DIR, rootPath)
                putExtra(TerminalActivity.EXTRA_PROJECT_PATH, rootPath)
            }
        }
        activityStarter(intent)
    }

    fun saveFileForClose(tabId: String, onComplete: () -> Unit) {
        lifecycleScope.launch {
            when (val result = editorManager.save(tabId, SaveReason.CLOSE)) {
                is SaveResult.Success -> {
                    onToastSuccess(Strings.toast_saved.strOr(context))
                    onComplete()
                }

                is SaveResult.Failure -> {
                    onToastError(Strings.toast_save_failed.strOr(context, result.message))
                }

                SaveResult.NoOp -> onComplete()
            }
        }
    }

    fun checkoutGitBranch(branch: String) {
        gitViewModel.checkout(branch) {
            onToastSuccess(Strings.toast_git_branch_switched.strOr(context, branch))
        }
    }

    suspend fun duplicateFileOrDirectory(source: File): File? {
        return withContext(Dispatchers.IO) {
            val parent = source.parentFile ?: return@withContext null
            val (base, ext) = if (source.isFile) {
                source.nameWithoutExtension to source.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            } else {
                source.name to ""
            }
            for (i in 1..999) {
                val suffix = if (i == 1) " copy" else " copy $i"
                val candidate = File(parent, base + suffix + ext)
                if (!candidate.exists()) {
                    return@withContext runCatching {
                        if (source.isDirectory) {
                            source.copyRecursively(candidate, overwrite = false)
                        } else {
                            source.copyTo(candidate, overwrite = false)
                        }
                        candidate
                    }.getOrNull()
                }
            }
            null
        }
    }

    fun onDuplicateSuccess(duplicated: File) {
        onToastSuccess(Strings.toast_file_duplicated.strOr(context, duplicated.name))
    }

    fun onDuplicateFailure() {
        onToastError(Strings.toast_duplicate_failed.strOr(context))
    }

    fun onProjectNotOpen() {
        onToastError(Strings.toast_please_open_project.strOr(context))
    }

    fun onGitCommitSuccess() {
        onToastSuccess(Strings.toast_commit_success.strOr(context))
    }

    fun onGitInitSuccess() {
        onToastSuccess(Strings.toast_git_init_success.strOr(context))
    }

    fun onNoOpenFile() {
        onToastInfo(Strings.toast_no_open_file.strOr(context))
    }

    fun onUnsupportedEditor() {
        onToastInfo(Strings.toast_file_not_support_format.strOr(context))
    }
}

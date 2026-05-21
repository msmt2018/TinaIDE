package com.wuxianggujun.tinaide.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.editor.IBookmarkRepository
import com.wuxianggujun.tinaide.core.format.CodeFormatter
import com.wuxianggujun.tinaide.core.format.FormatResult
import com.wuxianggujun.tinaide.core.format.FormatStyle
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.lang.CxxFileSupport
import com.wuxianggujun.tinaide.core.linux.LinuxEnvironmentProvider
import com.wuxianggujun.tinaide.core.linux.LinuxRunModePolicy
import com.wuxianggujun.tinaide.core.linux.UnavailableLinuxEnvironmentProvider
import com.wuxianggujun.tinaide.editor.IEditorManager
import com.wuxianggujun.tinaide.editor.session.SaveReason
import com.wuxianggujun.tinaide.editor.session.SaveResult
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.file.IProjectSession
import com.wuxianggujun.tinaide.storage.ProjectDirStructure
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import timber.log.Timber

/**
 * MainActivity 动作处理 ViewModel
 *
 * 职责：
 * - 文件保存操作
 * - 代码格式化
 * - 撤销/重做操作
 * - 剪贴板操作
 * - WorkspaceEdit 应用
 *
 * 设计原则：
 * - 从 MainActivity 中提取业务逻辑
 * - 使用 SharedFlow 发送一次性事件（如 Toast）
 */
class MainActivityActionsViewModel(
    application: Application,
    private val editorManager: IEditorManager,
    private val projectContext: IProjectContext,
    private val projectSession: IProjectSession,
    private val bookmarkRepository: IBookmarkRepository,
) : AndroidViewModel(application) {

    private val linuxEnvironmentProvider: LinuxEnvironmentProvider by lazy {
        runCatching {
            org.koin.core.context.GlobalContext.get().getOrNull<LinuxEnvironmentProvider>()
        }.getOrNull() ?: UnavailableLinuxEnvironmentProvider
    }

    /**
     * UI 事件（Toast 消息等）
     */
    sealed class UiEvent {
        data class ShowToast(val message: String, val type: ToastType = ToastType.INFO) : UiEvent()
    }

    enum class ToastType { SUCCESS, ERROR, INFO }

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val context: Context get() = getApplication()

    // ============ 文件保存操作 ============

    /**
     * 保存当前文件
     */
    fun saveCurrentFile(editorContainerState: EditorContainerState) {
        val saveTarget = when (val result = editorContainerState.getActiveSaveTargetResult()) {
            EditorContainerState.ActiveSaveTargetResult.NoOpenFile -> {
                showToast(Strings.toast_no_open_file.strOr(context), ToastType.INFO)
                return
            }

            is EditorContainerState.ActiveSaveTargetResult.Available -> result.target
        }
        viewModelScope.launch {
            val result = editorManager.save(saveTarget.tabId, SaveReason.MANUAL)
            when (result) {
                is SaveResult.Success -> {
                    showToast(Strings.toast_saved.strOr(context), ToastType.SUCCESS)
                    val fullText = editorContainerState.readActiveTabText() ?: ""
                    editorContainerState.notifyFileSaved(saveTarget.tabId, saveTarget.file, fullText)
                }
                is SaveResult.Failure -> showToast(Strings.toast_save_failed.strOr(context, result.message), ToastType.ERROR)
                SaveResult.NoOp -> { }
            }
        }
    }

    // ============ 书签功能 ============

    fun toggleBookmark(editorContainerState: EditorContainerState) {
        val projectRoot = projectContext.getCurrentProject()?.rootPath
        if (projectRoot.isNullOrBlank()) {
            showToast(Strings.bookmarks_no_project.strOr(context), ToastType.INFO)
            return
        }

        val bookmarkTarget = when (val result = editorContainerState.getActiveBookmarkTargetResult()) {
            EditorContainerState.ActiveBookmarkTargetResult.NoOpenFile,
            EditorContainerState.ActiveBookmarkTargetResult.UnsupportedEditor -> {
                showToast(Strings.toast_no_open_file.strOr(context), ToastType.INFO)
                return
            }

            EditorContainerState.ActiveBookmarkTargetResult.NoBookmarkableLine -> {
                showToast(Strings.toast_bookmark_ignored_blank_line.strOr(context), ToastType.INFO)
                return
            }

            is EditorContainerState.ActiveBookmarkTargetResult.Success -> result.target
        }

        viewModelScope.launch {
            runCatching {
                bookmarkRepository.toggle(
                    projectRoot,
                    bookmarkTarget.file.absolutePath,
                    bookmarkTarget.line
                )
            }
        }
    }

    fun goToNextBookmark(editorContainerState: EditorContainerState) {
        goToAdjacentBookmark(editorContainerState, next = true)
    }

    fun goToPreviousBookmark(editorContainerState: EditorContainerState) {
        goToAdjacentBookmark(editorContainerState, next = false)
    }

    fun navigateToBookmark(editorContainerState: EditorContainerState, filePath: String, line: Int) {
        navigateToLocation(editorContainerState, filePath, line)
    }

    private fun goToAdjacentBookmark(editorContainerState: EditorContainerState, next: Boolean) {
        val projectRoot = projectContext.getCurrentProject()?.rootPath
        if (projectRoot.isNullOrBlank()) {
            showToast(Strings.bookmarks_no_project.strOr(context), ToastType.INFO)
            return
        }

        val bookmarkContext = when (val result = editorContainerState.getActiveBookmarkCursorContextResult()) {
            EditorContainerState.ActiveBookmarkCursorContextResult.NoOpenFile,
            EditorContainerState.ActiveBookmarkCursorContextResult.UnsupportedEditor -> {
                showToast(Strings.toast_no_open_file.strOr(context), ToastType.INFO)
                return
            }

            is EditorContainerState.ActiveBookmarkCursorContextResult.Success -> result.context
        }
        val currentLine = bookmarkContext.line
        val currentFilePath = bookmarkContext.file.absolutePath

        viewModelScope.launch {
            val target = runCatching {
                if (next) {
                    bookmarkRepository.findNext(projectRoot, currentFilePath, currentLine)
                } else {
                    bookmarkRepository.findPrevious(projectRoot, currentFilePath, currentLine)
                }
            }.getOrNull()

            if (target == null) {
                showToast(Strings.bookmarks_empty.strOr(context), ToastType.INFO)
                return@launch
            }

            navigateToLocation(editorContainerState, target.filePath, target.line)
        }
    }

    private fun navigateToLocation(editorContainerState: EditorContainerState, filePath: String, line: Int) {
        editorContainerState.openFileAndGoToPosition(File(filePath), line, 0)
    }

    /**
     * 保存全部文件
     */
    fun saveAllFiles(editorContainerState: EditorContainerState? = null) {
        viewModelScope.launch {
            editorContainerState?.rememberDirtyTabsForSaveAllNotification()
            val results = editorManager.saveAll(SaveReason.MANUAL)
            val successes = results.filterIsInstance<SaveResult.Success>()
            val failures = results.filterIsInstance<SaveResult.Failure>()

            when {
                results.isEmpty() -> showToast(Strings.toast_no_files_to_save.strOr(context), ToastType.INFO)
                failures.isNotEmpty() -> showToast(Strings.toast_some_files_save_failed.strOr(context), ToastType.ERROR)
                else -> showToast(Strings.toast_files_saved.strOr(context, successes.size), ToastType.SUCCESS)
            }

            if (editorContainerState != null && results.isNotEmpty()) {
                editorContainerState.notifySuccessfulSaveAllResults(results)
            }
        }
    }

    /**
     * 确保所有编辑器已保存（用于编译/调试前）
     *
     * @return true 如果所有文件保存成功，false 如果有失败
     */
    suspend fun ensureAllEditorsSaved(actionName: String): Boolean {
        val results = editorManager.saveAll(SaveReason.MANUAL)
        if (results.isEmpty()) return true
        val failures = results.filterIsInstance<SaveResult.Failure>()
        if (failures.isNotEmpty()) {
            showToast(Strings.toast_save_failed_cancelled.strOr(context, actionName, failures.first().message), ToastType.ERROR)
            return false
        }
        showToast(Strings.toast_auto_saved.strOr(context, results.size), ToastType.SUCCESS)
        return true
    }

    // ============ 撤销/重做操作 ============

    /**
     * 执行撤销操作
     */
    fun performUndo(editorContainerState: EditorContainerState) {
        if (editorContainerState.undoInActiveTab()) {
            return
        }
        Timber.tag(TAG).w("performUndo: Tina editor callback unavailable")
    }

    /**
     * 执行重做操作
     */
    fun performRedo(editorContainerState: EditorContainerState) {
        if (editorContainerState.redoInActiveTab()) {
            return
        }
        Timber.tag(TAG).w("performRedo: Tina editor callback unavailable")
    }

    // ============ 编辑器文本操作 ============

    fun performSelectAll(editorContainerState: EditorContainerState) {
        editorContainerState.selectAllInActiveTab()
    }

    fun performCopy(editorContainerState: EditorContainerState) {
        val selection = editorContainerState.getSelectionSnapshotInActiveTab()
        if (selection != null) {
            writeTextToClipboard(label = "selection", text = selection.text)
            showToast(Strings.toast_copied.strOr(context), ToastType.SUCCESS)
        }
    }

    fun performCut(editorContainerState: EditorContainerState) {
        val selection = editorContainerState.getSelectionSnapshotInActiveTab()
        if (selection != null && editorContainerState.replaceSelectionInActiveTab("")) {
            writeTextToClipboard(label = "selection", text = selection.text)
            showToast(Strings.toast_cut.strOr(context), ToastType.SUCCESS)
        }
    }

    fun performPaste(editorContainerState: EditorContainerState) {
        val text = readTextFromClipboard().orEmpty()
        if (text.isBlank()) return
        if (editorContainerState.replaceSelectionInActiveTab(text)) {
            showToast(Strings.toast_pasted.strOr(context), ToastType.SUCCESS)
        }
    }

    fun toggleLineComment(editorContainerState: EditorContainerState) {
        when (
            editorContainerState.requestToggleLineCommentInActiveEditor { file ->
                guessLineCommentToken(file.extension.lowercase())
            }
        ) {
            EditorContainerState.ActiveEditorCommandResult.SUCCESS -> Unit
            EditorContainerState.ActiveEditorCommandResult.NO_OPEN_FILE -> {
                showToast(Strings.toast_no_open_file.strOr(context), ToastType.INFO)
            }

            EditorContainerState.ActiveEditorCommandResult.UNSUPPORTED_EDITOR -> {
                showToast(Strings.toast_file_not_support_format.strOr(context), ToastType.INFO)
            }
        }
    }

    // ============ 代码格式化 ============

    /**
     * 格式化当前文件的代码
     */
    fun formatCode(editorContainerState: EditorContainerState) {
        val formatTarget = when (val result = editorContainerState.snapshotActiveEditableEditorContent()) {
            EditorContainerState.ActiveEditableEditorSnapshotResult.NoOpenFile -> {
                showToast(Strings.toast_no_open_file.strOr(context), ToastType.INFO)
                return
            }

            EditorContainerState.ActiveEditableEditorSnapshotResult.UnsupportedEditor -> {
                showToast(Strings.toast_file_not_support_format.strOr(context), ToastType.INFO)
                return
            }

            is EditorContainerState.ActiveEditableEditorSnapshotResult.Success -> result.snapshot
        }
        val file = formatTarget.file
        val content = formatTarget.text

        val extension = file.extension.lowercase()

        // 检查是否支持格式化
        if (extension !in CxxFileSupport.editorRelatedExtensions) {
            showToast(Strings.toast_file_type_not_support_format.strOr(context), ToastType.INFO)
            return
        }

        viewModelScope.launch {
            showToast(Strings.toast_formatting.strOr(context), ToastType.INFO)

            try {
                val formatter = CodeFormatter(
                    context = context,
                    linuxEnvironmentProvider = linuxEnvironmentProvider
                )

                // 检查 clang-format 是否可用
                if (!formatter.isAvailable()) {
                    showToast(Strings.toast_clang_format_not_available.strOr(context), ToastType.ERROR)
                    return@launch
                }

                val runMode = LinuxRunModePolicy.resolve(
                    configuredMode = Prefs.clangFormatRunMode,
                    linuxEnvironmentAvailable = linuxEnvironmentProvider.get().isAvailable()
                )

                val filePath = when (runMode) {
                    LinuxRunModePolicy.RunMode.PROOT -> {
                        linuxEnvironmentProvider.get().toGuestPath(file.absolutePath)
                    }

                    LinuxRunModePolicy.RunMode.NATIVE -> file.absolutePath
                }

                // 执行格式化
                val result = formatter.format(
                    content = content,
                    fileName = filePath,
                    style = FormatStyle.FILE
                )

                when (result) {
                    is FormatResult.Success -> {
                        // 在主线程更新编辑器内容
                        withContext(Dispatchers.Main) {
                            applyFormattedContent(
                                editorContainerState = editorContainerState,
                                originalContent = content,
                                formattedContent = result.formattedContent
                            )
                        }
                    }
                    is FormatResult.Error -> {
                        showToast(Strings.toast_format_failed.strOr(context, result.message), ToastType.ERROR)
                    }
                }
            } catch (e: Exception) {
                showToast(Strings.toast_format_error.strOr(context, e.message ?: ""), ToastType.ERROR)
            }
        }
    }

    /**
     * 应用格式化后的内容到编辑器
     */
    private fun applyFormattedContent(
        editorContainerState: EditorContainerState,
        originalContent: String,
        formattedContent: String
    ) {
        if (formattedContent != originalContent) {
            if (editorContainerState.replaceActiveTabText(formattedContent)) {
                showToast(Strings.toast_format_done.strOr(context), ToastType.SUCCESS)
            } else {
                showToast(Strings.toast_file_not_support_format.strOr(context), ToastType.INFO)
            }
        } else {
            showToast(Strings.toast_code_already_formatted.strOr(context), ToastType.INFO)
        }
    }

    // ============ 剪贴板操作 ============

    /**
     * 复制文件路径到剪贴板
     */
    fun copyPathToClipboard(file: File) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("file path", file.absolutePath)
        clipboard.setPrimaryClip(clip)
        showToast(Strings.toast_path_copied.strOr(context), ToastType.SUCCESS)
    }

    fun copyNameToClipboard(file: File) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("file name", file.name)
        clipboard.setPrimaryClip(clip)
        showToast(Strings.toast_name_copied.strOr(context), ToastType.SUCCESS)
    }

    fun copyRelativePathToClipboard(file: File) {
        val project = projectContext.getCurrentProject()
        val relativePath = project?.rootPath
            ?.let { rootPath ->
                val root = File(rootPath)
                file.relativeToOrNull(root)?.path
            }
            ?.replace(File.separatorChar, '/')
            ?: file.absolutePath

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("relative path", relativePath)
        clipboard.setPrimaryClip(clip)
        showToast(Strings.toast_relative_path_copied.strOr(context), ToastType.SUCCESS)
    }

    // ============ WorkspaceEdit 应用 ============

    /**
     * 应用 LSP WorkspaceEdit
     */
    suspend fun applyWorkspaceEdit(
        editorContainerState: EditorContainerState,
        edit: WorkspaceEdit
    ): Boolean {
        val editsByFile = mutableMapOf<File, MutableList<TextEdit>>()

        edit.changes?.forEach { (uri, edits) ->
            workspaceUriToFile(uri)?.let { file ->
                editsByFile.getOrPut(file) { mutableListOf() }.addAll(edits)
            }
        }

        edit.documentChanges?.forEach { change ->
            if (change.isLeft) {
                val docEdit = change.left
                val file = workspaceUriToFile(docEdit.textDocument.uri) ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val rawEdits = docEdit.edits as List<*>
                val extracted = rawEdits.mapNotNull { item ->
                    when (item) {
                        is TextEdit -> item
                        is org.eclipse.lsp4j.jsonrpc.messages.Either<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            val either = item as org.eclipse.lsp4j.jsonrpc.messages.Either<TextEdit, *>
                            when {
                                either.isLeft -> either.left
                                else -> either.right as? TextEdit
                            }
                        }
                        else -> null
                    }
                }

                editsByFile.getOrPut(file) { mutableListOf() }.addAll(extracted)
            }
        }

        if (editsByFile.isEmpty()) return false

        var success = true
        editsByFile.forEach { (file, fileEdits) ->
            val sorted = fileEdits.sortedWith(
                compareByDescending<TextEdit> { it.range.start.line }
                    .thenByDescending { it.range.start.character }
                    .thenByDescending { it.range.end.line }
                    .thenByDescending { it.range.end.character }
            )

            val tabId = editorContainerState.findOpenTabIdByFileOrNull(file)
            if (tabId != null) {
                val mappedEdits = sorted.map { edit ->
                    EditorContainerState.TextEditOperation(
                        startLine = edit.range.start.line,
                        startColumn = edit.range.start.character,
                        endLine = edit.range.end.line,
                        endColumn = edit.range.end.character,
                        newText = edit.newText ?: ""
                    )
                }
                val applied = withContext(Dispatchers.Main.immediate) {
                    runCatching {
                        editorContainerState.applyTextEditsInTab(tabId, mappedEdits)
                    }.getOrDefault(false)
                }
                if (!applied) success = false
                return@forEach
            }

            // 文件未在编辑器中打开，直接修改文件
            val applied = withContext(Dispatchers.IO) {
                runCatching {
                    applyTextEditsToFile(file, sorted)
                }.getOrDefault(false)
            }

            if (!applied) success = false
        }

        return success
    }

    /**
     * 应用 TextEdit 列表到文件
     */
    private fun applyTextEditsToFile(file: File, edits: List<TextEdit>): Boolean {
        if (!file.isFile) return false
        val original = file.readText(Charsets.UTF_8)
        val updated = applyTextEditsToString(original, edits)
        file.writeText(updated, Charsets.UTF_8)
        return true
    }

    private fun applyTextEditsToString(original: String, edits: List<TextEdit>): String {
        var updated = original
        edits.forEach { textEdit ->
            val startOffset = positionToOffset(updated, textEdit.range.start.line, textEdit.range.start.character)
            val endOffset = positionToOffset(updated, textEdit.range.end.line, textEdit.range.end.character)
                .coerceAtLeast(startOffset)
            updated = updated.replaceRange(startOffset, endOffset, textEdit.newText ?: "")
        }
        return updated
    }

    private fun positionToOffset(text: String, line: Int, column: Int): Int {
        val lineStarts = computeLineStarts(text)
        val safeLine = line.coerceIn(0, lineStarts.lastIndex)
        val lineStart = lineStarts[safeLine]
        val lineEnd = if (safeLine + 1 < lineStarts.size) {
            (lineStarts[safeLine + 1] - 1).coerceAtLeast(lineStart)
        } else {
            text.length
        }
        val maxColumn = (lineEnd - lineStart).coerceAtLeast(0)
        val safeColumn = column.coerceIn(0, maxColumn)
        return lineStart + safeColumn
    }

    private fun computeLineStarts(text: String): IntArray {
        val starts = ArrayList<Int>()
        starts.add(0)
        text.forEachIndexed { index, ch ->
            if (ch == '\n') {
                starts.add(index + 1)
            }
        }
        return starts.toIntArray()
    }

    /**
     * 将 URI 转换为文件
     */
    private fun workspaceUriToFile(uri: String): File? = runCatching {
        val parsed = URI(uri)
        when {
            parsed.scheme == null -> File(uri)
            parsed.scheme.equals("file", ignoreCase = true) -> File(parsed)
            else -> null
        }
    }.getOrNull()

    // ============ 项目关闭 ============

    /**
     * 关闭项目并返回项目选择界面
     */
    suspend fun closeProjectAndReturn(forgetSession: Boolean): Boolean {
        val actionName = if (forgetSession) {
            Strings.action_close_and_forget.strOr(context)
        } else {
            Strings.action_close_project.strOr(context)
        }

        if (!ensureAllEditorsSaved(actionName)) return false

        if (!forgetSession) {
            editorManager.persistStateSnapshot()
        }
        editorManager.closeAll(clearPersistentState = forgetSession)

        if (forgetSession) {
            clearCurrentProjectState()
        }

        withContext(Dispatchers.IO) {
            projectSession.closeProject()
        }

        return true
    }

    /**
     * 清除当前项目状态
     */
    private fun clearCurrentProjectState() {
        val project = projectContext.getCurrentProject() ?: return
        val stateDir = ProjectDirStructure.getStateDir(project.rootPath)
        if (stateDir.exists()) stateDir.deleteRecursively()
        val tinaideDir = ProjectDirStructure.getTinaideDir(project.rootPath)
        if (tinaideDir.exists() && tinaideDir.list()?.isEmpty() == true) {
            tinaideDir.delete()
        }
    }

    // ============ 辅助方法 ============

    private fun showToast(message: String, type: ToastType) {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast(message, type))
        }
    }

    private fun readTextFromClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    private fun writeTextToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun guessLineCommentToken(extension: String): String = when (extension.lowercase()) {
        in CxxFileSupport.editorRelatedExtensions,
        "java", "kt", "kts",
        "js", "ts",
        "rs", "go", "cs", "swift" -> "//"
        "py", "sh", "bash", "zsh", "rb", "pl", "yaml", "yml", "toml", "ini", "conf" -> "#"
        else -> "//"
    }

    companion object {
        private const val TAG = "MainActionsViewModel"
    }
}

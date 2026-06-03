package com.wuxianggujun.tinaide.ai.integration

import android.content.Context
import com.wuxianggujun.tinaide.ai.tools.executor.editor.CurrentFileInfo
import com.wuxianggujun.tinaide.ai.tools.executor.editor.EditorToolCallbacks
import com.wuxianggujun.tinaide.ai.tools.executor.editor.SelectedCodeInfo
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.format.CodeFormatter
import com.wuxianggujun.tinaide.core.format.FormatResult
import com.wuxianggujun.tinaide.core.format.FormatStyle
import com.wuxianggujun.tinaide.core.lang.CxxFileSupport
import com.wuxianggujun.tinaide.core.linux.LinuxEnvironmentProvider
import com.wuxianggujun.tinaide.core.linux.LinuxRunModePolicy
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 编辑器工具回调实现
 *
 * 通过当前稳定的 EditorContainerState API 提供能力，
 * 避免直接依赖已重构的底层编辑器实例。
 */
class EditorToolCallbacksImpl(
    private val context: Context,
    private val editorState: EditorContainerState,
    private val projectRoot: String,
    private val linuxEnvironmentProvider: LinuxEnvironmentProvider
) : EditorToolCallbacks {

    companion object {
        private const val TAG = "EditorToolCallbacksImpl"
    }

    override fun getCurrentFile(): CurrentFileInfo? {
        val context = editorState.snapshotActiveFileContextOrNull() ?: return null
        return CurrentFileInfo(
            fileName = PathUtils.toRelativePath(context.file.absolutePath, projectRoot),
            language = context.language,
            content = context.content
        )
    }

    override fun getSelectedCode(): SelectedCodeInfo? {
        val selection = editorState.snapshotActiveSelectedCodeContextOrNull() ?: return null
        return SelectedCodeInfo(
            fileName = PathUtils.toRelativePath(selection.file.absolutePath, projectRoot),
            language = selection.language,
            startLine = selection.startLine,
            endLine = selection.endLine,
            content = selection.content
        )
    }

    override fun insertCode(code: String) {
        editorState.insertTextAtCursor(code)
    }

    override fun replaceSelectedCode(code: String) {
        val replaced = editorState.replaceSelectionInActiveTab(code)
        if (!replaced) {
            editorState.insertTextAtCursor(code)
        }
    }

    override suspend fun formatCode(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = PathUtils.resolveProjectFile(filePath, projectRoot)
                if (!file.exists()) {
                    Timber.tag(TAG).e("File not found: $filePath")
                    return@withContext false
                }

                val extension = file.extension.lowercase()
                if (extension !in CxxFileSupport.editorRelatedExtensions) {
                    Timber.tag(TAG).w("File type not supported for formatting: $extension")
                    return@withContext false
                }

                val isOpenInEditor = editorState.findOpenTabIdByFileOrNull(file) != null
                val content = when {
                    isOpenInEditor -> editorState.readTextFromOpenTabIfPresent(file)
                    else -> file.readText()
                } ?: run {
                    Timber.tag(TAG).e("Failed to read file content for formatting: %s", filePath)
                    return@withContext false
                }

                val formatter = CodeFormatter(
                    context = context,
                    linuxEnvironmentProvider = linuxEnvironmentProvider
                )

                if (!formatter.isAvailable()) {
                    Timber.tag(TAG).e("clang-format is not available")
                    return@withContext false
                }

                val runMode = LinuxRunModePolicy.resolve(
                    configuredMode = Prefs.clangFormatRunMode,
                    linuxEnvironmentAvailable = linuxEnvironmentProvider.get().isAvailable()
                )

                val formatFilePath = when (runMode) {
                    LinuxRunModePolicy.RunMode.PROOT -> {
                        linuxEnvironmentProvider.get().toGuestPath(file.absolutePath)
                    }
                    LinuxRunModePolicy.RunMode.NATIVE -> file.absolutePath
                }

                val result = formatter.format(
                    content = content,
                    fileName = formatFilePath,
                    style = FormatStyle.FILE
                )

                when (result) {
                    is FormatResult.Success -> {
                        if (result.formattedContent != content) {
                            val updated = if (isOpenInEditor) {
                                withContext(Dispatchers.Main) {
                                    editorState.updateOpenTabTextIfPresent(file, result.formattedContent)
                                }
                            } else {
                                file.writeText(result.formattedContent)
                                true
                            }
                            if (!updated) {
                                Timber.tag(TAG).e("Failed to apply formatted content: %s", filePath)
                                return@withContext false
                            }
                            Timber.tag(TAG).i("Code formatted successfully: $filePath")
                            true
                        } else {
                            Timber.tag(TAG).i("Code already formatted: $filePath")
                            true
                        }
                    }
                    is FormatResult.Error -> {
                        Timber.tag(TAG).e("Format failed: ${result.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to format code: $filePath")
                false
            }
        }
    }

    override suspend fun formatCodeRange(filePath: String, startLine: Int, endLine: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = PathUtils.resolveProjectFile(filePath, projectRoot)
                if (!file.exists()) {
                    Timber.tag(TAG).e("File not found: $filePath")
                    return@withContext false
                }

                val extension = file.extension.lowercase()
                if (extension !in CxxFileSupport.editorRelatedExtensions) {
                    Timber.tag(TAG).w("File type not supported for range formatting: $extension")
                    return@withContext false
                }

                val isOpenInEditor = editorState.findOpenTabIdByFileOrNull(file) != null
                val content = when {
                    isOpenInEditor -> editorState.readTextFromOpenTabIfPresent(file)
                    else -> file.readText()
                } ?: run {
                    Timber.tag(TAG).e("Failed to read file content for range formatting: %s", filePath)
                    return@withContext false
                }

                val formatter = CodeFormatter(
                    context = context,
                    linuxEnvironmentProvider = linuxEnvironmentProvider
                )

                if (!formatter.isAvailable()) {
                    Timber.tag(TAG).e("clang-format is not available")
                    return@withContext false
                }

                val totalLines = content.lineSequence().count().coerceAtLeast(1)
                val safeStartLine = startLine.coerceIn(1, totalLines)
                val safeEndLine = endLine.coerceIn(safeStartLine, totalLines)
                val runMode = LinuxRunModePolicy.resolve(
                    configuredMode = Prefs.clangFormatRunMode,
                    linuxEnvironmentAvailable = linuxEnvironmentProvider.get().isAvailable()
                )

                val formatFilePath = when (runMode) {
                    LinuxRunModePolicy.RunMode.PROOT -> {
                        linuxEnvironmentProvider.get().toGuestPath(file.absolutePath)
                    }
                    LinuxRunModePolicy.RunMode.NATIVE -> file.absolutePath
                }

                val result = formatter.formatRange(
                    content = content,
                    fileName = formatFilePath,
                    startLine = safeStartLine,
                    endLine = safeEndLine,
                    style = FormatStyle.FILE
                )

                when (result) {
                    is FormatResult.Success -> {
                        if (result.formattedContent != content) {
                            val updated = if (isOpenInEditor) {
                                withContext(Dispatchers.Main) {
                                    editorState.updateOpenTabTextIfPresent(file, result.formattedContent)
                                }
                            } else {
                                file.writeText(result.formattedContent)
                                true
                            }
                            if (!updated) {
                                Timber.tag(TAG).e("Failed to apply range formatted content: %s", filePath)
                                return@withContext false
                            }
                        }
                        Timber.tag(TAG).i(
                            "Code range formatted successfully: %s (%d:%d)",
                            filePath,
                            safeStartLine,
                            safeEndLine
                        )
                        true
                    }
                    is FormatResult.Error -> {
                        Timber.tag(TAG).e("Range format failed: ${result.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to range format code: $filePath")
                false
            }
        }
    }
}

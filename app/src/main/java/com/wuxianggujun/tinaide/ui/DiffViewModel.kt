package com.wuxianggujun.tinaide.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.i18n.R
import com.wuxianggujun.tinaide.diff.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 文件对比 ViewModel
 *
 * 管理文件对比的状态和业务逻辑
 */
class DiffViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val diffEngine = DiffEngine()

    // UI 状态
    private val _uiState = MutableStateFlow(DiffUiState())
    val uiState: StateFlow<DiffUiState> = _uiState.asStateFlow()

    // 当前对比结果
    private val _fileDiff = MutableStateFlow<FileDiff?>(null)
    val fileDiff: StateFlow<FileDiff?> = _fileDiff.asStateFlow()

    /**
     * 对比两个文件
     */
    fun compareTwoFiles(leftPath: String, rightPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val leftFile = File(leftPath)
                val rightFile = File(rightPath)

                // 验证文件
                if (!leftFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ctx.getString(R.string.diff_error_left_not_found, leftPath)
                    )
                    return@launch
                }

                if (!rightFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ctx.getString(R.string.diff_error_right_not_found, rightPath)
                    )
                    return@launch
                }

                // 检查文件大小
                val maxFileSize = 5 * 1024 * 1024L // 5MB
                if (leftFile.length() > maxFileSize || rightFile.length() > maxFileSize) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ctx.getString(R.string.diff_error_file_too_large)
                    )
                    return@launch
                }

                // 执行对比
                val diff = diffEngine.diff(leftPath, rightPath)

                _fileDiff.value = diff
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    leftFile = DiffFileInfo(leftPath, leftFile.name),
                    rightFile = DiffFileInfo(rightPath, rightFile.name)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.diff_error_failed, e.message ?: "")
                )
            }
        }
    }

    /**
     * 对比文件与文本
     */
    fun compareFileWithText(
        filePath: String,
        text: String,
        fileLabel: String = ctx.getString(R.string.diff_label_file),
        textLabel: String = ctx.getString(R.string.diff_label_editor)
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val file = File(filePath)

                if (!file.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ctx.getString(R.string.diff_error_file_not_found, filePath)
                    )
                    return@launch
                }

                val fileContent = file.readText()

                val diff = diffEngine.diff(
                    leftContent = fileContent,
                    rightContent = text,
                    leftFile = DiffFile(
                        path = filePath,
                        name = file.name,
                        label = fileLabel,
                        content = fileContent
                    ),
                    rightFile = DiffFile(
                        path = "",
                        name = ctx.getString(R.string.diff_editor_content),
                        label = textLabel,
                        content = text
                    )
                )

                _fileDiff.value = diff
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    leftFile = DiffFileInfo(filePath, file.name),
                    rightFile = DiffFileInfo("", textLabel)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.diff_error_failed, e.message ?: "")
                )
            }
        }
    }

    /**
     * 对比两段文本
     */
    fun compareTwoTexts(
        leftText: String,
        rightText: String,
        leftLabel: String = ctx.getString(R.string.diff_label_left),
        rightLabel: String = ctx.getString(R.string.diff_label_right)
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val diff = diffEngine.diff(
                    leftContent = leftText,
                    rightContent = rightText,
                    leftFile = DiffFile(
                        path = "",
                        name = leftLabel,
                        label = leftLabel,
                        content = leftText
                    ),
                    rightFile = DiffFile(
                        path = "",
                        name = rightLabel,
                        label = rightLabel,
                        content = rightText
                    )
                )

                _fileDiff.value = diff
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    leftFile = DiffFileInfo("", leftLabel),
                    rightFile = DiffFileInfo("", rightLabel)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ctx.getString(R.string.diff_error_failed, e.message ?: "")
                )
            }
        }
    }

    /**
     * 切换视图模式
     */
    fun setViewMode(mode: DiffViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    /**
     * 清除对比结果
     */
    fun clearDiff() {
        _fileDiff.value = null
        _uiState.value = DiffUiState()
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * Diff UI 状态
 */
data class DiffUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val leftFile: DiffFileInfo? = null,
    val rightFile: DiffFileInfo? = null,
    val viewMode: DiffViewMode = DiffViewMode.UNIFIED
)

/**
 * 文件信息
 */
data class DiffFileInfo(
    val path: String,
    val name: String
)

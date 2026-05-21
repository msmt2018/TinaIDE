package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.runtime.Immutable
import java.io.File

/**
 * 文件树节点数据
 */
@Immutable
data class FileTreeNode(
    val absolutePath: String,
    val name: String,
    val relativePath: String?,
    val level: Int,
    val isDirectory: Boolean,
    val isExpanded: Boolean = false,
    val isArtifactsDirectory: Boolean = false
)

@Immutable
data class FileTreeUiState(
    val rootPath: String? = null,
    val visibleNodes: List<FileTreeNode> = emptyList(),
    val selectedPath: String? = null,
    val isRefreshing: Boolean = false
)

/**
 * 文件上下文菜单操作
 */
sealed class FileContextAction {
    data class Rename(val file: File) : FileContextAction()
    data class Delete(val file: File) : FileContextAction()
    data class CopyPath(val file: File) : FileContextAction()
    data class CopyName(val file: File) : FileContextAction()
    data class CopyRelativePath(val file: File) : FileContextAction()
    data class NewFile(val parentDir: File) : FileContextAction()
    data class NewFolder(val parentDir: File) : FileContextAction()
    data class Duplicate(val file: File) : FileContextAction()
    data class OpenWith(val file: File) : FileContextAction()
    data class Share(val file: File) : FileContextAction()
    data class RevealInFileManager(val file: File) : FileContextAction()
}

package com.wuxianggujun.tinaide.model

import com.wuxianggujun.tinaide.ui.PanelType
import com.wuxianggujun.tinaide.ui.Theme
import java.io.File

/**
 * 项目类型枚举
 */
enum class ProjectType {
    CPP,      // C++ 项目
    C,        // C 项目
    MIXED,    // 混合项目
    UNKNOWN   // 未知类型
}

/**
 * 项目配置
 */
data class ProjectConfig(
    val language: String,
    val buildSystem: String = "manual",  // "manual", "cmake", "make"
    val sourceDirectories: List<String> = emptyList(),
    val includeDirectories: List<String> = emptyList(),
    val libraries: List<String> = emptyList(),
    val compilerFlags: List<String> = emptyList()
)

/**
 * 项目模型
 */
data class Project(
    val id: String,
    val name: String,
    val rootPath: String,
    val type: ProjectType,
    val config: ProjectConfig,
    val createdAt: Long,
    val lastModified: Long
)

/**
 * 文件节点
 */
data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val extension: String?,
    val size: Long,
    val lastModified: Long,
    val children: List<FileNode>? = null
)

/**
 * 编辑器状态
 */
data class EditorState(
    val filePath: String,
    val cursorLine: Int,
    val cursorColumn: Int,
    val scrollX: Int,
    val scrollY: Int,
    val selectionStart: Int,
    val selectionEnd: Int,
    val isDirty: Boolean
)

/**
 * 工作区状态
 */
data class WorkspaceState(
    val currentProject: String?,
    val openFiles: List<String>,
    val activeFile: String?,
    val editorStates: Map<String, EditorState>,
    val panelVisibility: Map<PanelType, Boolean>,
    val theme: Theme
)

/**
 * 插件元数据
 */
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val supportedExtensions: List<String>,
    val dependencies: List<String> = emptyList(),
    val enabled: Boolean = true
)

/**
 * 插件配置
 */
data class PluginConfig(
    val pluginId: String,
    val settings: Map<String, Any>
)

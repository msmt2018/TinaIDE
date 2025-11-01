package com.wuxianggujun.tinaide.file

import java.io.File

/**
 * 文件管理器接口
 * 负责管理项目文件和目录结构
 */
interface IFileManager {
    /**
     * 打开项目
     */
    fun openProject(path: String): Project
    
    /**
     * 关闭项目
     */
    fun closeProject()
    
    /**
     * 获取当前项目
     */
    fun getCurrentProject(): Project?
    
    /**
     * 创建文件
     */
    fun createFile(parent: File, name: String): File
    
    /**
     * 创建目录
     */
    fun createDirectory(parent: File, name: String): File
    
    /**
     * 删除文件
     */
    fun deleteFile(file: File): Boolean
    
    /**
     * 重命名文件
     */
    fun renameFile(file: File, newName: String): Boolean
    
    /**
     * 复制文件
     */
    fun copyFile(source: File, destination: File): Boolean
    
    /**
     * 移动文件
     */
    fun moveFile(source: File, destination: File): Boolean
    
    /**
     * 搜索文件
     */
    fun searchFiles(query: String): List<File>
    
    /**
     * 获取最近打开的文件
     */
    fun getRecentFiles(): List<File>
    
    /**
     * 添加文件监听器
     */
    fun addFileWatcher(path: String, listener: FileChangeListener)
    
    /**
     * 移除文件监听器
     */
    fun removeFileWatcher(path: String)
}

/**
 * 项目数据类
 */
data class Project(
    val name: String,
    val rootPath: String,
    val files: List<File>
)

/**
 * 文件变更监听器
 */
interface FileChangeListener {
    fun onFileCreated(file: File)
    fun onFileModified(file: File)
    fun onFileDeleted(file: File)
}

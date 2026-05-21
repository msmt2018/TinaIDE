package com.wuxianggujun.tinaide.file

import java.io.File

/**
 * 文件与目录基础操作接口。
 */
interface IFileOperations {
    fun createFile(parent: File, name: String): File
    fun createDirectory(parent: File, name: String): File
    fun deleteFile(file: File): Boolean
    fun renameFile(file: File, newName: String): Boolean
    fun copyFile(source: File, destination: File): Boolean
    fun moveFile(source: File, destination: File): Boolean
    fun searchFiles(query: String): List<File>
}

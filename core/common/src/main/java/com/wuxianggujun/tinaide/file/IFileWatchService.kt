package com.wuxianggujun.tinaide.file

/**
 * 文件监听服务接口。
 */
interface FileWatchRegistration {
    fun dispose()
}

interface IFileWatchService {
    fun addFileWatcher(path: String, listener: FileChangeListener): FileWatchRegistration
}

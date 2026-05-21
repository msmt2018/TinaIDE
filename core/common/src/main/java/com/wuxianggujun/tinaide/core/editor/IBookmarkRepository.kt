package com.wuxianggujun.tinaide.core.editor

import kotlinx.coroutines.flow.StateFlow

/**
 * 书签仓储接口（项目级）
 *
 * 对外提供稳定 API，隐藏缓存/持久化细节。
 *
 * 架构说明：
 * - 接口定义在 core:common 层
 * - 实现类在 feature:editor 层（BookmarkService）
 * - 通过 Koin DI 注入
 */
interface IBookmarkRepository {
    /**
     * 获取指定项目的书签列表（Flow，自动监听数据变化）
     */
    fun bookmarksFlow(projectPath: String): StateFlow<List<BookmarkInfo>>

    /**
     * 预加载指定项目的书签数据
     */
    suspend fun prefetch(projectPath: String)

    /**
     * 切换书签（如果存在则删除，不存在则添加）
     *
     * @return 添加的书签，如果是删除操作则返回 null
     */
    suspend fun toggle(projectPath: String, filePath: String, line: Int): BookmarkInfo?

    /**
     * 删除书签
     *
     * @return 是否成功删除
     */
    suspend fun remove(projectPath: String, filePath: String, line: Int): Boolean

    /**
     * 更新书签备注
     *
     * @return 是否成功更新
     */
    suspend fun updateNote(projectPath: String, filePath: String, line: Int, note: String): Boolean

    /**
     * 查找下一个书签
     */
    suspend fun findNext(projectPath: String, currentFilePath: String, currentLine: Int): BookmarkInfo?

    /**
     * 查找上一个书签
     */
    suspend fun findPrevious(projectPath: String, currentFilePath: String, currentLine: Int): BookmarkInfo?

    /**
     * 清除指定项目的所有书签
     */
    suspend fun clearAll(projectPath: String)

    /**
     * 清理不存在的文件的书签
     *
     * @return 清理的书签数量
     */
    suspend fun pruneMissingFiles(projectPath: String): Int
}

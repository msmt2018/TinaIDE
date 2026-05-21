package com.wuxianggujun.tinaide.terminal.persistence

import android.content.Context
import com.wuxianggujun.tinaide.terminal.persistence.db.TerminalDatabase
import com.wuxianggujun.tinaide.terminal.persistence.db.TerminalSessionEntity
import com.wuxianggujun.tinaide.terminal.persistence.db.TerminalStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 终端状态存储工具
 *
 * 使用 Room 数据库持久化终端会话状态。
 */
class TerminalStateStorage(context: Context) {
    private val database = TerminalDatabase.getInstance(context)
    private val stateDao = database.terminalStateDao()

    companion object {
        private const val TAG = "TerminalStateStorage"
    }

    /**
     * 加载终端状态
     *
     * @param projectPath 项目根目录路径
     * @return 终端状态，如果不存在或解析失败则返回 null
     */
    suspend fun load(projectPath: String): ProjectTerminalState? = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val state = stateDao.getState(projectPath)
            val sessions = stateDao.getSessions(projectPath)

            if (state == null && sessions.isEmpty()) {
                return@runCatching null
            }

            ProjectTerminalState(
                activeSessionId = state?.activeSessionId,
                sessions = sessions.map { it.toDomainModel() },
                updatedAt = state?.updatedAt ?: System.currentTimeMillis()
            ).normalized()
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "Failed to load terminal state for project: %s", projectPath)
        }.getOrNull()
    }

    /**
     * 保存终端状态
     *
     * @param projectPath 项目根目录路径
     * @param state 要保存的终端状态
     */
    suspend fun save(projectPath: String, state: ProjectTerminalState) = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = state.normalized()

            // 保存状态
            val stateEntity = TerminalStateEntity.fromSnapshot(projectPath, normalized)
            stateDao.insertState(stateEntity)

            // 先删除旧的会话，再插入新的
            stateDao.deleteSessions(projectPath)
            val sessionEntities = normalized.sessions.map { session ->
                TerminalSessionEntity.fromDomainModel(projectPath, session)
            }
            stateDao.insertSessions(sessionEntities)

            Timber.tag(TAG).d("Saved terminal state for project: %s (sessions=%d)", projectPath, normalized.sessions.size)
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "Failed to save terminal state for project: %s", projectPath)
        }
    }

    /**
     * 清除终端状态
     *
     * @param projectPath 项目根目录路径
     */
    suspend fun clear(projectPath: String) = withContext(Dispatchers.IO) {
        runCatching {
            stateDao.clearProjectTerminal(projectPath)
            Timber.tag(TAG).d("Cleared terminal state for project: %s", projectPath)
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "Failed to clear terminal state for project: %s", projectPath)
        }
    }

    /**
     * 检查终端状态是否存在
     *
     * @param projectPath 项目根目录路径
     * @return 如果状态文件存在则返回 true
     */
    suspend fun exists(projectPath: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val state = stateDao.getState(projectPath)
            val sessions = stateDao.getSessions(projectPath)
            state != null || sessions.isNotEmpty()
        }.getOrElse { false }
    }
}

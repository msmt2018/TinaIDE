package com.wuxianggujun.tinaide.file

import kotlinx.coroutines.flow.StateFlow

/**
 * 只读项目上下文接口。
 *
 * 用于隔离“获取当前项目”场景，避免调用方为了读取项目根路径
 * 或项目元数据而依赖完整的文件管理能力。
 *
 * 语义约束：
 * - [getCurrentProject] 仅返回当前会话内存态，**绝不做隐式恢复**。
 * - 若需要从偏好恢复上次会话，请使用 [IProjectSession.restoreLastSession]。
 * - 订阅者可通过 [currentProjectFlow] 响应式监听当前项目变化。
 */
interface IProjectContext {
    fun getCurrentProject(): Project?

    val currentProjectFlow: StateFlow<Project?>
}

package com.wuxianggujun.tinaide.core.compile.action

/**
 * 构建意图:用户对"是否构建"的选择,与"启动什么"正交。
 *
 * 与 [LaunchIntent] 组合成 [CompileRequest]。
 */
sealed interface BuildIntent {
    /**
     * 不构建,直接使用已有产物启动。
     *
     * 适用于:调试复用场景、手动 build 后只想连续启动。
     * 若缓存产物不存在,Planner 会返回 Invalid。
     */
    data object None : BuildIntent

    /**
     * 智能模式:产物新鲜则跳过构建,否则按需构建。
     *
     * 默认的 Run / Debug 语义走这个分支。
     */
    data object IfNeeded : BuildIntent

    /**
     * 强制重新构建,忽略所有缓存判断。
     *
     * 显式「Rebuild」或长按 Run 走这个分支。
     */
    data object Force : BuildIntent

    /**
     * 清理构建产物与中间文件。
     *
     * @property reconfigure 清理后是否立即 reconfigure(CMake 专用)
     */
    data class Clean(val reconfigure: Boolean = false) : BuildIntent
}

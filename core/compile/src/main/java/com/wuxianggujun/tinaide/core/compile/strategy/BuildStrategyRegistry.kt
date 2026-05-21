package com.wuxianggujun.tinaide.core.compile.strategy

import com.wuxianggujun.tinaide.core.compile.BuildSystem

/**
 * 策略注册表。供 `BuildPlanner` / `BuildExecutor` 按 [BuildSystem] 解析对应策略。
 *
 * 走 Koin 注入,构造期传入策略集合,避免 Planner 直接 `new`(满足 DIP)。
 *
 * 同一 [BuildSystem] 只允许一个策略;重复注册直接抛异常,避免运行期静默使用错误策略。
 */
class BuildStrategyRegistry(strategies: List<BuildStrategy>) {

    private val bySystem: Map<BuildSystem, BuildStrategy> = buildMap {
        strategies.forEach { s ->
            require(!containsKey(s.buildSystem)) {
                "duplicate strategy for ${s.buildSystem}: ${get(s.buildSystem)!!::class.simpleName} vs ${s::class.simpleName}"
            }
            put(s.buildSystem, s)
        }
    }

    /** 按构建系统查找策略;找不到返回 null,由 Planner 返回 `Invalid`。 */
    fun resolve(buildSystem: BuildSystem): BuildStrategy? = bySystem[buildSystem]

    /** 全部已注册策略(供调试 / UI 展示用)。 */
    fun all(): Collection<BuildStrategy> = bySystem.values
}

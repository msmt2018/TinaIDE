package com.wuxianggujun.tinaide.core.compile.pipeline

import com.wuxianggujun.tinaide.core.compile.artifact.Artifact
import com.wuxianggujun.tinaide.core.compile.artifact.ArtifactSpec
import com.wuxianggujun.tinaide.core.compile.artifact.BuildFingerprint
import com.wuxianggujun.tinaide.core.compile.strategy.BuildStrategy

/**
 * Planner 决策结果:表达"下一步到底该做什么"。
 *
 * 四态互斥:
 * - [Skip]  缓存命中,直接启动(带上 reason 供 UI/日志展示)
 * - [Build] 需要执行构建(含 Force / IfNeeded 触发 mismatch 两个来源)
 * - [CleanOnly] 仅清理,不继续启动
 * - [Invalid] 请求不合法(如 BuildIntent.None 但无缓存),Orchestrator 直接返回 BuildReport.Invalid
 */
sealed interface BuildPlan {

    data class Skip(val artifact: Artifact, val reason: String) : BuildPlan

    data class Build(
        val strategy: BuildStrategy,
        val spec: ArtifactSpec,
        val fingerprint: BuildFingerprint,
        val reason: String,
    ) : BuildPlan

    data class CleanOnly(
        val strategy: BuildStrategy,
        val reconfigure: Boolean,
    ) : BuildPlan

    data class Invalid(val reason: String) : BuildPlan
}

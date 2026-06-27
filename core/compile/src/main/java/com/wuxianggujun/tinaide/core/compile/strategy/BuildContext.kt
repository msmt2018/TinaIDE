package com.wuxianggujun.tinaide.core.compile.strategy

import android.content.Context
import com.wuxianggujun.tinaide.core.compile.BuildOptions
import com.wuxianggujun.tinaide.core.compile.BuildSystem
import java.io.File

/**
 * 策略执行上下文。
 *
 * 由 `BuildOrchestrator`/`EnvironmentValidator` 在进入 Planner 前构造一次,
 * 其后 Strategy / Planner / Executor 共享同一实例。
 *
 * 与旧 `CompileProjectUseCase` 的"一堆散参"签名相比,用一个 Context 传递,
 * 避免 Strategy 方法签名膨胀,同时方便后续扩展(如新增 `projectId` 用于 ArtifactId)。
 *
 * - [projectId] 产物身份用稳定 id(建议 projectRoot 绝对路径的 SHA-256 前 16 字节)
 * - [target] 目标名(CMake target / 单文件名);null 走策略默认解析
 */
data class BuildContext(
    val appContext: Context,
    val projectRoot: File,
    val buildDir: File,
    val buildSystem: BuildSystem,
    val options: BuildOptions,
    val projectId: String,
    val target: String? = null,
    val buildReason: String? = null,
)

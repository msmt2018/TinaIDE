package com.wuxianggujun.tinaide.core.compile.artifact

import java.io.File

/**
 * 产物规格:策略层的纯函数 `describeOutput(...)` 返回的描述,表达
 * "在当前配置下,这个 target 会产出怎样的产物"。
 *
 * Planner 基于此规格 + [ArtifactStore] 判断是否可跳过构建。
 * Executor 基于此规格驱动具体 Strategy 执行构建。
 *
 * [sources] 是参与该 target 增量判定的输入文件(绝对路径)：
 * 既可以是源码，也可以是 Makefile/CMakeLists.txt 之类的构建脚本。
 * Planner 用来做 mtime/size 增量校验，Fingerprint 也会把它们的路径布局纳入计算。
 */
data class ArtifactSpec(
    val id: ArtifactId,
    val expectedPath: File,
    val kind: ArtifactKind,
    val sources: List<File>,
)

package com.wuxianggujun.tinaide.core.compile.artifact

import java.io.File

/**
 * 产物存储,抽象以便测试替换。生产实现见 [JsonArtifactStore]。
 *
 * 作用域:每个 `buildDir` 一套存储,物理文件位于 `<buildDir>/.tina/artifacts/` 目录下的 `.json` 文件。
 */
interface ArtifactStore {
    /** 查找指定 id 的产物记录,找不到返回 null。不校验产物文件是否仍存在(交给 Planner)。 */
    suspend fun find(id: ArtifactId, buildDir: File): Artifact?

    /** 注册/覆盖产物记录。应在构建成功后由 Orchestrator 调用。 */
    suspend fun register(artifact: Artifact, buildDir: File)

    /** 删除指定 id 的产物记录(不删除产物文件)。 */
    suspend fun invalidate(id: ArtifactId, buildDir: File)

    /** 列出 buildDir 下所有已注册产物(用于 Clean / 调试)。 */
    suspend fun listAll(buildDir: File): List<Artifact>

    /** 清空 buildDir 下所有产物记录(Clean 场景)。 */
    suspend fun clearAll(buildDir: File)
}

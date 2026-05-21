package com.wuxianggujun.tinaide.core.compile

import com.wuxianggujun.tinaide.storage.ProjectDirStructure
import java.io.File

/**
 * 将私有工作区中的最终构建产物导出到项目目录。
 *
 * 设计原则：
 * - 真实构建仍发生在私有 workspace/build；
 * - 项目目录只保存可见的导出副本，不承载真实构建缓存；
 * - 导出失败不影响构建结果，只用于提升可见性与可分享性。
 */
internal object BuildArtifactExporter {

    data class ExportResult(
        val sourceArtifact: File,
        val exportedArtifact: File
    )

    fun export(
        projectRoot: File,
        artifactPath: String,
        variantName: String? = null
    ): Result<ExportResult> {
        return runCatching {
            val sourceArtifact = File(artifactPath)
            require(sourceArtifact.isFile) {
                "Artifact not found: ${sourceArtifact.absolutePath}"
            }

            val artifactsDir = variantName
                ?.takeIf { it.isNotBlank() }
                ?.let { variant ->
                    File(ProjectDirStructure.getArtifactsDir(projectRoot.absolutePath), variant)
                }
                ?: ProjectDirStructure.getArtifactsDir(projectRoot.absolutePath)
            artifactsDir.mkdirs()
            val exportedArtifact = File(artifactsDir, sourceArtifact.name)

            if (sourceArtifact.samePathAs(exportedArtifact)) {
                return@runCatching ExportResult(
                    sourceArtifact = sourceArtifact,
                    exportedArtifact = exportedArtifact
                )
            }

            sourceArtifact.copyTo(exportedArtifact, overwrite = true)
            ExportResult(
                sourceArtifact = sourceArtifact,
                exportedArtifact = exportedArtifact
            )
        }
    }

    private fun File.samePathAs(other: File): Boolean {
        val thisPath = runCatching { canonicalPath }.getOrElse { absolutePath }
        val otherPath = runCatching { other.canonicalPath }.getOrElse { other.absolutePath }
        return thisPath == otherPath
    }
}

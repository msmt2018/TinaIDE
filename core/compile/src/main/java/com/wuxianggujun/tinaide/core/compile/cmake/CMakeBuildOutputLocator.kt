package com.wuxianggujun.tinaide.core.compile.cmake

import com.wuxianggujun.tinaide.core.compile.MakeBuildOutputLocator
import com.wuxianggujun.tinaide.core.compile.TargetInfo
import java.io.File

/**
 * CMake 构建产物定位器。
 *
 * 统一处理以下场景：
 * - 私有 build 目录中的可执行文件、共享库、静态库
 * - 目标名与真实产物名存在 lib 前缀 / 扩展名差异
 * - CMake 解析失败或目标缺失时，回退到 build 目录递归扫描
 */
internal object CMakeBuildOutputLocator {

    enum class ArtifactKind {
        EXECUTABLE,
        SHARED_LIBRARY,
        STATIC_LIBRARY
    }

    private const val MAX_SCAN_DEPTH = 8

    private val commonSubdirs = listOf(
        "",
        "bin",
        "lib",
        "src",
        "Debug",
        "Release",
        "RelWithDebInfo",
        "MinSizeRel"
    )

    fun find(
        buildDir: File,
        target: TargetInfo?,
        acceptedKinds: List<ArtifactKind>
    ): String? {
        if (!buildDir.isDirectory || acceptedKinds.isEmpty()) return null

        val targetName = target?.outputName?.takeIf { it.isNotBlank() } ?: target?.name
        val orderedKinds = orderKinds(
            acceptedKinds = acceptedKinds,
            preferredKind = target?.type?.toArtifactKind()
        )

        if (!targetName.isNullOrBlank()) {
            orderedKinds.firstNotNullOfOrNull { kind ->
                findNamedArtifact(buildDir, targetName, kind)
                    ?: findNamedArtifactFromNinja(buildDir, targetName, kind)
            }?.let { return it.absolutePath }
        }

        return orderedKinds.asSequence()
            .flatMap { kind ->
                sequenceOf(
                    findNewestArtifact(buildDir, kind),
                    findNewestArtifactFromNinja(buildDir, kind)
                )
            }
            .filterNotNull()
            .maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    private fun findNamedArtifactFromNinja(
        buildDir: File,
        targetName: String,
        kind: ArtifactKind
    ): File? {
        val candidateNames = candidateNames(targetName, kind)
        if (candidateNames.isEmpty()) return null

        return parseNinjaArtifacts(buildDir)
            .asSequence()
            .filter { artifact ->
                artifact.kind == kind &&
                    artifact.outputFile.exists() &&
                    when (kind) {
                        ArtifactKind.EXECUTABLE -> {
                            val fileName = artifact.outputFile.name
                            fileName in candidateNames || artifact.outputFile.nameWithoutExtension in candidateNames
                        }
                        else -> artifact.outputFile.name in candidateNames
                    }
            }
            .map { it.outputFile }
            .firstOrNull()
    }

    private fun findNewestArtifactFromNinja(buildDir: File, kind: ArtifactKind): File? {
        return parseNinjaArtifacts(buildDir)
            .asSequence()
            .filter { artifact ->
                artifact.kind == kind && artifact.outputFile.exists()
            }
            .map { it.outputFile }
            .maxByOrNull { it.lastModified() }
    }

    private fun parseNinjaArtifacts(buildDir: File): List<NinjaArtifact> {
        val ninjaFile = File(buildDir, "build.ninja")
        if (!ninjaFile.isFile) return emptyList()

        return runCatching {
            ninjaFile.useLines { lines ->
                lines.mapNotNull { line -> parseNinjaArtifactLine(buildDir, line) }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseNinjaArtifactLine(buildDir: File, line: String): NinjaArtifact? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("build ")) return null

        val match = NINJA_BUILD_LINE_REGEX.matchEntire(trimmed) ?: return null
        val outputsSpec = match.groupValues[1]
        val ruleName = match.groupValues[2]
        val artifactKind = mapRuleNameToKind(ruleName) ?: return null

        val explicitOutputs = outputsSpec
            .substringBefore(" ||")
            .substringBefore(" |")
            .trim()
            .split(OUTPUT_SEPARATOR_REGEX)
            .filter { it.isNotBlank() }

        return explicitOutputs.firstNotNullOfOrNull { output ->
            val resolvedFile = resolveNinjaOutputFile(buildDir, output)
            if (!matchesKind(resolvedFile, artifactKind)) {
                null
            } else {
                NinjaArtifact(
                    outputFile = resolvedFile,
                    kind = artifactKind
                )
            }
        }
    }

    private fun orderKinds(
        acceptedKinds: List<ArtifactKind>,
        preferredKind: ArtifactKind?
    ): List<ArtifactKind> {
        val distinctKinds = acceptedKinds.distinct()
        if (preferredKind == null || preferredKind !in distinctKinds) {
            return distinctKinds
        }
        return buildList {
            add(preferredKind)
            addAll(distinctKinds.filter { it != preferredKind })
        }
    }

    private fun findNamedArtifact(
        buildDir: File,
        targetName: String,
        kind: ArtifactKind
    ): File? {
        val candidateNames = candidateNames(targetName, kind)
        if (candidateNames.isEmpty()) return null

        commonSubdirs.asSequence()
            .map { subdir -> if (subdir.isEmpty()) buildDir else File(buildDir, subdir) }
            .filter { it.isDirectory }
            .flatMap { dir ->
                candidateNames.asSequence().map { candidateName ->
                    File(dir, candidateName)
                }
            }
            .firstOrNull { file -> matchesKind(file, kind) }
            ?.let { return it }

        return buildDir.walkTopDown()
            .maxDepth(MAX_SCAN_DEPTH)
            .firstOrNull { file ->
                matchesKind(file, kind) && when (kind) {
                    ArtifactKind.EXECUTABLE -> {
                        file.name in candidateNames || file.nameWithoutExtension in candidateNames
                    }
                    else -> file.name in candidateNames
                }
            }
    }

    private fun findNewestArtifact(buildDir: File, kind: ArtifactKind): File? {
        return buildDir.walkTopDown()
            .maxDepth(MAX_SCAN_DEPTH)
            .filter { file -> matchesKind(file, kind) }
            .maxByOrNull { it.lastModified() }
    }

    private fun candidateNames(targetName: String, kind: ArtifactKind): Set<String> {
        val baseNames = buildSet {
            addBaseNames(targetName)
            addBaseNames(File(targetName).name)
        }.filter { it.isNotBlank() }

        return when (kind) {
            ArtifactKind.EXECUTABLE -> baseNames.toSet()
            ArtifactKind.SHARED_LIBRARY -> baseNames.flatMapTo(linkedSetOf()) { base ->
                listOf("$base.so", "lib$base.so")
            }
            ArtifactKind.STATIC_LIBRARY -> baseNames.flatMapTo(linkedSetOf()) { base ->
                listOf("$base.a", "lib$base.a")
            }
        }
    }

    private fun MutableSet<String>.addBaseNames(rawName: String) {
        val trimmed = rawName.trim()
        if (trimmed.isBlank()) return

        val withoutSo = trimmed.removeSuffix(".so")
        val withoutArchive = withoutSo.removeSuffix(".a")
        add(withoutArchive)
        add(withoutArchive.removePrefix("lib"))
    }

    private fun matchesKind(file: File, kind: ArtifactKind): Boolean {
        if (!file.isFile || !file.exists() || isIgnoredPath(file)) return false
        return when (kind) {
            ArtifactKind.EXECUTABLE -> MakeBuildOutputLocator.isRunnableArtifact(file)
            ArtifactKind.SHARED_LIBRARY -> file.extension.equals("so", ignoreCase = true)
            ArtifactKind.STATIC_LIBRARY -> file.extension.equals("a", ignoreCase = true)
        }
    }

    private fun isIgnoredPath(file: File): Boolean {
        val absolutePath = file.absolutePath
        return absolutePath.contains("${File.separator}CMakeFiles${File.separator}") ||
            absolutePath.contains("${File.separator}CMakeScratch${File.separator}") ||
            file.name.startsWith("cmTC_")
    }

    private fun resolveNinjaOutputFile(buildDir: File, output: String): File {
        val normalized = output.trim().replace('\\', File.separatorChar)
        val rawFile = File(normalized)
        return if (rawFile.isAbsolute) {
            rawFile
        } else {
            File(buildDir, normalized).toPath().normalize().toFile()
        }
    }

    private fun mapRuleNameToKind(ruleName: String): ArtifactKind? {
        return when {
            ruleName.contains("STATIC_LIBRARY", ignoreCase = true) -> ArtifactKind.STATIC_LIBRARY
            ruleName.contains("SHARED_LIBRARY", ignoreCase = true) ||
                ruleName.contains("MODULE_LIBRARY", ignoreCase = true) -> ArtifactKind.SHARED_LIBRARY
            ruleName.contains("EXECUTABLE", ignoreCase = true) -> ArtifactKind.EXECUTABLE
            else -> null
        }
    }

    private fun TargetInfo.Type.toArtifactKind(): ArtifactKind? {
        return when (this) {
            TargetInfo.Type.EXECUTABLE -> ArtifactKind.EXECUTABLE
            TargetInfo.Type.SHARED_LIBRARY -> ArtifactKind.SHARED_LIBRARY
            TargetInfo.Type.STATIC_LIBRARY -> ArtifactKind.STATIC_LIBRARY
            TargetInfo.Type.OTHER -> null
        }
    }

    private data class NinjaArtifact(
        val outputFile: File,
        val kind: ArtifactKind
    )

    private val NINJA_BUILD_LINE_REGEX = Regex("""^build\s+(.+?)\s*:\s+(\S+).*$""")
    private val OUTPUT_SEPARATOR_REGEX = Regex("""\s+""")
}

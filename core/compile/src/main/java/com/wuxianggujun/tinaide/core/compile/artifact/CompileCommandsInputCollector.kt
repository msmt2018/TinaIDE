package com.wuxianggujun.tinaide.core.compile.artifact

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object CompileCommandsInputCollector {

    private const val COMPILE_COMMANDS_FILE = "compile_commands.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun collect(
        projectRoot: File,
        buildDir: File,
        targetNames: Set<String>,
    ): CompileCommandsInputs? {
        val databaseFile = File(buildDir, COMPILE_COMMANDS_FILE).takeIf { it.isFile } ?: return null
        val entries = runCatching {
            json.decodeFromString<List<CompileCommandEntry>>(databaseFile.readText(Charsets.UTF_8))
        }.getOrNull().orEmpty()

        val resolvedEntries = entries
            .mapNotNull { entry ->
                val source = resolveSourceFile(projectRoot, entry) ?: return@mapNotNull null
                ResolvedCompileCommandEntry(
                    source = source,
                    targetName = resolveTargetName(entry),
                )
            }
            .filter { it.source.isFile }

        if (resolvedEntries.isEmpty()) return null

        val targetMatchedEntries = targetNames
            .takeIf { it.isNotEmpty() }
            ?.let { names -> resolvedEntries.filter { it.targetName in names } }
            .orEmpty()

        val selectedEntries = targetMatchedEntries.ifEmpty { resolvedEntries }
        return CompileCommandsInputs(
            databaseFile = databaseFile,
            sources = selectedEntries.map { it.source },
        )
    }

    private fun resolveSourceFile(projectRoot: File, entry: CompileCommandEntry): File? {
        val rawFile = entry.file?.trim().orEmpty()
        if (rawFile.isBlank()) return null

        val resolved = resolvePath(rawFile, entry.directory?.trim().orEmpty(), projectRoot)
        if (resolved.isFile) return resolved.absoluteFile

        val projectRelative = File(projectRoot, rawFile)
        return projectRelative.takeIf { it.isFile }?.absoluteFile
    }

    private fun resolvePath(rawPath: String, directory: String, projectRoot: File): File {
        if (isAbsolutePath(rawPath)) return File(rawPath)
        val baseDir = directory
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isAbsolute }
            ?: projectRoot
        return File(baseDir, rawPath)
    }

    private fun isAbsolutePath(path: String): Boolean =
        File(path).isAbsolute || path.startsWith("/")

    private fun resolveTargetName(entry: CompileCommandEntry): String? {
        val candidates = buildList {
            entry.output?.let(::add)
            entry.arguments?.joinToString(separator = " ")?.let(::add)
            entry.command?.let(::add)
        }
        return candidates.firstNotNullOfOrNull(::extractTargetName)
    }

    private fun extractTargetName(text: String): String? {
        val normalized = text.replace('\\', '/')
        return Regex("""(?:^|/)CMakeFiles/([^/]+)\.dir(?:/|$)""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    data class CompileCommandsInputs(
        val databaseFile: File,
        val sources: List<File>,
    )

    private data class ResolvedCompileCommandEntry(
        val source: File,
        val targetName: String?,
    )

    @Serializable
    private data class CompileCommandEntry(
        val directory: String? = null,
        val command: String? = null,
        val arguments: List<String>? = null,
        val file: String? = null,
        val output: String? = null,
    )
}

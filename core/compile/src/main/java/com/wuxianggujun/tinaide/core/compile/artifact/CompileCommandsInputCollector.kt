package com.wuxianggujun.tinaide.core.compile.artifact

import com.wuxianggujun.tinaide.core.compile.BuildDiagnosticsLog
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
        val databaseFile = File(buildDir, COMPILE_COMMANDS_FILE)
        if (!databaseFile.isFile) {
            BuildDiagnosticsLog.d {
                "compile_commands input collect: database missing path=${databaseFile.absolutePath} targets=${targetNames.summary()}"
            }
            return null
        }

        val entries = runCatching {
            json.decodeFromString<List<CompileCommandEntry>>(databaseFile.readText(Charsets.UTF_8))
        }.getOrElse { throwable ->
            BuildDiagnosticsLog.w(throwable) {
                "compile_commands input collect: parse failed path=${databaseFile.absolutePath}"
            }
            return null
        }

        val resolvedEntries = entries
            .mapNotNull { entry ->
                val source = resolveSourceFile(projectRoot, entry) ?: return@mapNotNull null
                ResolvedCompileCommandEntry(
                    source = source,
                    targetName = resolveTargetName(entry),
                )
            }
            .filter { it.source.isFile }

        if (resolvedEntries.isEmpty()) {
            BuildDiagnosticsLog.w {
                "compile_commands input collect: no resolvable source entries path=${databaseFile.absolutePath} " +
                    "entryCount=${entries.size}"
            }
            return null
        }

        val targetMatchedEntries = targetNames
            .takeIf { it.isNotEmpty() }
            ?.let { names -> resolvedEntries.filter { it.targetName in names } }
            .orEmpty()

        val selectedEntries = targetMatchedEntries.ifEmpty { resolvedEntries }
        BuildDiagnosticsLog.i {
            "compile_commands input collect: path=${databaseFile.absolutePath} entries=${entries.size} " +
                "resolved=${resolvedEntries.size} targets=${targetNames.summary()} targetMatched=${targetMatchedEntries.size} " +
                "selected=${selectedEntries.size} selectedSources=${selectedEntries.map { it.source }.summaryRelativeTo(projectRoot)}"
        }
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

    private fun Set<String>.summary(): String =
        if (isEmpty()) "<none>" else sorted().joinToString(separator = "|", limit = 8)

    private fun List<File>.summaryRelativeTo(projectRoot: File): String =
        if (isEmpty()) {
            "<none>"
        } else {
            take(8).joinToString(separator = "|") { file ->
                file.relativeToOrSelf(projectRoot).path.replace(File.separatorChar, '/')
            } + if (size > 8) "|...(+${size - 8})" else ""
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

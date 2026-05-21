package com.wuxianggujun.tinaide.ui.runtime

import android.content.Context
import java.io.File
import timber.log.Timber

/**
 * GUI 运行时共享库 staging。
 *
 * 设计目标：
 * - 构建产物仍保留在项目 build 目录；
 * - 真正运行前，把主库和同目录下的项目私有依赖复制到 app 私有目录；
 * - 外部已在 app 私有目录内的运行时库（如 installed-packages）保持原路径，避免重复拷贝。
 */
object GuiRuntimeLibraryStager {
    private const val TAG = "GuiRuntimeLibraryStager"

    data class StagedRuntime(
        val mainLibraryPath: String,
        val preloadLibraryPaths: List<String>
    )

    sealed class StageResult {
        data class Success(val runtime: StagedRuntime) : StageResult()
        data class Error(val message: String, val throwable: Throwable? = null) : StageResult()
    }

    fun stage(
        context: Context,
        mainLibraryPath: String,
        preloadLibraryPaths: List<String> = emptyList()
    ): StageResult {
        val privatePathPrefixes = buildList {
            context.applicationInfo.dataDir
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            context.applicationInfo.nativeLibraryDir
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }.distinct()

        return stage(
            mainLibrary = File(mainLibraryPath),
            preloadLibraryPaths = preloadLibraryPaths,
            stageRootDir = File(context.filesDir, "run-bin/gui"),
            privatePathPrefixes = privatePathPrefixes
        )
    }

    internal fun stage(
        mainLibrary: File,
        preloadLibraryPaths: List<String>,
        stageRootDir: File,
        privatePathPrefixes: List<String>
    ): StageResult {
        if (!mainLibrary.isFile) {
            return StageResult.Error("Main library not found: ${mainLibrary.absolutePath}")
        }

        return runCatching {
            stageRootDir.mkdirs()
            val stageKey = mainLibrary.absolutePath.hashCode().toUInt().toString(16)
            val stageDir = File(stageRootDir, "${mainLibrary.nameWithoutExtension}.$stageKey").apply {
                mkdirs()
            }

            val stagedMain = stageFile(mainLibrary, stageDir)
            val stagedPreloads = linkedSetOf<String>()

            preloadLibraryPaths
                .map(::File)
                .filter { it.isFile }
                .forEach { preload ->
                    val resolved = if (isPrivateRuntimePath(preload, privatePathPrefixes)) {
                        preload
                    } else {
                        stageFile(preload, stageDir)
                    }
                    if (resolved.absolutePath != stagedMain.absolutePath) {
                        stagedPreloads += resolved.absolutePath
                    }
                }

            collectSiblingProjectLibraries(mainLibrary).forEach { sibling ->
                val stagedSibling = stageFile(sibling, stageDir)
                if (stagedSibling.absolutePath != stagedMain.absolutePath) {
                    stagedPreloads += stagedSibling.absolutePath
                }
            }

            Timber.tag(TAG).i(
                "Staged GUI runtime: main=%s -> %s, preloadCount=%d",
                mainLibrary.absolutePath,
                stagedMain.absolutePath,
                stagedPreloads.size
            )

            StageResult.Success(
                StagedRuntime(
                    mainLibraryPath = stagedMain.absolutePath,
                    preloadLibraryPaths = stagedPreloads.toList()
                )
            )
        }.getOrElse { throwable ->
            Timber.tag(TAG).e(throwable, "Failed to stage GUI runtime: %s", mainLibrary.absolutePath)
            StageResult.Error(
                message = throwable.message ?: throwable.javaClass.simpleName,
                throwable = throwable
            )
        }
    }

    private fun stageFile(source: File, stageDir: File): File {
        val target = File(stageDir, source.name)
        source.copyTo(target, overwrite = true)
        return target
    }

    private fun collectSiblingProjectLibraries(mainLibrary: File): List<File> = mainLibrary.parentFile
        ?.listFiles { file ->
            file.isFile &&
                file.extension.equals("so", ignoreCase = true) &&
                file.absolutePath != mainLibrary.absolutePath
        }
        ?.sortedBy { it.name }
        .orEmpty()

    private fun isPrivateRuntimePath(file: File, privatePathPrefixes: List<String>): Boolean {
        val absolutePath = file.absolutePath
        return privatePathPrefixes.any { prefix ->
            absolutePath.startsWith(prefix)
        }
    }
}

package com.wuxianggujun.tinaide.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream

/**
 * Core logic for the `syncTreeSitterQueries` task registered by
 * [TinaAndroidAppTreeSitterPlugin].
 *
 * The task is a manual, run-once utility that downloads the upstream
 * nvim-treesitter repository snapshot and copies each grammar's
 * `queries/<lang>/highlights.scm` into `src/main/assets/tree-sitter-queries/<lang>/`
 * in the consuming application module. Extracted here so that the task
 * body does not have to be inlined in `app/build.gradle.kts`.
 */
internal object TinaTreeSitterQueriesSync {

    /**
     * Download highlights.scm files from the upstream nvim-treesitter
     * repository and copy them into [assetsRoot].
     *
     * @param langs Grammar identifiers to copy.
     * @param assetsRoot `src/main/assets` directory inside the consumer module.
     * @param upstreamRef Git ref (branch / tag / commit) of nvim-treesitter.
     * @param upstreamZipUrl Override for the zip download URL. Usually
     *   derived from [upstreamRef].
     * @param tmpRoot Scratch directory (typically `build/tmp/tree-sitter-queries`).
     */
    fun syncQueries(
        langs: Set<String>,
        assetsRoot: File,
        upstreamRef: String,
        upstreamZipUrl: String,
        tmpRoot: File,
        logger: Logger,
    ) {
        if (!assetsRoot.exists()) {
            throw GradleException("Missing assets directory: ${assetsRoot.absolutePath}")
        }

        val zipFile = tmpRoot.resolve("nvim-treesitter-$upstreamRef.zip")
        val unzipDir = tmpRoot.resolve("nvim-treesitter-$upstreamRef")

        downloadAndExtractIfNeeded(zipFile, unzipDir, upstreamZipUrl, tmpRoot, logger)

        val repoRoot = findRepoRoot(unzipDir)
            ?: throw GradleException(
                "Failed to locate extracted queries root under: ${unzipDir.absolutePath}",
            )

        for (lang in langs) {
            val src = repoRoot.resolve("queries/$lang/highlights.scm")
            if (!src.exists()) {
                logger.warn(
                    "Upstream highlights.scm not found for '$lang' under: ${repoRoot.absolutePath}",
                )
                continue
            }

            val destDir = assetsRoot.resolve("tree-sitter-queries/$lang").apply { mkdirs() }
            // 保留上游原文件（包含 `; inherits: ...` 声明）；继承展开在运行时由 TreeSitterQueryLoader 处理。
            destDir.resolve("highlights.scm").writeText(src.readText(Charsets.UTF_8), Charsets.UTF_8)
        }
    }

    private fun downloadAndExtractIfNeeded(
        zipFile: File,
        unzipDir: File,
        upstreamZipUrl: String,
        tmpRoot: File,
        logger: Logger,
    ) {
        if (unzipDir.exists()) return

        tmpRoot.mkdirs()
        logger.lifecycle("Downloading Tree-sitter queries: $upstreamZipUrl")
        URI(upstreamZipUrl).toURL().openStream().use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        }
        logger.lifecycle("Extracting: ${zipFile.absolutePath}")
        unzipDir.mkdirs()
        val unzipRootCanonical = unzipDir.canonicalFile
        ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = unzipDir.resolve(entry.name).canonicalFile
                // Avoid zip-slip (writing outside unzipDir)
                if (!outFile.path.startsWith(unzipRootCanonical.path + File.separator)) {
                    logger.warn("Skip suspicious zip entry: ${entry.name}")
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { os -> zis.copyTo(os) }
                }
            }
        }
    }

    private fun findRepoRoot(unzipDir: File): File? {
        // Zip root is usually nvim-treesitter-<hash>/...
        return unzipDir.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.startsWith("nvim-treesitter-") }
            ?: unzipDir.takeIf { it.isDirectory }
    }
}

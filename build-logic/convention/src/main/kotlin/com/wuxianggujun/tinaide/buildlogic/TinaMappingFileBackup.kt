package com.wuxianggujun.tinaide.buildlogic

import org.gradle.api.logging.Logger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Core logic for the `backupMappingFiles` task registered by
 * [TinaAndroidAppMappingPlugin].
 *
 * After a release build completes, this helper copies every
 * `build/outputs/mapping/<flavor>Release/mapping.txt` into
 * `app/mappings/<versionName>-<timestamp>/<flavor>Release/mapping.txt`
 * so that crash log de-obfuscation data can be committed to version
 * control.
 */
internal object TinaMappingFileBackup {

    private val TIMESTAMP_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun backupMappings(
        mappingRoot: File,
        backupsRoot: File,
        versionName: String,
        logger: Logger,
    ) {
        if (!mappingRoot.exists()) {
            logger.warn("No mapping files found. Run a release build first.")
            return
        }

        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        val backupRoot = backupsRoot.resolve("$versionName-$timestamp")

        mappingRoot.listFiles()
            ?.filter { it.isDirectory && it.name.endsWith("Release") }
            ?.forEach { flavorDir ->
                val mappingFile = flavorDir.resolve("mapping.txt")
                if (mappingFile.exists()) {
                    val destDir = backupRoot.resolve(flavorDir.name)
                    destDir.mkdirs()
                    mappingFile.copyTo(destDir.resolve("mapping.txt"), overwrite = true)
                    logger.lifecycle(
                        "Backed up: ${mappingFile.absolutePath} -> ${destDir.absolutePath}",
                    )
                }
            }

        if (backupRoot.exists()) {
            logger.lifecycle("Mapping files backed up to: ${backupRoot.absolutePath}")
            logger.lifecycle(
                "IMPORTANT: Commit this folder to version control for future crash analysis!",
            )
        }
    }
}

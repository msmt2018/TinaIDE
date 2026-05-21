package com.wuxianggujun.tinaide.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the release R8 mapping backup/upload pipeline for the
 * application module:
 *
 * - `backupMappingFiles` copies `build/outputs/mapping/<flavor>Release/mapping.txt`
 *   into `app/mappings/<versionName>-<timestamp>/<flavor>Release/mapping.txt`.
 * - `uploadMappingFiles` gzip-compresses each mapping file and POSTs it to
 *   the configured server endpoint so that crash reports can be
 *   de-obfuscated server-side.
 *
 * Both tasks are wired as `finalizedBy` on every `assemble*Release` /
 * `bundle*Release` task so that a successful release build automatically
 * archives and ships its mapping artefact.
 *
 * Configuration properties:
 * - `tina.releaseMapping.enabled` (default `true`): toggle the whole
 *   pipeline; when `false`, both tasks become no-ops and no finalizer
 *   is attached.
 * - `tina.releaseMapping.serverUrl` (default
 *   `https://tinaide.wuxianggujun.com`): upload target.
 *
 * Requires the consumer project to have applied
 * [TinaAndroidAppVersioningPlugin] because it reads version information
 * from the [TinaAppVersioningExtension] exposed by that plugin.
 */
class TinaAndroidAppMappingPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            val enabled = resolveBooleanGradleProperty(
                name = "tina.releaseMapping.enabled",
                default = true,
            )
            val serverUrl = providers.gradleProperty("tina.releaseMapping.serverUrl")
                .orNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_SERVER_URL

            // 两个任务都保持注册（即使当前被 disable），
            // 这样用户可以手动 `./gradlew :app:backupMappingFiles` 做一次性归档。
            tasks.register("backupMappingFiles") {
                group = "build"
                description = "Backup R8 mapping files for crash log deobfuscation."
                onlyIf { enabled }

                doLast {
                    val versionName = project.readAppVersionName()
                    TinaMappingFileBackup.backupMappings(
                        mappingRoot = project.file("build/outputs/mapping"),
                        backupsRoot = project.file("mappings"),
                        versionName = versionName,
                        logger = logger,
                    )
                }
            }

            tasks.register("uploadMappingFiles") {
                group = "build"
                description = "Upload R8 mapping files to server for crash log deobfuscation."
                onlyIf { enabled }

                doLast {
                    val versionName = project.readAppVersionName()
                    val versionCode = project.readAppVersionCode()
                    TinaMappingFileUpload.uploadMappings(
                        mappingRoot = project.file("build/outputs/mapping"),
                        serverUrl = serverUrl,
                        appVersionName = versionName,
                        appVersionCode = versionCode,
                        logger = logger,
                    )
                }
            }

            if (!enabled) {
                logger.lifecycle(
                    "tina.android.app.mapping: tina.releaseMapping.enabled=false, " +
                        "skipping finalizedBy wiring for release assemble/bundle tasks.",
                )
                return@with
            }

            afterEvaluate {
                tasks.matching { it.name.matches(Regex("assemble.*Release")) }.configureEach {
                    finalizedBy("backupMappingFiles", "uploadMappingFiles")
                }
                tasks.matching { it.name.matches(Regex("bundle.*Release")) }.configureEach {
                    finalizedBy("backupMappingFiles", "uploadMappingFiles")
                }
            }
        }
    }

    private fun Project.readAppVersionName(): String {
        return readVersioningExtension().versionName
    }

    private fun Project.readAppVersionCode(): Int {
        return readVersioningExtension().versionCode
    }

    private fun Project.readVersioningExtension(): TinaAppVersioningExtension {
        return extensions.findByType(TinaAppVersioningExtension::class.java)
            ?: throw GradleException(
                "tina.android.app.mapping requires tina.android.app.versioning to be applied " +
                    "first so that TinaAppVersioningExtension is available.",
            )
    }

    companion object {
        private const val DEFAULT_SERVER_URL = "https://tinaide.wuxianggujun.com"
    }
}

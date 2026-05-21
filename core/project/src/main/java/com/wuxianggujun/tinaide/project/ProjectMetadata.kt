package com.wuxianggujun.tinaide.project

import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

object ProjectMetadataStore {
    private const val TAG = "ProjectMetadataStore"
    private const val PROJECT_METADATA_SCHEMA_LEGACY = 1
    private const val PROJECT_METADATA_SCHEMA_CURRENT = 2

    private val json = JsonSerializer.pretty

    private const val META_DIR_NAME = ".tinaide"
    private const val META_FILE_NAME = "project.json"
    private val SCHEMA_VERSION_FIELD_REGEX = Regex("\"schemaVersion\"\\s*:")
    private const val MAX_PENDING_MIGRATION_NOTICES_PER_PROJECT = 8
    private val migrationNoticeBuffer =
        ConcurrentHashMap<String, ArrayDeque<MigrationNotice>>()

    data class MigrationNotice(
        val fromSchemaVersion: Int,
        val toSchemaVersion: Int,
        val cppStandardNormalized: Boolean,
        val nativeApiLevelNormalized: Boolean
    )

    /** 当前 IDE 版本，由 Application 初始化时设置 */
    var currentIdeVersion: String = "unknown"

    fun getMetaFile(projectRoot: File): File {
        return File(File(projectRoot, META_DIR_NAME), META_FILE_NAME)
    }

    /**
     * 读取项目元数据，如果需要会自动补全缺失字段
     */
    fun read(projectRoot: File): ProjectMetadata? {
        val file = getMetaFile(projectRoot)
        if (!file.exists()) return null

        return runCatching {
            val jsonContent = file.readText()
            val decoded = decodeWithSchemaCompatibility(jsonContent)
            if (decoded.id.isBlank()) {
                Timber.tag(TAG).w("Invalid metadata: id is blank for project ${projectRoot.name}")
                null
            } else {
                val migrated = migrateMetadataToLatest(decoded)
                if (migrated != decoded) {
                    Timber.tag(TAG).i(
                        "Migrated project metadata schema ${decoded.schemaVersion} -> ${migrated.schemaVersion}"
                    )
                    recordMigrationNotice(
                        projectRoot = projectRoot,
                        notice = MigrationNotice(
                            fromSchemaVersion = decoded.schemaVersion,
                            toSchemaVersion = migrated.schemaVersion,
                            cppStandardNormalized = decoded.cppStandard != migrated.cppStandard,
                            nativeApiLevelNormalized = decoded.nativeApiLevel != migrated.nativeApiLevel
                        )
                    )
                    write(projectRoot, migrated)
                }
                migrated
            }
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "Failed to read metadata for project ${projectRoot.name}")
        }.getOrNull()
    }

    /**
     * 确保项目有元数据，如果没有则创建，如果缺少字段则补全
     */
    fun ensure(
        projectRoot: File,
        displayNameFallback: String = projectRoot.name,
        buildSystem: ProjectBuildSystem? = null,
        cppStandard: CppStandard? = null,
        primaryLanguage: ProjectLanguage? = null,
        apkExportType: ProjectApkExportType? = null,
        nativeApiLevel: Int? = null
    ): ProjectMetadata {
        val normalizedNativeApiLevel = normalizeNativeApiLevel(nativeApiLevel)
        read(projectRoot)?.let { existing ->
            var needsUpdate = false
            var updated = existing

            if (existing.createdByIdeVersion == null) {
                updated = updated.copy(createdByIdeVersion = currentIdeVersion)
                needsUpdate = true
                Timber.tag(TAG).i("Added createdByIdeVersion for project ${projectRoot.name}")
            }

            if (existing.lastOpenedIdeVersion != currentIdeVersion) {
                updated = updated.copy(
                    lastOpenedIdeVersion = currentIdeVersion,
                    lastOpenedAt = System.currentTimeMillis()
                )
                needsUpdate = true
            }

            if (normalizedNativeApiLevel != null && existing.nativeApiLevel != normalizedNativeApiLevel) {
                updated = updated.copy(nativeApiLevel = normalizedNativeApiLevel)
                needsUpdate = true
            }

            if (apkExportType != null && existing.apkExportType != apkExportType) {
                updated = updated.copy(apkExportType = apkExportType)
                needsUpdate = true
            }

            if (needsUpdate) {
                write(projectRoot, updated)
            }
            return updated
        }

        val meta = ProjectMetadata(
            schemaVersion = PROJECT_METADATA_SCHEMA_CURRENT,
            id = UUID.randomUUID().toString(),
            displayName = displayNameFallback,
            createdAt = System.currentTimeMillis(),
            createdByIdeVersion = currentIdeVersion,
            buildSystem = buildSystem,
            cppStandard = cppStandard?.name,
            primaryLanguage = primaryLanguage?.name,
            apkExportType = apkExportType,
            lastOpenedIdeVersion = currentIdeVersion,
            lastOpenedAt = System.currentTimeMillis(),
            nativeApiLevel = normalizedNativeApiLevel
        )
        write(projectRoot, meta)
        return meta
    }

    fun write(projectRoot: File, metadata: ProjectMetadata): Boolean {
        val metadataToPersist = if (metadata.schemaVersion < PROJECT_METADATA_SCHEMA_CURRENT) {
            migrateMetadataToLatest(metadata)
        } else if (metadata.schemaVersion == PROJECT_METADATA_SCHEMA_CURRENT) {
            normalizeMetadata(metadata)
        } else {
            metadata
        }
        return runCatching {
            val dir = File(projectRoot, META_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, META_FILE_NAME)
            JsonSerializer.encodePrettyToFile(file, metadataToPersist)
            true
        }.onFailure { e ->
            Timber.tag(TAG).e(e, "Failed to write metadata for project ${projectRoot.name}")
        }.getOrElse { false }
    }

    fun updateBuildSystem(projectRoot: File, buildSystem: ProjectBuildSystem): Boolean {
        val existing = read(projectRoot) ?: return false
        val updated = existing.copy(buildSystem = buildSystem)
        return write(projectRoot, updated)
    }

    fun updateCppStandard(projectRoot: File, cppStandard: CppStandard): Boolean {
        val existing = read(projectRoot) ?: return false
        val updated = existing.copy(cppStandard = cppStandard.name)
        return write(projectRoot, updated)
    }

    fun updatePrimaryLanguage(projectRoot: File, language: ProjectLanguage): Boolean {
        val existing = read(projectRoot) ?: return false
        val updated = existing.copy(primaryLanguage = language.name)
        return write(projectRoot, updated)
    }

    fun updateApkExportType(projectRoot: File, apkExportType: ProjectApkExportType?): Boolean {
        val existing = read(projectRoot) ?: return false
        if (existing.apkExportType == apkExportType) return true
        return write(projectRoot, existing.copy(apkExportType = apkExportType))
    }

    fun updateLastOpened(projectRoot: File): Boolean {
        val existing = read(projectRoot) ?: return false
        val updated = existing.copy(
            lastOpenedIdeVersion = currentIdeVersion,
            lastOpenedAt = System.currentTimeMillis()
        )
        return write(projectRoot, updated)
    }

    fun updateNativeApiLevel(projectRoot: File, nativeApiLevel: Int?): Boolean {
        val existing = read(projectRoot) ?: return false
        val normalized = normalizeNativeApiLevel(nativeApiLevel)
        if (existing.nativeApiLevel == normalized) return true
        return write(projectRoot, existing.copy(nativeApiLevel = normalized))
    }

    fun updateNativeDependencyPaths(
        projectRoot: File,
        includeDirs: List<String>,
        libraryDirs: List<String>,
        runtimeDirs: List<String>
    ): Boolean {
        val existing = read(projectRoot) ?: ensure(projectRoot)
        val normalizedIncludeDirs = normalizePathEntries(includeDirs)
        val normalizedLibraryDirs = normalizePathEntries(libraryDirs)
        val normalizedRuntimeDirs = normalizePathEntries(runtimeDirs)

        val unchanged = existing.normalizedNativeIncludeDirs() == normalizedIncludeDirs &&
            existing.normalizedNativeLibraryDirs() == normalizedLibraryDirs &&
            existing.normalizedNativeRuntimeDirs() == normalizedRuntimeDirs
        if (unchanged) return true

        return write(
            projectRoot,
            existing.copy(
                nativeIncludeDirs = normalizedIncludeDirs,
                nativeLibraryDirs = normalizedLibraryDirs,
                nativeRuntimeDirs = normalizedRuntimeDirs
            )
        )
    }

    fun updateNativeBuildFlags(
        projectRoot: File,
        cFlags: String,
        cppFlags: String,
        ldFlags: String,
        ldLibs: String,
        cmakeArgs: List<String>
    ): Boolean {
        val existing = read(projectRoot) ?: ensure(projectRoot)
        val normalizedCFlags = normalizeFlagValue(cFlags)
        val normalizedCppFlags = normalizeFlagValue(cppFlags)
        val normalizedLdFlags = normalizeFlagValue(ldFlags)
        val normalizedLdLibs = normalizeFlagValue(ldLibs)
        val normalizedCMakeArgs = normalizePathEntries(cmakeArgs)

        val unchanged = existing.normalizedNativeCFlags() == normalizedCFlags &&
            existing.normalizedNativeCppFlags() == normalizedCppFlags &&
            existing.normalizedNativeLdFlags() == normalizedLdFlags &&
            existing.normalizedNativeLdLibs() == normalizedLdLibs &&
            existing.normalizedNativeCMakeArgs() == normalizedCMakeArgs
        if (unchanged) return true

        return write(
            projectRoot,
            existing.copy(
                nativeCFlags = normalizedCFlags,
                nativeCppFlags = normalizedCppFlags,
                nativeLdFlags = normalizedLdFlags,
                nativeLdLibs = normalizedLdLibs,
                nativeCMakeArgs = normalizedCMakeArgs
            )
        )
    }

    private fun decodeWithSchemaCompatibility(rawJson: String): ProjectMetadata {
        val hasSchemaVersion = SCHEMA_VERSION_FIELD_REGEX.containsMatchIn(rawJson)
        val decoded = json.decodeFromString<ProjectMetadata>(rawJson)
        return if (hasSchemaVersion) {
            decoded
        } else {
            decoded.copy(schemaVersion = PROJECT_METADATA_SCHEMA_LEGACY)
        }
    }

    private fun migrateMetadataToLatest(metadata: ProjectMetadata): ProjectMetadata {
        if (metadata.schemaVersion >= PROJECT_METADATA_SCHEMA_CURRENT) {
            return if (metadata.schemaVersion == PROJECT_METADATA_SCHEMA_CURRENT) {
                normalizeMetadata(metadata)
            } else {
                metadata
            }
        }

        var migrated = metadata
        while (migrated.schemaVersion < PROJECT_METADATA_SCHEMA_CURRENT) {
            migrated = when {
                migrated.schemaVersion <= PROJECT_METADATA_SCHEMA_LEGACY -> migrateFromV1ToV2(migrated)
                else -> {
                    Timber.tag(TAG).w(
                        "Unknown project metadata schema ${migrated.schemaVersion}, force marking latest"
                    )
                    migrated.copy(schemaVersion = PROJECT_METADATA_SCHEMA_CURRENT)
                }
            }
            // TODO(config-migration): schema 升级到 3+ 时，在这里追加 v2->v3 等迁移步骤。
        }
        return migrated
    }

    private fun migrateFromV1ToV2(metadata: ProjectMetadata): ProjectMetadata {
        return normalizeMetadata(
            metadata.copy(schemaVersion = PROJECT_METADATA_SCHEMA_CURRENT)
        )
    }

    private fun normalizeMetadata(metadata: ProjectMetadata): ProjectMetadata {
        return metadata.copy(
            schemaVersion = PROJECT_METADATA_SCHEMA_CURRENT,
            cppStandard = metadata.normalizedCppStandardValue(),
            nativeApiLevel = normalizeNativeApiLevel(metadata.nativeApiLevel),
            nativeIncludeDirs = normalizePathEntries(metadata.nativeIncludeDirs),
            nativeLibraryDirs = normalizePathEntries(metadata.nativeLibraryDirs),
            nativeRuntimeDirs = normalizePathEntries(metadata.nativeRuntimeDirs),
            nativeCFlags = normalizeFlagValue(metadata.nativeCFlags),
            nativeCppFlags = normalizeFlagValue(metadata.nativeCppFlags),
            nativeLdFlags = normalizeFlagValue(metadata.nativeLdFlags),
            nativeLdLibs = normalizeFlagValue(metadata.nativeLdLibs),
            nativeCMakeArgs = normalizePathEntries(metadata.nativeCMakeArgs)
        )
    }

    private fun recordMigrationNotice(projectRoot: File, notice: MigrationNotice) {
        val key = projectRoot.absolutePath
        val queue = migrationNoticeBuffer.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            if (queue.size >= MAX_PENDING_MIGRATION_NOTICES_PER_PROJECT) {
                queue.removeFirst()
            }
            queue.addLast(notice)
        }
    }

    fun consumeMigrationNotices(projectRoot: File): List<MigrationNotice> {
        val key = projectRoot.absolutePath
        val queue = migrationNoticeBuffer.remove(key) ?: return emptyList()
        synchronized(queue) {
            if (queue.isEmpty()) return emptyList()
            return queue.toList()
        }
    }

    private fun normalizeNativeApiLevel(nativeApiLevel: Int?): Int? {
        return nativeApiLevel?.takeIf { it in 21..35 }
    }

    private fun normalizePathEntries(paths: List<String>): List<String> {
        if (paths.isEmpty()) return emptyList()
        return paths.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    private fun normalizeFlagValue(value: String): String {
        if (value.isBlank()) return ""
        return value.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }
}

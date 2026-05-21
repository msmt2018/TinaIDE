package com.wuxianggujun.tinaide.core.linuxdistro

import java.io.File

class LinuxDistroInstaller(
    private val catalog: LinuxDistroCatalog,
    private val downloader: LinuxDistroDownloader = OkHttpLinuxDistroDownloader(),
    private val checksumVerifier: LinuxDistroChecksumVerifier = MessageDigestLinuxDistroChecksumVerifier(),
    private val archiveExtractor: LinuxDistroArchiveExtractor = TarLinuxDistroArchiveExtractor(),
    private val rootfsConfigurator: LinuxDistroRootfsConfigurator = BasicLinuxDistroRootfsConfigurator(),
    private val metadataStore: LinuxDistroInstallMetadataStore = JsonLinuxDistroInstallMetadataStore(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun install(
        request: LinuxDistroInstallRequest,
        progress: (LinuxDistroInstallProgress) -> Unit = {},
    ): LinuxDistroInstallResult {
        request.layout.ensureDirectories()
        progress(request.progress(LinuxDistroInstallPhase.PREPARING, 0.01f))

        val resolved = catalog.resolveArtifact(
            distroId = request.distroId,
            releaseId = request.releaseId,
            architecture = request.architecture,
        ) ?: error(
            "No rootfs artifact for distro=${request.distroId}, release=${request.releaseId ?: "default"}, " +
                "arch=${request.architecture}",
        )
        progress(request.progress(LinuxDistroInstallPhase.RESOLVING_ARTIFACT, 0.05f, resolved))

        val targetRootfsDir = request.layout.rootfsDir(resolved.distro.id)
        if (targetRootfsDir.isDirectory && !request.reinstall) {
            val installation = metadataStore.read(targetRootfsDir) ?: createInstallation(
                resolved = resolved,
                rootfsDir = targetRootfsDir,
                archiveFile = request.layout.archiveFile(resolved),
                installedAtEpochMillis = targetRootfsDir.lastModified().takeIf { it > 0L } ?: clock(),
            )
            metadataStore.write(targetRootfsDir, installation)
            progress(request.progress(LinuxDistroInstallPhase.COMPLETED, 1f, resolved))
            return LinuxDistroInstallResult(
                resolved = resolved,
                rootfsDir = targetRootfsDir,
                archiveFile = request.layout.archiveFile(resolved),
                installation = installation,
                installed = false,
            )
        }

        val archiveFile = request.layout.archiveFile(resolved)
        ensureArchive(resolved, archiveFile, request, progress)
        progress(request.progress(LinuxDistroInstallPhase.VERIFYING, 0.45f, resolved))
        try {
            checksumVerifier.requireValid(archiveFile, resolved.artifact.checksum)
        } catch (throwable: Throwable) {
            archiveFile.delete()
            throw throwable
        }

        val stagingRootfsDir = request.layout.newStagingRootfsDir(resolved.distro.id)
        stagingRootfsDir.deleteRecursively()
        try {
            progress(request.progress(LinuxDistroInstallPhase.EXTRACTING, 0.50f, resolved))
            archiveExtractor.extract(
                archiveFile = archiveFile,
                targetDir = stagingRootfsDir,
                format = resolved.artifact.format,
            ) { extractProgress ->
                progress(
                    request.progress(
                        LinuxDistroInstallPhase.EXTRACTING,
                        0.50f + extractProgress.coerceIn(0f, 1f) * 0.35f,
                        resolved,
                    ),
                )
            }

            progress(request.progress(LinuxDistroInstallPhase.CONFIGURING, 0.88f, resolved))
            rootfsConfigurator.configure(stagingRootfsDir, request.rootfsConfig)

            val installedAt = clock()
            val installation = createInstallation(
                resolved = resolved,
                rootfsDir = targetRootfsDir,
                archiveFile = archiveFile,
                installedAtEpochMillis = installedAt,
            )
            metadataStore.write(stagingRootfsDir, installation)

            progress(request.progress(LinuxDistroInstallPhase.REGISTERING, 0.95f, resolved))
            if (targetRootfsDir.exists()) {
                targetRootfsDir.deleteRecursively()
            }
            targetRootfsDir.parentFile?.mkdirs()
            check(stagingRootfsDir.renameTo(targetRootfsDir)) {
                "Failed to move rootfs from ${stagingRootfsDir.absolutePath} to ${targetRootfsDir.absolutePath}"
            }

            progress(request.progress(LinuxDistroInstallPhase.COMPLETED, 1f, resolved))
            return LinuxDistroInstallResult(
                resolved = resolved,
                rootfsDir = targetRootfsDir,
                archiveFile = archiveFile,
                installation = installation,
                installed = true,
            )
        } catch (throwable: Throwable) {
            stagingRootfsDir.deleteRecursively()
            throw throwable
        }
    }

    private suspend fun ensureArchive(
        resolved: ResolvedDistroArtifact,
        archiveFile: File,
        request: LinuxDistroInstallRequest,
        progress: (LinuxDistroInstallProgress) -> Unit,
    ) {
        if (archiveFile.isFile) {
            val existing = checksumVerifier.verify(archiveFile, resolved.artifact.checksum)
            if (existing.isValid) return
            archiveFile.delete()
        }

        progress(request.progress(LinuxDistroInstallPhase.DOWNLOADING, 0.10f, resolved))
        downloader.download(
            request = DistroDownloadRequest(
                url = resolved.artifact.url,
                targetFile = archiveFile,
                resume = true,
            ),
        ) { downloadProgress ->
            val fraction = downloadProgress.fraction ?: 0f
            progress(request.progress(LinuxDistroInstallPhase.DOWNLOADING, 0.10f + fraction * 0.30f, resolved))
        }
    }

    private fun createInstallation(
        resolved: ResolvedDistroArtifact,
        rootfsDir: File,
        archiveFile: File,
        installedAtEpochMillis: Long,
    ): InstalledLinuxDistro {
        return InstalledLinuxDistro(
            distroId = resolved.distro.id,
            releaseId = resolved.release.id,
            architecture = resolved.artifact.architecture,
            displayName = resolved.distro.displayName,
            packageManager = resolved.distro.packageManager,
            rootfsPath = rootfsDir.absolutePath,
            archivePath = archiveFile.absolutePath,
            checksum = resolved.artifact.checksum,
            installedAtEpochMillis = installedAtEpochMillis,
            updatedAtEpochMillis = clock().coerceAtLeast(installedAtEpochMillis),
        )
    }

    private fun LinuxDistroInstallRequest.progress(
        phase: LinuxDistroInstallPhase,
        fraction: Float,
        resolved: ResolvedDistroArtifact? = null,
    ): LinuxDistroInstallProgress {
        return LinuxDistroInstallProgress(
            phase = phase,
            fraction = fraction.coerceIn(0f, 1f),
            distroId = resolved?.distro?.id ?: distroId,
            releaseId = resolved?.release?.id ?: releaseId,
            architecture = resolved?.artifact?.architecture ?: architecture,
        )
    }
}

data class LinuxDistroInstallRequest(
    val distroId: String,
    val architecture: DistroArchitecture,
    val layout: LinuxDistroInstallLayout,
    val releaseId: String? = null,
    val reinstall: Boolean = false,
    val rootfsConfig: LinuxDistroRootfsConfig = LinuxDistroRootfsConfig(),
)

data class LinuxDistroInstallResult(
    val resolved: ResolvedDistroArtifact,
    val rootfsDir: File,
    val archiveFile: File,
    val installation: InstalledLinuxDistro,
    val installed: Boolean,
)

data class LinuxDistroInstallProgress(
    val phase: LinuxDistroInstallPhase,
    val fraction: Float,
    val distroId: String,
    val releaseId: String?,
    val architecture: DistroArchitecture,
)

enum class LinuxDistroInstallPhase {
    PREPARING,
    RESOLVING_ARTIFACT,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    CONFIGURING,
    REGISTERING,
    COMPLETED,
}

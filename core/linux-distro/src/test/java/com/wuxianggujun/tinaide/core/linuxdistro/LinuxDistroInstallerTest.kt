package com.wuxianggujun.tinaide.core.linuxdistro

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LinuxDistroInstallerTest {

    @Test
    fun manifestParser_shouldResolveArtifactByArchitecture() {
        val manifest = LinuxDistroManifestParser.decode(sampleManifest(checksum = "0".repeat(64)))
        val catalog = ManifestLinuxDistroCatalog(manifest)

        val resolved = catalog.resolveArtifact(
            distroId = "alpine",
            releaseId = "3.20",
            architecture = DistroArchitecture.AARCH64,
        )

        assertThat(resolved).isNotNull()
        assertThat(resolved!!.distro.packageManager).isEqualTo(DistroPackageManager.APK)
        assertThat(resolved.release.displayName).isEqualTo("Alpine 3.20")
        assertThat(resolved.artifact.format).isEqualTo(DistroArchiveFormat.TAR_GZ)
    }

    @Test
    fun catalog_shouldListOnlyInstallableDefaultArtifactsForArchitecture() {
        val manifest = LinuxDistroManifestParser.decode(sampleManifest(checksum = "0".repeat(64)))
        val catalog = ManifestLinuxDistroCatalog(manifest)

        val aarch64 = catalog.listInstallableDefaultArtifacts(DistroArchitecture.AARCH64)
        val x86_64 = catalog.listInstallableDefaultArtifacts(DistroArchitecture.X86_64)

        assertThat(aarch64.map { it.distro.id }).containsExactly("alpine")
        assertThat(aarch64.single().artifact.architecture).isEqualTo(DistroArchitecture.AARCH64)
        assertThat(x86_64).isEmpty()
    }

    @Test
    fun manifestParser_shouldResolveUbuntuLtsArtifact() {
        val manifest = LinuxDistroManifestParser.decode(sampleUbuntuManifest())
        val catalog = ManifestLinuxDistroCatalog(manifest)

        val resolved = catalog.resolveArtifact(
            distroId = "ubuntu",
            releaseId = "24.04",
            architecture = DistroArchitecture.X86_64,
        )

        assertThat(resolved).isNotNull()
        assertThat(resolved!!.distro.packageManager).isEqualTo(DistroPackageManager.APT)
        assertThat(resolved.release.version).isEqualTo("24.04.4")
        assertThat(resolved.artifact.sizeBytes).isEqualTo(29_989_394L)
    }

    @Test
    fun fileManifestSource_shouldLoadCatalogFromDisk() {
        val tempDir = createTempDirectory("linux-distro-manifest-source").toFile()
        val manifestFile = File(tempDir, "manifest.json").apply {
            writeText(sampleManifest(checksum = "0".repeat(64)))
        }

        val catalog = FileLinuxDistroManifestSource(manifestFile).loadCatalog()

        assertThat(catalog.resolveDistro("alpine")?.defaultReleaseId).isEqualTo("3.20")
    }

    @Test
    fun checksumVerifier_shouldValidateSha256() {
        val tempDir = createTempDirectory("linux-distro-checksum").toFile()
        val file = File(tempDir, "rootfs.tar.gz").apply { writeText("rootfs") }
        val checksum = DistroChecksum(DistroChecksumAlgorithm.SHA256, sha256("rootfs"))

        val result = MessageDigestLinuxDistroChecksumVerifier().verify(file, checksum)

        assertThat(result.isValid).isTrue()
    }

    @Test
    fun registry_shouldPersistUpsertAndRemoveInstallations() {
        val tempDir = createTempDirectory("linux-distro-registry").toFile()
        val registry = FileLinuxDistroInstallationRegistry(File(tempDir, "registry.json"))
        val installation = installedLinuxDistro(tempDir)

        registry.upsert(installation)

        assertThat(registry.find("alpine")).isEqualTo(installation)
        assertThat(registry.list()).containsExactly(installation)
        assertThat(registry.remove("alpine")).isTrue()
        assertThat(registry.list()).isEmpty()
    }

    @Test
    fun installer_shouldRunFullPipelineWithInjectedComponents() = runBlocking {
        val tempDir = createTempDirectory("linux-distro-install").toFile()
        val archiveContent = "fake archive"
        val checksum = sha256(archiveContent)
        val manifest = LinuxDistroManifestParser.decode(sampleManifest(checksum))
        val catalog = ManifestLinuxDistroCatalog(manifest)
        val phases = mutableListOf<LinuxDistroInstallPhase>()
        val installer = fakeInstaller(catalog, archiveContent)

        val result = installer.install(
            request = LinuxDistroInstallRequest(
                distroId = "alpine",
                releaseId = "3.20",
                architecture = DistroArchitecture.AARCH64,
                layout = LinuxDistroInstallLayout(runtimeDir = tempDir),
            ),
        ) { progress -> phases += progress.phase }

        assertThat(result.installed).isTrue()
        assertThat(result.installation.distroId).isEqualTo("alpine")
        assertThat(result.installation.profileId).isEqualTo("linux-distro:alpine")
        assertThat(File(result.rootfsDir, "bin/sh").isFile).isTrue()
        assertThat(File(result.rootfsDir, "etc/resolv.conf").readText()).contains("nameserver")
        assertThat(File(result.rootfsDir, ".tinaide/linux-distro.json").isFile).isTrue()
        assertThat(phases).containsAtLeast(
            LinuxDistroInstallPhase.DOWNLOADING,
            LinuxDistroInstallPhase.VERIFYING,
            LinuxDistroInstallPhase.EXTRACTING,
            LinuxDistroInstallPhase.CONFIGURING,
            LinuxDistroInstallPhase.COMPLETED,
        ).inOrder()
    }

    @Test
    fun manager_shouldInstallRegisterListAndUninstallDistro() = runBlocking {
        val tempDir = createTempDirectory("linux-distro-manager").toFile()
        val archiveContent = "fake archive"
        val checksum = sha256(archiveContent)
        val catalog = ManifestLinuxDistroCatalog(LinuxDistroManifestParser.decode(sampleManifest(checksum)))
        val layout = LinuxDistroInstallLayout(runtimeDir = tempDir)
        val registry = FileLinuxDistroInstallationRegistry(File(tempDir, "registry.json"))
        val manager = LinuxDistroManager(
            catalog = catalog,
            layout = layout,
            installer = fakeInstaller(catalog, archiveContent),
            registry = registry,
        )

        val result = manager.install(
            distroId = "alpine",
            releaseId = "3.20",
            architecture = DistroArchitecture.AARCH64,
        )

        assertThat(result.installed).isTrue()
        assertThat(manager.isInstalled("alpine")).isTrue()
        assertThat(manager.listInstalled(syncFromDisk = false).map { it.distroId }).containsExactly("alpine")
        assertThat(manager.listInstalled(syncFromDisk = true).map { it.distroId }).containsExactly("alpine")
        assertThat(manager.uninstall("alpine")).isTrue()
        assertThat(manager.isInstalled("alpine")).isFalse()
        assertThat(registry.list()).isEmpty()
    }

    private fun fakeInstaller(
        catalog: LinuxDistroCatalog,
        archiveContent: String,
    ): LinuxDistroInstaller {
        return LinuxDistroInstaller(
            catalog = catalog,
            downloader = object : LinuxDistroDownloader {
                override suspend fun download(
                    request: DistroDownloadRequest,
                    progress: (DistroDownloadProgress) -> Unit,
                ): File {
                    request.targetFile.writeText(archiveContent)
                    progress(DistroDownloadProgress(archiveContent.length.toLong(), archiveContent.length.toLong()))
                    return request.targetFile
                }
            },
            archiveExtractor = object : LinuxDistroArchiveExtractor {
                override fun extract(
                    archiveFile: File,
                    targetDir: File,
                    format: DistroArchiveFormat,
                    progress: (Float) -> Unit,
                ) {
                    File(targetDir, "bin/sh").apply {
                        parentFile?.mkdirs()
                        writeText("#!/bin/sh\n")
                    }
                    progress(1f)
                }
            },
            clock = { 1_800_000_000_000L },
        )
    }

    private fun installedLinuxDistro(tempDir: File): InstalledLinuxDistro {
        return InstalledLinuxDistro(
            distroId = "alpine",
            releaseId = "3.20",
            architecture = DistroArchitecture.AARCH64,
            displayName = "Alpine Linux",
            packageManager = DistroPackageManager.APK,
            rootfsPath = File(tempDir, "rootfs").absolutePath,
            archivePath = File(tempDir, "alpine.tar.gz").absolutePath,
            checksum = DistroChecksum(DistroChecksumAlgorithm.SHA256, "0".repeat(64)),
            installedAtEpochMillis = 1_800_000_000_000L,
        )
    }

    private fun sampleManifest(checksum: String): String = """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-04-28T00:00:00Z",
          "distros": [
            {
              "id": "alpine",
              "family": "ALPINE",
              "displayName": "Alpine Linux",
              "packageManager": "APK",
              "defaultReleaseId": "3.20",
              "releases": [
                {
                  "id": "3.20",
                  "version": "3.20",
                  "displayName": "Alpine 3.20",
                  "channel": "stable",
                  "artifacts": [
                    {
                      "architecture": "AARCH64",
                      "url": "https://example.invalid/alpine-rootfs.tar.gz",
                      "format": "TAR_GZ",
                      "checksum": {
                        "algorithm": "SHA256",
                        "value": "$checksum"
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun sampleUbuntuManifest(): String = """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-04-28T00:00:00Z",
          "distros": [
            {
              "id": "ubuntu",
              "family": "UBUNTU",
              "displayName": "Ubuntu",
              "packageManager": "APT",
              "defaultReleaseId": "24.04",
              "homepageUrl": "https://ubuntu.com/",
              "releases": [
                {
                  "id": "24.04",
                  "version": "24.04.4",
                  "displayName": "Ubuntu 24.04 LTS",
                  "channel": "lts",
                  "artifacts": [
                    {
                      "architecture": "X86_64",
                      "url": "https://cdimage.ubuntu.com/cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-amd64.tar.gz",
                      "format": "TAR_GZ",
                      "checksum": {
                        "algorithm": "SHA256",
                        "value": "c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58"
                      },
                      "sizeBytes": 29989394
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()
    private fun sha256(content: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

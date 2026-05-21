package com.wuxianggujun.tinaide.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectDirStructureApkPathsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val projectPath: String
        get() = tempFolder.root.absolutePath

    @Test
    fun `getApkExportDir points to tinaide apk-export`() {
        val dir = ProjectDirStructure.getApkExportDir(projectPath)

        assertThat(dir).isEqualTo(File(tempFolder.root, ".tinaide/apk-export"))
    }

    @Test
    fun `getApkExportIconsDir is child of apk-export`() {
        val dir = ProjectDirStructure.getApkExportIconsDir(projectPath)

        assertThat(dir).isEqualTo(File(tempFolder.root, ".tinaide/apk-export/icons"))
    }

    @Test
    fun `getApkExportRuntimeLibsDir is child of apk-export`() {
        val dir = ProjectDirStructure.getApkExportRuntimeLibsDir(projectPath)

        assertThat(dir).isEqualTo(File(tempFolder.root, ".tinaide/apk-export/runtime-libs"))
    }

    @Test
    fun `getApkPermissionsFile is permissions json under apk-export`() {
        val file = ProjectDirStructure.getApkPermissionsFile(projectPath)

        assertThat(file).isEqualTo(File(tempFolder.root, ".tinaide/apk-export/permissions.json"))
    }

    @Test
    fun `getApkSigningPropertiesFile is signing properties under apk-export`() {
        val file = ProjectDirStructure.getApkSigningPropertiesFile(projectPath)

        assertThat(file).isEqualTo(File(tempFolder.root, ".tinaide/apk-export/signing.properties"))
    }

    @Test
    fun `getKeystoreDir is sibling of apk-export`() {
        val dir = ProjectDirStructure.getKeystoreDir(projectPath)

        assertThat(dir).isEqualTo(File(tempFolder.root, ".tinaide/keystore"))
    }

    @Test
    fun `migrateLegacyApkSigningPropertiesIfNeeded returns false when no legacy file exists`() {
        val migrated = ProjectDirStructure.migrateLegacyApkSigningPropertiesIfNeeded(projectPath)

        assertThat(migrated).isFalse()
        assertThat(ProjectDirStructure.getApkSigningPropertiesFile(projectPath).exists()).isFalse()
    }

    @Test
    fun `migrateLegacyApkSigningPropertiesIfNeeded moves legacy file and deletes original`() {
        val legacy = File(tempFolder.root, ".tinaide/apk-signing.properties").apply {
            parentFile!!.mkdirs()
            writeText("keystoreFile=/tmp/key.jks\nkeyAlias=release\n")
        }

        val migrated = ProjectDirStructure.migrateLegacyApkSigningPropertiesIfNeeded(projectPath)

        assertThat(migrated).isTrue()
        assertThat(legacy.exists()).isFalse()
        val newFile = ProjectDirStructure.getApkSigningPropertiesFile(projectPath)
        assertThat(newFile.exists()).isTrue()
        assertThat(newFile.readText()).contains("keystoreFile=/tmp/key.jks")
    }

    @Test
    fun `migrateLegacyApkSigningPropertiesIfNeeded does not overwrite existing new file`() {
        File(tempFolder.root, ".tinaide/apk-signing.properties").apply {
            parentFile!!.mkdirs()
            writeText("legacy=value\n")
        }
        val newFile = ProjectDirStructure.getApkSigningPropertiesFile(projectPath).apply {
            parentFile!!.mkdirs()
            writeText("new=value\n")
        }

        val migrated = ProjectDirStructure.migrateLegacyApkSigningPropertiesIfNeeded(projectPath)

        assertThat(migrated).isFalse()
        assertThat(newFile.readText()).isEqualTo("new=value\n")
    }

    @Test
    fun `migrateLegacyApkSigningPropertiesIfNeeded returns false when legacy path is a directory`() {
        File(tempFolder.root, ".tinaide/apk-signing.properties").apply {
            mkdirs()
        }

        val migrated = ProjectDirStructure.migrateLegacyApkSigningPropertiesIfNeeded(projectPath)

        assertThat(migrated).isFalse()
    }
}

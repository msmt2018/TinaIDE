package com.wuxianggujun.tinaide.ui.compose.components

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.storage.ProjectDirStructure
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ApkExportPermissionProfileTest {

    @Test
    fun `default permission set is empty`() {
        assertThat(defaultApkPermissionSet).isEmpty()
    }

    @Test
    fun `buildRequestedPermissions returns empty list for empty selection`() {
        val requestedPermissions = buildRequestedPermissions(
            selectedBuiltinPermissions = emptySet()
        )

        assertThat(requestedPermissions).isEmpty()
    }

    @Test
    fun `rememberApkExportPermissions writes version code and runtime libraries to project scoped profile`() {
        val projectRoot = Files.createTempDirectory("apk-export-profile-test").toFile()
        val outputDir = File(projectRoot, "build/apk").apply { mkdirs() }
        val runtimeLib = File(projectRoot, ".tinaide/apk-export/runtime-libs/libfoo.so").apply {
            parentFile!!.mkdirs()
            writeText("foo")
        }

        try {
            rememberApkExportPermissions(
                outputDir = outputDir,
                fallbackRoot = projectRoot,
                selectedBuiltinPermissions = emptySet(),
                versionCode = 7,
                iconFilePath = null,
                additionalRuntimeLibraryPaths = listOf(runtimeLib.absolutePath)
            )

            val profileFile = ProjectDirStructure.getApkPermissionsFile(projectRoot.absolutePath)
            assertThat(profileFile.exists()).isTrue()

            val root = Json.parseToJsonElement(profileFile.readText()).jsonObject
            val runtimeLibraries = root["additionalRuntimeLibraryPaths"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty()

            assertThat(root["versionCode"]?.jsonPrimitive?.content).isEqualTo("7")
            assertThat(runtimeLibraries).containsExactly(runtimeLib.absolutePath)
        } finally {
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun `loadRememberedApkExportPermissions restores version code and runtime libraries from fallback file`() {
        val projectRoot = Files.createTempDirectory("apk-export-profile-load").toFile()
        val outputDir = Files.createTempDirectory("apk-export-profile-load-output").toFile()
        val runtimeLib = File(projectRoot, ".tinaide/apk-export/runtime-libs/libfoo.so").apply {
            parentFile!!.mkdirs()
            writeText("foo")
        }
        val profileFile = File(projectRoot, "apk-export-permissions.json")

        try {
            profileFile.writeText(
                """
                {
                  "selectedBuiltinPermissions": [],
                  "versionCode": 7,
                  "additionalRuntimeLibraryPaths": [
                    "${runtimeLib.absolutePath.replace("\\", "\\\\")}"
                  ]
                }
                """.trimIndent()
            )
            val remembered = loadRememberedApkExportPermissions(
                outputDir = outputDir,
                fallbackRoot = projectRoot
            )

            assertThat(profileFile.exists()).isTrue()
            assertThat(remembered?.versionCode).isEqualTo(7)
            assertThat(remembered?.additionalRuntimeLibraryPaths)
                .containsExactly(runtimeLib.absolutePath)
        } finally {
            outputDir.deleteRecursively()
            projectRoot.deleteRecursively()
        }
    }
}

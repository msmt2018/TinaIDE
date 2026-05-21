package com.wuxianggujun.tinaide.project

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class PluginZipTemplateInstallTest {

    @Test
    fun `plugin zip template install replaces placeholders and writes plugin metadata`() {
        val tempDir = Files.createTempDirectory("plugin-template-install").toFile()
        val zipFile = Files.createTempFile("plugin-template", ".zip").toFile()

        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                zip.writeEntry(
                    "manifest.json",
                    """
                    {
                      "id": "com.example.{{PROJECT_NAME}}",
                      "name": "{{PROJECT_NAME}}",
                      "version": "0.1.0",
                      "type": "config"
                    }
                    """.trimIndent()
                )
                zip.writeEntry(
                    "README.md",
                    "# {{PROJECT_NAME}}\n"
                )
            }

            val installed = ProjectTemplateInstaller.install(
                destDir = tempDir,
                projectName = "hello-plugin",
                templateSpec = ProjectTemplateSpec.Zip(
                    id = "plugin:tinaide.plugin.starters:config-basic",
                    zipFile = zipFile,
                    buildSystem = ProjectBuildSystem.PLUGIN,
                    primaryLanguage = ProjectLanguage.MIXED
                )
            )

            val metadata = ProjectMetadataStore.read(tempDir)

            assertThat(installed).isTrue()
            assertThat(tempDir.resolve("manifest.json").readText(Charsets.UTF_8))
                .contains("com.example.hello-plugin")
            assertThat(tempDir.resolve("README.md").readText(Charsets.UTF_8))
                .contains("# hello-plugin")
            assertThat(metadata?.buildSystem).isEqualTo(ProjectBuildSystem.PLUGIN)
            assertThat(metadata?.primaryLanguage).isEqualTo(ProjectLanguage.MIXED.name)
        } finally {
            tempDir.deleteRecursively()
            zipFile.delete()
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}

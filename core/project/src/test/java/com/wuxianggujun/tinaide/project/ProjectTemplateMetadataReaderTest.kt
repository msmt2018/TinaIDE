package com.wuxianggujun.tinaide.project

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class ProjectTemplateMetadataReaderTest {

    @Test
    fun `readFromZip parses template metadata`() {
        val zipFile = Files.createTempFile("template-metadata", ".zip").toFile()

        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                zip.writeEntry(
                    ProjectTemplateMetadataReader.METADATA_FILE_NAME,
                    """
                    {
                      "name": "Game Starter",
                      "description": "SDL3 starter project",
                      "author": "Tina",
                      "buildSystem": "cmake",
                      "primaryLanguage": "c++",
                      "ndkTemplate": true,
                      "variables": {
                        "AUTHOR": "TinaIDE",
                        "EMPTY": "",
                        "COUNT": 1
                      }
                    }
                    """.trimIndent()
                )
                zip.writeEntry("CMakeLists.txt", "project({{PROJECT_NAME}})")
            }

            val metadata = ProjectTemplateMetadataReader.readFromZip(zipFile)

            assertThat(metadata?.name).isEqualTo("Game Starter")
            assertThat(metadata?.description).isEqualTo("SDL3 starter project")
            assertThat(metadata?.author).isEqualTo("Tina")
            assertThat(metadata?.buildSystem).isEqualTo(ProjectBuildSystem.CMAKE)
            assertThat(metadata?.primaryLanguage).isEqualTo(ProjectLanguage.CPP)
            assertThat(metadata?.isNdkTemplate).isTrue()
            assertThat(metadata?.variables).containsExactly("AUTHOR", "TinaIDE")
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun `readFromZip returns null for invalid metadata`() {
        val zipFile = Files.createTempFile("template-metadata-invalid", ".zip").toFile()

        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                zip.writeEntry(ProjectTemplateMetadataReader.METADATA_FILE_NAME, "{")
                zip.writeEntry("main.cpp", "int main() { return 0; }")
            }

            assertThat(ProjectTemplateMetadataReader.readFromZip(zipFile)).isNull()
        } finally {
            zipFile.delete()
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}

package com.wuxianggujun.tinaide.ai.integration

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test

class PathUtilsTest {

    @Test
    fun `normalizeFilePath removes file URI prefixes`() {
        assertThat(PathUtils.normalizeFilePath("file:///tmp/project/main.cpp"))
            .isEqualTo("/tmp/project/main.cpp")
        assertThat(PathUtils.normalizeFilePath("file:/tmp/project/main.cpp"))
            .isEqualTo("/tmp/project/main.cpp")
        assertThat(PathUtils.normalizeFilePath("/tmp/project/main.cpp"))
            .isEqualTo("/tmp/project/main.cpp")
    }

    @Test
    fun `toRelativePath returns path relative to project root`() {
        val projectRoot = createTempDirectory(prefix = "path-utils-project-").toFile()
        try {
            val sourceFile = File(projectRoot, "src/main.cpp")
            sourceFile.parentFile!!.mkdirs()
            sourceFile.writeText("int main() { return 0; }")

            val relativePath = PathUtils.toRelativePath(
                absolutePath = sourceFile.toURI().toString(),
                projectRoot = projectRoot.absolutePath
            )

            assertThat(relativePath.replace(File.separatorChar, '/'))
                .isEqualTo("src/main.cpp")
        } finally {
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun `toRelativePath keeps normalized path outside project root`() {
        val projectRoot = createTempDirectory(prefix = "path-utils-project-").toFile()
        val externalRoot = createTempDirectory(prefix = "path-utils-external-").toFile()
        try {
            val externalFile = File(externalRoot, "outside.cpp")
            externalFile.writeText("int outside() { return 1; }")

            val relativePath = PathUtils.toRelativePath(
                absolutePath = externalFile.toURI().toString(),
                projectRoot = projectRoot.absolutePath
            )

            assertThat(File(relativePath).canonicalFile)
                .isEqualTo(externalFile.canonicalFile)
        } finally {
            projectRoot.deleteRecursively()
            externalRoot.deleteRecursively()
        }
    }

    @Test
    fun `toAbsolutePath resolves relative path against project root`() {
        val projectRoot = createTempDirectory(prefix = "path-utils-project-").toFile()
        try {
            val absolutePath = PathUtils.toAbsolutePath(
                relativePath = "src/main.cpp",
                projectRoot = projectRoot.absolutePath
            )

            assertThat(File(absolutePath).canonicalFile)
                .isEqualTo(File(projectRoot, "src/main.cpp").canonicalFile)
        } finally {
            projectRoot.deleteRecursively()
        }
    }
}

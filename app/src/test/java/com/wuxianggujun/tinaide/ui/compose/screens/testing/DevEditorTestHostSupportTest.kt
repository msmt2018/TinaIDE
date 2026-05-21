package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test

class DevEditorTestHostSupportTest {

    @Test
    fun renderFixtureContent_shouldReplaceWorkspaceTokenWithNormalizedPath() {
        val workspaceDir = Files.createTempDirectory("dev-editor-workspace").toFile()

        try {
            val rendered = DevEditorTestHostSupport.renderFixtureContent(
                workspaceDir = workspaceDir,
                rawContent = """
                    {
                      "directory": "$DEV_EDITOR_WORKSPACE_PATH_TOKEN",
                      "file": "$DEV_EDITOR_WORKSPACE_PATH_TOKEN/src/main.cpp"
                    }
                """.trimIndent()
            )
            val normalizedWorkspace = workspaceDir.absolutePath.replace(File.separatorChar, '/')

            assertThat(rendered).contains(normalizedWorkspace)
            assertThat(rendered).doesNotContain(DEV_EDITOR_WORKSPACE_PATH_TOKEN)
            assertThat(rendered).contains("$normalizedWorkspace/src/main.cpp")
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    @Test
    fun resolveActiveFixtureIndex_shouldReturnNullForEmptyAndClampOutOfRange() {
        val fixtures = listOf(
            DevEditorFixture("a.txt", "a"),
            DevEditorFixture("b.txt", "b"),
            DevEditorFixture("c.txt", "c")
        )

        assertThat(DevEditorTestHostSupport.resolveActiveFixtureIndex(emptyList(), 0)).isNull()
        assertThat(DevEditorTestHostSupport.resolveActiveFixtureIndex(fixtures, -4)).isEqualTo(0)
        assertThat(DevEditorTestHostSupport.resolveActiveFixtureIndex(fixtures, 1)).isEqualTo(1)
        assertThat(DevEditorTestHostSupport.resolveActiveFixtureIndex(fixtures, 99)).isEqualTo(2)
    }

    @Test
    fun createProjectContext_shouldExposeStableProjectMetadataForWorkspace() {
        val workspaceDir = Files.createTempDirectory("dev-editor-project-context").toFile()

        try {
            val project = checkNotNull(
                DevEditorTestHostSupport.createProjectContext(
                    workspaceKey = "clangd",
                    workspaceDir = workspaceDir
                ).getCurrentProject()
            )
            val buildWorkspaceDir = File(workspaceDir, ".workspace")

            assertThat(project.id).isEqualTo("dev-clangd")
            assertThat(project.name).isEqualTo("dev-clangd")
            assertThat(project.rootPath).isEqualTo(workspaceDir.absolutePath)
            assertThat(project.workspaceRootPath).isEqualTo(buildWorkspaceDir.absolutePath)
            assertThat(project.buildDirPath).isEqualTo(File(buildWorkspaceDir, "build").absolutePath)
            assertThat(project.files).isEmpty()
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    @Test
    fun clangdFixtures_shouldKeepCrossFileSamplesAndWorkspaceTokensStable() {
        val fixtures = DevEditorTestSamples.clangdFixtures().associateBy { it.relativePath }
        val compileCommands = fixtures.getValue("compile_commands.json").content
        val clangdConfig = fixtures.getValue(".clangd").content
        val mainSource = fixtures.getValue("src/main.cpp").content
        val headerSource = fixtures.getValue("include/math_utils.h").content
        val implSource = fixtures.getValue("src/math_utils.cpp").content

        assertThat(fixtures.keys).containsExactly(
            "src/main.cpp",
            "include/math_utils.h",
            "src/math_utils.cpp",
            "compile_commands.json",
            ".clangd"
        )
        assertThat(mainSource).contains("#include \"math_utils.h\"")
        assertThat(mainSource).contains("math::Accumulator")
        assertThat(headerSource).contains("class Accumulator")
        assertThat(headerSource).contains("describe_total")
        assertThat(implSource).contains("Accumulator::add")
        assertThat(implSource).contains("describe_total")
        assertThat(compileCommands).contains(DEV_EDITOR_WORKSPACE_PATH_TOKEN)
        assertThat(compileCommands).contains("-I$DEV_EDITOR_WORKSPACE_PATH_TOKEN/include")
        assertThat(clangdConfig).contains(DEV_EDITOR_WORKSPACE_PATH_TOKEN)
        assertThat(clangdConfig).contains("UnusedIncludes: Strict")
    }
}

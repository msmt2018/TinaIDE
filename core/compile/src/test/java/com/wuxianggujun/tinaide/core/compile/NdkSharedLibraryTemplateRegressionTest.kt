package com.wuxianggujun.tinaide.core.compile

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import org.junit.Test

class NdkSharedLibraryTemplateRegressionTest {

    @Test
    fun `ndk shared library template provides runnable test target`() {
        val zipPath = locateRepoRoot()
            .resolve("app/src/main/assets/bundled_plugins/tinaide.project.templates/templates/ndk_shared_library.zip")

        val cmake = readZipEntry(zipPath, "CMakeLists.txt")
        val header = readZipEntry(zipPath, "include/{{PROJECT_NAME}}.h")
        val testMain = readZipEntry(zipPath, "test/main.cpp")

        assertThat(cmake).contains("add_library({{PROJECT_NAME}} SHARED")
        assertThat(cmake).contains("target_compile_definitions({{PROJECT_NAME}} PRIVATE {{PROJECT_NAME_UPPER}}_BUILDING_LIBRARY)")
        assertThat(cmake).contains("set_target_properties({{PROJECT_NAME}} PROPERTIES")
        assertThat(cmake).contains("CXX_VISIBILITY_PRESET default")
        assertThat(cmake).contains("VISIBILITY_INLINES_HIDDEN OFF")
        assertThat(cmake).contains("add_executable({{PROJECT_NAME}}_test")
        assertThat(cmake).contains("target_link_libraries({{PROJECT_NAME}}_test PRIVATE {{PROJECT_NAME}})")
        assertThat(cmake).contains("target_link_libraries({{PROJECT_NAME}}_test PRIVATE \${log-lib})")
        assertThat(header).contains("#define {{PROJECT_NAME_UPPER}}_API")
        assertThat(header).contains("{{PROJECT_NAME_UPPER}}_API const char* {{PROJECT_NAME}}_get_greeting(void);")
        assertThat(header).contains("{{PROJECT_NAME_UPPER}}_API int {{PROJECT_NAME}}_add(int a, int b);")
        assertThat(testMain).contains("printf(\"Greeting: %s\\n\", greeting);")
        assertThat(testMain).contains("printf(\"All tests passed!\\n\");")
    }

    @Test
    fun `ndk shared library manifest declares default targets`() {
        val manifestPath = locateRepoRoot()
            .resolve("app/src/main/assets/bundled_plugins/tinaide.project.templates/manifest.json")
        val manifest = String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8)

        assertThat(manifest).contains("\"id\": \"ndk-shared-library\"")
        assertThat(manifest).contains("\"defaultRunTargetName\": \"{{PROJECT_NAME}}_test\"")
        assertThat(manifest).contains("\"defaultSdlTargetName\": \"{{PROJECT_NAME}}\"")
    }

    private fun readZipEntry(zipPath: Path, entryName: String): String = ZipFile(zipPath.toFile()).use { zip ->
        val entry = requireNotNull(zip.getEntry(entryName)) {
            "$entryName missing from ndk_shared_library.zip"
        }
        zip.getInputStream(entry).use { input ->
            String(input.readBytes(), StandardCharsets.UTF_8)
        }
    }

    private fun locateRepoRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (true) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent ?: error("Repository root with settings.gradle.kts was not found")
        }
    }
}

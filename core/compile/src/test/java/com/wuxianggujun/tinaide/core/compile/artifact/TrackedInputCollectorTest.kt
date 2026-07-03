package com.wuxianggujun.tinaide.core.compile.artifact

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrackedInputCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `make collector includes makefile and deep source while excluding build outputs`() {
        val projectRoot = tempFolder.newFolder("make-project")
        val makefile = File(projectRoot, "Makefile").apply { writeText("all:\n\t@true\n") }
        val deepSource = File(projectRoot, "src/deep/nested/main.c").apply {
            parentFile?.mkdirs()
            writeText("int main() { return 0; }")
        }
        File(projectRoot, "build/generated.c").apply {
            parentFile?.mkdirs()
            writeText("int generated() { return 1; }")
        }

        val tracked = TrackedInputCollector.collectMakeInputs(projectRoot, setOf("c", "h"))

        assertThat(tracked).contains(makefile)
        assertThat(tracked).contains(deepSource)
        assertThat(tracked).doesNotContain(File(projectRoot, "build/generated.c"))
    }

    @Test
    fun `cmake reconfigure collector includes project sources and cmake scripts while excluding build outputs`() {
        val projectRoot = tempFolder.newFolder("cmake-project")
        val targetSource = File(projectRoot, "src/main.cpp").apply {
            parentFile?.mkdirs()
            writeText("int main() { return 0; }")
        }
        val linkedLibrarySource = File(projectRoot, "src/lib.cpp").apply {
            parentFile?.mkdirs()
            writeText("int lib() { return 1; }")
        }
        val header = File(projectRoot, "include/lib.hpp").apply {
            parentFile?.mkdirs()
            writeText("int lib();")
        }
        val rootCmake = File(projectRoot, "CMakeLists.txt").apply { writeText("add_executable(app src/main.cpp)") }
        val moduleCmake = File(projectRoot, "cmake/toolchain/custom.cmake").apply {
            parentFile?.mkdirs()
            writeText("set(CMAKE_CXX_STANDARD 20)")
        }
        val generatedSource = File(projectRoot, "build/generated.cpp").apply {
            parentFile?.mkdirs()
            writeText("int generated() { return 2; }")
        }

        val tracked = TrackedInputCollector.collectCMakeReconfigureInputs(projectRoot)

        assertThat(tracked).contains(targetSource)
        assertThat(tracked).contains(linkedLibrarySource)
        assertThat(tracked).contains(header)
        assertThat(tracked).contains(rootCmake)
        assertThat(tracked).contains(moduleCmake)
        assertThat(tracked).doesNotContain(generatedSource)
    }

    @Test
    fun `cmake compile collector uses compile commands target closure when available`() {
        val projectRoot = tempFolder.newFolder("cmake-compile-commands-project")
        val buildDir = File(projectRoot, "cmake-build").apply { mkdirs() }
        val appSource = File(projectRoot, "src/app.cpp").apply {
            parentFile?.mkdirs()
            writeText("int main() { return 0; }")
        }
        val linkedLibrarySource = File(projectRoot, "src/core.cpp").apply {
            parentFile?.mkdirs()
            writeText("int core() { return 1; }")
        }
        val unrelatedConfiguredSource = File(projectRoot, "tools/other.cpp").apply {
            parentFile?.mkdirs()
            writeText("int other() { return 2; }")
        }
        val unconfiguredSource = File(projectRoot, "scratch/manual.cpp").apply {
            parentFile?.mkdirs()
            writeText("int manual() { return 3; }")
        }
        File(projectRoot, "CMakeLists.txt").apply {
            writeText(
                """
                add_library(core src/core.cpp)
                add_executable(app src/app.cpp)
                target_link_libraries(app PRIVATE core)
                add_executable(other tools/other.cpp)
                """.trimIndent(),
            )
        }
        val compileCommands = File(buildDir, "compile_commands.json").apply {
            writeText(
                """
                [
                  {
                    "directory": "${jsonPath(projectRoot)}",
                    "command": "clang++ -o CMakeFiles/app.dir/src/app.cpp.o -c ${jsonPath(appSource)}",
                    "file": "${jsonPath(appSource)}",
                    "output": "CMakeFiles/app.dir/src/app.cpp.o"
                  },
                  {
                    "directory": "${jsonPath(projectRoot)}",
                    "command": "clang++ -o CMakeFiles/core.dir/src/core.cpp.o -c ${jsonPath(linkedLibrarySource)}",
                    "file": "${jsonPath(linkedLibrarySource)}",
                    "output": "CMakeFiles/core.dir/src/core.cpp.o"
                  },
                  {
                    "directory": "${jsonPath(projectRoot)}",
                    "command": "clang++ -o CMakeFiles/other.dir/tools/other.cpp.o -c ${jsonPath(unrelatedConfiguredSource)}",
                    "file": "${jsonPath(unrelatedConfiguredSource)}",
                    "output": "CMakeFiles/other.dir/tools/other.cpp.o"
                  }
                ]
                """.trimIndent(),
                Charsets.UTF_8,
            )
        }

        val tracked = TrackedInputCollector.collectCMakeCompileInputs(
            projectRoot = projectRoot,
            buildDir = buildDir,
            targetNames = setOf("app", "core"),
            targetSources = listOf(appSource),
        )

        assertThat(tracked).contains(appSource)
        assertThat(tracked).contains(linkedLibrarySource)
        assertThat(tracked).contains(compileCommands)
        assertThat(tracked).doesNotContain(unrelatedConfiguredSource)
        assertThat(tracked).doesNotContain(unconfiguredSource)
    }

    @Test
    fun `cmake compile collector falls back to all compile commands when target names do not match`() {
        val projectRoot = tempFolder.newFolder("cmake-compile-commands-fallback-project")
        val buildDir = File(projectRoot, "cmake-build").apply { mkdirs() }
        val configuredSource = File(projectRoot, "src/main.cpp").apply {
            parentFile?.mkdirs()
            writeText("int main() { return 0; }")
        }
        val unconfiguredSource = File(projectRoot, "src/manual.cpp").apply {
            parentFile?.mkdirs()
            writeText("int manual() { return 1; }")
        }
        File(projectRoot, "CMakeLists.txt").writeText("add_executable(app src/main.cpp)")
        File(buildDir, "compile_commands.json").writeText(
            """
            [
              {
                "directory": "${jsonPath(projectRoot)}",
                "command": "clang++ -o unknown-object.o -c src/main.cpp",
                "file": "src/main.cpp"
              }
            ]
            """.trimIndent(),
            Charsets.UTF_8,
        )

        val tracked = TrackedInputCollector.collectCMakeCompileInputs(
            projectRoot = projectRoot,
            buildDir = buildDir,
            targetNames = setOf("app"),
            targetSources = emptyList(),
        )

        assertThat(tracked).contains(configuredSource)
        assertThat(tracked).doesNotContain(unconfiguredSource)
    }

    private fun jsonPath(file: File): String = file.absolutePath.replace('\\', '/')
}

package com.wuxianggujun.tinaide.cmake

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CMakeDocTest {

    @Test
    fun parse_shouldExposeProjectMetadataAndLanguages() {
        val doc = CMake.parse(
            """
            cmake_minimum_required(VERSION 3.22)
            project(TinaNative VERSION 1.2.3 LANGUAGES C CXX)
            """.trimIndent()
        ).getOrThrow()

        assertThat(doc.projectName).isEqualTo("TinaNative")
        assertThat(doc.projectVersion).isEqualTo("1.2.3")
        assertThat(doc.minimumVersion).isEqualTo("3.22")
        assertThat(doc.languages).containsExactly("C", "CXX").inOrder()
    }

    @Test
    fun parse_shouldCollectTargetsAndTargetScopedProperties() {
        val doc = CMake.parse(
            """
            add_executable(app WIN32 main.cpp ui.cpp)
            add_library(core SHARED core.cpp)
            add_library(alias ALIAS core)
            add_custom_target(generate_assets)
            target_link_libraries(app PRIVATE core PUBLIC log)
            target_include_directories(app PRIVATE include SYSTEM public/include)
            target_compile_definitions(app PRIVATE DEBUG=1 PUBLIC USE_LOG)
            """.trimIndent()
        ).getOrThrow()

        assertThat(doc.targets.map { it.name })
            .containsExactly("app", "core", "generate_assets")
            .inOrder()
        assertThat(doc.targets[0].type).isEqualTo(CMakeDoc.TargetType.EXECUTABLE)
        assertThat(doc.targets[0].sources).containsExactly("main.cpp", "ui.cpp").inOrder()
        assertThat(doc.targets[1].type).isEqualTo(CMakeDoc.TargetType.SHARED_LIBRARY)
        assertThat(doc.targets[1].sources).containsExactly("core.cpp")
        assertThat(doc.targets[2].type).isEqualTo(CMakeDoc.TargetType.CUSTOM_TARGET)

        assertThat(doc.getTargetLibraries("app")).containsExactly("core", "log").inOrder()
        assertThat(doc.getTargetIncludeDirectories("app"))
            .containsExactly("include", "public/include")
            .inOrder()
        assertThat(doc.getTargetCompileDefinitions("app"))
            .containsExactly("DEBUG=1", "USE_LOG")
            .inOrder()
    }

    @Test
    fun parse_shouldCollectVariablesSubdirectoriesAndPackages() {
        val doc = CMake.parse(
            """
            set(SOURCES main.cpp util.cpp PARENT_SCOPE)
            set(ENABLE_CACHE ON CACHE BOOL "enable")
            add_subdirectory(native)
            find_package(Threads REQUIRED)
            find_package(Qt6 6.5 REQUIRED COMPONENTS Core Widgets)
            """.trimIndent()
        ).getOrThrow()

        assertThat(doc.variables[0].name).isEqualTo("SOURCES")
        assertThat(doc.variables[0].values).containsExactly("main.cpp", "util.cpp").inOrder()
        assertThat(doc.variables[0].isParentScope).isTrue()
        assertThat(doc.variables[0].isCache).isFalse()

        assertThat(doc.variables[1].name).isEqualTo("ENABLE_CACHE")
        assertThat(doc.variables[1].values).containsExactly("ON").inOrder()
        assertThat(doc.variables[1].isCache).isTrue()

        assertThat(doc.subdirectories).containsExactly("native")
        assertThat(doc.packages.map { it.name }).containsExactly("Threads", "Qt6").inOrder()
        assertThat(doc.packages[0].isRequired).isTrue()
        assertThat(doc.packages[1].version).isEqualTo("6.5")
        assertThat(doc.packages[1].components).containsExactly("Core", "Widgets").inOrder()
    }
}

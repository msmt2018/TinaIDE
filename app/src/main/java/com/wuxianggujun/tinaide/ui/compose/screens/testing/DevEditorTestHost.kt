package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.config.IConfigManager
import com.wuxianggujun.tinaide.editor.EditorManager
import com.wuxianggujun.tinaide.editor.session.SaveReason
import com.wuxianggujun.tinaide.editor.symbol.ProjectSymbolIndexService
import com.wuxianggujun.tinaide.editor.theme.PluginEditorThemeRegistry
import com.wuxianggujun.tinaide.file.IProjectContext
import com.wuxianggujun.tinaide.file.Project
import com.wuxianggujun.tinaide.plugin.PluginManager
import com.wuxianggujun.tinaide.plugin.PluginSnippetManager
import com.wuxianggujun.tinaide.ui.compose.components.EditorContainer
import com.wuxianggujun.tinaide.ui.compose.components.TinaTopBar
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import com.wuxianggujun.tinaide.ui.compose.state.editor.rememberEditorContainerState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext

internal const val DEV_EDITOR_WORKSPACE_PATH_TOKEN = "__DEV_EDITOR_WORKSPACE__"

internal data class DevEditorFixture(
    val relativePath: String,
    val content: String
)

internal object DevEditorTestHostSupport {
    fun createWorkspaceDir(cacheDir: File, workspaceKey: String): File = File(cacheDir, "dev-editor-tests/$workspaceKey").apply {
        deleteRecursively()
        mkdirs()
    }

    fun createProjectContext(workspaceKey: String, workspaceDir: File): IProjectContext {
        val buildWorkspaceDir = File(workspaceDir, ".workspace").apply { mkdirs() }
        val project = Project(
            id = "dev-$workspaceKey",
            name = "dev-$workspaceKey",
            rootPath = workspaceDir.absolutePath,
            workspaceRootPath = buildWorkspaceDir.absolutePath,
            files = emptyList(),
            buildDirPath = File(buildWorkspaceDir, "build").absolutePath
        )
        val flow = MutableStateFlow<Project?>(project).asStateFlow()
        return object : IProjectContext {
            override fun getCurrentProject(): Project = project
            override val currentProjectFlow: StateFlow<Project?> = flow
        }
    }

    suspend fun materializeFixtures(
        workspaceDir: File,
        fixtures: List<DevEditorFixture>
    ): List<File> = withContext(Dispatchers.IO) {
        fixtures.map { fixture ->
            File(workspaceDir, fixture.relativePath).apply {
                parentFile?.mkdirs()
                writeText(
                    renderFixtureContent(
                        workspaceDir = workspaceDir,
                        rawContent = fixture.content
                    ),
                    Charsets.UTF_8
                )
            }
        }
    }

    fun renderFixtureContent(
        workspaceDir: File,
        rawContent: String
    ): String {
        val normalizedWorkspacePath = workspaceDir.absolutePath.replace(File.separatorChar, '/')
        return rawContent.replace(DEV_EDITOR_WORKSPACE_PATH_TOKEN, normalizedWorkspacePath)
    }

    fun resolveActiveFixtureIndex(
        fixtures: List<DevEditorFixture>,
        requestedIndex: Int
    ): Int? {
        if (fixtures.isEmpty()) return null
        return requestedIndex.coerceIn(0, fixtures.lastIndex)
    }

    suspend fun bootstrapEditorState(
        editorManager: EditorManager,
        editorState: com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState,
        workspaceDir: File,
        fixtures: List<DevEditorFixture>,
        activeFixtureIndex: Int
    ): List<File> {
        editorManager.closeAll(clearPersistentState = true)
        val writtenFiles = materializeFixtures(workspaceDir, fixtures)
        writtenFiles.forEach(editorState::openFile)
        resolveActiveFixtureIndex(fixtures, activeFixtureIndex)
            ?.let(editorState::selectTab)
        return writtenFiles
    }
}

@Composable
internal fun DevEditorTestHost(
    workspaceKey: String,
    title: String,
    fixtures: List<DevEditorFixture>,
    onNavigateBack: (() -> Unit)? = null,
    activeFixtureIndex: Int = fixtures.lastIndex.coerceAtLeast(0),
    reloadToken: Any? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    headerContent: @Composable ColumnScope.(workspaceDir: File, editorState: EditorContainerState) -> Unit = { _, _ -> },
    footerContent: @Composable ColumnScope.(workspaceDir: File, editorState: EditorContainerState) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configManager: IConfigManager = koinInject()
    val snippetManager: PluginSnippetManager = koinInject()
    val pluginThemeRegistry: PluginEditorThemeRegistry = koinInject()
    val pluginManager = remember(context) { PluginManager.getInstance(context.applicationContext) }
    val workspaceDir = remember(workspaceKey) {
        DevEditorTestHostSupport.createWorkspaceDir(
            cacheDir = context.cacheDir,
            workspaceKey = workspaceKey
        )
    }
    val projectContext = remember(workspaceDir) {
        DevEditorTestHostSupport.createProjectContext(
            workspaceKey = workspaceKey,
            workspaceDir = workspaceDir
        )
    }
    val projectSymbolIndexServiceProvider = remember {
        { GlobalContext.getOrNull()?.getOrNull<ProjectSymbolIndexService>() }
    }
    val editorManager = remember(workspaceDir, configManager, projectContext) {
        createIsolatedEditorManager(
            context = context.applicationContext,
            configManager = configManager,
            projectContext = projectContext,
            projectSymbolIndexServiceProvider = projectSymbolIndexServiceProvider
        )
    }
    val editorState = rememberEditorContainerState(
        editorManager = editorManager,
        snippetManager = snippetManager,
        pluginThemeRegistry = pluginThemeRegistry,
        projectSymbolIndexServiceProvider = projectSymbolIndexServiceProvider,
        projectRootPathProvider = { workspaceDir.absolutePath }
    )

    LaunchedEffect(fixtures, activeFixtureIndex, reloadToken) {
        DevEditorTestHostSupport.bootstrapEditorState(
            editorManager = editorManager,
            editorState = editorState,
            workspaceDir = workspaceDir,
            fixtures = fixtures,
            activeFixtureIndex = activeFixtureIndex
        )
    }

    DisposableEffect(editorManager, workspaceDir) {
        onDispose {
            editorManager.closeAll(clearPersistentState = true)
            editorManager.onDestroy()
            workspaceDir.deleteRecursively()
        }
    }

    Scaffold(
        topBar = {
            TinaTopBar(
                title = title,
                onNavigateBack = { onNavigateBack?.invoke() },
                actions = topBarActions
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            headerContent(workspaceDir, editorState)
            EditorContainer(
                state = editorState,
                editorManager = editorManager,
                pluginManager = pluginManager,
                hostCommandExecutor = null,
                onOpenFileTree = {},
                onEditorStateChanged = { _, _, _, _ -> },
                onSaveFile = { tabId, onComplete ->
                    scope.launch {
                        editorManager.save(tabId, SaveReason.MANUAL)
                        onComplete()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            footerContent(workspaceDir, editorState)
        }
    }
}

@Composable
internal fun DevEditorInfoCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
internal fun DevEditorSectionCard(
    title: String,
    body: @Composable ColumnScope.() -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            body()
        }
    }
}

internal object DevEditorTestSamples {
    fun themePreviewFixtures(): List<DevEditorFixture> = listOf(
        DevEditorFixture("ThemePreview.kt", kotlinThemePreviewSample()),
        DevEditorFixture("preview.json", jsonThemePreviewSample()),
        DevEditorFixture("layout.xml", xmlThemePreviewSample()),
        DevEditorFixture("CMakeLists.txt", cmakePreviewSample()),
        DevEditorFixture("Makefile", makePreviewSample())
    )

    fun treeSitterSamples(): List<TreeSitterSampleOption> = listOf(
        TreeSitterSampleOption(
            id = "cmake",
            label = "CMakeLists.txt",
            fixture = DevEditorFixture("CMakeLists.txt", cmakePreviewSample())
        ),
        TreeSitterSampleOption(
            id = "make",
            label = "Makefile",
            fixture = DevEditorFixture("Makefile", makePreviewSample())
        ),
        TreeSitterSampleOption(
            id = "json",
            label = "preview.json",
            fixture = DevEditorFixture("preview.json", jsonThemePreviewSample())
        ),
        TreeSitterSampleOption(
            id = "xml",
            label = "layout.xml",
            fixture = DevEditorFixture("layout.xml", xmlThemePreviewSample())
        ),
        TreeSitterSampleOption(
            id = "kotlin",
            label = "ThemePreview.kt",
            fixture = DevEditorFixture("ThemePreview.kt", kotlinThemePreviewSample())
        )
    )

    fun editorScrollFixture(): List<DevEditorFixture> = listOf(
        DevEditorFixture(
            relativePath = "EditorScroll.kt",
            content = EditorScrollTestScreenSupport.buildScrollProbeContent(1600)
        )
    )

    fun cppScrollStressFixture(): DevEditorFixture {
        val content = buildString {
            appendLine("#include <algorithm>")
            appendLine("#include <iostream>")
            appendLine("#include <string>")
            appendLine("#include <vector>")
            appendLine()
            appendLine("namespace stress_test {")
            appendLine()

            repeat(420) { classIndex ->
                appendLine("class StressClass$classIndex {")
                appendLine("public:")
                appendLine("    StressClass$classIndex() = default;")
                appendLine("    ~StressClass$classIndex() = default;")
                appendLine()
                repeat(6) { methodIndex ->
                    appendLine("    std::string method$methodIndex(int seed) const {")
                    appendLine("        std::vector<int> values;")
                    appendLine("        values.reserve(128);")
                    appendLine("        for (int i = 0; i < 128; ++i) {")
                    appendLine("            values.push_back(seed + i + $classIndex + $methodIndex);")
                    appendLine("        }")
                    appendLine("        std::sort(values.begin(), values.end());")
                    appendLine("        return \"StressClass$classIndex::method$methodIndex -> \" + std::to_string(values.back());")
                    appendLine("    }")
                    appendLine()
                }
                appendLine("};")
                appendLine()
            }

            appendLine("}  // namespace stress_test")
            appendLine()
            appendLine("int main() {")
            appendLine("    stress_test::StressClass0 demo;")
            appendLine("    std::cout << demo.method0(7) << std::endl;")
            appendLine("    return 0;")
            appendLine("}")
        }
        return DevEditorFixture("stress.cpp", content)
    }

    fun clangdFixtures(): List<DevEditorFixture> = listOf(
        DevEditorFixture("src/main.cpp", clangdMainSample()),
        DevEditorFixture("include/math_utils.h", clangdHeaderSample()),
        DevEditorFixture("src/math_utils.cpp", clangdSourceSample()),
        DevEditorFixture("compile_commands.json", clangdCompileCommandsSample()),
        DevEditorFixture(".clangd", clangdConfigSample())
    )

    private fun kotlinThemePreviewSample(): String = """
            package preview

            import kotlin.math.absoluteValue

            data class RenderState(
                val title: String,
                val count: Int,
                val enabled: Boolean
            )

            private const val AccentColor = 0xFF4A5D23

            fun buildPreview(state: RenderState): String {
                val banner = "[${'$'}{state.count}] ${'$'}{state.title}"
                return if (state.enabled) {
                    "ready:${'$'}banner:${'$'}AccentColor"
                } else {
                    "paused:${'$'}{banner.lowercase()}"
                }
            }

            fun sampleSequence(limit: Int): List<String> {
                return buildList {
                    for (index in 0 until limit) {
                        val sign = if (index % 2 == 0) "even" else "odd"
                        add("${'$'}sign-${'$'}{index.absoluteValue}")
                    }
                }
            }
    """.trimIndent()

    private fun jsonThemePreviewSample(): String = """
            {
              "name": "theme-preview",
              "enabled": true,
              "threshold": 0.82,
              "languages": ["kotlin", "json", "xml", "cmake", "make"],
              "metadata": {
                "owner": "dev-options",
                "updatedAt": "2026-04-06T10:30:00Z"
              }
            }
    """.trimIndent()

    private fun xmlThemePreviewSample(): String = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:id="@+id/title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Theme Preview"
                    android:textStyle="bold" />

                <Button
                    android:id="@+id/action"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Apply" />
            </LinearLayout>
    """.trimIndent()

    private fun cmakePreviewSample(): String = """
            cmake_minimum_required(VERSION 3.24)
            project(ThemePreview LANGUAGES C CXX)

            set(CMAKE_CXX_STANDARD 20)
            set(PREVIEW_SOURCES
                src/main.cpp
                src/theme_preview.cpp
            )

            add_library(theme_preview STATIC ${'$'}{PREVIEW_SOURCES})
            target_compile_definitions(theme_preview PRIVATE ENABLE_THEME_TRACE=1)
            target_include_directories(theme_preview PUBLIC include)

            function(register_preview_target target_name)
                message(STATUS "Register target: ${'$'}{target_name}")
            endfunction()

            register_preview_target(theme_preview)
    """.trimIndent()

    private fun makePreviewSample(): String = """
            APP_NAME := theme-preview
            BUILD_DIR := build
            SOURCES := main.c preview.c utils.c
            OBJECTS := $(SOURCES:%.c=$(BUILD_DIR)/%.o)
            CFLAGS := -Wall -Wextra -O2

            .PHONY: all clean run print-vars

            all: $(BUILD_DIR)/$(APP_NAME)

            $(BUILD_DIR)/$(APP_NAME): $(OBJECTS)
            	$(CC) $(CFLAGS) -o $@ $^

            $(BUILD_DIR)/%.o: %.c
            	@mkdir -p $(BUILD_DIR)
            	$(CC) $(CFLAGS) -c $< -o $@

            print-vars:
            	@echo "app=$(APP_NAME)"
            	@echo "objects=$(OBJECTS)"

            clean:
            	rm -rf $(BUILD_DIR)
    """.trimIndent()

    private fun clangdMainSample(): String = """
            #include "math_utils.h"

            #include <iostream>
            #include <vector>

            int main() {
                std::vector<int> values = {1, 2, 3, 4};
                math::Accumulator accumulator;
                for (int value : values) {
                    accumulator.add(value);
                }

                const auto total = accumulator.total();
                std::cout << math::describe_total(total) << std::endl;
                return 0;
            }
    """.trimIndent()

    private fun clangdHeaderSample(): String = """
            #pragma once

            #include <string>

            namespace math {

            class Accumulator {
            public:
                void add(int value);
                [[nodiscard]] int total() const;

            private:
                int value_ = 0;
            };

            [[nodiscard]] std::string describe_total(int value);

            }  // namespace math
    """.trimIndent()

    private fun clangdSourceSample(): String = """
            #include "math_utils.h"

            #include <sstream>

            namespace math {

            void Accumulator::add(int value) {
                value_ += value;
            }

            int Accumulator::total() const {
                return value_;
            }

            std::string describe_total(int value) {
                std::ostringstream stream;
                stream << "total=" << value;
                return stream.str();
            }

            }  // namespace math
    """.trimIndent()

    private fun clangdCompileCommandsSample(): String = """
            [
              {
                "directory": "__DEV_EDITOR_WORKSPACE__",
                "command": "clang++ -std=c++20 -I__DEV_EDITOR_WORKSPACE__/include -c src/main.cpp -o build/main.o",
                "file": "src/main.cpp"
              },
              {
                "directory": "__DEV_EDITOR_WORKSPACE__",
                "command": "clang++ -std=c++20 -I__DEV_EDITOR_WORKSPACE__/include -c src/math_utils.cpp -o build/math_utils.o",
                "file": "src/math_utils.cpp"
              }
            ]
    """.trimIndent()

    private fun clangdConfigSample(): String = """
            CompileFlags:
              Add: [-std=c++20, -Wall, -I__DEV_EDITOR_WORKSPACE__/include]
            Diagnostics:
              UnusedIncludes: Strict
    """.trimIndent()
}
internal data class TreeSitterSampleOption(
    val id: String,
    val label: String,
    val fixture: DevEditorFixture
)

internal fun createIsolatedEditorManager(
    context: android.content.Context,
    configManager: IConfigManager,
    projectContext: IProjectContext,
    projectSymbolIndexServiceProvider: () -> ProjectSymbolIndexService?
): EditorManager = EditorManager(
    context = context,
    configManager = configManager,
    projectContextProvider = { projectContext },
    projectSymbolIndexServiceProvider = projectSymbolIndexServiceProvider
).also {
    it.onCreate()
    it.closeAll(clearPersistentState = true)
}

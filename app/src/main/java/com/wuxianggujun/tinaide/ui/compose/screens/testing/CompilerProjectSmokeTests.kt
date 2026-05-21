package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.content.Context
import com.wuxianggujun.tinaide.core.compile.BuildOptions
import com.wuxianggujun.tinaide.core.compile.BuildResult
import com.wuxianggujun.tinaide.core.compile.BuildSystem
import com.wuxianggujun.tinaide.core.compile.ConfigureResult
import com.wuxianggujun.tinaide.core.compile.action.CompileRequest
import com.wuxianggujun.tinaide.core.compile.cmake.NativeCMakeBuildExecutor
import com.wuxianggujun.tinaide.core.compile.event.BuildReport
import com.wuxianggujun.tinaide.core.compile.pipeline.BuildContextFactory
import com.wuxianggujun.tinaide.core.compile.pipeline.BuildOrchestrator
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.linux.LinuxRunModePolicy
import com.wuxianggujun.tinaide.core.lsp.CompileDatabaseProvider
import com.wuxianggujun.tinaide.core.lsp.LspClientSession
import com.wuxianggujun.tinaide.core.lsp.LspConnectionProvider
import com.wuxianggujun.tinaide.core.lsp.NativeClangdConnectionProvider
import com.wuxianggujun.tinaide.core.lsp.PRootClangdConnectionProvider
import com.wuxianggujun.tinaide.core.proot.PRootBootstrap
import com.wuxianggujun.tinaide.project.CppStandard
import com.wuxianggujun.tinaide.project.ProjectBuildSystem
import com.wuxianggujun.tinaide.project.ProjectMetadataStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.CompletionTriggerKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.koin.core.context.GlobalContext

private const val PROJECT_SMOKE_ROOT_DIR = "compiler-diagnostic-project-smoke"
private const val COMPLETION_PLACEHOLDER = "__COMPLETE_EXPR__"
private const val LSP_CONNECT_TIMEOUT_MS = 1_500L
private const val LSP_QUIET_PERIOD_MS = 800L
private const val LSP_MAX_WAIT_MS = 6_000L

private data class ProjectSmokeScenario(
    val id: String,
    val displayName: String,
    val description: String,
    val files: Map<String, String>,
    val targetFileRelativePath: String,
    val buildReplacement: String,
    val completionReplacement: String,
    val completionNeedle: String,
    val expectedCompletionLabels: List<String>
)

private data class LineColumn(
    val line: Int,
    val column: Int
)

private data class ProjectSmokeCheck(
    val name: String,
    val passed: Boolean,
    val detail: String? = null
)

data class ProjectSmokeStageTiming(
    val label: String,
    val durationMs: Long
)

data class ProjectSmokeRunResult(
    val scenarioId: String,
    val displayName: String,
    val description: String,
    val success: Boolean,
    val reportText: String,
    val totalDurationMs: Long? = null,
    val stageTimings: List<ProjectSmokeStageTiming> = emptyList(),
    val compileCommandsPath: String? = null,
    val clangdMode: String? = null,
    val diagnosticsTotalCount: Int? = null,
    val diagnosticsErrorCount: Int? = null,
    val diagnosticsWarningCount: Int? = null,
    val completionCandidateCount: Int? = null,
    val matchedCompletionLabels: List<String> = emptyList(),
    val passedChecksCount: Int = 0,
    val totalChecksCount: Int = 0,
    val failedStage: String? = null,
    val failureReason: String? = null,
    val failureDetail: String? = null
)

internal data class ProjectSmokeScenarioSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val files: Map<String, String>,
    val targetFileRelativePath: String,
    val buildReplacement: String,
    val completionReplacement: String,
    val completionNeedle: String,
    val expectedCompletionLabels: List<String>
)

internal data class ProjectSmokeLineColumn(
    val line: Int,
    val column: Int
)

private class ProjectDiagnosticCollector {
    private val diagnosticsByUri = ConcurrentHashMap<String, List<Diagnostic>>()
    private val lastUpdateAt = AtomicLong(0L)

    fun record(fileUri: String, diagnostics: List<Diagnostic>) {
        diagnosticsByUri[fileUri] = diagnostics
        lastUpdateAt.set(System.currentTimeMillis())
    }

    fun latestFor(fileUri: String): List<Diagnostic> = diagnosticsByUri[fileUri].orEmpty()

    suspend fun awaitQuietPeriod(
        maxWaitMs: Long = LSP_MAX_WAIT_MS,
        quietPeriodMs: Long = LSP_QUIET_PERIOD_MS
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxWaitMs) {
            val last = lastUpdateAt.get()
            if (last == 0L) {
                if (System.currentTimeMillis() - start >= quietPeriodMs) return
            } else if (System.currentTimeMillis() - last >= quietPeriodMs) {
                return
            }
            delay(120)
        }
    }
}

internal object CompilerProjectSmokeTestSupport {
    val completionPlaceholder: String
        get() = COMPLETION_PLACEHOLDER

    fun scenarioSpecs(): List<ProjectSmokeScenarioSpec> = projectSmokeScenarios().map(ProjectSmokeScenario::toSpec)

    fun resolveCompletionPositionInContent(
        content: String,
        completionNeedle: String
    ): ProjectSmokeLineColumn? = resolveCompletionPosition(
        content = content,
        completionNeedle = completionNeedle
    )?.toSpec()

    fun offsetToLineColumnInText(text: String, offset: Int): ProjectSmokeLineColumn = offsetToLineColumn(
        text = text,
        offset = offset
    ).toSpec()

    fun extractCompletionLabelsFromResult(
        result: Either<List<CompletionItem>, CompletionList>?
    ): List<String> = extractCompletionLabels(result)

    fun normalizeCompletionLabelsForMatch(labels: List<String>): List<String> = normalizeCompletionLabels(labels)

    fun formatDuration(durationMs: Long): String = formatDurationText(durationMs)

    fun limitReportText(text: String, maxChars: Int = 1_600): String = limitText(text, maxChars)

    fun buildUnhandledExceptionResultForScenario(
        scenarioId: String,
        throwable: Throwable
    ): ProjectSmokeRunResult = buildUnhandledExceptionResult(
        scenario = requireScenario(scenarioId),
        throwable = throwable
    )

    fun buildSampleLoggerResult(
        scenarioId: String,
        success: Boolean,
        failureDetail: String? = null
    ): ProjectSmokeRunResult {
        val logger = ProjectSmokeLogger(requireScenario(scenarioId))
        logger.appendDetailLine("样例日志")
        logger.setProjectPaths(File("project-root"), File("project-root/build"))
        logger.setCompileCommandsPath(File("project-root/build/compile_commands.json"))
        logger.setClangdMode("NATIVE")
        logger.setDiagnostics(totalCount = 3, errorCount = 1, warningCount = 2)
        logger.setCompletionCount(6)
        logger.setMatchedCompletionLabels(listOf("empty", "emplace_back"))
        logger.passCheck("compile_commands", "ready")
        logger.passCheck("compile_commands", "updated")
        logger.failCheck("clangd diagnostics", "1 error")
        logger.recordStageDuration("准备", 85)
        logger.recordStageDuration("准备", 95)
        logger.recordStageDuration("补全", 1_520)
        return if (success) {
            logger.buildSuccess()
        } else {
            logger.buildFailure(
                failedStage = "clangd diagnostics",
                failureReason = "存在 error 级诊断",
                failureDetail = failureDetail
            )
        }
    }

    private fun requireScenario(scenarioId: String): ProjectSmokeScenario = projectSmokeScenarios().firstOrNull { it.id == scenarioId }
        ?: error("Unknown smoke scenario: $scenarioId")
}

private fun projectSmokeScenarios(): List<ProjectSmokeScenario> = listOf(singleFileSmokeScenario, builtInCmakeSmokeScenario, sdl3CmakeSmokeScenario)

private fun ProjectSmokeScenario.toSpec(): ProjectSmokeScenarioSpec = ProjectSmokeScenarioSpec(
    id = id,
    displayName = displayName,
    description = description,
    files = files,
    targetFileRelativePath = targetFileRelativePath,
    buildReplacement = buildReplacement,
    completionReplacement = completionReplacement,
    completionNeedle = completionNeedle,
    expectedCompletionLabels = expectedCompletionLabels
)

private fun LineColumn.toSpec(): ProjectSmokeLineColumn = ProjectSmokeLineColumn(line = line, column = column)

private val builtInCmakeSmokeScenario = ProjectSmokeScenario(
    id = "cmake_std_headers",
    displayName = "CMake 标准库/私有头样例",
    description = "验证 CMake compile_commands、PRIVATE include、本地头文件和 std::vector/std::string 语义分析",
    files = mapOf(
        "CMakeLists.txt" to """
            cmake_minimum_required(VERSION 3.16)
            project(cmake_std_headers LANGUAGES CXX)
            
            set(CMAKE_CXX_STANDARD 17)
            set(CMAKE_CXX_STANDARD_REQUIRED ON)
            set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
            
            add_library(cmake_std_headers SHARED
                src/main.cpp
                src/sample.cpp
            )
            
            target_include_directories(cmake_std_headers PRIVATE src)
        """.trimIndent(),
        "src/sample.hpp" to """
            #pragma once
            
            #include <string>
            #include <vector>
            
            std::string joinValues(const std::vector<std::string>& values);
        """.trimIndent(),
        "src/sample.cpp" to """
            #include "sample.hpp"
            
            #include <sstream>
            
            std::string joinValues(const std::vector<std::string>& values) {
                std::ostringstream builder;
                for (std::size_t i = 0; i < values.size(); ++i) {
                    if (i > 0) {
                        builder << ",";
                    }
                    builder << values[i];
                }
                return builder.str();
            }
        """.trimIndent(),
        "src/main.cpp" to """
            #include "sample.hpp"
            
            #include <string>
            #include <vector>
            
            extern "C" int cmake_project_smoke() {
                std::vector<std::string> names = {"alpha", "beta"};
                const std::string joined = joinValues(names);
                $COMPLETION_PLACEHOLDER
                return static_cast<int>(joined.size());
            }
        """.trimIndent()
    ),
    targetFileRelativePath = "src/main.cpp",
    buildReplacement = "auto namesSize = names.size();",
    completionReplacement = "names.em",
    completionNeedle = "names.em",
    expectedCompletionLabels = listOf("emplace_back", "empty")
)

private val sdl3CmakeSmokeScenario = ProjectSmokeScenario(
    id = "sdl3_std_headers",
    displayName = "SDL3 CMake 样例",
    description = "验证 SDL3 + CMake + std::vector<std::string> 场景下的 compile_commands、头文件搜索和 clangd 误报",
    files = mapOf(
        "CMakeLists.txt" to """
            cmake_minimum_required(VERSION 3.16)
            project(sdl3_cmake_smoke LANGUAGES CXX)
            
            set(CMAKE_CXX_STANDARD 17)
            set(CMAKE_CXX_STANDARD_REQUIRED ON)
            set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
            
            find_package(SDL3 CONFIG REQUIRED)
            
            add_library(sdl3_cmake_smoke SHARED
                src/main.cpp
                src/render_probe.cpp
            )
            
            target_include_directories(sdl3_cmake_smoke PRIVATE src)
            target_link_libraries(sdl3_cmake_smoke PRIVATE SDL3::SDL3)
        """.trimIndent(),
        "src/render_probe.hpp" to """
            #pragma once
            
            #include <string>
            #include <vector>
            
            std::vector<std::string> collectRenderDriverNames();
        """.trimIndent(),
        "src/render_probe.cpp" to """
            #include "render_probe.hpp"
            
            #include <SDL3/SDL.h>
            
            std::vector<std::string> collectRenderDriverNames() {
                std::vector<std::string> driverNames;
                const int count = SDL_GetNumRenderDrivers();
                for (int i = 0; i < count; ++i) {
                    const char* name = SDL_GetRenderDriver(i);
                    if (name != nullptr) {
                        driverNames.emplace_back(name);
                    }
                }
                return driverNames;
            }
        """.trimIndent(),
        "src/main.cpp" to """
            #include "render_probe.hpp"
            
            #include <SDL3/SDL.h>
            #include <string>
            #include <vector>
            
            extern "C" int sdl3_project_smoke() {
                if (!SDL_Init(SDL_INIT_VIDEO)) {
                    return -1;
                }
                std::vector<std::string> driverNames = collectRenderDriverNames();
                $COMPLETION_PLACEHOLDER
                SDL_Quit();
                return static_cast<int>(driverNames.size());
            }
        """.trimIndent()
    ),
    targetFileRelativePath = "src/main.cpp",
    buildReplacement = "auto driverCount = driverNames.size();",
    completionReplacement = "driverNames.em",
    completionNeedle = "driverNames.em",
    expectedCompletionLabels = listOf("emplace_back", "empty")
)

private val singleFileSmokeScenario = ProjectSmokeScenario(
    id = "single_file_std_headers",
    displayName = "Single-file 标准库样例",
    description = "验证单文件项目的 compile_commands 生成、clangd diagnostics 和 std::vector<std::string> 补全",
    files = mapOf(
        "main.cpp" to """
            #include <string>
            #include <vector>
            
            int main() {
                std::vector<std::string> names = {"alpha", "beta"};
                $COMPLETION_PLACEHOLDER
                return static_cast<int>(names.size());
            }
        """.trimIndent()
    ),
    targetFileRelativePath = "main.cpp",
    buildReplacement = "auto namesSize = names.size();",
    completionReplacement = "names.em",
    completionNeedle = "names.em",
    expectedCompletionLabels = listOf("emplace_back", "empty")
)

suspend fun runBuiltInCmakeProjectSmokeTest(
    context: Context,
    toolchainId: String? = null,
    toolchainLabel: String? = null,
    useRecommendedTinaExec: Boolean = false,
): ProjectSmokeRunResult = runSmokeScenarioSafely(builtInCmakeSmokeScenario) {
    runProjectSmokeScenario(
        context = context,
        scenario = builtInCmakeSmokeScenario,
        toolchainId = toolchainId,
        toolchainLabel = toolchainLabel,
        useRecommendedTinaExec = useRecommendedTinaExec,
    )
}

suspend fun runSdl3CmakeProjectSmokeTest(
    context: Context,
    toolchainId: String? = null,
    toolchainLabel: String? = null,
    useRecommendedTinaExec: Boolean = false,
): ProjectSmokeRunResult = runSmokeScenarioSafely(sdl3CmakeSmokeScenario) {
    runProjectSmokeScenario(
        context = context,
        scenario = sdl3CmakeSmokeScenario,
        toolchainId = toolchainId,
        toolchainLabel = toolchainLabel,
        useRecommendedTinaExec = useRecommendedTinaExec,
    )
}

suspend fun runSingleFileProjectSmokeTest(
    context: Context,
    toolchainId: String? = null,
    toolchainLabel: String? = null,
    useRecommendedTinaExec: Boolean = false,
): ProjectSmokeRunResult = runSmokeScenarioSafely(singleFileSmokeScenario) {
    runSingleFileSmokeScenario(
        context = context,
        scenario = singleFileSmokeScenario,
        toolchainId = toolchainId,
        toolchainLabel = toolchainLabel,
        useRecommendedTinaExec = useRecommendedTinaExec,
    )
}

suspend fun runAllProjectSmokeTests(
    context: Context,
    toolchainId: String? = null,
    toolchainLabel: String? = null,
    useRecommendedTinaExec: Boolean = false,
): List<ProjectSmokeRunResult> = listOf(
    runSingleFileProjectSmokeTest(context, toolchainId, toolchainLabel, useRecommendedTinaExec),
    runBuiltInCmakeProjectSmokeTest(context, toolchainId, toolchainLabel, useRecommendedTinaExec),
    runSdl3CmakeProjectSmokeTest(context, toolchainId, toolchainLabel, useRecommendedTinaExec)
)

private suspend fun runSmokeScenarioSafely(
    scenario: ProjectSmokeScenario,
    block: suspend () -> ProjectSmokeRunResult
): ProjectSmokeRunResult = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (throwable: Throwable) {
    buildUnhandledExceptionResult(scenario, throwable)
}

private fun appendProjectSmokeToolchainHeader(
    logger: ProjectSmokeLogger,
    toolchainId: String?,
    toolchainLabel: String?,
    useRecommendedTinaExec: Boolean,
) {
    if (toolchainId == null && toolchainLabel == null) return

    logger.appendDetailLine("工具链变体: ${toolchainLabel ?: "未指定"}")
    logger.appendDetailLine("toolchainId: ${toolchainId ?: "默认激活工具链"}")
    logger.appendDetailLine("tina-exec: ${if (useRecommendedTinaExec) "enabled" else "disabled"}")
    logger.appendDetailLine()
}

private fun buildUnhandledExceptionResult(
    scenario: ProjectSmokeScenario,
    throwable: Throwable
): ProjectSmokeRunResult {
    val failureReason = throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.java.simpleName
    val failureDetail = limitText(throwable.stackTraceToString())
    val reportText = buildString {
        appendLine("=== ${scenario.displayName} ===")
        appendLine("结果: 失败")
        appendLine("说明: ${scenario.description}")
        appendLine("失败阶段: 执行异常")
        appendLine("失败原因: $failureReason")
        appendLine()
        appendLine("失败详情:")
        appendLine(failureDetail)
    }
    return ProjectSmokeRunResult(
        scenarioId = scenario.id,
        displayName = scenario.displayName,
        description = scenario.description,
        success = false,
        reportText = reportText,
        failedStage = "执行异常",
        failureReason = failureReason,
        failureDetail = failureDetail
    )
}

private suspend fun runProjectSmokeScenario(
    context: Context,
    scenario: ProjectSmokeScenario,
    toolchainId: String?,
    toolchainLabel: String?,
    useRecommendedTinaExec: Boolean,
): ProjectSmokeRunResult {
    val logger = ProjectSmokeLogger(scenario)
    appendProjectSmokeToolchainHeader(logger, toolchainId, toolchainLabel, useRecommendedTinaExec)
    logger.appendDetailLine("场景说明: ${scenario.description}")
    logger.appendDetailLine()

    val projectDir = prepareScenarioProject(context, scenario, logger)
    val buildDir = File(projectDir, "build")
    logger.setProjectPaths(projectDir, buildDir)
    logger.appendDetailLine("项目目录: ${projectDir.absolutePath}")
    logger.appendDetailLine("构建目录: ${buildDir.absolutePath}")
    logger.appendDetailLine()

    val cmakeExecutor = NativeCMakeBuildExecutor(context)
    val cmakeOptions = NativeCMakeBuildExecutor.Options(
        parallelJobs = 1,
        cppStandard = "c++17",
        toolchainId = toolchainId,
        useRecommendedTinaExec = useRecommendedTinaExec,
        traceToolchainShim = useRecommendedTinaExec,
    )
    var sourceCompileCommandsFile: File? = null

    logger.appendDetailLine("[1/4] CMake 配置")
    val configureStartedAt = System.nanoTime()
    val configure = cmakeExecutor.configure(projectDir, buildDir, cmakeOptions)
    logger.recordStageDuration("CMake 配置", elapsedSinceNanos(configureStartedAt))
    when (configure) {
        is ConfigureResult.Success -> {
            logger.passCheck("CMake 配置", "configure 成功")
            logger.appendDetailLine("配置成功")
            val compileCommandsPath = configure.compileCommandsPath
                ?.takeIf { it.exists() }
                ?: File(buildDir, "compile_commands.json").takeIf { it.exists() }

            if (compileCommandsPath == null) {
                logger.failCheck("compile_commands 生成", "未找到 compile_commands.json")
                logger.appendDetailLine("未找到 compile_commands.json")
                return logger.buildFailure(
                    failedStage = "compile_commands 检查",
                    failureReason = "CMake 配置成功，但未生成 compile_commands.json"
                )
            }
            sourceCompileCommandsFile = compileCommandsPath
            logger.setCompileCommandsPath(compileCommandsPath)
            logger.passCheck("compile_commands 生成", compileCommandsPath.absolutePath)
            logger.appendDetailLine("compile_commands: ${compileCommandsPath.absolutePath}")
        }

        is ConfigureResult.Error -> {
            logger.failCheck("CMake 配置", "configure 失败")
            logger.appendDetailLine("配置失败:")
            logger.appendDetailLine(configure.message)
            return logger.buildFailure(
                failedStage = "CMake 配置",
                failureReason = "configure 失败",
                failureDetail = configure.message
            )
        }
    }
    logger.appendDetailLine()

    logger.appendDetailLine("[2/4] CMake 构建")
    val buildStartedAt = System.nanoTime()
    val build = cmakeExecutor.build(projectDir, buildDir, options = cmakeOptions)
    logger.recordStageDuration("CMake 构建", elapsedSinceNanos(buildStartedAt))
    when (build) {
        is BuildResult.Success -> {
            logger.passCheck("CMake 构建", "build 成功")
            logger.appendDetailLine("构建成功")
            if (build.message.isNotBlank()) {
                logger.appendDetailLine(limitText(build.message))
            }
        }

        is BuildResult.Error -> {
            val rawOutput = limitText(build.rawOutput)
            logger.failCheck("CMake 构建", "build 失败")
            logger.appendDetailLine("构建失败:")
            logger.appendDetailLine(rawOutput)
            return logger.buildFailure(
                failedStage = "CMake 构建",
                failureReason = "build 失败",
                failureDetail = rawOutput
            )
        }
    }
    logger.appendDetailLine()

    val lspCompileCommandsDir = CompileDatabaseProvider(context)
        .prepareProvidedCompileCommandsForLsp(
            sourceCompileCommandsFile ?: return logger.buildFailure(
                failedStage = "compile_commands 准备",
                failureReason = "source compile_commands 缺失"
            ),
            projectDir.absolutePath,
            toolchainId = toolchainId,
        )
        ?: return logger.buildFailure(
            failedStage = "compile_commands 准备",
            failureReason = "无法为 clangd 准备 compile_commands"
        )
    val lspCompileCommandsFile = File(lspCompileCommandsDir, "compile_commands.json")
    logger.setCompileCommandsPath(lspCompileCommandsFile)
    logger.appendDetailLine("clangd compile_commands: ${lspCompileCommandsFile.absolutePath}")
    logger.appendDetailLine()

    val sourceFile = File(projectDir, scenario.targetFileRelativePath)
    val buildSource = sourceFile.readText()
    val completionSource = buildSource.replace(scenario.buildReplacement, scenario.completionReplacement)
    val completionPosition = resolveCompletionPosition(completionSource, scenario.completionNeedle)
        ?: return logger.buildFailure(
            failedStage = "补全定位",
            failureReason = "无法定位补全触发位置",
            failureDetail = scenario.completionNeedle
        )
    val documentUri = sourceFile.toURI().toString()
    val workspaceRootUri = projectDir.toURI().toString()

    logger.appendDetailLine("[3/4] 启动 clangd 并收集 diagnostics")
    logger.appendDetailLine("文档 URI: $documentUri")
    val diagnosticCollector = ProjectDiagnosticCollector()

    val runMode = LinuxRunModePolicy.resolve(
        configuredMode = Prefs.clangdRunMode,
        linuxEnvironmentAvailable = runCatching { PRootBootstrap.isEnvironmentReady(context) }.getOrDefault(false)
    )
    logger.setClangdMode(runMode.name)
    logger.appendDetailLine("clangd 模式: ${runMode.name}")

    val connectionProvider = createClangdProvider(
        context = context,
        workingDir = projectDir.absolutePath,
        compileCommandsDir = lspCompileCommandsDir.absolutePath,
        runMode = runMode,
        toolchainId = toolchainId,
        useRecommendedTinaExec = useRecommendedTinaExec,
    )

    val session = LspClientSession(
        connectionProvider = connectionProvider,
        documentUri = documentUri,
        workspaceRootUri = workspaceRootUri,
        diagnosticsConsumer = { uri, diagnostics ->
            diagnosticCollector.record(uri, diagnostics)
        },
        tag = "ProjectSmoke.${scenario.id}"
    )

    try {
        val diagnosticsStartedAt = System.nanoTime()
        val connectResult = session.connect(
            languageId = "cpp",
            initialText = buildSource
        )
        if (connectResult.isFailure) {
            logger.recordStageDuration("clangd 诊断", elapsedSinceNanos(diagnosticsStartedAt))
            val stackTrace = limitText(connectResult.exceptionOrNull()?.stackTraceToString().orEmpty())
            logger.failCheck("clangd 连接", "连接失败")
            logger.appendDetailLine("clangd 连接失败:")
            logger.appendDetailLine(stackTrace)
            return logger.buildFailure(
                failedStage = "clangd 连接",
                failureReason = "clangd 会话初始化失败",
                failureDetail = stackTrace
            )
        }
        logger.passCheck("clangd 连接", runMode.name)

        delay(LSP_CONNECT_TIMEOUT_MS)
        diagnosticCollector.awaitQuietPeriod()
        val diagnostics = diagnosticCollector.latestFor(documentUri)
        val errorDiagnostics = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
        val warningDiagnostics = diagnostics.filter { it.severity == DiagnosticSeverity.Warning }
        logger.setDiagnostics(
            totalCount = diagnostics.size,
            errorCount = errorDiagnostics.size,
            warningCount = warningDiagnostics.size
        )
        logger.recordStageDuration("clangd 诊断", elapsedSinceNanos(diagnosticsStartedAt))
        logger.appendDetailLine("diagnostics 总数: ${diagnostics.size}")
        logger.appendDetailLine("error 数量: ${errorDiagnostics.size}")
        logger.appendDetailLine("warning 数量: ${warningDiagnostics.size}")

        if (errorDiagnostics.isNotEmpty()) {
            logger.failCheck("clangd diagnostics", "${errorDiagnostics.size} 个 error")
            logger.appendDetailLine("发现 clangd 错误诊断:")
            errorDiagnostics.forEach { diagnostic ->
                logger.appendDetailLine(formatDiagnostic(diagnostic))
            }
            return logger.buildFailure(
                failedStage = "clangd diagnostics",
                failureReason = "出现 error 级别诊断",
                failureDetail = errorDiagnostics.joinToString("\n") { formatDiagnostic(it) }
            )
        }
        logger.passCheck("clangd diagnostics", "无 error 级别诊断")
        logger.appendDetailLine("未发现 error 级别误报")
        if (warningDiagnostics.isNotEmpty()) {
            logger.appendDetailLine("warning 预览:")
            warningDiagnostics.take(5).forEach { diagnostic ->
                logger.appendDetailLine(formatDiagnostic(diagnostic))
            }
        }
        logger.appendDetailLine()

        logger.appendDetailLine("[4/4] 请求补全")
        val completionStartedAt = System.nanoTime()
        session.didChangeFull(completionSource)
        diagnosticCollector.awaitQuietPeriod(maxWaitMs = 2_500L, quietPeriodMs = 300L)
        val completionLabels = requestCompletionLabels(
            session = session,
            documentUri = documentUri,
            position = completionPosition
        )
        logger.recordStageDuration("补全请求", elapsedSinceNanos(completionStartedAt))
        logger.setCompletionCount(completionLabels.size)
        logger.appendDetailLine("补全候选数量: ${completionLabels.size}")
        if (completionLabels.isNotEmpty()) {
            logger.appendDetailLine("前 12 项: ${completionLabels.take(12).joinToString()}")
        }
        val normalizedCompletionLabels = normalizeCompletionLabels(completionLabels)
        if (normalizedCompletionLabels != completionLabels) {
            logger.appendDetailLine("归一化后前 12 项: ${normalizedCompletionLabels.take(12).joinToString()}")
        }

        val matchedLabels = scenario.expectedCompletionLabels.filter { expectedLabel ->
            expectedLabel in normalizedCompletionLabels
        }
        if (matchedLabels.isEmpty()) {
            logger.failCheck("clangd 补全", "未命中 ${scenario.expectedCompletionLabels.joinToString()}")
            logger.appendDetailLine("未命中预期补全项: ${scenario.expectedCompletionLabels.joinToString()}")
            return logger.buildFailure(
                failedStage = "clangd 补全",
                failureReason = "未命中预期补全项",
                failureDetail = buildString {
                    append("期望: ${scenario.expectedCompletionLabels.joinToString()}")
                    append("，实际前 12 项: ${completionLabels.take(12).joinToString()}")
                    if (normalizedCompletionLabels != completionLabels) {
                        append("，归一化后前 12 项: ${normalizedCompletionLabels.take(12).joinToString()}")
                    }
                }
            )
        }

        logger.setMatchedCompletionLabels(matchedLabels)
        logger.passCheck("clangd 补全", matchedLabels.joinToString())
        logger.appendDetailLine("命中预期补全项: ${matchedLabels.joinToString()}")
        logger.appendDetailLine()
        logger.appendDetailLine("结论: 编译成功，clangd 诊断未发现 error，补全可用")
        return logger.buildSuccess()
    } finally {
        runCatching { session.close() }
    }
}

private suspend fun runSingleFileSmokeScenario(
    context: Context,
    scenario: ProjectSmokeScenario,
    toolchainId: String?,
    toolchainLabel: String?,
    useRecommendedTinaExec: Boolean,
): ProjectSmokeRunResult {
    val logger = ProjectSmokeLogger(scenario)
    appendProjectSmokeToolchainHeader(logger, toolchainId, toolchainLabel, useRecommendedTinaExec)
    logger.appendDetailLine("场景说明: ${scenario.description}")
    logger.appendDetailLine()

    val projectDir = prepareScenarioProject(context, scenario, logger)
    val buildDir = File(projectDir, "build")
    logger.setProjectPaths(projectDir, buildDir)
    logger.appendDetailLine("项目目录: ${projectDir.absolutePath}")
    logger.appendDetailLine("输出目录: ${buildDir.absolutePath}")
    logger.appendDetailLine()

    ProjectMetadataStore.ensure(
        projectRoot = projectDir,
        displayNameFallback = scenario.displayName,
        buildSystem = ProjectBuildSystem.SINGLE_FILE,
        cppStandard = CppStandard.CPP_17
    )

    val orchestrator = GlobalContext.get().get<BuildOrchestrator>()
    val buildContextFactory = GlobalContext.get().get<BuildContextFactory>()
    val buildOptions = BuildOptions(
        cppStandard = CppStandard.CPP_17.flag,
        toolchainId = toolchainId,
    )
    val ctx = buildContextFactory.create(
        appContext = context,
        projectRoot = projectDir,
        buildDir = buildDir,
        buildSystem = BuildSystem.SINGLE_FILE,
        options = buildOptions,
        target = null,
    )

    logger.appendDetailLine("[1/4] Single-file 编译")
    val singleFileBuildStartedAt = System.nanoTime()
    val build = orchestrator.run(CompileRequest.buildOnly(), ctx)
    logger.recordStageDuration("Single-file 编译", elapsedSinceNanos(singleFileBuildStartedAt))
    when (build) {
        is BuildReport.BuiltOnly -> {
            logger.passCheck("Single-file 编译", "build 成功")
            logger.appendDetailLine("构建成功: ${build.artifact.absolutePath}")
        }

        is BuildReport.BuildFailed -> {
            val rawOutput = limitText(build.reason)
            logger.failCheck("Single-file 编译", "build 失败")
            logger.appendDetailLine("构建失败:")
            logger.appendDetailLine(rawOutput)
            return logger.buildFailure(
                failedStage = "Single-file 编译",
                failureReason = "build 失败",
                failureDetail = rawOutput,
            )
        }

        else -> {
            val message = "unexpected orchestrator report: ${build::class.simpleName}"
            logger.failCheck("Single-file 编译", message)
            return logger.buildFailure(failedStage = "Single-file 编译", failureReason = message)
        }
    }
    logger.appendDetailLine()

    logger.appendDetailLine("[2/4] 生成 compile_commands")
    val compileCommandsStartedAt = System.nanoTime()
    val sourceFile = File(projectDir, scenario.targetFileRelativePath)
    val compileProvider = CompileDatabaseProvider(context)
    val prepared = compileProvider.prepare(
        file = sourceFile,
        projectRootPath = projectDir.absolutePath,
        toolchainId = toolchainId,
    )
        ?: run {
            logger.recordStageDuration("compile_commands 生成", elapsedSinceNanos(compileCommandsStartedAt))
            return logger.buildFailure(
                failedStage = "compile_commands 准备",
                failureReason = "CompileDatabaseProvider.prepare() 返回 null"
            )
        }
    val ensured = compileProvider.ensureWithResult(prepared)
        ?: run {
            logger.recordStageDuration("compile_commands 生成", elapsedSinceNanos(compileCommandsStartedAt))
            return logger.buildFailure(
                failedStage = "compile_commands 生成",
                failureReason = "ensureWithResult() 返回 null"
            )
        }
    val compileCommandsFile = File(ensured.compileCommandsDir, "compile_commands.json")
    if (!compileCommandsFile.isFile) {
        logger.failCheck("compile_commands 生成", "未找到 compile_commands.json")
        logger.appendDetailLine("compile_commands.json 不存在: ${compileCommandsFile.absolutePath}")
        logger.recordStageDuration("compile_commands 生成", elapsedSinceNanos(compileCommandsStartedAt))
        return logger.buildFailure(
            failedStage = "compile_commands 生成",
            failureReason = "未生成 compile_commands.json",
            failureDetail = compileCommandsFile.absolutePath
        )
    }
    logger.setCompileCommandsPath(compileCommandsFile)
    logger.passCheck(
        "compile_commands 生成",
        if (ensured.regenerated) "generated" else "reused"
    )
    logger.recordStageDuration("compile_commands 生成", elapsedSinceNanos(compileCommandsStartedAt))
    logger.appendDetailLine("compile_commands: ${compileCommandsFile.absolutePath}")
    logger.appendDetailLine()

    val buildSource = sourceFile.readText()
    val completionSource = buildSource.replace(scenario.buildReplacement, scenario.completionReplacement)
    val completionPosition = resolveCompletionPosition(completionSource, scenario.completionNeedle)
        ?: return logger.buildFailure(
            failedStage = "补全定位",
            failureReason = "无法定位补全触发位置",
            failureDetail = scenario.completionNeedle
        )
    val documentUri = sourceFile.toURI().toString()
    val workspaceRootUri = projectDir.toURI().toString()

    logger.appendDetailLine("[3/4] 连接 clangd 并收集 diagnostics")
    logger.appendDetailLine("文档 URI: $documentUri")
    val diagnosticCollector = ProjectDiagnosticCollector()

    val runMode = LinuxRunModePolicy.resolve(
        configuredMode = Prefs.clangdRunMode,
        linuxEnvironmentAvailable = runCatching { PRootBootstrap.isEnvironmentReady(context) }.getOrDefault(false)
    )
    logger.setClangdMode(runMode.name)
    logger.appendDetailLine("clangd 模式: ${runMode.name}")

    val connectionProvider = createClangdProvider(
        context = context,
        workingDir = projectDir.absolutePath,
        compileCommandsDir = ensured.compileCommandsDir.absolutePath,
        runMode = runMode,
        toolchainId = toolchainId,
        useRecommendedTinaExec = useRecommendedTinaExec,
    )

    val session = LspClientSession(
        connectionProvider = connectionProvider,
        documentUri = documentUri,
        workspaceRootUri = workspaceRootUri,
        diagnosticsConsumer = { uri, diagnostics ->
            diagnosticCollector.record(uri, diagnostics)
        },
        tag = "ProjectSmoke.${scenario.id}"
    )

    try {
        val diagnosticsStartedAt = System.nanoTime()
        val connectResult = session.connect(
            languageId = "cpp",
            initialText = buildSource
        )
        if (connectResult.isFailure) {
            logger.recordStageDuration("clangd 诊断", elapsedSinceNanos(diagnosticsStartedAt))
            val stackTrace = limitText(connectResult.exceptionOrNull()?.stackTraceToString().orEmpty())
            logger.failCheck("clangd 连接", "连接失败")
            logger.appendDetailLine("clangd 连接失败:")
            logger.appendDetailLine(stackTrace)
            return logger.buildFailure(
                failedStage = "clangd 连接",
                failureReason = "clangd 会话初始化失败",
                failureDetail = stackTrace
            )
        }
        logger.passCheck("clangd 连接", runMode.name)

        delay(LSP_CONNECT_TIMEOUT_MS)
        diagnosticCollector.awaitQuietPeriod()
        val diagnostics = diagnosticCollector.latestFor(documentUri)
        val errorDiagnostics = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
        val warningDiagnostics = diagnostics.filter { it.severity == DiagnosticSeverity.Warning }
        logger.setDiagnostics(
            totalCount = diagnostics.size,
            errorCount = errorDiagnostics.size,
            warningCount = warningDiagnostics.size
        )
        logger.recordStageDuration("clangd 诊断", elapsedSinceNanos(diagnosticsStartedAt))
        logger.appendDetailLine("diagnostics 数量: ${diagnostics.size}")
        logger.appendDetailLine("error 数量: ${errorDiagnostics.size}")
        logger.appendDetailLine("warning 数量: ${warningDiagnostics.size}")

        if (errorDiagnostics.isNotEmpty()) {
            logger.failCheck("clangd diagnostics", "${errorDiagnostics.size} 个 error")
            logger.appendDetailLine("存在 clangd 错误诊断:")
            errorDiagnostics.forEach { diagnostic ->
                logger.appendDetailLine(formatDiagnostic(diagnostic))
            }
            return logger.buildFailure(
                failedStage = "clangd diagnostics",
                failureReason = "存在 error 级诊断",
                failureDetail = errorDiagnostics.joinToString("\n") { formatDiagnostic(it) }
            )
        }
        logger.passCheck("clangd diagnostics", "无 error 诊断")
        logger.appendDetailLine("未发现 error 诊断")
        if (warningDiagnostics.isNotEmpty()) {
            logger.appendDetailLine("warning 预览:")
            warningDiagnostics.take(5).forEach { diagnostic ->
                logger.appendDetailLine(formatDiagnostic(diagnostic))
            }
        }
        logger.appendDetailLine()

        logger.appendDetailLine("[4/4] 请求补全")
        val completionStartedAt = System.nanoTime()
        session.didChangeFull(completionSource)
        diagnosticCollector.awaitQuietPeriod(maxWaitMs = 2_500L, quietPeriodMs = 300L)
        val completionLabels = requestCompletionLabels(
            session = session,
            documentUri = documentUri,
            position = completionPosition
        )
        logger.recordStageDuration("补全请求", elapsedSinceNanos(completionStartedAt))
        logger.setCompletionCount(completionLabels.size)
        logger.appendDetailLine("补全候选数量: ${completionLabels.size}")
        if (completionLabels.isNotEmpty()) {
            logger.appendDetailLine("前 12 项: ${completionLabels.take(12).joinToString()}")
        }
        val normalizedCompletionLabels = normalizeCompletionLabels(completionLabels)
        if (normalizedCompletionLabels != completionLabels) {
            logger.appendDetailLine("归一化后前 12 项: ${normalizedCompletionLabels.take(12).joinToString()}")
        }

        val matchedLabels = scenario.expectedCompletionLabels.filter { expectedLabel ->
            expectedLabel in normalizedCompletionLabels
        }
        if (matchedLabels.isEmpty()) {
            logger.failCheck("clangd 补全", "未命中 ${scenario.expectedCompletionLabels.joinToString()}")
            logger.appendDetailLine("未命中预期补全项: ${scenario.expectedCompletionLabels.joinToString()}")
            return logger.buildFailure(
                failedStage = "clangd 补全",
                failureReason = "未命中预期补全项",
                failureDetail = buildString {
                    append("期望: ${scenario.expectedCompletionLabels.joinToString()}")
                    append("，实际前 12 项: ${completionLabels.take(12).joinToString()}")
                    if (normalizedCompletionLabels != completionLabels) {
                        append("，归一化后前 12 项: ${normalizedCompletionLabels.take(12).joinToString()}")
                    }
                }
            )
        }

        logger.setMatchedCompletionLabels(matchedLabels)
        logger.passCheck("clangd 补全", matchedLabels.joinToString())
        logger.appendDetailLine("命中预期补全项: ${matchedLabels.joinToString()}")
        logger.appendDetailLine()
        logger.appendDetailLine("结论: single-file 项目的 compile_commands 和 clangd 补全可用")
        return logger.buildSuccess()
    } finally {
        runCatching { session.close() }
    }
}

private fun prepareScenarioProject(
    context: Context,
    scenario: ProjectSmokeScenario,
    logger: ProjectSmokeLogger
): File {
    val rootDir = File(context.filesDir, PROJECT_SMOKE_ROOT_DIR)
    if (!rootDir.exists()) {
        rootDir.mkdirs()
    }

    val projectDir = File(rootDir, scenario.id)
    if (projectDir.exists()) {
        projectDir.deleteRecursively()
    }
    projectDir.mkdirs()

    scenario.files.forEach { (relativePath, templateContent) ->
        val target = File(projectDir, relativePath)
        target.parentFile?.mkdirs()
        val content = templateContent.replace(COMPLETION_PLACEHOLDER, scenario.buildReplacement)
        target.writeText(content)
        logger.appendDetailLine("写入: $relativePath")
    }
    logger.appendDetailLine()
    return projectDir
}

private fun createClangdProvider(
    context: Context,
    workingDir: String,
    compileCommandsDir: String,
    runMode: LinuxRunModePolicy.RunMode,
    toolchainId: String?,
    useRecommendedTinaExec: Boolean,
): LspConnectionProvider = when (runMode) {
    LinuxRunModePolicy.RunMode.NATIVE ->
        NativeClangdConnectionProvider(
            context = context,
            workingDir = workingDir,
            compileCommandsDir = compileCommandsDir,
            toolchainId = toolchainId,
            useRecommendedTinaExec = useRecommendedTinaExec,
        )

    LinuxRunModePolicy.RunMode.PROOT ->
        PRootClangdConnectionProvider(
            context = context,
            workingDir = workingDir,
            compileCommandsDir = compileCommandsDir
        )
}

private suspend fun requestCompletionLabels(
    session: LspClientSession,
    documentUri: String,
    position: LineColumn
): List<String> {
    repeat(3) { attempt ->
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier(documentUri)
            this.position = Position(position.line, position.column)
            context = CompletionContext().apply {
                triggerKind = CompletionTriggerKind.Invoked
            }
        }
        val result = session.completion(params)?.get(5, TimeUnit.SECONDS)
        val labels = extractCompletionLabels(result)
        if (labels.isNotEmpty()) {
            return labels
        }
        if (attempt < 2) {
            delay(250)
        }
    }
    return emptyList()
}

private fun extractCompletionLabels(
    result: Either<List<CompletionItem>, CompletionList>?
): List<String> = when {
    result == null -> emptyList()
    result.isLeft -> result.left.map { it.label }
    else -> result.right.items.map { it.label }
}

private fun normalizeCompletionLabels(labels: List<String>): List<String> = labels
    .map { label -> label.trim() }
    .filter { label -> label.isNotEmpty() }
    .distinct()

private fun resolveCompletionPosition(
    content: String,
    completionNeedle: String
): LineColumn? {
    val endOffset = content.indexOf(completionNeedle)
        .takeIf { it >= 0 }
        ?.plus(completionNeedle.length)
        ?: return null
    return offsetToLineColumn(content, endOffset)
}

private fun offsetToLineColumn(text: String, offset: Int): LineColumn {
    var line = 0
    var column = 0
    var index = 0
    while (index < offset && index < text.length) {
        if (text[index] == '\n') {
            line += 1
            column = 0
        } else {
            column += 1
        }
        index += 1
    }
    return LineColumn(line = line, column = column)
}

private fun formatDiagnostic(diagnostic: Diagnostic): String {
    val start = diagnostic.range?.start
    val line = (start?.line ?: 0) + 1
    val column = (start?.character ?: 0) + 1
    return "[$line:$column] ${diagnostic.message}"
}

private fun elapsedSinceNanos(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L

private fun formatDurationText(durationMs: Long): String {
    if (durationMs < 1_000L) return "$durationMs ms"
    val seconds = durationMs / 1_000L
    val hundredths = (durationMs % 1_000L) / 10L
    return "$seconds.${hundredths.toString().padStart(2, '0')} s"
}

private fun limitText(text: String, maxChars: Int = 1_600): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars) + "\n...<截断>..."
}

private class ProjectSmokeLogger(
    private val scenario: ProjectSmokeScenario
) {
    private val startedAtNanos = System.nanoTime()
    private val detailBuilder = StringBuilder()
    private val checks = mutableListOf<ProjectSmokeCheck>()
    private val stageTimings = mutableListOf<ProjectSmokeStageTiming>()
    private var projectDir: String? = null
    private var buildDir: String? = null
    private var compileCommandsPath: String? = null
    private var clangdMode: String? = null
    private var diagnosticsTotalCount: Int? = null
    private var diagnosticsErrorCount: Int? = null
    private var diagnosticsWarningCount: Int? = null
    private var completionCandidateCount: Int? = null
    private var matchedCompletionLabels: List<String> = emptyList()
    private var failedStage: String? = null
    private var failureReason: String? = null
    private var failureDetail: String? = null

    fun appendDetailLine(text: String = "") {
        detailBuilder.append(text).append('\n')
    }

    fun setProjectPaths(projectDir: File, buildDir: File) {
        this.projectDir = projectDir.absolutePath
        this.buildDir = buildDir.absolutePath
    }

    fun setCompileCommandsPath(file: File) {
        compileCommandsPath = file.absolutePath
    }

    fun setClangdMode(mode: String) {
        clangdMode = mode
    }

    fun setDiagnostics(totalCount: Int, errorCount: Int, warningCount: Int) {
        diagnosticsTotalCount = totalCount
        diagnosticsErrorCount = errorCount
        diagnosticsWarningCount = warningCount
    }

    fun setCompletionCount(count: Int) {
        completionCandidateCount = count
    }

    fun setMatchedCompletionLabels(labels: List<String>) {
        matchedCompletionLabels = labels
    }

    fun recordStageDuration(label: String, durationMs: Long) {
        val value = ProjectSmokeStageTiming(label = label, durationMs = durationMs)
        val index = stageTimings.indexOfFirst { it.label == label }
        if (index >= 0) {
            stageTimings[index] = value
        } else {
            stageTimings += value
        }
    }

    fun passCheck(name: String, detail: String? = null) {
        upsertCheck(name = name, passed = true, detail = detail)
    }

    fun failCheck(name: String, detail: String? = null) {
        upsertCheck(name = name, passed = false, detail = detail)
    }

    fun buildSuccess(): ProjectSmokeRunResult = buildReport(success = true)

    fun buildFailure(
        failedStage: String,
        failureReason: String,
        failureDetail: String? = null
    ): ProjectSmokeRunResult {
        this.failedStage = failedStage
        this.failureReason = failureReason
        this.failureDetail = failureDetail
        return buildReport(success = false)
    }

    private fun upsertCheck(name: String, passed: Boolean, detail: String?) {
        val index = checks.indexOfFirst { it.name == name }
        val value = ProjectSmokeCheck(name = name, passed = passed, detail = detail)
        if (index >= 0) {
            checks[index] = value
        } else {
            checks += value
        }
    }

    private fun buildReport(success: Boolean): ProjectSmokeRunResult {
        val totalDurationMs = elapsedSinceNanos(startedAtNanos)
        val reportText = buildString {
            appendLine("=== ${scenario.displayName} ===")
            appendLine("结果: ${if (success) "通过" else "失败"}")
            appendLine("说明: ${scenario.description}")
            projectDir?.let { appendLine("项目目录: $it") }
            buildDir?.let { appendLine("构建目录: $it") }
            compileCommandsPath?.let { appendLine("compile_commands: $it") }
            clangdMode?.let { appendLine("clangd 模式: $it") }
            if (!success) {
                failedStage?.let { appendLine("失败阶段: $it") }
                failureReason?.let { appendLine("失败原因: $it") }
            }

            if (checks.isNotEmpty()) {
                appendLine()
                appendLine("检查项:")
                checks.forEach { check ->
                    val status = if (check.passed) "[PASS]" else "[FAIL]"
                    val suffix = check.detail?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
                    appendLine("$status ${check.name}$suffix")
                }
            }

            if (
                diagnosticsTotalCount != null ||
                completionCandidateCount != null ||
                matchedCompletionLabels.isNotEmpty()
            ) {
                appendLine()
                appendLine("关键指标:")
                diagnosticsTotalCount?.let { appendLine("- diagnostics 总数: $it") }
                diagnosticsErrorCount?.let { appendLine("- error 数量: $it") }
                diagnosticsWarningCount?.let { appendLine("- warning 数量: $it") }
                completionCandidateCount?.let { appendLine("- 补全候选数量: $it") }
                if (matchedCompletionLabels.isNotEmpty()) {
                    appendLine("- 命中补全项: ${matchedCompletionLabels.joinToString()}")
                }
            }

            if (stageTimings.isNotEmpty()) {
                appendLine()
                appendLine("耗时指标:")
                appendLine("- 总耗时: ${formatDurationText(totalDurationMs)}")
                stageTimings.forEach { timing ->
                    appendLine("- ${timing.label}: ${formatDurationText(timing.durationMs)}")
                }
            }

            if (!failureDetail.isNullOrBlank()) {
                appendLine()
                appendLine("失败详情:")
                appendLine(limitText(failureDetail!!))
            }

            if (detailBuilder.isNotBlank()) {
                appendLine()
                appendLine("详细日志:")
                append(detailBuilder.toString().trimEnd())
                appendLine()
            }
        }
        return ProjectSmokeRunResult(
            scenarioId = scenario.id,
            displayName = scenario.displayName,
            description = scenario.description,
            success = success,
            reportText = reportText,
            totalDurationMs = totalDurationMs,
            stageTimings = stageTimings.toList(),
            compileCommandsPath = compileCommandsPath,
            clangdMode = clangdMode,
            diagnosticsTotalCount = diagnosticsTotalCount,
            diagnosticsErrorCount = diagnosticsErrorCount,
            diagnosticsWarningCount = diagnosticsWarningCount,
            completionCandidateCount = completionCandidateCount,
            matchedCompletionLabels = matchedCompletionLabels.toList(),
            passedChecksCount = checks.count { it.passed },
            totalChecksCount = checks.size,
            failedStage = failedStage,
            failureReason = failureReason,
            failureDetail = failureDetail
        )
    }
}

package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.ndk.AndroidSysrootManager
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CompilerDiagnosticsTestSupportTest {

    @Test
    fun actionCatalog_shouldKeepIdsOrderAndKindsStable() {
        val actions = CompilerDiagnosticsTestSupport.actionSpecs()

        assertThat(actions.map { it.id }).containsExactly(
            "check_toolchain_files",
            "test_compiler_executable",
            "test_compile_only",
            "test_link_only",
            "test_full_compile",
            "check_library_dependencies",
            "test_linker_directly",
            "test_linker_full_link",
            "test_clang_find_linker",
            "test_clang_direct_no_outer_linker",
            "test_clang_linker_resolution_modes",
            "single_file_project_smoke",
            "cmake_project_smoke",
            "sdl3_project_smoke",
            "project_smoke_compare_all",
            "launcher_chain_compare"
        ).inOrder()
        assertThat(actions.map { it.id }.distinct()).hasSize(actions.size)
        assertThat(actions.map { it.key }).containsExactly(
            CompilerDiagnosticsActionKey.CHECK_TOOLCHAIN_FILES,
            CompilerDiagnosticsActionKey.TEST_COMPILER_EXECUTABLE,
            CompilerDiagnosticsActionKey.TEST_COMPILE_ONLY,
            CompilerDiagnosticsActionKey.TEST_LINK_ONLY,
            CompilerDiagnosticsActionKey.TEST_FULL_COMPILE,
            CompilerDiagnosticsActionKey.CHECK_LIBRARY_DEPENDENCIES,
            CompilerDiagnosticsActionKey.TEST_LINKER_DIRECTLY,
            CompilerDiagnosticsActionKey.TEST_LINKER_FULL_LINK,
            CompilerDiagnosticsActionKey.TEST_CLANG_FIND_LINKER,
            CompilerDiagnosticsActionKey.TEST_CLANG_DIRECT_NO_OUTER_LINKER,
            CompilerDiagnosticsActionKey.TEST_CLANG_LINKER_RESOLUTION_MODES,
            CompilerDiagnosticsActionKey.SINGLE_FILE_PROJECT_SMOKE,
            CompilerDiagnosticsActionKey.CMAKE_PROJECT_SMOKE,
            CompilerDiagnosticsActionKey.SDL3_PROJECT_SMOKE,
            CompilerDiagnosticsActionKey.PROJECT_SMOKE_COMPARE_ALL,
            CompilerDiagnosticsActionKey.LAUNCHER_CHAIN_COMPARE
        ).inOrder()
        assertThat(actions.map { it.key }.distinct()).hasSize(actions.size)
        assertThat(actions.map { it.titleRes }.distinct()).hasSize(actions.size)
        assertThat(actions.count { it.type == CompilerDiagnosticsActionType.STANDARD }).isEqualTo(12)
        assertThat(actions.filter { it.type != CompilerDiagnosticsActionType.STANDARD }.map { it.id })
            .containsExactly(
                "single_file_project_smoke",
                "cmake_project_smoke",
                "sdl3_project_smoke",
                "project_smoke_compare_all"
            ).inOrder()
        assertThat(
            CompilerDiagnosticsTestSupport.resolveActionTitle(actions.first()) { "resource" }
        ).isEqualTo("resource")
        assertThat(
            CompilerDiagnosticsTestSupport.resolveActionTitle(actions.last()) { "resource-tail" }
        ).isEqualTo("resource-tail")
    }

    @Test
    fun requireActionSpec_shouldResolveCatalogEntriesAndRejectUnknownIds() {
        val actions = CompilerDiagnosticsTestSupport.actionSpecs()

        actions.forEach { action ->
            assertThat(CompilerDiagnosticsTestSupport.requireActionSpec(action.id)).isEqualTo(action)
        }

        val error = runCatching {
            CompilerDiagnosticsTestSupport.requireActionSpec("missing_action")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error?.message).contains("Unknown compiler diagnostics action: missing_action")
    }

    @Test
    fun resolveLaunchPlan_shouldKeepActionDispatchStable() {
        val actions = CompilerDiagnosticsTestSupport.actionSpecs()
        val singleSmokePlans = mutableMapOf<String, CompilerDiagnosticsActionLaunchPlan>()
        var compareAllPlan: CompilerDiagnosticsActionLaunchPlan? = null

        actions.forEach { action ->
            val plan = CompilerDiagnosticsTestSupport.resolveLaunchPlan(action.id)

            assertThat(plan.key).isEqualTo(action.key)
            assertThat(plan.type).isEqualTo(action.type)

            when (action.type) {
                CompilerDiagnosticsActionType.STANDARD -> {
                    assertThat(plan.smokeScenarioId).isNull()
                }

                CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE -> {
                    singleSmokePlans[action.id] = plan
                }

                CompilerDiagnosticsActionType.PROJECT_SMOKE_COMPARE_ALL -> {
                    compareAllPlan = plan
                    assertThat(plan.smokeScenarioId).isNull()
                }
            }
        }

        assertThat(singleSmokePlans.keys).containsExactly(
            "single_file_project_smoke",
            "cmake_project_smoke",
            "sdl3_project_smoke"
        )
        assertThat(singleSmokePlans["single_file_project_smoke"]?.smokeScenarioId)
            .isEqualTo("single_file_std_headers")
        assertThat(singleSmokePlans["cmake_project_smoke"]?.smokeScenarioId)
            .isEqualTo("cmake_std_headers")
        assertThat(singleSmokePlans["sdl3_project_smoke"]?.smokeScenarioId)
            .isEqualTo("sdl3_std_headers")
        assertThat(compareAllPlan).isNotNull()
        assertThat(compareAllPlan?.smokeScenarioId).isNull()
    }

    @Test
    fun resolveExecutionPlan_shouldKeepRuntimeDispatchStable() {
        val expectedTargets = mapOf(
            "check_toolchain_files" to CompilerDiagnosticsExecutionTarget.CHECK_TOOLCHAIN_FILES,
            "test_compiler_executable" to CompilerDiagnosticsExecutionTarget.TEST_COMPILER_EXECUTABLE,
            "test_compile_only" to CompilerDiagnosticsExecutionTarget.TEST_COMPILE_ONLY,
            "test_link_only" to CompilerDiagnosticsExecutionTarget.TEST_LINK_ONLY,
            "test_full_compile" to CompilerDiagnosticsExecutionTarget.TEST_FULL_COMPILE,
            "check_library_dependencies" to CompilerDiagnosticsExecutionTarget.CHECK_LIBRARY_DEPENDENCIES,
            "test_linker_directly" to CompilerDiagnosticsExecutionTarget.TEST_LINKER_DIRECTLY,
            "test_linker_full_link" to CompilerDiagnosticsExecutionTarget.TEST_LINKER_FULL_LINK,
            "test_clang_find_linker" to CompilerDiagnosticsExecutionTarget.TEST_CLANG_FIND_LINKER,
            "test_clang_direct_no_outer_linker" to
                CompilerDiagnosticsExecutionTarget.TEST_CLANG_DIRECT_NO_OUTER_LINKER,
            "test_clang_linker_resolution_modes" to
                CompilerDiagnosticsExecutionTarget.TEST_CLANG_LINKER_RESOLUTION_MODES,
            "launcher_chain_compare" to CompilerDiagnosticsExecutionTarget.LAUNCHER_CHAIN_COMPARE,
            "single_file_project_smoke" to CompilerDiagnosticsExecutionTarget.SINGLE_FILE_PROJECT_SMOKE,
            "cmake_project_smoke" to CompilerDiagnosticsExecutionTarget.CMAKE_PROJECT_SMOKE,
            "sdl3_project_smoke" to CompilerDiagnosticsExecutionTarget.SDL3_PROJECT_SMOKE,
            "project_smoke_compare_all" to
                CompilerDiagnosticsExecutionTarget.PROJECT_SMOKE_COMPARE_ALL
        )

        CompilerDiagnosticsTestSupport.actionSpecs().forEach { action ->
            val plan = CompilerDiagnosticsTestSupport.resolveExecutionPlan(action.id)

            assertThat(plan.target).isEqualTo(expectedTargets.getValue(action.id))
            assertThat(plan.type).isEqualTo(action.type)
        }
    }

    @Test
    fun smokeFormattingHelpers_shouldProduceReadableTexts() {
        val fastest = smokeResult(
            scenarioId = "single_file_std_headers",
            displayName = "13. 单文件闭环",
            totalDurationMs = 820
        )
        val middle = smokeResult(
            scenarioId = "custom_scenario",
            displayName = "18. 自定义样例",
            totalDurationMs = 1_250
        )
        val slowest = smokeResult(
            scenarioId = "sdl3_std_headers",
            displayName = "15. SDL3 闭环",
            totalDurationMs = 2_340
        )
        val allResults = listOf(fastest, middle, slowest)
        val stageTimings = listOf(
            ProjectSmokeStageTiming(label = "准备", durationMs = 100),
            ProjectSmokeStageTiming(label = "构建", durationMs = 450),
            ProjectSmokeStageTiming(label = "补全", durationMs = 320)
        )

        assertThat(CompilerDiagnosticsTestSupport.formatDuration(85)).isEqualTo("85 ms")
        assertThat(CompilerDiagnosticsTestSupport.formatDuration(1_520)).isEqualTo("1.52 s")
        assertThat(
            CompilerDiagnosticsTestSupport.formatTotalDuration(
                result = fastest,
                allResults = allResults,
                fastestLabel = "最快",
                slowestLabel = "最慢"
            )
        ).isEqualTo("820 ms (最快)")
        assertThat(
            CompilerDiagnosticsTestSupport.formatTotalDuration(
                result = middle,
                allResults = allResults,
                fastestLabel = "最快",
                slowestLabel = "最慢"
            )
        ).isEqualTo("1.25 s (+430 ms)")
        assertThat(
            CompilerDiagnosticsTestSupport.formatTotalDuration(
                result = slowest,
                allResults = allResults,
                fastestLabel = "最快",
                slowestLabel = "最慢"
            )
        ).isEqualTo("2.34 s (最慢, +1.52 s)")
        assertThat(
            CompilerDiagnosticsTestSupport.formatStageTiming(
                timing = stageTimings[0],
                stageTimings = stageTimings,
                bottleneckLabel = "瓶颈"
            )
        ).isEqualTo("100 ms")
        assertThat(
            CompilerDiagnosticsTestSupport.formatStageTiming(
                timing = stageTimings[1],
                stageTimings = stageTimings,
                bottleneckLabel = "瓶颈"
            )
        ).isEqualTo("450 ms (瓶颈)")
        assertThat(
            CompilerDiagnosticsTestSupport.smokeScenarioTitle(
                result = fastest,
                singleFileTitle = "13. 单文件标准头样例",
                cmakeTitle = "14. CMake 标准头样例",
                sdl3Title = "15. SDL3 标准头样例"
            )
        ).isEqualTo("单文件标准头样例")
        assertThat(
            CompilerDiagnosticsTestSupport.smokeScenarioTitle(
                result = middle,
                singleFileTitle = "13. 单文件标准头样例",
                cmakeTitle = "14. CMake 标准头样例",
                sdl3Title = "15. SDL3 标准头样例"
            )
        ).isEqualTo("自定义样例")
    }

    @Test
    fun compilerPathHelpers_shouldFilterFlagsExtractLinkerAndDescribeAlias() {
        val filteredFlags = CompilerDiagnosticsTestSupport.extractCompileOnlyFlags(
            listOf("-Wall", "-Winvalid-pch", "-L/system/lib", "-Winvalid-offsetof", "-Lcustom")
        )
        val linkerPath = CompilerDiagnosticsTestSupport.extractLinkerPath(
            """clang++: note: invoking "/data/user/0/com.demo/files/toolchain/bin/ld.lld" with args"""
        )

        assertThat(filteredFlags).containsExactly(
            "-Wall",
            "-Winvalid-pch",
            "-Winvalid-offsetof"
        ).inOrder()
        assertThat(CompilerDiagnosticsTestSupport.prependPathSegment("/clang/bin", null))
            .isEqualTo("/clang/bin")
        assertThat(CompilerDiagnosticsTestSupport.prependPathSegment("/clang/bin", "/usr/bin"))
            .isEqualTo("/clang/bin:/usr/bin")
        assertThat(linkerPath)
            .isEqualTo("/data/user/0/com.demo/files/toolchain/bin/ld.lld")
        assertThat(
            CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
                path = "/data/user/0/com.demo/cache/linker",
                packageName = "com.demo",
                aliasEquivalent = true
            )
        ).contains("等价")
        assertThat(
            CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
                path = "/data/user/0/com.demo/cache/linker",
                packageName = "com.demo",
                aliasEquivalent = false
            )
        ).contains("不等价")
        assertThat(
            CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
                path = "/data/user/0/com.demo/cache/linker",
                packageName = "com.demo",
                aliasEquivalent = null
            )
        ).contains("无法完成")
        assertThat(
            CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
                path = "/system/bin/linker64",
                packageName = "com.demo",
                aliasEquivalent = true
            )
        ).contains("跳过")
    }

    @Test
    fun runTestAndLoggerHelpers_shouldWrapFailuresAndPreserveLines() = runBlocking {
        val successOutput = CompilerDiagnosticsTestSupport.runSafely { "done" }
        val failureOutput = CompilerDiagnosticsTestSupport.runSafely {
            throw IllegalStateException("boom")
        }
        val explicitFailureOutput = CompilerDiagnosticsTestSupport.buildRunFailureOutput(
            message = "boom",
            stackTrace = "stack-line-1\nstack-line-2"
        )
        val forwardedLines = mutableListOf<String>()
        val logger = CompilerDiagnosticsTextLogger(
            tag = "Test",
            lineSink = forwardedLines::add
        )

        logger.appendLine("first")
        logger.appendLine()
        logger.appendLine("second")

        assertThat(successOutput).isEqualTo("done")
        assertThat(failureOutput).contains("❌ 测试失败:")
        assertThat(failureOutput).contains("boom")
        assertThat(explicitFailureOutput).isEqualTo(
            "❌ 测试失败:\nboom\n\nstack-line-1\nstack-line-2"
        )
        assertThat(logger.getOutput().replace("\r\n", "\n"))
            .isEqualTo("first\n\nsecond\n")
        assertThat(forwardedLines).containsExactly("first", "second").inOrder()
    }

    @Test
    fun launchMatrixReportHelpers_shouldFormatCaseLinesAndRecommendations() {
        val report = LaunchMatrixCaseReport(
            caseName = "Case2 shell-shim-without-exec",
            workingDirPath = "/tmp/work",
            command = listOf("/system/bin/sh", "/tmp/wrapper.sh", "--flag"),
            extraEnv = linkedMapOf("LD_PRELOAD" to "/tmp/preload.so", "PATH" to "/clang/bin"),
            exitCode = 7,
            durationMs = 234,
            objectFile = LaunchMatrixFileSnapshot(
                path = "/tmp/work/out.o",
                exists = true,
                sizeBytes = 1024
            ),
            depFile = LaunchMatrixFileSnapshot(
                path = "/tmp/work/out.d",
                exists = false,
                sizeBytes = 0
            ),
            stdout = "stdout-line",
            stderr = "stderr-line"
        )
        val blankOutputReport = report.copy(stdout = "   ", stderr = "")

        val lines = CompilerDiagnosticsTestSupport.buildLaunchMatrixCaseLines(report)
        val blankOutputLines = CompilerDiagnosticsTestSupport.buildLaunchMatrixCaseLines(blankOutputReport)
        val recommendations = CompilerDiagnosticsTestSupport.launcherChainRecommendationLines()

        assertThat(lines).containsExactly(
            "--- Case2 shell-shim-without-exec ---",
            "工作目录: /tmp/work",
            "命令:",
            "/system/bin/sh /tmp/wrapper.sh --flag",
            "额外环境变量:",
            "  LD_PRELOAD=/tmp/preload.so",
            "  PATH=/clang/bin",
            "退出码: 7",
            "耗时: 234ms",
            "目标对象: /tmp/work/out.o (exists=true, size=1024)",
            "依赖文件: /tmp/work/out.d (exists=false, size=0)",
            "标准输出:",
            "stdout-line",
            "标准错误:",
            "stderr-line",
            ""
        ).inOrder()
        assertThat(blankOutputLines).doesNotContain("标准输出:")
        assertThat(blankOutputLines).doesNotContain("标准错误:")
        assertThat(recommendations).containsExactly(
            "1. 若基线对照全通过，但 CMake-like current-native-runner 也失败，问题更像 CMake try_compile 形态本身，而不是 wrapper 独有问题。",
            "2. 若 CMake-like current-native-runner 成功，而 wrapper-direct 失败，说明主问题就在 wrapper 链，可考虑直接回退为 linker64 作为 CMake compiler launcher。",
            "3. 若只有 shell-shim 成功，而 current/wrapper 都失败，说明问题更像 exec/preload 与真实 clang 组合方式。",
            "4. 若所有 CMake-like case 都失败，说明问题更像 API28 flags、相对 depfile、Scratch 工作目录或 ROM 限制。"
        ).inOrder()
    }

    @Test
    fun clangLookupHelpers_shouldExposeStableQueriesAndReadableSections() {
        val queries = CompilerDiagnosticsTestSupport.clangProgramLookupSpecs()
        val lookupLines = CompilerDiagnosticsTestSupport.buildClangProgramLookupLines(
            ClangProgramLookupReport(
                title = queries.first().title,
                commandLine = "/clang/bin/clang++ -print-prog-name=ld.lld",
                exitCode = 0,
                output = "/clang/bin/ld.lld\n",
                error = "warning-line"
            )
        )
        val pathCheckLines = CompilerDiagnosticsTestSupport.buildClangResolvedPathCheckLines(
            ClangResolvedPathCheckReport(
                title = "测试3: 检查 clang++ 返回的 ld.lld 路径",
                path = "/clang/bin/ld.lld",
                exists = true,
                sizeBytes = 4096,
                canExecute = true
            )
        )
        val missingPathCheckLines = CompilerDiagnosticsTestSupport.buildClangResolvedPathCheckLines(
            ClangResolvedPathCheckReport(
                title = "测试3: 检查 clang++ 返回的 ld.lld 路径",
                path = "/clang/bin/missing-ld.lld",
                exists = false,
                sizeBytes = 0,
                canExecute = false
            )
        )

        assertThat(queries.map { it.title }).containsExactly(
            "测试1: 查询 ld.lld 路径",
            "测试2: 查询 lld 路径"
        ).inOrder()
        assertThat(queries.map { it.programName }).containsExactly("ld.lld", "lld").inOrder()
        assertThat(lookupLines).containsExactly(
            "测试1: 查询 ld.lld 路径",
            "命令: /clang/bin/clang++ -print-prog-name=ld.lld",
            "",
            "退出码: 0",
            "输出: /clang/bin/ld.lld",
            "错误: warning-line",
            ""
        ).inOrder()
        assertThat(pathCheckLines).containsExactly(
            "测试3: 检查 clang++ 返回的 ld.lld 路径",
            "路径: /clang/bin/ld.lld",
            "存在: true",
            "大小: 4096 bytes",
            "可执行: true"
        ).inOrder()
        assertThat(missingPathCheckLines).containsExactly(
            "测试3: 检查 clang++ 返回的 ld.lld 路径",
            "路径: /clang/bin/missing-ld.lld",
            "存在: false"
        ).inOrder()
    }

    @Test
    fun customReportTextSet_shouldDriveLaunchLookupAndRecommendationLabels() {
        val textSet = CompilerDiagnosticsReportTextSet.Default.copy(
            failurePrefix = "FAIL!",
            workingDirectoryLabel = "WD",
            commandLabel = "CMD",
            extraEnvironmentLabel = "ENV",
            exitCodeLabel = "CODE",
            durationLabel = "COST",
            objectFileLabel = "OBJ",
            dependencyFileLabel = "DEP",
            stdoutLabel = "OUT_STREAM",
            stderrLabel = "ERR_STREAM",
            lookupTitleLdLld = "Lookup ld.lld",
            lookupTitleLld = "Lookup lld",
            resolvedPathCheckTitle = "Resolved path check",
            outputLabel = "OUT",
            errorLabel = "ERR",
            pathLabel = "PATH",
            existsLabel = "EXISTS",
            sizeLabel = "SIZE",
            executableLabel = "EXEC",
            resolvedLinkerLabel = "LINKER",
            unresolvedLinkerText = "N/A",
            pathAliasCheckSkipped = "ALIAS_SKIP",
            pathAliasCheckEquivalent = "ALIAS_EQ",
            pathAliasCheckDifferent = "ALIAS_DIFF",
            pathAliasCheckUnavailable = "ALIAS_NA",
            linkerResolutionCase1Title = "LR-1",
            linkerResolutionCase2Title = "LR-2",
            linkerResolutionCase3Title = "LR-3",
            linkerResolutionCase4Title = "LR-4",
            linkerResolutionCase5Title = "LR-5",
            launcherChainRecommendations = listOf("REC-1", "REC-2")
        )
        val launchLines = CompilerDiagnosticsTestSupport.buildLaunchMatrixCaseLines(
            LaunchMatrixCaseReport(
                caseName = "Custom Case",
                workingDirPath = "/tmp/work",
                command = listOf("/system/bin/sh", "/tmp/wrapper.sh", "--flag"),
                extraEnv = linkedMapOf("LD_PRELOAD" to "/tmp/preload.so", "PATH" to "/clang/bin"),
                exitCode = 7,
                durationMs = 234,
                objectFile = LaunchMatrixFileSnapshot(
                    path = "/tmp/work/out.o",
                    exists = true,
                    sizeBytes = 1024
                ),
                depFile = LaunchMatrixFileSnapshot(
                    path = "/tmp/work/out.d",
                    exists = false,
                    sizeBytes = 0
                ),
                stdout = "stdout-line",
                stderr = "stderr-line"
            ),
            textSet
        )
        val queries = CompilerDiagnosticsTestSupport.clangProgramLookupSpecs(textSet)
        val resolutionCases = CompilerDiagnosticsTestSupport.clangLinkerResolutionCaseSpecs(
            binDirPath = "/clang/bin",
            currentPath = "/usr/bin",
            forceLldArg = "-fuse-ld=/clang/bin/ld.lld",
            textSet = textSet
        )
        val lookupLines = CompilerDiagnosticsTestSupport.buildClangProgramLookupLines(
            ClangProgramLookupReport(
                title = queries.first().title,
                commandLine = "/clang/bin/clang++ -print-prog-name=ld.lld",
                exitCode = 0,
                output = "/clang/bin/ld.lld\n",
                error = "warning-line"
            ),
            textSet
        )
        val pathCheckLines = CompilerDiagnosticsTestSupport.buildClangResolvedPathCheckLines(
            ClangResolvedPathCheckReport(
                title = textSet.resolvedPathCheckTitle,
                path = "/clang/bin/ld.lld",
                exists = true,
                sizeBytes = 4096,
                canExecute = true
            ),
            textSet
        )
        val linkerLines = CompilerDiagnosticsTestSupport.buildClangLinkerResolutionCaseLines(
            ClangLinkerResolutionCaseReport(
                caseName = "CaseCustom",
                command = listOf("/clang/bin/clang++", "/tmp/test.cpp", "-###"),
                extraEnv = linkedMapOf("PATH" to "/clang/bin:/usr/bin"),
                exitCode = 1,
                linkerPath = "/clang/bin/ld.lld",
                stderr = "driver stderr"
            ),
            textSet
        )
        val unresolvedLinkerLines = CompilerDiagnosticsTestSupport.buildClangLinkerResolutionCaseLines(
            ClangLinkerResolutionCaseReport(
                caseName = "CaseMissing",
                command = listOf("/clang/bin/clang++", "/tmp/test.cpp", "-###"),
                exitCode = 2
            ),
            textSet
        )
        val failureOutput = CompilerDiagnosticsTestSupport.buildRunFailureOutput(
            message = "boom",
            stackTrace = "stack-line",
            textSet = textSet
        )
        val aliasEquivalentHint = CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
            path = "/data/user/0/com.demo/cache/linker",
            packageName = "com.demo",
            aliasEquivalent = true,
            textSet = textSet
        )
        val aliasDifferentHint = CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
            path = "/data/user/0/com.demo/cache/linker",
            packageName = "com.demo",
            aliasEquivalent = false,
            textSet = textSet
        )
        val aliasUnavailableHint = CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
            path = "/data/user/0/com.demo/cache/linker",
            packageName = "com.demo",
            aliasEquivalent = null,
            textSet = textSet
        )
        val aliasSkippedHint = CompilerDiagnosticsTestSupport.buildPathAliasHintFromAliasStatus(
            path = "/system/bin/linker64",
            packageName = "com.demo",
            aliasEquivalent = true,
            textSet = textSet
        )
        val recommendations = CompilerDiagnosticsTestSupport.launcherChainRecommendationLines(textSet)

        assertThat(failureOutput).isEqualTo("FAIL!\nboom\n\nstack-line")
        assertThat(launchLines).containsExactly(
            "--- Custom Case ---",
            "WD: /tmp/work",
            "CMD:",
            "/system/bin/sh /tmp/wrapper.sh --flag",
            "ENV:",
            "  LD_PRELOAD=/tmp/preload.so",
            "  PATH=/clang/bin",
            "CODE: 7",
            "COST: 234ms",
            "OBJ: /tmp/work/out.o (exists=true, size=1024)",
            "DEP: /tmp/work/out.d (exists=false, size=0)",
            "OUT_STREAM:",
            "stdout-line",
            "ERR_STREAM:",
            "stderr-line",
            ""
        ).inOrder()
        assertThat(queries.map { it.title }).containsExactly(
            "Lookup ld.lld",
            "Lookup lld"
        ).inOrder()
        assertThat(resolutionCases.map { it.caseName }).containsExactly(
            "LR-1",
            "LR-2",
            "LR-3",
            "LR-4",
            "LR-5"
        ).inOrder()
        assertThat(lookupLines).containsExactly(
            "Lookup ld.lld",
            "CMD: /clang/bin/clang++ -print-prog-name=ld.lld",
            "",
            "CODE: 0",
            "OUT: /clang/bin/ld.lld",
            "ERR: warning-line",
            ""
        ).inOrder()
        assertThat(pathCheckLines).containsExactly(
            "Resolved path check",
            "PATH: /clang/bin/ld.lld",
            "EXISTS: true",
            "SIZE: 4096 bytes",
            "EXEC: true"
        ).inOrder()
        assertThat(linkerLines).containsExactly(
            "--- CaseCustom ---",
            "CMD:",
            "/clang/bin/clang++ /tmp/test.cpp -###",
            "ENV:",
            "  PATH=/clang/bin:/usr/bin",
            "CODE: 1",
            "LINKER: /clang/bin/ld.lld",
            "ERR_STREAM:",
            "driver stderr",
            ""
        ).inOrder()
        assertThat(unresolvedLinkerLines).contains("LINKER: N/A")
        assertThat(aliasEquivalentHint).isEqualTo("ALIAS_EQ")
        assertThat(aliasDifferentHint).isEqualTo("ALIAS_DIFF")
        assertThat(aliasUnavailableHint).isEqualTo("ALIAS_NA")
        assertThat(aliasSkippedHint).isEqualTo("ALIAS_SKIP")
        assertThat(recommendations).containsExactly("REC-1", "REC-2").inOrder()
    }

    @Test
    fun reportTextSetHelpers_shouldFormatDynamicLabels() {
        val textSet = CompilerDiagnosticsReportTextSet.Default.copy(
            testCommandLabel = "RUN",
            forcedLinkerArgumentLabel = "FORCED",
            runtimeTripleDirectoryLabelFormat = "Triple %1\$d",
            directExecutionMissingObjectMessageFormat = "MISS %1\$s"
        )

        assertThat(textSet.sectionTitle("Section")).isEqualTo("=== Section ===")
        assertThat(textSet.testCommandLine("/clang/bin/clang++ --version"))
            .isEqualTo("RUN: /clang/bin/clang++ --version")
        assertThat(textSet.forcedLinkerArgumentLine("-fuse-ld=lld"))
            .isEqualTo("FORCED: -fuse-ld=lld")
        assertThat(textSet.runtimeTripleDirectoryLabel(24)).isEqualTo("Triple 24")
        assertThat(textSet.directExecutionMissingObjectMessage("test_compile.o"))
            .isEqualTo("MISS test_compile.o")
    }

    @Test
    fun customReportTextSet_shouldDriveDiagnosticAndToolchainLabels() {
        val textSet = CompilerDiagnosticsReportTextSet.Default.copy(
            commandLabel = "CMD",
            exitCodeLabel = "CODE",
            stdoutLabel = "STDOUT_STREAM",
            stderrLabel = "STDERR_STREAM",
            existsLabel = "EXISTS",
            sizeLabel = "SIZE",
            criticalBinariesLabel = "BINARIES",
            runtimeLibrariesLabel = "RUNTIME",
            preferredBuiltinsLabel = "BEST_BUILTINS",
            preferredUnwindLabel = "BEST_UNWIND",
            installedLabel = "INSTALLED_STATE",
            outputFileLabel = "OUTFILE"
        )
        val diagnosticLines = CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                title = "=== Custom Diagnostic ===",
                preCommandLines = listOf("before-line"),
                commandLabel = "Custom command:",
                command = listOf("/clang/bin/ld.lld", "--version"),
                separateSections = true,
                exitCode = 0,
                stdout = "stdout-line",
                stderr = "stderr-line",
                outputFiles = listOf(
                    DiagnosticOutputFileReport(
                        label = "Artifact",
                        path = "/tmp/a.out",
                        exists = true,
                        sizeBytes = 64
                    )
                )
            ),
            textSet
        )
        val toolchainLines = CompilerDiagnosticsTestSupport.buildToolchainFilesCheckLines(
            ToolchainFilesCheckReport(
                toolchainDir = DiagnosticDirectoryReport(
                    label = "ToolchainDir",
                    path = "/toolchain",
                    exists = true
                ),
                binDir = DiagnosticDirectoryReport(
                    label = "BinDir",
                    path = "/toolchain/bin",
                    exists = true
                ),
                criticalBinaries = listOf(
                    DiagnosticBinaryStatusReport(
                        name = "clang++",
                        exists = true,
                        sizeBytes = 123,
                        canExecute = true
                    )
                ),
                runtimeDirectories = listOf(
                    DiagnosticDirectoryReport(
                        label = "RuntimeDir",
                        path = "/toolchain/lib/clang/18/lib/linux",
                        exists = true
                    )
                ),
                runtimeLibraries = listOf(
                    DiagnosticNamedPathStatusReport(
                        label = "builtins(linux)",
                        path = "/toolchain/lib/builtins.a",
                        exists = true
                    )
                ),
                preferredBuiltinsPath = "/toolchain/lib/builtins.a",
                preferredUnwindPath = "/toolchain/lib/unwind.a",
                sysrootDir = DiagnosticDirectoryReport(
                    label = "SysrootDir",
                    path = "/sysroot",
                    exists = true
                ),
                sysrootInstalled = true
            ),
            textSet
        )
        val directExecutionLines = CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
            ClangDirectExecutionReport(
                title = "Direct execution",
                command = listOf("/clang/bin/clang++", "/tmp/full.cpp", "-o", "/tmp/full"),
                exitCode = 0,
                stdout = "stdout-line",
                stderr = "stderr-line",
                outputPath = "/tmp/full",
                outputExists = true,
                outputSizeBytes = 8192
            ),
            textSet
        )

        assertThat(diagnosticLines).containsAtLeast(
            "=== Custom Diagnostic ===",
            "before-line",
            "Custom command:",
            "/clang/bin/ld.lld --version",
            "CODE: 0",
            "STDOUT_STREAM:",
            "stdout-line",
            "STDERR_STREAM:",
            "stderr-line",
            "Artifact: /tmp/a.out",
            "EXISTS: true",
            "SIZE: 64 bytes"
        ).inOrder()
        assertThat(toolchainLines).contains("ToolchainDir: /toolchain")
        assertThat(toolchainLines).contains("BinDir: /toolchain/bin")
        assertThat(toolchainLines).contains("SysrootDir: /sysroot")
        assertThat(toolchainLines).contains("BINARIES:")
        assertThat(toolchainLines).contains("RUNTIME:")
        assertThat(toolchainLines).contains("  BEST_BUILTINS: /toolchain/lib/builtins.a")
        assertThat(toolchainLines).contains("  BEST_UNWIND: /toolchain/lib/unwind.a")
        assertThat(toolchainLines).contains("INSTALLED_STATE: true")
        assertThat(directExecutionLines).containsExactly(
            "Direct execution",
            "CMD:",
            "/clang/bin/clang++ /tmp/full.cpp -o /tmp/full",
            "CODE: 0",
            "STDOUT_STREAM:",
            "stdout-line",
            "STDERR_STREAM:",
            "stderr-line",
            "OUTFILE: /tmp/full",
            "EXISTS: true",
            "SIZE: 8192 bytes"
        ).inOrder()
    }

    @Test
    fun linkerResolutionHelpers_shouldBuildStableCasesAndFormatReports() {
        val cases = CompilerDiagnosticsTestSupport.clangLinkerResolutionCaseSpecs(
            binDirPath = "/clang/bin",
            currentPath = "/usr/bin",
            forceLldArg = "-fuse-ld=/clang/bin/ld.lld"
        )
        val lines = CompilerDiagnosticsTestSupport.buildClangLinkerResolutionCaseLines(
            ClangLinkerResolutionCaseReport(
                caseName = cases[2].caseName,
                command = listOf("/clang/bin/clang++", "/tmp/test.cpp", "-###"),
                extraEnv = linkedMapOf("PATH" to "/clang/bin:/usr/bin"),
                exitCode = 1,
                linkerPath = "/data/user/0/com.demo/files/toolchain/bin/ld.lld",
                pathAliasHint = "路径别名检查: /data/user/0 与 /data/data 等价（dev+ino 一致）",
                stderr = "driver stderr"
            )
        )
        val unresolvedLines = CompilerDiagnosticsTestSupport.buildClangLinkerResolutionCaseLines(
            ClangLinkerResolutionCaseReport(
                caseName = cases[0].caseName,
                command = listOf("/clang/bin/clang++", "/tmp/test.cpp", "-###"),
                exitCode = 2,
                pathAliasHint = "should not be emitted",
                stdout = "driver stdout"
            )
        )

        assertThat(cases.map { it.caseName }).containsExactly(
            "Case1: baseline (-fuse-ld=lld)",
            "Case2: +COMPILER_PATH + -fuse-ld=lld",
            "Case3: +PATH前置bin + -fuse-ld=lld",
            "Case4: + -B<bin> + -fuse-ld=lld",
            "Case5: absolute -fuse-ld=<ld.lld>"
        ).inOrder()
        assertThat(cases[0].extraArgs).containsExactly("-fuse-ld=lld").inOrder()
        assertThat(cases[1].extraEnv).isEqualTo(mapOf("COMPILER_PATH" to "/clang/bin"))
        assertThat(cases[2].extraEnv).isEqualTo(mapOf("PATH" to "/clang/bin:/usr/bin"))
        assertThat(cases[3].extraArgs).containsExactly("-fuse-ld=lld", "-B/clang/bin").inOrder()
        assertThat(cases[4].extraArgs).containsExactly("-fuse-ld=/clang/bin/ld.lld").inOrder()

        assertThat(lines).containsExactly(
            "--- Case3: +PATH前置bin + -fuse-ld=lld ---",
            "命令:",
            "/clang/bin/clang++ /tmp/test.cpp -###",
            "额外环境变量:",
            "  PATH=/clang/bin:/usr/bin",
            "退出码: 1",
            "解析到的 linker: /data/user/0/com.demo/files/toolchain/bin/ld.lld",
            "路径别名检查: /data/user/0 与 /data/data 等价（dev+ino 一致）",
            "标准错误:",
            "driver stderr",
            ""
        ).inOrder()
        assertThat(unresolvedLines).contains("解析到的 linker: <未解析到>")
        assertThat(unresolvedLines).contains("标准输出:")
        assertThat(unresolvedLines).contains("driver stdout")
        assertThat(unresolvedLines).doesNotContain("should not be emitted")
    }

    @Test
    fun diagnosticCommandHelpers_shouldFormatSectionsArtifactsAndMissingPrerequisites() {
        val completedLines = CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                preCommandLines = listOf(
                    "lld: /clang/bin/ld.lld",
                    "obj: /tmp/test.o (exists=true, size=128)"
                ),
                commandLabel = "链接命令:",
                command = listOf("/clang/bin/ld.lld", "--version"),
                postCommandLines = listOf("强制链接器参数: -fuse-ld=/clang/bin/ld.lld"),
                separateSections = true,
                exitCode = 1,
                stdout = "stdout-line",
                stderr = "stderr-line",
                outputFiles = listOf(
                    DiagnosticOutputFileReport(
                        label = "可执行文件",
                        path = "/tmp/a.out",
                        exists = true,
                        sizeBytes = 4096
                    )
                )
            )
        )
        val missingLines = CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                preCommandLines = listOf(
                    "lld: /clang/bin/ld.lld",
                    "obj: /tmp/test.o (exists=false, size=0)"
                ),
                separateSections = true,
                missingPrerequisiteMessage = "❌ 关键链接文件缺失，跳过执行"
            )
        )
        val versionLines = CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                title = "=== 直接测试 lld 链接器 ===",
                preCommandLines = listOf("测试命令: /clang/bin/ld.lld --version"),
                separateSections = true,
                exitCode = 0,
                stdout = "LLD 18.1.0",
                stderr = "warn-line"
            )
        )
        val pathOnlyLines = CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                outputFiles = listOf(
                    DiagnosticOutputFileReport(
                        label = "输出文件",
                        path = "/tmp/out.bin"
                    )
                )
            )
        )

        assertThat(completedLines).containsExactly(
            "lld: /clang/bin/ld.lld",
            "obj: /tmp/test.o (exists=true, size=128)",
            "",
            "链接命令:",
            "/clang/bin/ld.lld --version",
            "强制链接器参数: -fuse-ld=/clang/bin/ld.lld",
            "",
            "退出码: 1",
            "标准输出:",
            "stdout-line",
            "标准错误:",
            "stderr-line",
            "",
            "可执行文件: /tmp/a.out",
            "存在: true",
            "大小: 4096 bytes"
        ).inOrder()
        assertThat(missingLines).containsExactly(
            "lld: /clang/bin/ld.lld",
            "obj: /tmp/test.o (exists=false, size=0)",
            "",
            "❌ 关键链接文件缺失，跳过执行"
        ).inOrder()
        assertThat(versionLines).containsExactly(
            "=== 直接测试 lld 链接器 ===",
            "测试命令: /clang/bin/ld.lld --version",
            "",
            "退出码: 0",
            "标准输出:",
            "LLD 18.1.0",
            "标准错误:",
            "warn-line"
        ).inOrder()
        assertThat(pathOnlyLines).containsExactly("输出文件: /tmp/out.bin").inOrder()
    }

    @Test
    fun toolchainFilesCheckHelpers_shouldFormatCriticalBinariesRuntimeAndSysroot() {
        val lines = CompilerDiagnosticsTestSupport.buildToolchainFilesCheckLines(
            ToolchainFilesCheckReport(
                toolchainDir = DiagnosticDirectoryReport(
                    label = "工具链目录",
                    path = "/toolchain",
                    exists = true
                ),
                binDir = DiagnosticDirectoryReport(
                    label = "Bin 目录",
                    path = "/toolchain/bin",
                    exists = true
                ),
                criticalBinaries = listOf(
                    DiagnosticBinaryStatusReport(
                        name = "clang++",
                        exists = true,
                        sizeBytes = 123,
                        canExecute = true
                    ),
                    DiagnosticBinaryStatusReport(
                        name = "ld.lld",
                        exists = false,
                        sizeBytes = 0,
                        canExecute = false
                    )
                ),
                runtimeDirectories = listOf(
                    DiagnosticDirectoryReport(
                        label = "Clang Runtime Linux Base 目录",
                        path = "/toolchain/lib/clang/18/lib/linux",
                        exists = true
                    ),
                    DiagnosticDirectoryReport(
                        label = "Clang Runtime Linux Arch 目录",
                        path = "/toolchain/lib/clang/18/lib/linux/aarch64",
                        exists = true
                    ),
                    DiagnosticDirectoryReport(
                        label = "Clang Runtime Triple 目录(api28)",
                        path = "/sysroot/usr/lib/aarch64-linux-android/28",
                        exists = false
                    )
                ),
                runtimeLibraries = listOf(
                    DiagnosticNamedPathStatusReport(
                        label = "builtins(linux)",
                        path = "/toolchain/lib/clang/18/lib/linux/libclang_rt.builtins-aarch64-android.a",
                        exists = true
                    ),
                    DiagnosticNamedPathStatusReport(
                        label = "builtins(triple)",
                        path = "/sysroot/usr/lib/aarch64-linux-android/28/libclang_rt.builtins-aarch64-android.a",
                        exists = false
                    ),
                    DiagnosticNamedPathStatusReport(
                        label = "libunwind(linux)",
                        path = "/toolchain/lib/clang/18/lib/linux/aarch64/libunwind.a",
                        exists = true
                    ),
                    DiagnosticNamedPathStatusReport(
                        label = "libunwind(triple)",
                        path = "/sysroot/usr/lib/aarch64-linux-android/28/libunwind.a",
                        exists = false
                    )
                ),
                preferredBuiltinsPath = "/toolchain/lib/clang/18/lib/linux/libclang_rt.builtins-aarch64-android.a",
                preferredUnwindPath = "/toolchain/lib/clang/18/lib/linux/aarch64/libunwind.a",
                sysrootDir = DiagnosticDirectoryReport(
                    label = "Sysroot 目录",
                    path = "/sysroot",
                    exists = true
                ),
                sysrootInstalled = true
            )
        )

        assertThat(lines).containsExactly(
            "=== 工具链文件完整性检查 ===",
            "",
            "工具链目录: /toolchain",
            "存在: true",
            "",
            "Bin 目录: /toolchain/bin",
            "存在: true",
            "",
            "关键二进制文件:",
            "  clang++: ✓ (123 bytes, canExec=true)",
            "  ld.lld: ✗ (0 bytes, canExec=false)",
            "",
            "Clang Runtime Linux Base 目录: /toolchain/lib/clang/18/lib/linux",
            "存在: true",
            "Clang Runtime Linux Arch 目录: /toolchain/lib/clang/18/lib/linux/aarch64",
            "存在: true",
            "Clang Runtime Triple 目录(api28): /sysroot/usr/lib/aarch64-linux-android/28",
            "存在: false",
            "Runtime 库文件:",
            "  builtins(linux): ✓ (/toolchain/lib/clang/18/lib/linux/libclang_rt.builtins-aarch64-android.a)",
            "  builtins(triple): ✗ (/sysroot/usr/lib/aarch64-linux-android/28/libclang_rt.builtins-aarch64-android.a)",
            "  libunwind(linux): ✓ (/toolchain/lib/clang/18/lib/linux/aarch64/libunwind.a)",
            "  libunwind(triple): ✗ (/sysroot/usr/lib/aarch64-linux-android/28/libunwind.a)",
            "  优先 builtins: /toolchain/lib/clang/18/lib/linux/libclang_rt.builtins-aarch64-android.a",
            "  优先 libunwind: /toolchain/lib/clang/18/lib/linux/aarch64/libunwind.a",
            "",
            "Sysroot 目录: /sysroot",
            "存在: true",
            "已安装: true"
        ).inOrder()
    }

    @Test
    fun libraryDependencyCheckHelpers_shouldFormatDirectoryListings() {
        val lines = CompilerDiagnosticsTestSupport.buildLibraryDependencyCheckLines(
            LibraryDependencyCheckReport(
                directories = listOf(
                    DiagnosticDirectoryReport(
                        label = "Clang Runtime Linux Base 目录",
                        path = "/toolchain/lib/clang/18/lib/linux",
                        exists = true,
                        entriesTitle = "Linux Base 目录内容:",
                        entries = listOf(
                            DiagnosticDirectoryEntryReport(
                                name = "libclang_rt.builtins-aarch64-android.a",
                                sizeBytes = 2048
                            ),
                            DiagnosticDirectoryEntryReport(
                                name = "libc++.a",
                                sizeBytes = 1024
                            )
                        )
                    ),
                    DiagnosticDirectoryReport(
                        label = "Clang Runtime Linux Arch 目录",
                        path = "/toolchain/lib/clang/18/lib/linux/aarch64",
                        exists = false,
                        entriesTitle = "Linux Arch 子目录内容:"
                    ),
                    DiagnosticDirectoryReport(
                        label = "Clang Runtime Triple 目录(api28)",
                        path = "/sysroot/usr/lib/aarch64-linux-android/28",
                        exists = true,
                        entriesTitle = "Triple 子目录内容:",
                        entries = listOf(
                            DiagnosticDirectoryEntryReport(
                                name = "libunwind.a",
                                sizeBytes = 512
                            )
                        )
                    )
                )
            )
        )

        assertThat(lines).containsExactly(
            "=== 库文件依赖检查 ===",
            "",
            "Clang Runtime Linux Base 目录: /toolchain/lib/clang/18/lib/linux",
            "存在: true",
            "Clang Runtime Linux Arch 目录: /toolchain/lib/clang/18/lib/linux/aarch64",
            "存在: false",
            "Clang Runtime Triple 目录(api28): /sysroot/usr/lib/aarch64-linux-android/28",
            "存在: true",
            "",
            "Linux Base 目录内容:",
            "  libclang_rt.builtins-aarch64-android.a (2048 bytes)",
            "  libc++.a (1024 bytes)",
            "Triple 子目录内容:",
            "  libunwind.a (512 bytes)"
        ).inOrder()
    }

    @Test
    fun clangDirectExecutionHelpers_shouldDescribeMissingAndCompletedRuns() {
        val missingLines = CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
            ClangDirectExecutionReport(
                title = "测试A: 仅链接（直启 clang++）",
                missingPrerequisiteMessage = "❌ 缺少 test_compile.o，请先运行「3. 仅编译测试」"
            )
        )
        val executionLines = CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
            ClangDirectExecutionReport(
                title = "测试B: 完整编译+链接（直启 clang++）",
                command = listOf("/clang/bin/clang++", "/tmp/full.cpp", "-o", "/tmp/full"),
                exitCode = 0,
                stdout = "stdout-line",
                stderr = "stderr-line",
                outputPath = "/tmp/full",
                outputExists = true,
                outputSizeBytes = 8192
            )
        )
        val missingOutputLines = CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
            ClangDirectExecutionReport(
                title = "测试A: 仅链接（直启 clang++）",
                command = listOf("/clang/bin/clang++", "/tmp/test.o", "-o", "/tmp/test"),
                exitCode = 1,
                outputPath = "/tmp/test",
                outputExists = false,
                outputSizeBytes = 0
            )
        )

        assertThat(missingLines).containsExactly(
            "测试A: 仅链接（直启 clang++）",
            "❌ 缺少 test_compile.o，请先运行「3. 仅编译测试」"
        ).inOrder()
        assertThat(executionLines).containsExactly(
            "测试B: 完整编译+链接（直启 clang++）",
            "命令:",
            "/clang/bin/clang++ /tmp/full.cpp -o /tmp/full",
            "退出码: 0",
            "标准输出:",
            "stdout-line",
            "标准错误:",
            "stderr-line",
            "输出文件: /tmp/full",
            "存在: true",
            "大小: 8192 bytes"
        ).inOrder()
        assertThat(missingOutputLines).contains("存在: false")
        assertThat(missingOutputLines).doesNotContain("大小: 0 bytes")
    }

    @Test
    fun runtimeHelpers_shouldSortVersionsAndResolvePreferredArtifacts() {
        val rootDir = Files.createTempDirectory("compiler-diagnostics-support-test").toFile()
        try {
            val binDir = File(rootDir, "bin").apply { mkdirs() }
            createFile(File(binDir, "clang-9"))
            createFile(File(binDir, "clang-18"))
            createFile(File(binDir, "clang-17"))
            createFile(File(binDir, "clang"))

            val installDir = File(rootDir, "toolchain")
            File(installDir, "lib/clang/17.0.0/lib/linux").mkdirs()
            val latestVersionDir = File(installDir, "lib/clang/18.1.2")
            val latestLinuxBaseDir = File(latestVersionDir, "lib/linux").apply { mkdirs() }
            val latestLinuxArchDir = File(latestLinuxBaseDir, "aarch64").apply { mkdirs() }
            createFile(File(latestLinuxBaseDir, "libclang_rt.builtins-aarch64-android.a"))
            createFile(File(latestLinuxArchDir, "libunwind.a"))
            val api29TripleDir = File(
                latestVersionDir,
                "lib/aarch64-unknown-linux-android29"
            ).apply {
                mkdirs()
            }
            createFile(File(api29TripleDir, "libclang_rt.builtins.a"))
            createFile(File(api29TripleDir, "libunwind.a"))

            val api29Layout = CompilerDiagnosticsTestSupport.resolveRuntimeLayout(
                installDir = installDir,
                arch = AndroidSysrootManager.Companion.Arch.ARM64,
                apiLevel = 29
            )
            val api28Layout = CompilerDiagnosticsTestSupport.resolveRuntimeLayout(
                installDir = installDir,
                arch = AndroidSysrootManager.Companion.Arch.ARM64,
                apiLevel = 28
            )

            assertThat(CompilerDiagnosticsTestSupport.compareVersionNames("18.1.2", "18.1.10"))
                .isLessThan(0)
            assertThat(CompilerDiagnosticsTestSupport.findVersionedClangBinaryNames(binDir))
                .containsExactly("clang-9", "clang-17", "clang-18")
                .inOrder()
            assertThat(api29Layout.linuxBaseDir).isEqualTo(latestLinuxBaseDir)
            assertThat(api29Layout.linuxArchDir).isEqualTo(latestLinuxArchDir)
            assertThat(api29Layout.preferredBuiltins)
                .isEqualTo(File(api29TripleDir, "libclang_rt.builtins.a"))
            assertThat(api29Layout.preferredUnwind)
                .isEqualTo(File(api29TripleDir, "libunwind.a"))
            assertThat(api28Layout.preferredBuiltins).isEqualTo(api28Layout.linuxBuiltins)
            assertThat(api28Layout.preferredUnwind)
                .isEqualTo(File(api28Layout.linuxArchDir, "libunwind.a"))
        } finally {
            rootDir.deleteRecursively()
        }
    }

    private fun smokeResult(
        scenarioId: String,
        displayName: String,
        totalDurationMs: Long
    ): ProjectSmokeRunResult = ProjectSmokeRunResult(
        scenarioId = scenarioId,
        displayName = displayName,
        description = "desc",
        success = true,
        reportText = "report",
        totalDurationMs = totalDurationMs
    )

    private fun createFile(file: File, content: String = "x") {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}

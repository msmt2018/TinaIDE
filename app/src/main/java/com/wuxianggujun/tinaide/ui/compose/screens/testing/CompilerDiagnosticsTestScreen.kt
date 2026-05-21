package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.ndk.AndroidNativeToolchainManager
import com.wuxianggujun.tinaide.core.ndk.AndroidSysrootManager
import com.wuxianggujun.tinaide.core.util.NativeExecutableRunner
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val DIAGNOSTIC_TARGET_API_LEVEL = 24
private const val CMAKE_LIKE_DIAGNOSTIC_TARGET_API_LEVEL = 28
private const val SINGLE_FILE_PROJECT_SMOKE_RESULT_ID = "single_file_std_headers"
private const val CMAKE_PROJECT_SMOKE_RESULT_ID = "cmake_std_headers"
private const val SDL3_PROJECT_SMOKE_RESULT_ID = "sdl3_std_headers"

private data class DiagnosticToolchainSelection(
    val toolchainId: String,
) {
    val useRecommendedTinaExec: Boolean = false
}

private suspend fun resolveDiagnosticToolchainSelection(
    context: Context,
): DiagnosticToolchainSelection = withContext(Dispatchers.IO) {
    val manager = AndroidNativeToolchainManager(context.applicationContext)
    val toolchainId = manager.installAssetVariant("patched").getOrThrow()
    DiagnosticToolchainSelection(toolchainId = toolchainId)
}

private fun DiagnosticToolchainSelection.displayName(context: Context): String = Strings.compiler_diagnostics_toolchain_variant_patched.strOr(context)

private fun appendDiagnosticToolchainHeader(
    logger: CompilerDiagnosticsTextLogger,
    context: Context,
    selection: DiagnosticToolchainSelection,
    installDir: File,
    binDir: File,
) {
    logger.appendLine("工具链变体: ${selection.displayName(context)}")
    logger.appendLine("toolchainId: ${selection.toolchainId}")
    logger.appendLine("tina-exec: ${if (selection.useRecommendedTinaExec) "enabled" else "disabled"}")
    logger.appendLine("installDir: ${installDir.absolutePath}")
    logger.appendLine("binDir: ${binDir.absolutePath}")
    logger.appendLine()
}

private data class LaunchInputs(
    val compilerArgs: List<String>,
    val objectFile: File,
    val depFile: File,
    val workingDir: File,
)

private data class LaunchCase(
    val name: String,
    val commandFactory: (LaunchInputs) -> List<String>,
    val preferLinker64: Boolean,
    val extraEnv: Map<String, String> = emptyMap(),
    val customizeEnvironment: (MutableMap<String, String>) -> Unit = {},
)

internal enum class CompilerDiagnosticsActionType {
    STANDARD,
    PROJECT_SMOKE_SINGLE,
    PROJECT_SMOKE_COMPARE_ALL,
}

internal enum class CompilerDiagnosticsActionKey {
    CHECK_TOOLCHAIN_FILES,
    TEST_COMPILER_EXECUTABLE,
    TEST_COMPILE_ONLY,
    TEST_LINK_ONLY,
    TEST_FULL_COMPILE,
    CHECK_LIBRARY_DEPENDENCIES,
    TEST_LINKER_DIRECTLY,
    TEST_LINKER_FULL_LINK,
    TEST_CLANG_FIND_LINKER,
    TEST_CLANG_DIRECT_NO_OUTER_LINKER,
    TEST_CLANG_LINKER_RESOLUTION_MODES,
    LAUNCHER_CHAIN_COMPARE,
    SINGLE_FILE_PROJECT_SMOKE,
    CMAKE_PROJECT_SMOKE,
    SDL3_PROJECT_SMOKE,
    PROJECT_SMOKE_COMPARE_ALL,
}

internal data class CompilerDiagnosticsActionSpec(
    val id: String,
    val key: CompilerDiagnosticsActionKey,
    val type: CompilerDiagnosticsActionType,
    @param:StringRes @get:StringRes val titleRes: Int,
)

internal data class CompilerDiagnosticsActionLaunchPlan(
    val key: CompilerDiagnosticsActionKey,
    val type: CompilerDiagnosticsActionType,
    val smokeScenarioId: String? = null,
)

internal enum class CompilerDiagnosticsExecutionTarget {
    CHECK_TOOLCHAIN_FILES,
    TEST_COMPILER_EXECUTABLE,
    TEST_COMPILE_ONLY,
    TEST_LINK_ONLY,
    TEST_FULL_COMPILE,
    CHECK_LIBRARY_DEPENDENCIES,
    TEST_LINKER_DIRECTLY,
    TEST_LINKER_FULL_LINK,
    TEST_CLANG_FIND_LINKER,
    TEST_CLANG_DIRECT_NO_OUTER_LINKER,
    TEST_CLANG_LINKER_RESOLUTION_MODES,
    LAUNCHER_CHAIN_COMPARE,
    SINGLE_FILE_PROJECT_SMOKE,
    CMAKE_PROJECT_SMOKE,
    SDL3_PROJECT_SMOKE,
    PROJECT_SMOKE_COMPARE_ALL,
}

internal data class CompilerDiagnosticsExecutionPlan(
    val target: CompilerDiagnosticsExecutionTarget,
    val type: CompilerDiagnosticsActionType,
)

internal data class LaunchMatrixFileSnapshot(
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long,
)

internal data class LaunchMatrixCaseReport(
    val caseName: String,
    val workingDirPath: String,
    val command: List<String>,
    val extraEnv: Map<String, String> = emptyMap(),
    val exitCode: Int,
    val durationMs: Long,
    val objectFile: LaunchMatrixFileSnapshot,
    val depFile: LaunchMatrixFileSnapshot,
    val stdout: String = "",
    val stderr: String = "",
)

internal data class ClangProgramLookupSpec(
    val title: String,
    val programName: String,
)

internal data class ClangProgramLookupReport(
    val title: String,
    val commandLine: String,
    val exitCode: Int,
    val output: String,
    val error: String = "",
)

internal data class ClangResolvedPathCheckReport(
    val title: String,
    val path: String,
    val exists: Boolean,
    val sizeBytes: Long? = null,
    val canExecute: Boolean? = null,
)

internal data class ClangLinkerResolutionCaseSpec(
    val caseName: String,
    val extraArgs: List<String>,
    val extraEnv: Map<String, String> = emptyMap(),
)

internal data class ClangLinkerResolutionCaseReport(
    val caseName: String,
    val command: List<String>,
    val extraEnv: Map<String, String> = emptyMap(),
    val exitCode: Int,
    val linkerPath: String? = null,
    val pathAliasHint: String? = null,
    val stdout: String = "",
    val stderr: String = "",
)

internal data class DiagnosticOutputFileReport(
    val label: String,
    val path: String,
    val exists: Boolean? = null,
    val sizeBytes: Long? = null,
)

internal data class DiagnosticCommandReport(
    val title: String? = null,
    val preCommandLines: List<String> = emptyList(),
    val missingPrerequisiteMessage: String? = null,
    val commandLabel: String = "命令:",
    val command: List<String> = emptyList(),
    val postCommandLines: List<String> = emptyList(),
    val separateSections: Boolean = false,
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val outputFiles: List<DiagnosticOutputFileReport> = emptyList(),
    val trailingLines: List<String> = emptyList(),
)

internal data class DiagnosticBinaryStatusReport(
    val name: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val canExecute: Boolean,
)

internal data class DiagnosticDirectoryEntryReport(
    val name: String,
    val sizeBytes: Long,
)

internal data class DiagnosticDirectoryReport(
    val label: String,
    val path: String,
    val exists: Boolean,
    val entriesTitle: String? = null,
    val entries: List<DiagnosticDirectoryEntryReport> = emptyList(),
)

internal data class DiagnosticNamedPathStatusReport(
    val label: String,
    val path: String,
    val exists: Boolean,
)

internal data class ToolchainFilesCheckReport(
    val title: String = "=== 工具链文件完整性检查 ===",
    val toolchainDir: DiagnosticDirectoryReport,
    val binDir: DiagnosticDirectoryReport,
    val criticalBinaries: List<DiagnosticBinaryStatusReport>,
    val runtimeDirectories: List<DiagnosticDirectoryReport> = emptyList(),
    val runtimeLibraries: List<DiagnosticNamedPathStatusReport> = emptyList(),
    val preferredBuiltinsPath: String? = null,
    val preferredUnwindPath: String? = null,
    val sysrootDir: DiagnosticDirectoryReport,
    val sysrootInstalled: Boolean,
)

internal data class LibraryDependencyCheckReport(
    val title: String = "=== 库文件依赖检查 ===",
    val directories: List<DiagnosticDirectoryReport>,
)

internal data class ClangDirectExecutionReport(
    val title: String,
    val missingPrerequisiteMessage: String? = null,
    val command: List<String> = emptyList(),
    val exitCode: Int? = null,
    val stdout: String = "",
    val stderr: String = "",
    val outputPath: String? = null,
    val outputExists: Boolean? = null,
    val outputSizeBytes: Long? = null,
)

internal data class CompilerDiagnosticsReportTextSet(
    val failurePrefix: String = "❌ 测试失败:",
    val workingDirectoryLabel: String = "工作目录",
    val commandLabel: String = "命令",
    val extraEnvironmentLabel: String = "额外环境变量",
    val exitCodeLabel: String = "退出码",
    val durationLabel: String = "耗时",
    val objectFileLabel: String = "目标对象",
    val dependencyFileLabel: String = "依赖文件",
    val stdoutLabel: String = "标准输出",
    val stderrLabel: String = "标准错误",
    val lookupTitleLdLld: String = "测试1: 查询 ld.lld 路径",
    val lookupTitleLld: String = "测试2: 查询 lld 路径",
    val resolvedPathCheckTitle: String = "测试3: 检查 clang++ 返回的 ld.lld 路径",
    val outputLabel: String = "输出",
    val errorLabel: String = "错误",
    val pathLabel: String = "路径",
    val existsLabel: String = "存在",
    val sizeLabel: String = "大小",
    val executableLabel: String = "可执行",
    val resolvedLinkerLabel: String = "解析到的 linker",
    val unresolvedLinkerText: String = "<未解析到>",
    val criticalBinariesLabel: String = "关键二进制文件",
    val runtimeLibrariesLabel: String = "Runtime 库文件",
    val preferredBuiltinsLabel: String = "优先 builtins",
    val preferredUnwindLabel: String = "优先 libunwind",
    val installedLabel: String = "已安装",
    val outputFileLabel: String = "输出文件",
    val toolchainFilesCheckTitle: String = "工具链文件完整性检查",
    val libraryDependencyCheckTitle: String = "库文件依赖检查",
    val compilerExecutableSectionTitle: String = "编译器可执行性测试",
    val compileOnlySectionTitle: String = "仅编译测试（生成 .o 文件）",
    val linkOnlySectionTitle: String = "仅链接测试（.o → 可执行文件）",
    val fullCompileSectionTitle: String = "完整编译+链接测试",
    val linkerDirectSectionTitle: String = "直接测试 lld 链接器",
    val linkerFullLinkSectionTitle: String = "直接测试 lld 完整链接参数",
    val clangFindLinkerSectionTitle: String = "测试 clang++ 查找 lld 路径",
    val clangDirectSectionTitle: String = "直启 clang++（不走外层 linker64）",
    val linkerResolutionSectionTitle: String = "链接器解析策略对照（不执行链接，仅打印驱动决策）",
    val launcherChainSectionTitle: String = "启动链对照测速/诊断（current / sh / no-exec）",
    val testCommandLabel: String = "测试命令",
    val forcedLinkerArgumentLabel: String = "强制链接器参数",
    val missingCompileObjectMessage: String = "❌ 错误: 目标文件不存在，请先运行「仅编译测试」",
    val criticalLinkFilesMissingMessage: String = "❌ 关键链接文件缺失，跳过执行",
    val directExecutionMissingObjectMessageFormat: String =
        "❌ 缺少 %1\$s，请先运行「3. 仅编译测试」",
    val toolchainDirectoryLabel: String = "工具链目录",
    val binDirectoryLabel: String = "Bin 目录",
    val runtimeLinuxBaseDirectoryLabel: String = "Clang Runtime Linux Base 目录",
    val runtimeLinuxArchDirectoryLabel: String = "Clang Runtime Linux Arch 目录",
    val runtimeTripleDirectoryLabelFormat: String = "Clang Runtime Triple 目录(api%1\$d)",
    val sysrootDirectoryLabel: String = "Sysroot 目录",
    val linuxBaseEntriesTitle: String = "Linux Base 目录内容:",
    val linuxArchEntriesTitle: String = "Linux Arch 子目录内容:",
    val tripleEntriesTitle: String = "Triple 子目录内容:",
    val directExecutionExplanation: String =
        "说明: 本测试会强制 direct exec clang++，不通过 NativeExecutableRunner 的 linker64 包装。",
    val directExecutionTestATitle: String = "测试A: 仅链接（直启 clang++）",
    val directExecutionTestBTitle: String = "测试B: 完整编译+链接（直启 clang++）",
    val directExecutionComparisonSuggestion: String =
        "对比建议: 将本测试的退出码/错误信息与第 4、5 项对照。",
    val resolutionModeExplanation: String = "说明: 这里使用 -###，不会真正执行链接。",
    val conclusionSuggestionsLabel: String = "结论建议",
    val baselineMatrixSectionTitle: String = "基线 compile-only 对照（缓存目录 + 绝对路径）",
    val cmakeLikeIntroTitle: String =
        "CMake-like try_compile 对照（API 28 + Scratch 目录 + 相对 depfile）",
    val simulatedTargetTripleLabel: String = "模拟目标 triple",
    val simulatedScratchDirectoryLabel: String = "模拟 Scratch 目录",
    val simulatedObjectRelativePathLabel: String = "模拟对象相对路径",
    val simulatedDepfileRelativePathLabel: String = "模拟 depfile 相对路径",
    val cmakeLikeMatrixSectionTitle: String = "CMake-like try_compile launch matrix",
    val pathAliasCheckSkipped: String = "路径别名检查: 非应用私有目录路径（跳过）",
    val pathAliasCheckEquivalent: String =
        "路径别名检查: /data/user/0 与 /data/data 等价（dev+ino 一致）",
    val pathAliasCheckDifferent: String =
        "路径别名检查: 当前路径与别名路径不等价（dev+ino 不一致）",
    val pathAliasCheckUnavailable: String =
        "路径别名检查: 无法完成 dev+ino 校验（路径可能不存在或无权限）",
    val linkerResolutionCase1Title: String = "Case1: baseline (-fuse-ld=lld)",
    val linkerResolutionCase2Title: String = "Case2: +COMPILER_PATH + -fuse-ld=lld",
    val linkerResolutionCase3Title: String = "Case3: +PATH前置bin + -fuse-ld=lld",
    val linkerResolutionCase4Title: String = "Case4: + -B<bin> + -fuse-ld=lld",
    val linkerResolutionCase5Title: String = "Case5: absolute -fuse-ld=<ld.lld>",
    val launcherChainCase1Title: String = "Case1 current-native-runner",
    val launcherChainCase2Title: String = "Case2 shell-shim-without-exec",
    val launcherChainCase3Title: String = "Case3 shell-shim-with-linker-preload",
    val launcherChainRecommendations: List<String> = listOf(
        "1. 若基线对照全通过，但 CMake-like current-native-runner 也失败，问题更像 CMake try_compile 形态本身，而不是 wrapper 独有问题。",
        "2. 若 CMake-like current-native-runner 成功，而 wrapper-direct 失败，说明主问题就在 wrapper 链，可考虑直接回退为 linker64 作为 CMake compiler launcher。",
        "3. 若只有 shell-shim 成功，而 current/wrapper 都失败，说明问题更像 exec/preload 与真实 clang 组合方式。",
        "4. 若所有 CMake-like case 都失败，说明问题更像 API28 flags、相对 depfile、Scratch 工作目录或 ROM 限制。"
    )
) {
    companion object {
        val Default = CompilerDiagnosticsReportTextSet()

        fun fromContext(context: android.content.Context): CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet(
            failurePrefix = context.getString(Strings.compiler_diagnostics_test_report_failure_prefix),
            workingDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_working_directory
            ),
            commandLabel = context.getString(Strings.compiler_diagnostics_test_report_command),
            extraEnvironmentLabel = context.getString(
                Strings.compiler_diagnostics_test_report_extra_environment
            ),
            exitCodeLabel = context.getString(Strings.compiler_diagnostics_test_report_exit_code),
            durationLabel = context.getString(Strings.compiler_diagnostics_test_report_duration),
            objectFileLabel = context.getString(Strings.compiler_diagnostics_test_report_object_file),
            dependencyFileLabel = context.getString(
                Strings.compiler_diagnostics_test_report_dependency_file
            ),
            stdoutLabel = context.getString(Strings.compiler_diagnostics_test_report_stdout),
            stderrLabel = context.getString(Strings.compiler_diagnostics_test_report_stderr),
            lookupTitleLdLld = context.getString(
                Strings.compiler_diagnostics_test_report_lookup_ld_lld_title
            ),
            lookupTitleLld = context.getString(
                Strings.compiler_diagnostics_test_report_lookup_lld_title
            ),
            resolvedPathCheckTitle = context.getString(
                Strings.compiler_diagnostics_test_report_resolved_path_check_title
            ),
            outputLabel = context.getString(Strings.compiler_diagnostics_test_report_output),
            errorLabel = context.getString(Strings.compiler_diagnostics_test_report_error),
            pathLabel = context.getString(Strings.compiler_diagnostics_test_report_path),
            existsLabel = context.getString(Strings.compiler_diagnostics_test_report_exists),
            sizeLabel = context.getString(Strings.compiler_diagnostics_test_report_size),
            executableLabel = context.getString(
                Strings.compiler_diagnostics_test_report_executable
            ),
            resolvedLinkerLabel = context.getString(
                Strings.compiler_diagnostics_test_report_resolved_linker
            ),
            unresolvedLinkerText = context.getString(
                Strings.compiler_diagnostics_test_report_unresolved_linker
            ),
            criticalBinariesLabel = context.getString(
                Strings.compiler_diagnostics_test_report_critical_binaries
            ),
            runtimeLibrariesLabel = context.getString(
                Strings.compiler_diagnostics_test_report_runtime_libraries
            ),
            preferredBuiltinsLabel = context.getString(
                Strings.compiler_diagnostics_test_report_preferred_builtins
            ),
            preferredUnwindLabel = context.getString(
                Strings.compiler_diagnostics_test_report_preferred_unwind
            ),
            installedLabel = context.getString(
                Strings.compiler_diagnostics_test_report_installed
            ),
            outputFileLabel = context.getString(
                Strings.compiler_diagnostics_test_report_output_file
            ),
            toolchainFilesCheckTitle = context.getString(
                Strings.compiler_diagnostics_test_check_toolchain_files
            ),
            libraryDependencyCheckTitle = context.getString(
                Strings.compiler_diagnostics_test_check_library_dependencies
            ),
            compilerExecutableSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_compiler_executable
            ),
            compileOnlySectionTitle = context.getString(
                Strings.compiler_diagnostics_test_compile_only
            ),
            linkOnlySectionTitle = context.getString(Strings.compiler_diagnostics_test_link_only),
            fullCompileSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_full_compile
            ),
            linkerDirectSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_linker_directly
            ),
            linkerFullLinkSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_linker_full_link
            ),
            clangFindLinkerSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_clang_find_linker
            ),
            clangDirectSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_clang_direct_no_outer_linker
            ),
            linkerResolutionSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_clang_linker_resolution_modes
            ),
            launcherChainSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_launcher_chain_compare
            ),
            testCommandLabel = context.getString(
                Strings.compiler_diagnostics_test_report_test_command
            ),
            forcedLinkerArgumentLabel = context.getString(
                Strings.compiler_diagnostics_test_report_forced_linker_argument
            ),
            missingCompileObjectMessage = context.getString(
                Strings.compiler_diagnostics_test_report_missing_compile_object
            ),
            criticalLinkFilesMissingMessage = context.getString(
                Strings.compiler_diagnostics_test_report_missing_critical_link_files
            ),
            directExecutionMissingObjectMessageFormat = context.getString(
                Strings.compiler_diagnostics_test_report_missing_direct_execution_object
            ),
            toolchainDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_toolchain_directory
            ),
            binDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_bin_directory
            ),
            runtimeLinuxBaseDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_runtime_linux_base_directory
            ),
            runtimeLinuxArchDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_runtime_linux_arch_directory
            ),
            runtimeTripleDirectoryLabelFormat = context.getString(
                Strings.compiler_diagnostics_test_report_runtime_triple_directory
            ),
            sysrootDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_sysroot_directory
            ),
            linuxBaseEntriesTitle = context.getString(
                Strings.compiler_diagnostics_test_report_linux_base_entries_title
            ),
            linuxArchEntriesTitle = context.getString(
                Strings.compiler_diagnostics_test_report_linux_arch_entries_title
            ),
            tripleEntriesTitle = context.getString(
                Strings.compiler_diagnostics_test_report_triple_entries_title
            ),
            directExecutionExplanation = context.getString(
                Strings.compiler_diagnostics_test_report_direct_execution_explanation
            ),
            directExecutionTestATitle = context.getString(
                Strings.compiler_diagnostics_test_report_direct_execution_test_a_title
            ),
            directExecutionTestBTitle = context.getString(
                Strings.compiler_diagnostics_test_report_direct_execution_test_b_title
            ),
            directExecutionComparisonSuggestion = context.getString(
                Strings.compiler_diagnostics_test_report_direct_execution_comparison_suggestion
            ),
            resolutionModeExplanation = context.getString(
                Strings.compiler_diagnostics_test_report_resolution_mode_explanation
            ),
            conclusionSuggestionsLabel = context.getString(
                Strings.compiler_diagnostics_test_report_conclusion_suggestions
            ),
            baselineMatrixSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_report_baseline_matrix_section_title
            ),
            cmakeLikeIntroTitle = context.getString(
                Strings.compiler_diagnostics_test_report_cmake_like_intro_title
            ),
            simulatedTargetTripleLabel = context.getString(
                Strings.compiler_diagnostics_test_report_simulated_target_triple
            ),
            simulatedScratchDirectoryLabel = context.getString(
                Strings.compiler_diagnostics_test_report_simulated_scratch_directory
            ),
            simulatedObjectRelativePathLabel = context.getString(
                Strings.compiler_diagnostics_test_report_simulated_object_relative_path
            ),
            simulatedDepfileRelativePathLabel = context.getString(
                Strings.compiler_diagnostics_test_report_simulated_depfile_relative_path
            ),
            cmakeLikeMatrixSectionTitle = context.getString(
                Strings.compiler_diagnostics_test_report_cmake_like_matrix_section_title
            ),
            pathAliasCheckSkipped = context.getString(
                Strings.compiler_diagnostics_test_report_path_alias_skipped
            ),
            pathAliasCheckEquivalent = context.getString(
                Strings.compiler_diagnostics_test_report_path_alias_equivalent
            ),
            pathAliasCheckDifferent = context.getString(
                Strings.compiler_diagnostics_test_report_path_alias_different
            ),
            pathAliasCheckUnavailable = context.getString(
                Strings.compiler_diagnostics_test_report_path_alias_unavailable
            ),
            linkerResolutionCase1Title = context.getString(
                Strings.compiler_diagnostics_test_report_linker_resolution_case_1
            ),
            linkerResolutionCase2Title = context.getString(
                Strings.compiler_diagnostics_test_report_linker_resolution_case_2
            ),
            linkerResolutionCase3Title = context.getString(
                Strings.compiler_diagnostics_test_report_linker_resolution_case_3
            ),
            linkerResolutionCase4Title = context.getString(
                Strings.compiler_diagnostics_test_report_linker_resolution_case_4
            ),
            linkerResolutionCase5Title = context.getString(
                Strings.compiler_diagnostics_test_report_linker_resolution_case_5
            ),
            launcherChainCase1Title = context.getString(
                Strings.compiler_diagnostics_test_report_launcher_chain_case_1
            ),
            launcherChainCase2Title = context.getString(
                Strings.compiler_diagnostics_test_report_launcher_chain_case_2
            ),
            launcherChainCase3Title = context.getString(
                Strings.compiler_diagnostics_test_report_launcher_chain_case_3
            ),
            launcherChainRecommendations = listOf(
                context.getString(
                    Strings.compiler_diagnostics_test_launcher_chain_recommendation_1
                ),
                context.getString(
                    Strings.compiler_diagnostics_test_launcher_chain_recommendation_2
                ),
                context.getString(
                    Strings.compiler_diagnostics_test_launcher_chain_recommendation_3
                ),
                context.getString(
                    Strings.compiler_diagnostics_test_launcher_chain_recommendation_4
                )
            )
        )
    }

    fun sectionTitle(title: String): String = "=== $title ==="

    fun header(label: String): String = "$label:"

    fun labeledValue(label: String, value: Any?): String = "$label: $value"

    fun durationLine(durationMs: Long): String = "$durationLabel: ${durationMs}ms"

    fun testCommandLine(command: String): String = labeledValue(testCommandLabel, command)

    fun launchMatrixFileLine(label: String, snapshot: LaunchMatrixFileSnapshot): String = "$label: ${snapshot.path} (exists=${snapshot.exists}, size=${snapshot.sizeBytes})"

    fun forcedLinkerArgumentLine(argument: String): String = labeledValue(forcedLinkerArgumentLabel, argument)

    fun sizeLine(sizeBytes: Long): String = "$sizeLabel: $sizeBytes bytes"

    fun preferenceLine(label: String, value: String): String = "  $label: $value"

    fun runtimeTripleDirectoryLabel(apiLevel: Int): String = String.format(Locale.ROOT, runtimeTripleDirectoryLabelFormat, apiLevel)

    fun directExecutionMissingObjectMessage(fileName: String): String = String.format(Locale.ROOT, directExecutionMissingObjectMessageFormat, fileName)
}

internal object CompilerDiagnosticsTestCatalog {
    val actions: List<CompilerDiagnosticsActionSpec> = listOf(
        CompilerDiagnosticsActionSpec(
            id = "check_toolchain_files",
            key = CompilerDiagnosticsActionKey.CHECK_TOOLCHAIN_FILES,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_check_toolchain_files,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_compiler_executable",
            key = CompilerDiagnosticsActionKey.TEST_COMPILER_EXECUTABLE,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_compiler_executable,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_compile_only",
            key = CompilerDiagnosticsActionKey.TEST_COMPILE_ONLY,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_compile_only,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_link_only",
            key = CompilerDiagnosticsActionKey.TEST_LINK_ONLY,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_link_only,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_full_compile",
            key = CompilerDiagnosticsActionKey.TEST_FULL_COMPILE,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_full_compile,
        ),
        CompilerDiagnosticsActionSpec(
            id = "check_library_dependencies",
            key = CompilerDiagnosticsActionKey.CHECK_LIBRARY_DEPENDENCIES,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_check_library_dependencies,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_linker_directly",
            key = CompilerDiagnosticsActionKey.TEST_LINKER_DIRECTLY,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_linker_directly,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_linker_full_link",
            key = CompilerDiagnosticsActionKey.TEST_LINKER_FULL_LINK,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_linker_full_link,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_clang_find_linker",
            key = CompilerDiagnosticsActionKey.TEST_CLANG_FIND_LINKER,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_clang_find_linker,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_clang_direct_no_outer_linker",
            key = CompilerDiagnosticsActionKey.TEST_CLANG_DIRECT_NO_OUTER_LINKER,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_clang_direct_no_outer_linker,
        ),
        CompilerDiagnosticsActionSpec(
            id = "test_clang_linker_resolution_modes",
            key = CompilerDiagnosticsActionKey.TEST_CLANG_LINKER_RESOLUTION_MODES,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_clang_linker_resolution_modes,
        ),
        CompilerDiagnosticsActionSpec(
            id = "single_file_project_smoke",
            key = CompilerDiagnosticsActionKey.SINGLE_FILE_PROJECT_SMOKE,
            type = CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE,
            titleRes = Strings.compiler_diagnostics_test_single_file_project_smoke,
        ),
        CompilerDiagnosticsActionSpec(
            id = "cmake_project_smoke",
            key = CompilerDiagnosticsActionKey.CMAKE_PROJECT_SMOKE,
            type = CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE,
            titleRes = Strings.compiler_diagnostics_test_cmake_project_smoke,
        ),
        CompilerDiagnosticsActionSpec(
            id = "sdl3_project_smoke",
            key = CompilerDiagnosticsActionKey.SDL3_PROJECT_SMOKE,
            type = CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE,
            titleRes = Strings.compiler_diagnostics_test_sdl3_project_smoke,
        ),
        CompilerDiagnosticsActionSpec(
            id = "project_smoke_compare_all",
            key = CompilerDiagnosticsActionKey.PROJECT_SMOKE_COMPARE_ALL,
            type = CompilerDiagnosticsActionType.PROJECT_SMOKE_COMPARE_ALL,
            titleRes = Strings.compiler_diagnostics_test_project_smoke_compare_all,
        ),
        CompilerDiagnosticsActionSpec(
            id = "launcher_chain_compare",
            key = CompilerDiagnosticsActionKey.LAUNCHER_CHAIN_COMPARE,
            type = CompilerDiagnosticsActionType.STANDARD,
            titleRes = Strings.compiler_diagnostics_test_launcher_chain_compare,
        ),
    )
}

internal object CompilerDiagnosticsTestSupport {
    fun actionSpecs(): List<CompilerDiagnosticsActionSpec> = CompilerDiagnosticsTestCatalog.actions

    fun requireActionSpec(actionId: String): CompilerDiagnosticsActionSpec = actionSpecs().firstOrNull { it.id == actionId }
        ?: error("Unknown compiler diagnostics action: $actionId")

    fun resolveLaunchPlan(actionId: String): CompilerDiagnosticsActionLaunchPlan = requireActionSpec(actionId).toLaunchPlan()

    fun resolveExecutionPlan(actionId: String): CompilerDiagnosticsExecutionPlan = resolveLaunchPlan(actionId).toExecutionPlan()

    fun resolveActionTitle(
        action: CompilerDiagnosticsActionSpec,
        resolveString: (Int) -> String
    ): String = resolveCompilerDiagnosticsActionTitle(action, resolveString)

    fun extractCompileOnlyFlags(flags: List<String>): List<String> = extractCompileOnlyFlagsInternal(flags)

    fun formatDuration(durationMs: Long): String = formatDurationValue(durationMs)

    fun formatTotalDuration(
        result: ProjectSmokeRunResult,
        allResults: List<ProjectSmokeRunResult>,
        fastestLabel: String,
        slowestLabel: String
    ): String = formatTotalDurationValueText(
        durationMs = result.totalDurationMs,
        allDurations = allResults.mapNotNull { it.totalDurationMs },
        fastestLabel = fastestLabel,
        slowestLabel = slowestLabel
    )

    fun formatStageTiming(
        timing: ProjectSmokeStageTiming,
        stageTimings: List<ProjectSmokeStageTiming>,
        bottleneckLabel: String
    ): String = formatStageTimingValueText(
        durationMs = timing.durationMs,
        stageDurations = stageTimings.map { it.durationMs },
        bottleneckLabel = bottleneckLabel
    )

    fun smokeScenarioTitle(
        result: ProjectSmokeRunResult,
        singleFileTitle: String,
        cmakeTitle: String,
        sdl3Title: String
    ): String = smokeScenarioTitleText(
        scenarioId = result.scenarioId,
        displayName = result.displayName,
        singleFileTitle = singleFileTitle,
        cmakeTitle = cmakeTitle,
        sdl3Title = sdl3Title
    )

    fun compareVersionNames(left: String, right: String): Int = compareVersionName(left, right)

    fun findVersionedClangBinaryNames(binDir: File): List<String> = findVersionedClangBinaries(binDir)

    fun resolveRuntimeLayout(
        installDir: File,
        arch: AndroidSysrootManager.Companion.Arch,
        apiLevel: Int = DIAGNOSTIC_TARGET_API_LEVEL,
    ): ClangRuntimeLayout = resolveClangRuntimeLayout(installDir, arch, apiLevel)

    fun prependPathSegment(path: String, currentPath: String?): String = prependPath(path, currentPath)

    fun extractLinkerPath(output: String): String? = extractLinkerPathFromDriverOutput(output)

    fun buildPathAliasHintFromAliasStatus(
        path: String,
        packageName: String,
        aliasEquivalent: Boolean?,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): String = buildPathAliasHintText(path, packageName, aliasEquivalent, textSet)

    fun buildRunFailureOutput(
        message: String?,
        stackTrace: String,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): String = formatRunTestFailureOutput(message, stackTrace, textSet)

    suspend fun runSafely(block: suspend () -> String): String = runTest(block)

    fun buildLaunchMatrixCaseLines(
        report: LaunchMatrixCaseReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildLaunchMatrixCaseLinesInternal(report, textSet)

    fun clangProgramLookupSpecs(
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<ClangProgramLookupSpec> = clangProgramLookupSpecsInternal(textSet)

    fun buildClangProgramLookupLines(
        report: ClangProgramLookupReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildClangProgramLookupLinesInternal(report, textSet)

    fun buildClangResolvedPathCheckLines(
        report: ClangResolvedPathCheckReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildClangResolvedPathCheckLinesInternal(report, textSet)

    fun clangLinkerResolutionCaseSpecs(
        binDirPath: String,
        currentPath: String?,
        forceLldArg: String,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<ClangLinkerResolutionCaseSpec> = buildClangLinkerResolutionCaseSpecsInternal(
        binDirPath = binDirPath,
        currentPath = currentPath,
        forceLldArg = forceLldArg,
        textSet = textSet
    )

    fun buildClangLinkerResolutionCaseLines(
        report: ClangLinkerResolutionCaseReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildClangLinkerResolutionCaseLinesInternal(report, textSet)

    fun buildDiagnosticCommandLines(
        report: DiagnosticCommandReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildDiagnosticCommandLinesInternal(report, textSet)

    fun buildToolchainFilesCheckLines(
        report: ToolchainFilesCheckReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildToolchainFilesCheckLinesInternal(report, textSet)

    fun buildLibraryDependencyCheckLines(
        report: LibraryDependencyCheckReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildLibraryDependencyCheckLinesInternal(report, textSet)

    fun buildClangDirectExecutionLines(
        report: ClangDirectExecutionReport,
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = buildClangDirectExecutionLinesInternal(report, textSet)

    fun launcherChainRecommendationLines(
        textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
    ): List<String> = launcherChainRecommendationLinesInternal(textSet)
}

private fun CompilerDiagnosticsActionSpec.toLaunchPlan(): CompilerDiagnosticsActionLaunchPlan = when (key) {
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
    CompilerDiagnosticsActionKey.LAUNCHER_CHAIN_COMPARE -> buildLaunchPlan(
        expectedType = CompilerDiagnosticsActionType.STANDARD
    )

    CompilerDiagnosticsActionKey.SINGLE_FILE_PROJECT_SMOKE -> buildLaunchPlan(
        expectedType = CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE,
        smokeScenarioId = SINGLE_FILE_PROJECT_SMOKE_RESULT_ID
    )

    CompilerDiagnosticsActionKey.CMAKE_PROJECT_SMOKE -> buildLaunchPlan(
        expectedType = CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE,
        smokeScenarioId = CMAKE_PROJECT_SMOKE_RESULT_ID
    )

    CompilerDiagnosticsActionKey.SDL3_PROJECT_SMOKE -> buildLaunchPlan(
        expectedType = CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE,
        smokeScenarioId = SDL3_PROJECT_SMOKE_RESULT_ID
    )

    CompilerDiagnosticsActionKey.PROJECT_SMOKE_COMPARE_ALL -> buildLaunchPlan(
        expectedType = CompilerDiagnosticsActionType.PROJECT_SMOKE_COMPARE_ALL
    )
}

private fun CompilerDiagnosticsActionSpec.buildLaunchPlan(
    expectedType: CompilerDiagnosticsActionType,
    smokeScenarioId: String? = null
): CompilerDiagnosticsActionLaunchPlan {
    check(type == expectedType) {
        "Action type mismatch for $id: expected=$expectedType actual=$type"
    }
    if (expectedType == CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE) {
        check(!smokeScenarioId.isNullOrBlank()) {
            "Smoke scenario id is required for action: $id"
        }
    } else {
        check(smokeScenarioId == null) {
            "Smoke scenario id must be null for action: $id"
        }
    }
    return CompilerDiagnosticsActionLaunchPlan(
        key = key,
        type = expectedType,
        smokeScenarioId = smokeScenarioId
    )
}

private fun CompilerDiagnosticsActionLaunchPlan.toExecutionPlan(): CompilerDiagnosticsExecutionPlan = when (type) {
    CompilerDiagnosticsActionType.STANDARD -> CompilerDiagnosticsExecutionPlan(
        target = when (key) {
            CompilerDiagnosticsActionKey.CHECK_TOOLCHAIN_FILES ->
                CompilerDiagnosticsExecutionTarget.CHECK_TOOLCHAIN_FILES
            CompilerDiagnosticsActionKey.TEST_COMPILER_EXECUTABLE ->
                CompilerDiagnosticsExecutionTarget.TEST_COMPILER_EXECUTABLE
            CompilerDiagnosticsActionKey.TEST_COMPILE_ONLY ->
                CompilerDiagnosticsExecutionTarget.TEST_COMPILE_ONLY
            CompilerDiagnosticsActionKey.TEST_LINK_ONLY ->
                CompilerDiagnosticsExecutionTarget.TEST_LINK_ONLY
            CompilerDiagnosticsActionKey.TEST_FULL_COMPILE ->
                CompilerDiagnosticsExecutionTarget.TEST_FULL_COMPILE
            CompilerDiagnosticsActionKey.CHECK_LIBRARY_DEPENDENCIES ->
                CompilerDiagnosticsExecutionTarget.CHECK_LIBRARY_DEPENDENCIES
            CompilerDiagnosticsActionKey.TEST_LINKER_DIRECTLY ->
                CompilerDiagnosticsExecutionTarget.TEST_LINKER_DIRECTLY
            CompilerDiagnosticsActionKey.TEST_LINKER_FULL_LINK ->
                CompilerDiagnosticsExecutionTarget.TEST_LINKER_FULL_LINK
            CompilerDiagnosticsActionKey.TEST_CLANG_FIND_LINKER ->
                CompilerDiagnosticsExecutionTarget.TEST_CLANG_FIND_LINKER
            CompilerDiagnosticsActionKey.TEST_CLANG_DIRECT_NO_OUTER_LINKER ->
                CompilerDiagnosticsExecutionTarget.TEST_CLANG_DIRECT_NO_OUTER_LINKER
            CompilerDiagnosticsActionKey.TEST_CLANG_LINKER_RESOLUTION_MODES ->
                CompilerDiagnosticsExecutionTarget.TEST_CLANG_LINKER_RESOLUTION_MODES
            CompilerDiagnosticsActionKey.LAUNCHER_CHAIN_COMPARE ->
                CompilerDiagnosticsExecutionTarget.LAUNCHER_CHAIN_COMPARE
            CompilerDiagnosticsActionKey.SINGLE_FILE_PROJECT_SMOKE,
            CompilerDiagnosticsActionKey.CMAKE_PROJECT_SMOKE,
            CompilerDiagnosticsActionKey.SDL3_PROJECT_SMOKE,
            CompilerDiagnosticsActionKey.PROJECT_SMOKE_COMPARE_ALL -> {
                error("Unsupported standard launch key: $key")
            }
        },
        type = type
    )

    CompilerDiagnosticsActionType.PROJECT_SMOKE_SINGLE -> CompilerDiagnosticsExecutionPlan(
        target = when (smokeScenarioId) {
            SINGLE_FILE_PROJECT_SMOKE_RESULT_ID ->
                CompilerDiagnosticsExecutionTarget.SINGLE_FILE_PROJECT_SMOKE
            CMAKE_PROJECT_SMOKE_RESULT_ID ->
                CompilerDiagnosticsExecutionTarget.CMAKE_PROJECT_SMOKE
            SDL3_PROJECT_SMOKE_RESULT_ID ->
                CompilerDiagnosticsExecutionTarget.SDL3_PROJECT_SMOKE
            else -> error("Unsupported smoke scenario id: $smokeScenarioId")
        },
        type = type
    )

    CompilerDiagnosticsActionType.PROJECT_SMOKE_COMPARE_ALL -> {
        check(smokeScenarioId == null) {
            "Smoke scenario id must be null for compare-all action: $smokeScenarioId"
        }
        CompilerDiagnosticsExecutionPlan(
            target = CompilerDiagnosticsExecutionTarget.PROJECT_SMOKE_COMPARE_ALL,
            type = type
        )
    }
}

private fun extractCompileOnlyFlagsInternal(flags: List<String>): List<String> = flags.filterNot { it.startsWith("-L") }

/**
 * 编译器诊断测试页面（开发者选项）
 *
 * 用于逐步测试编译链路的各个阶段，快速定位问题：
 * 1. 工具链文件完整性检查
 * 2. 编译器可执行性测试
 * 3. 仅编译测试（生成 .o 文件）
 * 4. 仅链接测试（链接 .o 生成可执行文件）
 * 5. 完整编译+链接测试
 * 6. 库文件依赖检查
 * 7. lld 版本可执行性测试
 * 8. 直接调用 lld 完整链接参数测试
 * 9. clang++ 查找 lld 路径
 * 10. 直启 clang++（不走外层 linker64）测试
 * 11. 链接器解析策略对照测试（ENV/-B/-fuse-ld）
 * 12. Single-file 真实项目闭环测试（compile_commands + clangd）
 * 13. CMake 真实项目闭环测试（编译 + clangd）
 * 14. SDL3 CMake 真实项目闭环测试（编译 + clangd）
 * 15. 三种真实项目闭环对照测试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilerDiagnosticsTestScreen(onNavigateBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val navigateBack = onNavigateBack ?: {
        (context as? ComponentActivity)?.finish()
        Unit
    }
    val coroutineScope = rememberCoroutineScope()
    val actionSpecs = remember { CompilerDiagnosticsTestCatalog.actions }

    var testOutput by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var smokeResults by remember { mutableStateOf(emptyList<ProjectSmokeRunResult>()) }

    suspend fun runSmokeAction(action: suspend (DiagnosticToolchainSelection) -> List<ProjectSmokeRunResult>) {
        isRunning = true
        try {
            smokeResults = emptyList()
            val selection = resolveDiagnosticToolchainSelection(context)
            val results = action(selection)
            smokeResults = results
            results.forEach { result ->
                Timber.tag("CompilerDiagnostics").i(
                    "Smoke result: id=%s success=%s failedStage=%s failureReason=%s checks=%d/%d",
                    result.scenarioId,
                    result.success,
                    result.failedStage ?: "-",
                    result.failureReason ?: "-",
                    result.passedChecksCount,
                    result.totalChecksCount
                )
                result.failureDetail
                    ?.takeIf { it.isNotBlank() }
                    ?.let { detail ->
                        Timber.tag("CompilerDiagnostics").i(
                            "Smoke failure detail: id=%s detail=%s",
                            result.scenarioId,
                            clipSmokeFailureDetailForLog(detail)
                        )
                    }
            }
            testOutput = results.joinToString(separator = "\n\n") { it.reportText }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            smokeResults = emptyList()
            testOutput = formatRunTestFailureOutput(
                message = throwable.message,
                stackTrace = throwable.stackTraceToString(),
                textSet = CompilerDiagnosticsReportTextSet.fromContext(context),
            )
        } finally {
            isRunning = false
        }
    }

    fun launchStandardAction(action: suspend (DiagnosticToolchainSelection) -> String) {
        coroutineScope.launch {
            isRunning = true
            try {
                smokeResults = emptyList()
                testOutput = runTest {
                    val selection = resolveDiagnosticToolchainSelection(context)
                    action(selection)
                }
            } finally {
                isRunning = false
            }
        }
    }

    fun launchSmokeAction(action: suspend (DiagnosticToolchainSelection) -> List<ProjectSmokeRunResult>) {
        coroutineScope.launch {
            runSmokeAction(action)
        }
    }

    fun launchAction(actionId: String) {
        when (CompilerDiagnosticsTestSupport.resolveExecutionPlan(actionId).target) {
            CompilerDiagnosticsExecutionTarget.CHECK_TOOLCHAIN_FILES -> {
                launchStandardAction { selection -> checkToolchainFiles(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_COMPILER_EXECUTABLE -> {
                launchStandardAction { selection -> testCompilerExecutable(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_COMPILE_ONLY -> {
                launchStandardAction { selection -> testCompileOnly(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_LINK_ONLY -> {
                launchStandardAction { selection -> testLinkOnly(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_FULL_COMPILE -> {
                launchStandardAction { selection -> testFullCompile(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.CHECK_LIBRARY_DEPENDENCIES -> {
                launchStandardAction { selection -> checkLibraryDependencies(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_LINKER_DIRECTLY -> {
                launchStandardAction { selection -> testLinkerDirectly(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_LINKER_FULL_LINK -> {
                launchStandardAction { selection -> testLinkerFullLink(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_CLANG_FIND_LINKER -> {
                launchStandardAction { selection -> testClangFindLinker(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_CLANG_DIRECT_NO_OUTER_LINKER -> {
                launchStandardAction { selection -> testClangDirectNoOuterLinker(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.TEST_CLANG_LINKER_RESOLUTION_MODES -> {
                launchStandardAction { selection -> testClangLinkerResolutionModes(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.LAUNCHER_CHAIN_COMPARE -> {
                launchStandardAction { selection -> testLauncherChainComparison(context, selection) }
            }
            CompilerDiagnosticsExecutionTarget.SINGLE_FILE_PROJECT_SMOKE -> {
                launchSmokeAction { selection ->
                    listOf(
                        runSingleFileProjectSmokeTest(
                            context = context,
                            toolchainId = selection.toolchainId,
                            toolchainLabel = selection.displayName(context),
                            useRecommendedTinaExec = selection.useRecommendedTinaExec,
                        )
                    )
                }
            }
            CompilerDiagnosticsExecutionTarget.CMAKE_PROJECT_SMOKE -> {
                launchSmokeAction { selection ->
                    listOf(
                        runBuiltInCmakeProjectSmokeTest(
                            context = context,
                            toolchainId = selection.toolchainId,
                            toolchainLabel = selection.displayName(context),
                            useRecommendedTinaExec = selection.useRecommendedTinaExec,
                        )
                    )
                }
            }
            CompilerDiagnosticsExecutionTarget.SDL3_PROJECT_SMOKE -> {
                launchSmokeAction { selection ->
                    listOf(
                        runSdl3CmakeProjectSmokeTest(
                            context = context,
                            toolchainId = selection.toolchainId,
                            toolchainLabel = selection.displayName(context),
                            useRecommendedTinaExec = selection.useRecommendedTinaExec,
                        )
                    )
                }
            }
            CompilerDiagnosticsExecutionTarget.PROJECT_SMOKE_COMPARE_ALL -> {
                launchSmokeAction { selection ->
                    runAllProjectSmokeTests(
                        context = context,
                        toolchainId = selection.toolchainId,
                        toolchainLabel = selection.displayName(context),
                        useRecommendedTinaExec = selection.useRecommendedTinaExec,
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Strings.dev_options_compiler_diagnostics_test)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                Strings.compiler_diagnostics_test_back_content_description
                            )
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 顶部标题区域（不滚动）
            Text(
                text = stringResource(Strings.compiler_diagnostics_test_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(Strings.dev_options_compiler_diagnostics_toolchain_variant_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = stringResource(Strings.compiler_diagnostics_toolchain_variant_patched),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(Strings.compiler_diagnostics_toolchain_variant_patched_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 按钮区域（可滚动）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                actionSpecs.forEach { action ->
                    TestButton(
                        text = compilerDiagnosticsActionTitle(action),
                        enabled = !isRunning,
                        onClick = { launchAction(action.id) }
                    )
                }

                if (smokeResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProjectSmokeComparisonSection(smokeResults = smokeResults)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 输出区域（固定高度，独立滚动）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(Strings.compiler_diagnostics_test_output_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = testOutput.ifBlank {
                            stringResource(Strings.compiler_diagnostics_test_output_placeholder)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun compilerDiagnosticsActionTitle(action: CompilerDiagnosticsActionSpec): String = stringResource(action.titleRes)

private fun resolveCompilerDiagnosticsActionTitle(
    action: CompilerDiagnosticsActionSpec,
    resolveString: (Int) -> String
): String = resolveString(action.titleRes)

@Composable
private fun TestButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text)
    }
}

@Composable
private fun ProjectSmokeComparisonSection(smokeResults: List<ProjectSmokeRunResult>) {
    Text(
        text = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_title),
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(modifier = Modifier.height(8.dp))
    smokeResults.forEach { result ->
        ProjectSmokeResultCard(
            result = result,
            allResults = smokeResults
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ProjectSmokeResultCard(
    result: ProjectSmokeRunResult,
    allResults: List<ProjectSmokeRunResult>
) {
    val containerColor = if (result.success) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (result.success) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = smokeScenarioTitle(result),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(
                            if (result.success) {
                                Strings.compiler_diagnostics_test_project_smoke_compare_status_passed
                            } else {
                                Strings.compiler_diagnostics_test_project_smoke_compare_status_failed
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_checks),
                value = "${result.passedChecksCount}/${result.totalChecksCount}"
            )
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_total_duration),
                value = formatTotalDurationValue(result, allResults)
            )
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_clangd_mode),
                value = result.clangdMode ?: "-"
            )
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_diagnostics_total),
                value = result.diagnosticsTotalCount?.toString() ?: "-"
            )
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_diagnostics_error),
                value = result.diagnosticsErrorCount?.toString() ?: "-"
            )
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_diagnostics_warning),
                value = result.diagnosticsWarningCount?.toString() ?: "-"
            )
            SmokeMetricLine(
                label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_completion_count),
                value = result.completionCandidateCount?.toString() ?: "-"
            )
            if (result.matchedCompletionLabels.isNotEmpty()) {
                SmokeMetricLine(
                    label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_hits),
                    value = result.matchedCompletionLabels.joinToString()
                )
            }
            if (result.stageTimings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_stage_timings),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                result.stageTimings.forEach { timing ->
                    SmokeMetricLine(
                        label = timing.label,
                        value = formatStageTimingValue(
                            timing = timing,
                            stageTimings = result.stageTimings
                        )
                    )
                }
            }
            if (!result.success) {
                SmokeMetricLine(
                    label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_failed_stage),
                    value = result.failedStage ?: "-"
                )
                SmokeMetricLine(
                    label = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_failure_reason),
                    value = result.failureReason ?: "-"
                )
            }
        }
    }
}

@Composable
private fun SmokeMetricLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun formatTotalDurationValue(
    result: ProjectSmokeRunResult,
    allResults: List<ProjectSmokeRunResult>
): String = formatTotalDurationValueText(
    durationMs = result.totalDurationMs,
    allDurations = allResults.mapNotNull { it.totalDurationMs },
    fastestLabel = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_fastest),
    slowestLabel = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_slowest)
)

@Composable
private fun formatStageTimingValue(
    timing: ProjectSmokeStageTiming,
    stageTimings: List<ProjectSmokeStageTiming>
): String = formatStageTimingValueText(
    durationMs = timing.durationMs,
    stageDurations = stageTimings.map { it.durationMs },
    bottleneckLabel = stringResource(Strings.compiler_diagnostics_test_project_smoke_compare_bottleneck)
)

private fun formatDurationValue(durationMs: Long): String {
    if (durationMs < 1_000L) return "$durationMs ms"
    val seconds = durationMs / 1_000L
    val hundredths = (durationMs % 1_000L) / 10L
    return "$seconds.${hundredths.toString().padStart(2, '0')} s"
}

private fun clipSmokeFailureDetailForLog(text: String, maxChars: Int = 2_400): String = if (text.length <= maxChars) {
    text
} else {
    text.take(maxChars) + "...(truncated)"
}

@Composable
private fun smokeScenarioTitle(result: ProjectSmokeRunResult): String = smokeScenarioTitleText(
    scenarioId = result.scenarioId,
    displayName = result.displayName,
    singleFileTitle = stringResource(Strings.compiler_diagnostics_test_single_file_project_smoke),
    cmakeTitle = stringResource(Strings.compiler_diagnostics_test_cmake_project_smoke),
    sdl3Title = stringResource(Strings.compiler_diagnostics_test_sdl3_project_smoke)
)

private fun formatTotalDurationValueText(
    durationMs: Long?,
    allDurations: List<Long>,
    fastestLabel: String,
    slowestLabel: String
): String {
    val value = durationMs ?: return "-"
    val base = formatDurationValue(value)
    if (allDurations.size < 2) return base
    val fastest = allDurations.minOrNull() ?: return base
    val slowest = allDurations.maxOrNull() ?: return base
    if (fastest == slowest) return base
    return when (value) {
        fastest -> "$base ($fastestLabel)"
        slowest -> "$base ($slowestLabel, +${formatDurationValue(value - fastest)})"
        else -> "$base (+${formatDurationValue(value - fastest)})"
    }
}

private fun formatStageTimingValueText(
    durationMs: Long,
    stageDurations: List<Long>,
    bottleneckLabel: String
): String {
    val base = formatDurationValue(durationMs)
    val longest = stageDurations.maxOrNull() ?: return base
    if (stageDurations.size < 2 || durationMs != longest) return base
    return "$base ($bottleneckLabel)"
}

private fun smokeScenarioTitleText(
    scenarioId: String,
    displayName: String,
    singleFileTitle: String,
    cmakeTitle: String,
    sdl3Title: String
): String {
    val title = when (scenarioId) {
        "single_file_std_headers" -> singleFileTitle
        "cmake_std_headers" -> cmakeTitle
        "sdl3_std_headers" -> sdl3Title
        else -> displayName
    }
    return title.substringAfter(". ", title)
}

private suspend fun runTest(block: suspend () -> String): String = withContext(Dispatchers.IO) {
    try {
        block()
    } catch (e: Exception) {
        formatRunTestFailureOutput(
            message = e.message,
            stackTrace = e.stackTraceToString()
        )
    }
}

private fun formatRunTestFailureOutput(
    message: String?,
    stackTrace: String,
    textSet: CompilerDiagnosticsReportTextSet = CompilerDiagnosticsReportTextSet.Default
): String = "${textSet.failurePrefix}\n$message\n\n$stackTrace"

private fun buildLaunchMatrixCaseLinesInternal(
    report: LaunchMatrixCaseReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = buildList {
    add("--- ${report.caseName} ---")
    add(textSet.labeledValue(textSet.workingDirectoryLabel, report.workingDirPath))
    add(textSet.header(textSet.commandLabel))
    add(report.command.joinToString(" "))
    if (report.extraEnv.isNotEmpty()) {
        add(textSet.header(textSet.extraEnvironmentLabel))
        report.extraEnv.forEach { (key, value) ->
            add("  $key=$value")
        }
    }
    add(textSet.labeledValue(textSet.exitCodeLabel, report.exitCode))
    add(textSet.durationLine(report.durationMs))
    add(textSet.launchMatrixFileLine(textSet.objectFileLabel, report.objectFile))
    add(textSet.launchMatrixFileLine(textSet.dependencyFileLabel, report.depFile))
    if (report.stdout.isNotBlank()) {
        add(textSet.header(textSet.stdoutLabel))
        add(report.stdout)
    }
    if (report.stderr.isNotBlank()) {
        add(textSet.header(textSet.stderrLabel))
        add(report.stderr)
    }
    add("")
}

private fun launcherChainRecommendationLinesInternal(
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = textSet.launcherChainRecommendations

private fun clangProgramLookupSpecsInternal(
    textSet: CompilerDiagnosticsReportTextSet
): List<ClangProgramLookupSpec> = listOf(
    ClangProgramLookupSpec(
        title = textSet.lookupTitleLdLld,
        programName = "ld.lld"
    ),
    ClangProgramLookupSpec(
        title = textSet.lookupTitleLld,
        programName = "lld"
    )
)

private fun buildClangProgramLookupLinesInternal(
    report: ClangProgramLookupReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = buildList {
    add(report.title)
    add(textSet.labeledValue(textSet.commandLabel, report.commandLine))
    add("")
    add(textSet.labeledValue(textSet.exitCodeLabel, report.exitCode))
    add(textSet.labeledValue(textSet.outputLabel, report.output.trim()))
    if (report.error.isNotBlank()) {
        add(textSet.labeledValue(textSet.errorLabel, report.error))
    }
    add("")
}

private fun buildClangResolvedPathCheckLinesInternal(
    report: ClangResolvedPathCheckReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = buildList {
    add(report.title)
    add(textSet.labeledValue(textSet.pathLabel, report.path))
    add(textSet.labeledValue(textSet.existsLabel, report.exists))
    if (report.exists) {
        report.sizeBytes?.let { add(textSet.sizeLine(it)) }
        report.canExecute?.let { add(textSet.labeledValue(textSet.executableLabel, it)) }
    }
}

private fun buildClangLinkerResolutionCaseSpecsInternal(
    binDirPath: String,
    currentPath: String?,
    forceLldArg: String,
    textSet: CompilerDiagnosticsReportTextSet
): List<ClangLinkerResolutionCaseSpec> = listOf(
    ClangLinkerResolutionCaseSpec(
        caseName = textSet.linkerResolutionCase1Title,
        extraArgs = listOf("-fuse-ld=lld")
    ),
    ClangLinkerResolutionCaseSpec(
        caseName = textSet.linkerResolutionCase2Title,
        extraArgs = listOf("-fuse-ld=lld"),
        extraEnv = mapOf("COMPILER_PATH" to binDirPath)
    ),
    ClangLinkerResolutionCaseSpec(
        caseName = textSet.linkerResolutionCase3Title,
        extraArgs = listOf("-fuse-ld=lld"),
        extraEnv = mapOf("PATH" to prependPath(binDirPath, currentPath))
    ),
    ClangLinkerResolutionCaseSpec(
        caseName = textSet.linkerResolutionCase4Title,
        extraArgs = listOf("-fuse-ld=lld", "-B$binDirPath")
    ),
    ClangLinkerResolutionCaseSpec(
        caseName = textSet.linkerResolutionCase5Title,
        extraArgs = listOf(forceLldArg)
    )
)

private fun buildClangLinkerResolutionCaseLinesInternal(
    report: ClangLinkerResolutionCaseReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = buildList {
    add("--- ${report.caseName} ---")
    add(textSet.header(textSet.commandLabel))
    add(report.command.joinToString(" "))
    if (report.extraEnv.isNotEmpty()) {
        add(textSet.header(textSet.extraEnvironmentLabel))
        report.extraEnv.forEach { (key, value) ->
            add("  $key=$value")
        }
    }
    add(textSet.labeledValue(textSet.exitCodeLabel, report.exitCode))
    add(
        textSet.labeledValue(
            textSet.resolvedLinkerLabel,
            report.linkerPath ?: textSet.unresolvedLinkerText
        )
    )
    if (report.linkerPath != null && report.pathAliasHint != null) {
        add(report.pathAliasHint)
    }
    if (report.stdout.isNotBlank()) {
        add(textSet.header(textSet.stdoutLabel))
        add(report.stdout)
    }
    if (report.stderr.isNotBlank()) {
        add(textSet.header(textSet.stderrLabel))
        add(report.stderr)
    }
    add("")
}

private fun buildDiagnosticCommandLinesInternal(
    report: DiagnosticCommandReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> {
    return buildList {
        report.title?.let(::add)

        val hasPreCommandLines = report.preCommandLines.isNotEmpty()
        val hasCommandSection = report.command.isNotEmpty()
        val hasPostCommandLines = report.postCommandLines.isNotEmpty()
        val hasResultSection = report.exitCode != null ||
            report.stdout.isNotBlank() ||
            report.stderr.isNotBlank()
        val hasOutputFiles = report.outputFiles.isNotEmpty()

        if (hasPreCommandLines) {
            addAll(report.preCommandLines)
        }

        report.missingPrerequisiteMessage?.let { message ->
            if (report.separateSections && hasPreCommandLines) {
                add("")
            }
            add(message)
            addAll(report.trailingLines)
            return@buildList
        }

        if (report.separateSections && hasPreCommandLines && (hasCommandSection || hasPostCommandLines)) {
            add("")
        }
        if (hasCommandSection) {
            add(report.commandLabel)
            add(report.command.joinToString(" "))
        }
        if (hasPostCommandLines) {
            addAll(report.postCommandLines)
        }

        if (report.separateSections &&
            (hasPreCommandLines || hasCommandSection || hasPostCommandLines) &&
            hasResultSection
        ) {
            add("")
        }

        report.exitCode?.let { add(textSet.labeledValue(textSet.exitCodeLabel, it)) }
        if (report.stdout.isNotBlank()) {
            add(textSet.header(textSet.stdoutLabel))
            add(report.stdout)
        }
        if (report.stderr.isNotBlank()) {
            add(textSet.header(textSet.stderrLabel))
            add(report.stderr)
        }

        if (report.separateSections && hasOutputFiles && hasResultSection) {
            add("")
        }
        report.outputFiles.forEach { outputFile ->
            add("${outputFile.label}: ${outputFile.path}")
            outputFile.exists?.let { exists ->
                add(textSet.labeledValue(textSet.existsLabel, exists))
                if (exists) {
                    outputFile.sizeBytes?.let { sizeBytes ->
                        add(textSet.sizeLine(sizeBytes))
                    }
                }
            }
        }
        addAll(report.trailingLines)
    }
}

private fun buildToolchainFilesCheckLinesInternal(
    report: ToolchainFilesCheckReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = buildList {
    add(report.title)
    add("")
    addAll(buildDirectoryStatusLines(report.toolchainDir, textSet))
    add("")
    addAll(buildDirectoryStatusLines(report.binDir, textSet))
    add("")
    add(textSet.header(textSet.criticalBinariesLabel))
    report.criticalBinaries.forEach { binary ->
        add(
            "  ${binary.name}: ${if (binary.exists) "✓" else "✗"} " +
                "(${binary.sizeBytes} bytes, canExec=${binary.canExecute})"
        )
    }
    add("")
    report.runtimeDirectories.forEach { directory ->
        addAll(buildDirectoryStatusLines(directory, textSet))
    }
    if (report.runtimeLibraries.isNotEmpty()) {
        add(textSet.header(textSet.runtimeLibrariesLabel))
        report.runtimeLibraries.forEach { runtimeLibrary ->
            add(
                "  ${runtimeLibrary.label}: ${if (runtimeLibrary.exists) "✓" else "✗"} " +
                    "(${runtimeLibrary.path})"
            )
        }
        report.preferredBuiltinsPath?.let {
            add(textSet.preferenceLine(textSet.preferredBuiltinsLabel, it))
        }
        report.preferredUnwindPath?.let {
            add(textSet.preferenceLine(textSet.preferredUnwindLabel, it))
        }
    }
    add("")
    addAll(buildDirectoryStatusLines(report.sysrootDir, textSet))
    add(textSet.labeledValue(textSet.installedLabel, report.sysrootInstalled))
}

private fun buildLibraryDependencyCheckLinesInternal(
    report: LibraryDependencyCheckReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> {
    return buildList {
        add(report.title)
        add("")
        report.directories.forEach { directory ->
            addAll(buildDirectoryStatusLines(directory, textSet))
        }
        add("")
        report.directories.forEach { directory ->
            if (!directory.exists || directory.entriesTitle == null) {
                return@forEach
            }
            add(directory.entriesTitle)
            directory.entries.forEach { entry ->
                add("  ${entry.name} (${entry.sizeBytes} bytes)")
            }
        }
    }
}

private fun buildClangDirectExecutionLinesInternal(
    report: ClangDirectExecutionReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = buildDiagnosticCommandLinesInternal(
    DiagnosticCommandReport(
        title = report.title,
        missingPrerequisiteMessage = report.missingPrerequisiteMessage,
        commandLabel = textSet.header(textSet.commandLabel),
        command = report.command,
        exitCode = report.exitCode,
        stdout = report.stdout,
        stderr = report.stderr,
        outputFiles = listOfNotNull(
            report.outputPath?.let { path ->
                DiagnosticOutputFileReport(
                    label = textSet.outputFileLabel,
                    path = path,
                    exists = report.outputExists,
                    sizeBytes = report.outputSizeBytes
                )
            }
        )
    ),
    textSet
)

private fun buildDiagnosticOutputFileReport(
    label: String,
    file: File
): DiagnosticOutputFileReport {
    val exists = file.exists()
    return DiagnosticOutputFileReport(
        label = label,
        path = file.absolutePath,
        exists = exists,
        sizeBytes = if (exists) file.length() else null
    )
}

private fun buildDirectoryStatusLines(
    report: DiagnosticDirectoryReport,
    textSet: CompilerDiagnosticsReportTextSet
): List<String> = listOf(
    "${report.label}: ${report.path}",
    textSet.labeledValue(textSet.existsLabel, report.exists)
)

private fun buildDiagnosticDirectoryReport(
    label: String,
    directory: File,
    entriesTitle: String? = null
): DiagnosticDirectoryReport {
    val exists = directory.exists()
    return DiagnosticDirectoryReport(
        label = label,
        path = directory.absolutePath,
        exists = exists,
        entriesTitle = entriesTitle,
        entries = if (exists && entriesTitle != null) {
            directory.listFiles()
                ?.map { file -> DiagnosticDirectoryEntryReport(file.name, file.length()) }
                .orEmpty()
        } else {
            emptyList()
        }
    )
}

private fun buildDiagnosticBinaryStatusReport(
    name: String,
    file: File
): DiagnosticBinaryStatusReport {
    val exists = file.exists()
    return DiagnosticBinaryStatusReport(
        name = name,
        exists = exists,
        sizeBytes = if (exists) file.length() else 0L,
        canExecute = file.canExecute()
    )
}

private fun buildDiagnosticNamedPathStatusReport(
    label: String,
    file: File
): DiagnosticNamedPathStatusReport = DiagnosticNamedPathStatusReport(
    label = label,
    path = file.absolutePath,
    exists = file.exists()
)

// ========== 测试函数 ==========

/**
 * 日志输出辅助类
 * 同时输出到 UI 和 Timber 日志
 */
internal class CompilerDiagnosticsTextLogger(
    private val tag: String = "CompilerDiagnostics",
    private val lineSink: ((String) -> Unit)? = null,
) {
    private val output = StringBuilder()

    fun appendLine(message: String = "") {
        output.appendLine(message)
        if (message.isNotEmpty()) {
            lineSink?.invoke(message) ?: Timber.tag(tag).i(message)
        }
    }

    fun getOutput(): String = output.toString()
}

private fun forceUseBundledLinkerFlag(binDir: File): String = "-fuse-ld=${File(binDir, "ld.lld").absolutePath}"

private fun compareVersionName(left: String, right: String): Int {
    val leftParts = left.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val rightParts = right.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
    val size = maxOf(leftParts.size, rightParts.size)
    for (i in 0 until size) {
        val l = leftParts.getOrElse(i) { 0 }
        val r = rightParts.getOrElse(i) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return left.compareTo(right)
}

private fun findVersionedClangBinaries(binDir: File): List<String> {
    val regex = Regex("""^clang-\d+$""")
    return binDir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && regex.matches(it.name) }
        ?.map { it.name }
        ?.sortedWith { left, right -> compareVersionName(left, right) }
        ?.toList()
        ?: emptyList()
}

private fun resolveClangRuntimeBaseDir(installDir: File): File {
    val clangRoot = File(installDir, "lib/clang")
    val allVersionDirs = clangRoot.listFiles()
        ?.filter { it.isDirectory }
        ?: emptyList()
    val versionDir = clangRoot.listFiles()
        ?.filter { it.isDirectory && it.name.any(Char::isDigit) }
        ?.maxWithOrNull { a, b -> compareVersionName(a.name, b.name) }
    val selectedDir = versionDir ?: allVersionDirs.maxByOrNull { it.name }
    return if (selectedDir != null) File(selectedDir, "lib/linux") else File(clangRoot, "lib/linux")
}

private fun resolveRuntimeArchName(arch: AndroidSysrootManager.Companion.Arch): String = when (arch) {
    AndroidSysrootManager.Companion.Arch.ARM64 -> "aarch64"
    AndroidSysrootManager.Companion.Arch.X86_64 -> "x86_64"
}

internal data class ClangRuntimeLayout(
    val apiLevel: Int,
    val linuxBaseDir: File,
    val linuxArchDir: File,
    val linuxBuiltins: File,
    val tripleDir: File,
    val tripleBuiltins: File,
    val tripleUnwind: File,
) {
    val preferredBuiltins: File
        get() = if (tripleBuiltins.exists()) tripleBuiltins else linuxBuiltins

    val preferredUnwindDir: File
        get() = if (tripleUnwind.exists()) tripleDir else linuxArchDir

    val preferredUnwind: File
        get() = File(preferredUnwindDir, "libunwind.a")
}

private fun resolveClangRuntimeLayout(
    installDir: File,
    arch: AndroidSysrootManager.Companion.Arch,
    apiLevel: Int = DIAGNOSTIC_TARGET_API_LEVEL,
): ClangRuntimeLayout {
    val archName = resolveRuntimeArchName(arch)
    val linuxBaseDir = resolveClangRuntimeBaseDir(installDir)
    val clangVersionDir = linuxBaseDir.parentFile
    val tripleDir = if (clangVersionDir != null) {
        File(clangVersionDir, "$archName-unknown-linux-android$apiLevel")
    } else {
        File(installDir, "lib/clang/unknown/lib/$archName-unknown-linux-android$apiLevel")
    }
    return ClangRuntimeLayout(
        apiLevel = apiLevel,
        linuxBaseDir = linuxBaseDir,
        linuxArchDir = File(linuxBaseDir, archName),
        linuxBuiltins = File(linuxBaseDir, "libclang_rt.builtins-$archName-android.a"),
        tripleDir = tripleDir,
        tripleBuiltins = File(tripleDir, "libclang_rt.builtins.a"),
        tripleUnwind = File(tripleDir, "libunwind.a"),
    )
}

private fun enableLlvmExecTrace(builder: ProcessBuilder) {
    // 打开 LLVM Program.inc 中的 Android exec 诊断日志。
    builder.environment()["TINAIDE_LLVM_EXEC_TRACE"] = "1"
}

private fun buildNativeDiagnosticProcessBuilder(
    context: android.content.Context,
    binDir: File,
    command: List<String>,
    workingDir: File? = null,
    preferLinker64: Boolean = NativeExecutableRunner.shouldPreferLinker64(),
    useRecommendedTinaExec: Boolean = true,
    extraEnv: Map<String, String> = emptyMap(),
    customizeEnvironment: (MutableMap<String, String>) -> Unit = {},
): ProcessBuilder {
    val fullCommand = NativeExecutableRunner.buildCommand(
        executable = command.first(),
        args = command.drop(1),
        preferLinker64 = preferLinker64
    )
    return ProcessBuilder(fullCommand).apply {
        if (workingDir != null) {
            directory(workingDir)
        }
        NativeExecutableRunner.configureEnvironment(
            this,
            context.applicationInfo.nativeLibraryDir,
            binDir.absolutePath,
            context.cacheDir.absolutePath,
            context.filesDir.absolutePath
        )
        if (useRecommendedTinaExec) {
            NativeExecutableRunner.applyRecommendedTinaExec(
                environment = environment(),
                context = context.applicationContext,
                fullCommand = fullCommand
            )
        }
        enableLlvmExecTrace(this)
        extraEnv.forEach { (key, value) ->
            environment()[key] = value
        }
        customizeEnvironment(environment())
    }
}

private fun prependPath(path: String, currentPath: String?): String = if (currentPath.isNullOrBlank()) path else "$path:$currentPath"

private fun extractLinkerPathFromDriverOutput(output: String): String? {
    val regex = Regex("\"(/[^\"]*(?:ld\\.lld|lld|ld64\\.lld))\"")
    return regex.find(output)?.groupValues?.getOrNull(1)
}

private fun getFileIdentity(path: String): Pair<Long, Long>? = try {
    val stat = Os.stat(path)
    stat.st_dev to stat.st_ino
} catch (_: ErrnoException) {
    null
}

private fun isSameFileByStat(pathA: String, pathB: String): Boolean? {
    val idA = getFileIdentity(pathA) ?: return null
    val idB = getFileIdentity(pathB) ?: return null
    return idA == idB
}

private fun buildPathAliasHint(
    path: String,
    packageName: String,
    textSet: CompilerDiagnosticsReportTextSet
): String = buildPathAliasHintText(
    path = path,
    packageName = packageName,
    aliasEquivalent = buildPathAliasEquivalent(path, packageName),
    textSet = textSet
)

private fun buildPathAliasEquivalent(path: String, packageName: String): Boolean? {
    val userPrefix = "/data/user/0/$packageName/"
    val dataPrefix = "/data/data/$packageName/"

    val aliasCandidate = when {
        path.startsWith(userPrefix) -> dataPrefix + path.removePrefix(userPrefix)
        path.startsWith(dataPrefix) -> userPrefix + path.removePrefix(dataPrefix)
        else -> null
    }

    if (aliasCandidate == null) {
        return null
    }

    return isSameFileByStat(path, aliasCandidate)
}

private fun buildPathAliasHintText(
    path: String,
    packageName: String,
    aliasEquivalent: Boolean?,
    textSet: CompilerDiagnosticsReportTextSet
): String {
    val userPrefix = "/data/user/0/$packageName/"
    val dataPrefix = "/data/data/$packageName/"
    val isAppPrivatePath = path.startsWith(userPrefix) || path.startsWith(dataPrefix)
    if (!isAppPrivatePath) {
        return textSet.pathAliasCheckSkipped
    }

    return when (aliasEquivalent) {
        true -> textSet.pathAliasCheckEquivalent
        false -> textSet.pathAliasCheckDifferent
        null -> textSet.pathAliasCheckUnavailable
    }
}

private suspend fun checkToolchainFiles(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)

    val toolchainDir = toolchainManager.getInstallDir(selection.toolchainId)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(logger, context, selection, toolchainDir, binDir)
    val versionedClangs = findVersionedClangBinaries(binDir)
    val criticalFiles = buildList {
        add("clang++")
        add("clang")
        addAll(versionedClangs)
        add("ld.lld")
        add("lld")
    }.distinct()

    val arch = AndroidSysrootManager.Companion.Arch.current()
    val runtimeLayout = resolveClangRuntimeLayout(toolchainDir, arch)
    val linuxArchUnwind = File(runtimeLayout.linuxArchDir, "libunwind.a")
    val sysrootDir = sysrootManager.getSysrootDir(arch)
    CompilerDiagnosticsTestSupport.buildToolchainFilesCheckLines(
        ToolchainFilesCheckReport(
            title = textSet.sectionTitle(textSet.toolchainFilesCheckTitle),
            toolchainDir = buildDiagnosticDirectoryReport(textSet.toolchainDirectoryLabel, toolchainDir),
            binDir = buildDiagnosticDirectoryReport(textSet.binDirectoryLabel, binDir),
            criticalBinaries = criticalFiles.map { fileName ->
                buildDiagnosticBinaryStatusReport(fileName, File(binDir, fileName))
            },
            runtimeDirectories = listOf(
                buildDiagnosticDirectoryReport(
                    textSet.runtimeLinuxBaseDirectoryLabel,
                    runtimeLayout.linuxBaseDir
                ),
                buildDiagnosticDirectoryReport(
                    textSet.runtimeLinuxArchDirectoryLabel,
                    runtimeLayout.linuxArchDir
                ),
                buildDiagnosticDirectoryReport(
                    label = textSet.runtimeTripleDirectoryLabel(runtimeLayout.apiLevel),
                    directory = runtimeLayout.tripleDir
                )
            ),
            runtimeLibraries = if (
                runtimeLayout.linuxBaseDir.exists() ||
                runtimeLayout.linuxArchDir.exists() ||
                runtimeLayout.tripleDir.exists()
            ) {
                listOf(
                    buildDiagnosticNamedPathStatusReport("builtins(linux)", runtimeLayout.linuxBuiltins),
                    buildDiagnosticNamedPathStatusReport("builtins(triple)", runtimeLayout.tripleBuiltins),
                    buildDiagnosticNamedPathStatusReport("libunwind(linux)", linuxArchUnwind),
                    buildDiagnosticNamedPathStatusReport("libunwind(triple)", runtimeLayout.tripleUnwind)
                )
            } else {
                emptyList()
            },
            preferredBuiltinsPath = runtimeLayout.preferredBuiltins.absolutePath,
            preferredUnwindPath = runtimeLayout.preferredUnwind.absolutePath,
            sysrootDir = buildDiagnosticDirectoryReport(textSet.sysrootDirectoryLabel, sysrootDir),
            sysrootInstalled = sysrootManager.isInstalled(arch)
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun testCompilerExecutable(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    val toolchainManager = AndroidNativeToolchainManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath

    val processBuilder = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = listOf(clangPath, "--version")
    )

    val process = processBuilder.start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }
    val errorText = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
        DiagnosticCommandReport(
            title = textSet.sectionTitle(textSet.compilerExecutableSectionTitle),
            preCommandLines = listOf(textSet.testCommandLine("$clangPath --version")),
            separateSections = true,
            exitCode = exitCode,
            stdout = outputText,
            stderr = errorText
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun testCompileOnly(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    // 创建测试源文件
    val testSource = File(context.cacheDir, "test_compile.cpp")
    testSource.writeText(
        """
        #include <iostream>
        int main() {
            std::cout << "Hello, World!" << std::endl;
            return 0;
        }
        """.trimIndent()
    )

    val testObject = File(context.cacheDir, "test_compile.o")
    if (testObject.exists()) testObject.delete()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath
    val forceLldArg = forceUseBundledLinkerFlag(binDir)
    val arch = AndroidSysrootManager.Companion.Arch.current()

    // 构建编译命令（仅编译，不链接）
    val ndkFlags = sysrootManager.getCompilerFlags(DIAGNOSTIC_TARGET_API_LEVEL, arch, isCpp = true)
    val command = buildList {
        add(clangPath)
        add(testSource.absolutePath)
        add("-c") // 仅编译
        add("-o")
        add(testObject.absolutePath)
        add("-std=c++17")
        add("-fintegrated-cc1")
        add(forceLldArg)
        addAll(ndkFlags)
    }

    val processBuilder = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = command,
        workingDir = context.cacheDir
    )

    val process = processBuilder.start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }
    val errorText = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
        DiagnosticCommandReport(
            title = textSet.sectionTitle(textSet.compileOnlySectionTitle),
            commandLabel = textSet.header(textSet.commandLabel),
            command = command,
            postCommandLines = listOf(textSet.forcedLinkerArgumentLine(forceLldArg)),
            separateSections = true,
            exitCode = exitCode,
            stdout = outputText,
            stderr = errorText,
            outputFiles = listOf(buildDiagnosticOutputFileReport(textSet.objectFileLabel, testObject))
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun testLinkOnly(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    // 先确保有 .o 文件
    val testObject = File(context.cacheDir, "test_compile.o")
    if (!testObject.exists()) {
        return CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                title = textSet.sectionTitle(textSet.linkOnlySectionTitle),
                missingPrerequisiteMessage = textSet.missingCompileObjectMessage
            ),
            textSet
        ).joinToString(separator = "\n")
    }

    val testBinary = File(context.cacheDir, "test_link")
    if (testBinary.exists()) testBinary.delete()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath
    val forceLldArg = forceUseBundledLinkerFlag(binDir)
    val arch = AndroidSysrootManager.Companion.Arch.current()

    // 构建链接命令（添加 -v 参数输出详细信息）
    val ndkFlags = sysrootManager.getCompilerFlags(DIAGNOSTIC_TARGET_API_LEVEL, arch, isCpp = true)
    val command = buildList {
        add(clangPath)
        add(testObject.absolutePath)
        add("-o")
        add(testBinary.absolutePath)
        add("-v") // 输出详细的链接器调用信息
        add(forceLldArg)
        addAll(ndkFlags)
    }

    val processBuilder = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = command,
        workingDir = context.cacheDir
    )

    val process = processBuilder.start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }
    val errorText = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
        DiagnosticCommandReport(
            title = textSet.sectionTitle(textSet.linkOnlySectionTitle),
            commandLabel = textSet.header(textSet.commandLabel),
            command = command,
            postCommandLines = listOf(textSet.forcedLinkerArgumentLine(forceLldArg)),
            separateSections = true,
            exitCode = exitCode,
            stdout = outputText,
            stderr = errorText,
            outputFiles = listOf(buildDiagnosticOutputFileReport(textSet.outputFileLabel, testBinary))
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun testFullCompile(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    // 创建测试源文件
    val testSource = File(context.cacheDir, "test_full.cpp")
    testSource.writeText(
        """
        #include <iostream>
        int main() {
            std::cout << "Full compile test!" << std::endl;
            return 0;
        }
        """.trimIndent()
    )

    val testBinary = File(context.cacheDir, "test_full")
    if (testBinary.exists()) testBinary.delete()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath
    val forceLldArg = forceUseBundledLinkerFlag(binDir)
    val arch = AndroidSysrootManager.Companion.Arch.current()

    // 构建完整编译命令（添加 -v 参数输出详细信息）
    val ndkFlags = sysrootManager.getCompilerFlags(DIAGNOSTIC_TARGET_API_LEVEL, arch, isCpp = true)
    val command = buildList {
        add(clangPath)
        add(testSource.absolutePath)
        add("-o")
        add(testBinary.absolutePath)
        add("-std=c++17")
        add("-fintegrated-cc1")
        add("-O2")
        add("-v") // 输出详细的编译和链接器调用信息
        add(forceLldArg)
        addAll(ndkFlags)
    }

    val processBuilder = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = command,
        workingDir = context.cacheDir
    )

    val process = processBuilder.start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }
    val errorText = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
        DiagnosticCommandReport(
            title = textSet.sectionTitle(textSet.fullCompileSectionTitle),
            commandLabel = textSet.header(textSet.commandLabel),
            command = command,
            postCommandLines = listOf(textSet.forcedLinkerArgumentLine(forceLldArg)),
            separateSections = true,
            exitCode = exitCode,
            stdout = outputText,
            stderr = errorText,
            outputFiles = listOf(buildDiagnosticOutputFileReport(textSet.outputFileLabel, testBinary))
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun checkLibraryDependencies(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    val toolchainManager = AndroidNativeToolchainManager(context)
    val arch = AndroidSysrootManager.Companion.Arch.current()
    val installDir = toolchainManager.getInstallDir(selection.toolchainId)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(logger, context, selection, installDir, binDir)
    val runtimeLayout = resolveClangRuntimeLayout(installDir, arch)
    CompilerDiagnosticsTestSupport.buildLibraryDependencyCheckLines(
        LibraryDependencyCheckReport(
            title = textSet.sectionTitle(textSet.libraryDependencyCheckTitle),
            directories = listOf(
                buildDiagnosticDirectoryReport(
                    label = textSet.runtimeLinuxBaseDirectoryLabel,
                    directory = runtimeLayout.linuxBaseDir,
                    entriesTitle = textSet.linuxBaseEntriesTitle
                ),
                buildDiagnosticDirectoryReport(
                    label = textSet.runtimeLinuxArchDirectoryLabel,
                    directory = runtimeLayout.linuxArchDir,
                    entriesTitle = textSet.linuxArchEntriesTitle
                ),
                buildDiagnosticDirectoryReport(
                    label = textSet.runtimeTripleDirectoryLabel(runtimeLayout.apiLevel),
                    directory = runtimeLayout.tripleDir,
                    entriesTitle = textSet.tripleEntriesTitle
                )
            )
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun testLinkerDirectly(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    val toolchainManager = AndroidNativeToolchainManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val lldPath = File(binDir, "ld.lld").absolutePath

    val processBuilder = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = listOf(lldPath, "--version")
    )

    val process = processBuilder.start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }
    val errorText = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
        DiagnosticCommandReport(
            title = textSet.sectionTitle(textSet.linkerDirectSectionTitle),
            preCommandLines = listOf(textSet.testCommandLine("$lldPath --version")),
            separateSections = true,
            exitCode = exitCode,
            stdout = outputText,
            stderr = errorText
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

private suspend fun testLinkerFullLink(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)

    val testObject = File(context.cacheDir, "test_compile.o")
    if (!testObject.exists()) {
        return CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                title = textSet.sectionTitle(textSet.linkerFullLinkSectionTitle),
                missingPrerequisiteMessage = textSet.missingCompileObjectMessage
            ),
            textSet
        ).joinToString(separator = "\n")
    }

    val testBinary = File(context.cacheDir, "test_link_lld_full")
    if (testBinary.exists()) testBinary.delete()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val lldPath = File(binDir, "ld.lld").absolutePath
    val arch = AndroidSysrootManager.Companion.Arch.current()
    val apiLevel = DIAGNOSTIC_TARGET_API_LEVEL
    val linkerEmulation = when (arch) {
        AndroidSysrootManager.Companion.Arch.ARM64 -> "aarch64linux"
        AndroidSysrootManager.Companion.Arch.X86_64 -> "elf_x86_64"
    }

    val sysrootDir = sysrootManager.getSysrootDir(arch)
    val sysrootLibApiDir = File(sysrootDir, "usr/lib/${arch.triple}/$apiLevel")
    val sysrootLibArchDir = File(sysrootDir, "usr/lib/${arch.triple}")
    val sysrootLibDir = File(sysrootDir, "usr/lib")

    val runtimeLayout = resolveClangRuntimeLayout(toolchainManager.getInstallDir(selection.toolchainId), arch, apiLevel)
    val builtins = runtimeLayout.preferredBuiltins
    val libunwind = runtimeLayout.preferredUnwind
    val runtimeSearchDirs = linkedSetOf(
        runtimeLayout.preferredUnwindDir.absolutePath,
        runtimeLayout.linuxArchDir.absolutePath,
        runtimeLayout.tripleDir.absolutePath,
    ).map { File(it) }.filter { it.exists() }
    val crtBegin = File(sysrootLibApiDir, "crtbegin_dynamic.o")
    val crtEnd = File(sysrootLibApiDir, "crtend_android.o")

    val prerequisiteLines = listOf(
        "lld: $lldPath",
        "obj: ${testObject.absolutePath} (exists=${testObject.exists()}, size=${testObject.length()})",
        "crtbegin: ${crtBegin.absolutePath} (exists=${crtBegin.exists()})",
        "crtend: ${crtEnd.absolutePath} (exists=${crtEnd.exists()})",
        "builtins(优先): ${builtins.absolutePath} (exists=${builtins.exists()})",
        "builtins(linux): ${runtimeLayout.linuxBuiltins.absolutePath} (exists=${runtimeLayout.linuxBuiltins.exists()})",
        "builtins(triple): ${runtimeLayout.tripleBuiltins.absolutePath} (exists=${runtimeLayout.tripleBuiltins.exists()})",
        "libunwind(优先): ${libunwind.absolutePath} (exists=${libunwind.exists()})",
        "libunwind(linux): ${File(runtimeLayout.linuxArchDir, "libunwind.a").absolutePath} (exists=${File(runtimeLayout.linuxArchDir, "libunwind.a").exists()})",
        "libunwind(triple): ${runtimeLayout.tripleUnwind.absolutePath} (exists=${runtimeLayout.tripleUnwind.exists()})"
    )

    if (!crtBegin.exists() || !crtEnd.exists() || !builtins.exists() || !libunwind.exists()) {
        CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
            DiagnosticCommandReport(
                title = textSet.sectionTitle(textSet.linkerFullLinkSectionTitle),
                preCommandLines = prerequisiteLines,
                separateSections = true,
                missingPrerequisiteMessage = textSet.criticalLinkFilesMissingMessage
            ),
            textSet
        ).forEach(logger::appendLine)
        return logger.getOutput()
    }

    val command = buildList {
        add(lldPath)
        add("--sysroot=${sysrootDir.absolutePath}")
        add("-EL")
        add("--fix-cortex-a53-843419")
        add("-z")
        add("now")
        add("-z")
        add("relro")
        add("-z")
        add("max-page-size=16384")
        add("--no-rosegment")
        add("--hash-style=gnu")
        add("--eh-frame-hdr")
        add("-m")
        add(linkerEmulation)
        add("-pie")
        add("-dynamic-linker")
        add("/system/bin/linker64")
        add("-o")
        add(testBinary.absolutePath)
        add(crtBegin.absolutePath)
        add("-L${sysrootLibApiDir.absolutePath}")
        runtimeSearchDirs.forEach { add("-L${it.absolutePath}") }
        add("-L${sysrootLibApiDir.absolutePath}")
        add("-L${sysrootLibArchDir.absolutePath}")
        add("-L${sysrootLibDir.absolutePath}")
        add(testObject.absolutePath)
        add("-lc++")
        add("-lm")
        add(builtins.absolutePath)
        add("-l:libunwind.a")
        add("-ldl")
        add("-lc")
        add(builtins.absolutePath)
        add("-l:libunwind.a")
        add("-ldl")
        add(crtEnd.absolutePath)
    }

    val processBuilder = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = command,
        workingDir = context.cacheDir
    )

    val process = processBuilder.start()
    val outputText = process.inputStream.bufferedReader().use { it.readText() }
    val errorText = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    CompilerDiagnosticsTestSupport.buildDiagnosticCommandLines(
        DiagnosticCommandReport(
            title = textSet.sectionTitle(textSet.linkerFullLinkSectionTitle),
            preCommandLines = prerequisiteLines,
            commandLabel = textSet.header(textSet.commandLabel),
            command = command,
            separateSections = true,
            exitCode = exitCode,
            stdout = outputText,
            stderr = errorText,
            outputFiles = listOf(buildDiagnosticOutputFileReport(textSet.outputFileLabel, testBinary))
        ),
        textSet
    ).forEach(logger::appendLine)

    return logger.getOutput()
}

/**
 * 测试 clang++ 能否找到 lld 链接器
 * 使用 -print-prog-name=ld.lld 查询 clang++ 内部的链接器路径解析
 */
private suspend fun testClangFindLinker(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)
    logger.appendLine(textSet.sectionTitle(textSet.clangFindLinkerSectionTitle))
    logger.appendLine()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath

    var lldPath = ""
    CompilerDiagnosticsTestSupport.clangProgramLookupSpecs(textSet).forEach { lookup ->
        val command = listOf(clangPath, "-print-prog-name=${lookup.programName}")
        val processBuilder = buildNativeDiagnosticProcessBuilder(
            context = context,
            binDir = binDir,
            useRecommendedTinaExec = selection.useRecommendedTinaExec,
            command = command
        )

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val error = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        val report = ClangProgramLookupReport(
            title = lookup.title,
            commandLine = command.joinToString(" "),
            exitCode = exitCode,
            output = output,
            error = error
        )

        CompilerDiagnosticsTestSupport.buildClangProgramLookupLines(report, textSet)
            .forEach(logger::appendLine)

        if (lookup.programName == "ld.lld") {
            lldPath = output.trim()
        }
    }

    if (lldPath.isNotBlank() && lldPath.startsWith("/")) {
        val lldFile = File(lldPath)
        val report = ClangResolvedPathCheckReport(
            title = textSet.resolvedPathCheckTitle,
            path = lldPath,
            exists = lldFile.exists(),
            sizeBytes = lldFile.length(),
            canExecute = lldFile.canExecute()
        )
        CompilerDiagnosticsTestSupport.buildClangResolvedPathCheckLines(report, textSet)
            .forEach(logger::appendLine)
    }

    return logger.getOutput()
}

/**
 * 直启 clang++（不走外层 linker64）对照测试。
 *
 * 目的：
 * - 与第 4/5 项进行直接对比，快速判断外层 linker64 包装是否影响 clang++ 子进程启动。
 */
private suspend fun testClangDirectNoOuterLinker(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)
    logger.appendLine(textSet.sectionTitle(textSet.clangDirectSectionTitle))
    logger.appendLine()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath
    val forceLldArg = forceUseBundledLinkerFlag(binDir)
    val arch = AndroidSysrootManager.Companion.Arch.current()
    val ndkFlags = sysrootManager.getCompilerFlags(DIAGNOSTIC_TARGET_API_LEVEL, arch, isCpp = true)

    logger.appendLine(textSet.directExecutionExplanation)
    logger.appendLine("clang++: $clangPath")
    logger.appendLine(textSet.forcedLinkerArgumentLine(forceLldArg))
    logger.appendLine()

    // ===== 测试A: 仅链接（.o -> 可执行） =====
    val testObject = File(context.cacheDir, "test_compile.o")
    if (!testObject.exists()) {
        CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
            ClangDirectExecutionReport(
                title = textSet.directExecutionTestATitle,
                missingPrerequisiteMessage = textSet.directExecutionMissingObjectMessage(testObject.name)
            ),
            textSet
        ).forEach(logger::appendLine)
    } else {
        val linkOut = File(context.cacheDir, "test_link_direct")
        if (linkOut.exists()) linkOut.delete()

        val linkCommand = buildList {
            add(clangPath)
            add(testObject.absolutePath)
            add("-o")
            add(linkOut.absolutePath)
            add("-v")
            add(forceLldArg)
            addAll(ndkFlags)
        }

        val linkPb = buildNativeDiagnosticProcessBuilder(
            context = context,
            binDir = binDir,
            useRecommendedTinaExec = selection.useRecommendedTinaExec,
            command = linkCommand,
            workingDir = context.cacheDir,
            preferLinker64 = false
        )

        val linkProcess = linkPb.start()
        val linkStdout = linkProcess.inputStream.bufferedReader().use { it.readText() }
        val linkStderr = linkProcess.errorStream.bufferedReader().use { it.readText() }
        val linkExit = linkProcess.waitFor()

        CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
            ClangDirectExecutionReport(
                title = textSet.directExecutionTestATitle,
                command = linkCommand,
                exitCode = linkExit,
                stdout = linkStdout,
                stderr = linkStderr,
                outputPath = linkOut.absolutePath,
                outputExists = linkOut.exists(),
                outputSizeBytes = linkOut.length()
            ),
            textSet
        ).forEach(logger::appendLine)
    }
    logger.appendLine()

    // ===== 测试B: 完整编译+链接 =====
    val fullSrc = File(context.cacheDir, "test_full_direct.cpp")
    fullSrc.writeText(
        """
        #include <iostream>
        int main() {
            std::cout << "direct clang test" << std::endl;
            return 0;
        }
        """.trimIndent()
    )
    val fullOut = File(context.cacheDir, "test_full_direct")
    if (fullOut.exists()) fullOut.delete()

    val fullCommand = buildList {
        add(clangPath)
        add(fullSrc.absolutePath)
        add("-o")
        add(fullOut.absolutePath)
        add("-std=c++17")
        add("-fintegrated-cc1")
        add("-O2")
        add("-v")
        add(forceLldArg)
        addAll(ndkFlags)
    }

    val fullPb = buildNativeDiagnosticProcessBuilder(
        context = context,
        binDir = binDir,
        useRecommendedTinaExec = selection.useRecommendedTinaExec,
        command = fullCommand,
        workingDir = context.cacheDir,
        preferLinker64 = false
    )

    val fullProcess = fullPb.start()
    val fullStdout = fullProcess.inputStream.bufferedReader().use { it.readText() }
    val fullStderr = fullProcess.errorStream.bufferedReader().use { it.readText() }
    val fullExit = fullProcess.waitFor()

    CompilerDiagnosticsTestSupport.buildClangDirectExecutionLines(
        ClangDirectExecutionReport(
            title = textSet.directExecutionTestBTitle,
            command = fullCommand,
            exitCode = fullExit,
            stdout = fullStdout,
            stderr = fullStderr,
            outputPath = fullOut.absolutePath,
            outputExists = fullOut.exists(),
            outputSizeBytes = fullOut.length()
        ),
        textSet
    ).forEach(logger::appendLine)

    logger.appendLine()
    logger.appendLine(textSet.directExecutionComparisonSuggestion)
    return logger.getOutput()
}

/**
 * 链接器解析策略对照测试。
 *
 * 目标：
 * - 不真正执行链接（使用 -###），只观察 clang 驱动最终解析到的 linker 路径。
 * - 对照环境变量与参数策略，判断“找 lld”是否是主因。
 */
private suspend fun testClangLinkerResolutionModes(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)
    logger.appendLine(textSet.sectionTitle(textSet.linkerResolutionSectionTitle))
    logger.appendLine()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath
    val forceLldArg = forceUseBundledLinkerFlag(binDir)
    val arch = AndroidSysrootManager.Companion.Arch.current()
    val ndkFlags = sysrootManager.getCompilerFlags(DIAGNOSTIC_TARGET_API_LEVEL, arch, isCpp = true)
    val binDirPath = binDir.absolutePath
    val currentPath = System.getenv("PATH")

    val testSource = File(context.cacheDir, "test_linker_resolution.cpp")
    testSource.writeText(
        """
        int main() { return 0; }
        """.trimIndent()
    )
    val testBinary = File(context.cacheDir, "test_linker_resolution")

    val cases = CompilerDiagnosticsTestSupport.clangLinkerResolutionCaseSpecs(
        binDirPath = binDirPath,
        currentPath = currentPath,
        forceLldArg = forceLldArg,
        textSet = textSet
    )

    logger.appendLine("clang++: $clangPath")
    logger.appendLine("binDir: $binDirPath")
    logger.appendLine(textSet.resolutionModeExplanation)
    logger.appendLine()

    for (case in cases) {
        val command = buildList {
            add(clangPath)
            add(testSource.absolutePath)
            add("-o")
            add(testBinary.absolutePath)
            add("-v")
            add("-###")
            addAll(case.extraArgs)
            addAll(ndkFlags)
        }

        logger.appendLine(textSet.header(textSet.commandLabel))
        logger.appendLine(command.joinToString(" "))
        if (case.extraEnv.isNotEmpty()) {
            logger.appendLine(textSet.header(textSet.extraEnvironmentLabel))
            case.extraEnv.forEach { (k, v) ->
                logger.appendLine("  $k=$v")
            }
        }

        val processBuilder = buildNativeDiagnosticProcessBuilder(
            context = context,
            binDir = binDir,
            useRecommendedTinaExec = selection.useRecommendedTinaExec,
            command = command,
            workingDir = context.cacheDir,
            extraEnv = case.extraEnv
        )

        val process = processBuilder.start()
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        val mergedOutput = if (stderr.isNotBlank()) stderr else stdout
        val linkerPath = extractLinkerPathFromDriverOutput(mergedOutput)
        val report = ClangLinkerResolutionCaseReport(
            caseName = case.caseName,
            command = command,
            extraEnv = case.extraEnv,
            exitCode = exitCode,
            linkerPath = linkerPath,
            pathAliasHint = linkerPath?.let {
                buildPathAliasHint(it, context.packageName, textSet)
            },
            stdout = stdout,
            stderr = stderr
        )

        CompilerDiagnosticsTestSupport.buildClangLinkerResolutionCaseLines(report, textSet)
            .forEach(logger::appendLine)
    }

    return logger.getOutput()
}

/**
 * 启动链对照诊断：
 * 用同一个“接近 CMake try_compile”的仅编译命令，对比当前链路、shell shim、shell shim(不带 exec/preload)。
 */
private suspend fun testLauncherChainComparison(
    context: android.content.Context,
    selection: DiagnosticToolchainSelection,
): String {
    val logger = CompilerDiagnosticsTextLogger()
    val textSet = CompilerDiagnosticsReportTextSet.fromContext(context)
    logger.appendLine(textSet.sectionTitle(textSet.launcherChainSectionTitle))
    logger.appendLine()

    val toolchainManager = AndroidNativeToolchainManager(context)
    val sysrootManager = AndroidSysrootManager(context)
    val binDir = toolchainManager.getBinDir(selection.toolchainId)
    appendDiagnosticToolchainHeader(
        logger,
        context,
        selection,
        toolchainManager.getInstallDir(selection.toolchainId),
        binDir
    )
    val clangPath = File(binDir, "clang++").absolutePath
    val linker64Path = "/system/bin/linker64"
    val linkerPreloadPath = File(
        context.applicationInfo.nativeLibraryDir,
        "libtina_exec_linker_ld_preload.so"
    ).absolutePath
    val directPreloadPath = File(
        context.applicationInfo.nativeLibraryDir,
        "libtina_exec_direct_ld_preload.so"
    ).absolutePath
    val arch = AndroidSysrootManager.Companion.Arch.current()
    val ndkFlags = sysrootManager.getCompilerFlags(DIAGNOSTIC_TARGET_API_LEVEL, arch, isCpp = true)
    val workDir = File(context.cacheDir, "launcher-chain-compare").apply { mkdirs() }
    val sourceFile = File(workDir, "launcher_chain_compare.cpp").apply {
        writeText(
            """
            int main() {
                return 0;
            }
            """.trimIndent()
        )
    }
    val shellShim = File(workDir, "clang_linker_wrapper.sh").apply {
        writeText(
            """
            #!/system/bin/sh
            exec "$linker64Path" "$clangPath" "${'$'}@"
            """.trimIndent()
        )
    }

    logger.appendLine("clang++: $clangPath")
    logger.appendLine("linker preload: $linkerPreloadPath (exists=${File(linkerPreloadPath).exists()})")
    logger.appendLine("direct preload: $directPreloadPath (exists=${File(directPreloadPath).exists()})")
    logger.appendLine("shell shim: ${shellShim.absolutePath}")
    logger.appendLine()

    val cases = buildList {
        add(
            LaunchCase(
                name = textSet.launcherChainCase1Title,
                commandFactory = { inputs ->
                    listOf(clangPath) + inputs.compilerArgs
                },
                preferLinker64 = true
            )
        )
        add(
            LaunchCase(
                name = textSet.launcherChainCase2Title,
                commandFactory = { inputs ->
                    listOf("/system/bin/sh", shellShim.absolutePath) + inputs.compilerArgs
                },
                preferLinker64 = false,
                customizeEnvironment = { env ->
                    env.remove("LD_PRELOAD")
                    env.remove("TINA_EXEC__SYSTEM_LINKER_EXEC__MODE")
                    env.remove("TINA_EXEC__PROC_SELF_EXE")
                    env.remove("TINA_APP__DATA_DIR")
                    env.remove("TINA_APP__LEGACY_DATA_DIR")
                    env.remove("TINA_ROOTFS")
                    env.remove("TINA_PREFIX")
                }
            )
        )
        add(
            LaunchCase(
                name = textSet.launcherChainCase3Title,
                commandFactory = { inputs ->
                    listOf("/system/bin/sh", shellShim.absolutePath) + inputs.compilerArgs
                },
                preferLinker64 = false,
                customizeEnvironment = { env ->
                    env["LD_PRELOAD"] = linkerPreloadPath
                }
            )
        )
    }

    suspend fun runLaunchMatrix(
        sectionTitle: String,
        inputsFactory: (Int) -> LaunchInputs
    ) {
        logger.appendLine(textSet.sectionTitle(sectionTitle))
        logger.appendLine()

        cases.forEachIndexed { index, case ->
            val inputs = inputsFactory(index)
            inputs.objectFile.parentFile?.mkdirs()
            inputs.depFile.parentFile?.mkdirs()
            inputs.objectFile.delete()
            inputs.depFile.delete()
            val command = case.commandFactory(inputs)

            logger.appendLine("--- ${case.name} ---")
            logger.appendLine(textSet.labeledValue(textSet.workingDirectoryLabel, inputs.workingDir.absolutePath))
            logger.appendLine(textSet.header(textSet.commandLabel))
            logger.appendLine(command.joinToString(" "))
            if (case.extraEnv.isNotEmpty()) {
                logger.appendLine(textSet.header(textSet.extraEnvironmentLabel))
                case.extraEnv.forEach { (key, value) ->
                    logger.appendLine("  $key=$value")
                }
            }

            val start = System.currentTimeMillis()
            val processBuilder = buildNativeDiagnosticProcessBuilder(
                context = context,
                binDir = binDir,
                useRecommendedTinaExec = selection.useRecommendedTinaExec,
                command = command,
                workingDir = inputs.workingDir,
                preferLinker64 = case.preferLinker64,
                extraEnv = case.extraEnv,
                customizeEnvironment = case.customizeEnvironment
            )
            val process = processBuilder.start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val durationMs = System.currentTimeMillis() - start

            CompilerDiagnosticsTestSupport.buildLaunchMatrixCaseLines(
                LaunchMatrixCaseReport(
                    caseName = case.name,
                    workingDirPath = inputs.workingDir.absolutePath,
                    command = command,
                    extraEnv = case.extraEnv,
                    exitCode = exitCode,
                    durationMs = durationMs,
                    objectFile = LaunchMatrixFileSnapshot(
                        path = inputs.objectFile.absolutePath,
                        exists = inputs.objectFile.exists(),
                        sizeBytes = inputs.objectFile.length()
                    ),
                    depFile = LaunchMatrixFileSnapshot(
                        path = inputs.depFile.absolutePath,
                        exists = inputs.depFile.exists(),
                        sizeBytes = inputs.depFile.length()
                    ),
                    stdout = stdout,
                    stderr = stderr
                ),
                textSet
            ).forEach(logger::appendLine)
        }
    }

    runLaunchMatrix(textSet.baselineMatrixSectionTitle) { index ->
        val objectFile = File(workDir, "case_${index + 1}.o")
        val depFile = File(workDir, "case_${index + 1}.d")
        LaunchInputs(
            compilerArgs = buildList {
                add(sourceFile.absolutePath)
                add("-c")
                add("-o")
                add(objectFile.absolutePath)
                add("-MD")
                add("-MF")
                add(depFile.absolutePath)
                add("-MT")
                add(objectFile.name)
                add("-std=c++17")
                add("-fintegrated-cc1")
                addAll(ndkFlags)
            },
            objectFile = objectFile,
            depFile = depFile,
            workingDir = workDir
        )
    }

    val cmakeLikeRoot = File(workDir, "cmake-like").apply {
        deleteRecursively()
        mkdirs()
    }
    val cmakeLikeScratchDir = File(
        cmakeLikeRoot,
        "CMakeFiles/CMakeScratch/TryCompile-simulated"
    ).apply { mkdirs() }
    val cmakeLikeObjectRelPath = "CMakeFiles/cmTC_simulated.dir/testCXXCompiler.cxx.o"
    val cmakeLikeDepRelPath = "CMakeFiles/cmTC_simulated.dir/testCXXCompiler.cxx.o.d"
    File(cmakeLikeScratchDir, "CMakeFiles/cmTC_simulated.dir").mkdirs()
    val cmakeLikeSourceFile = File(cmakeLikeScratchDir, "testCXXCompiler.cxx").apply {
        writeText(
            """
            int main(int argc, char** argv) {
                return argc > 0 && argv != nullptr ? 0 : 1;
            }
            """.trimIndent()
        )
    }
    val cmakeLikeTargetTriple = "${arch.triple}$CMAKE_LIKE_DIAGNOSTIC_TARGET_API_LEVEL"
    val cmakeLikeCompileFlags = extractCompileOnlyFlagsInternal(
        sysrootManager.getCompilerFlags(
            CMAKE_LIKE_DIAGNOSTIC_TARGET_API_LEVEL,
            arch,
            isCpp = true
        )
    )

    logger.appendLine(textSet.sectionTitle(textSet.cmakeLikeIntroTitle))
    logger.appendLine(textSet.labeledValue(textSet.simulatedTargetTripleLabel, cmakeLikeTargetTriple))
    logger.appendLine(
        textSet.labeledValue(textSet.simulatedScratchDirectoryLabel, cmakeLikeScratchDir.absolutePath)
    )
    logger.appendLine(
        textSet.labeledValue(textSet.simulatedObjectRelativePathLabel, cmakeLikeObjectRelPath)
    )
    logger.appendLine(
        textSet.labeledValue(textSet.simulatedDepfileRelativePathLabel, cmakeLikeDepRelPath)
    )
    logger.appendLine()

    runLaunchMatrix(textSet.cmakeLikeMatrixSectionTitle) {
        val objectFile = File(cmakeLikeScratchDir, cmakeLikeObjectRelPath)
        val depFile = File(cmakeLikeScratchDir, cmakeLikeDepRelPath)
        LaunchInputs(
            compilerArgs = buildList {
                add("--target=$cmakeLikeTargetTriple")
                addAll(cmakeLikeCompileFlags)
                add("-std=c++17")
                add("-fintegrated-cc1")
                add("-O2")
                add("-g")
                add("-DNDEBUG")
                add("-v")
                add("-MD")
                add("-MT")
                add(cmakeLikeObjectRelPath)
                add("-MF")
                add(cmakeLikeDepRelPath)
                add("-o")
                add(cmakeLikeObjectRelPath)
                add("-c")
                add(cmakeLikeSourceFile.absolutePath)
            },
            objectFile = objectFile,
            depFile = depFile,
            workingDir = cmakeLikeScratchDir
        )
    }

    logger.appendLine(textSet.header(textSet.conclusionSuggestionsLabel))
    CompilerDiagnosticsTestSupport.launcherChainRecommendationLines(textSet)
        .forEach(logger::appendLine)
    return logger.getOutput()
}

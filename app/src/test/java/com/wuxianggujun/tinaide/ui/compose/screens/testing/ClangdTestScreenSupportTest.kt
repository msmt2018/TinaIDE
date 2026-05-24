package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.testing.lsp.BatchTestResult
import com.wuxianggujun.tinaide.testing.lsp.BenchmarkResult
import com.wuxianggujun.tinaide.testing.lsp.LspJsonTemplate
import com.wuxianggujun.tinaide.testing.lsp.LspTestManager
import com.wuxianggujun.tinaide.testing.lsp.LspTestParams
import com.wuxianggujun.tinaide.testing.lsp.LspTestScenario
import com.wuxianggujun.tinaide.testing.lsp.TemplateCategory
import com.wuxianggujun.tinaide.testing.lsp.TestCategory
import com.wuxianggujun.tinaide.testing.lsp.TestStatus
import com.wuxianggujun.tinaide.testing.lsp.TestSuiteConfig
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ClangdTestScreenSupportTest {

    @Test
    fun scenarioVisibilityHelpers_shouldMatchExpectedSections() {
        assertThat(ClangdTestScreenSupport.shouldShowParamsSection(LspTestScenario.INITIALIZE))
            .isFalse()
        assertThat(ClangdTestScreenSupport.shouldShowParamsSection(LspTestScenario.CUSTOM))
            .isFalse()
        assertThat(ClangdTestScreenSupport.shouldShowParamsSection(LspTestScenario.HOVER))
            .isTrue()

        assertThat(ClangdTestScreenSupport.shouldShowTriggerCharField(LspTestScenario.COMPLETION))
            .isTrue()
        assertThat(ClangdTestScreenSupport.shouldShowTriggerCharField(LspTestScenario.DEFINITION))
            .isFalse()

        assertThat(ClangdTestScreenSupport.shouldShowCustomJsonSection(LspTestScenario.CUSTOM))
            .isTrue()
        assertThat(ClangdTestScreenSupport.shouldShowCustomJsonSection(LspTestScenario.COMPLETION))
            .isFalse()
    }

    @Test
    fun buildTestParams_shouldFallbackInvalidNumbersToZero() {
        val params = ClangdTestScreenSupport.buildTestParams(
            fileUri = "file:///tmp/main.cpp",
            line = "abc",
            column = "",
            triggerChar = ".",
            customJson = """{"custom":true}"""
        )

        assertThat(params.fileUri).isEqualTo("file:///tmp/main.cpp")
        assertThat(params.line).isEqualTo(0)
        assertThat(params.column).isEqualTo(0)
        assertThat(params.triggerChar).isEqualTo(".")
        assertThat(params.customJson).isEqualTo("""{"custom":true}""")
    }

    @Test
    fun benchmarkScenarios_shouldOnlyExposeBasicAndNavigationEntriesInStableOrder() {
        val scenarios = ClangdTestScreenSupport.benchmarkScenarios()

        assertThat(scenarios).containsExactly(
            LspTestScenario.INITIALIZE,
            LspTestScenario.COMPLETION,
            LspTestScenario.DEFINITION,
            LspTestScenario.HOVER,
            LspTestScenario.IMPLEMENTATION,
            LspTestScenario.DECLARATION,
            LspTestScenario.TYPE_DEFINITION
        ).inOrder()
        assertThat(scenarios.map { it.category }.distinct()).containsExactly(
            TestCategory.BASIC,
            TestCategory.NAVIGATION
        )
    }

    @Test
    fun resolveBenchmarkScenario_shouldFallbackWhenSelectionIsUnsupported() {
        val resolved = ClangdTestScreenSupport.resolveBenchmarkScenario(
            LspTestScenario.BENCHMARK_COMPLETION
        )

        assertThat(resolved).isEqualTo(LspTestScenario.INITIALIZE)
    }

    @Test
    fun parseBenchmarkIterations_shouldFallbackInvalidZeroAndNegativeValues() {
        assertThat(ClangdTestScreenSupport.parseBenchmarkIterations("25")).isEqualTo(25)
        assertThat(ClangdTestScreenSupport.parseBenchmarkIterations(" 8 ")).isEqualTo(8)
        assertThat(ClangdTestScreenSupport.parseBenchmarkIterations("0")).isEqualTo(10)
        assertThat(ClangdTestScreenSupport.parseBenchmarkIterations("-3")).isEqualTo(10)
        assertThat(ClangdTestScreenSupport.parseBenchmarkIterations("oops")).isEqualTo(10)
        assertThat(ClangdTestScreenSupport.parseBenchmarkIterations("", fallback = 6)).isEqualTo(6)
    }

    @Test
    fun buildProgressPercent_shouldClampOutOfRangeProgress() {
        assertThat(ClangdTestScreenSupport.buildProgressPercent(-0.2f)).isEqualTo(0)
        assertThat(ClangdTestScreenSupport.buildProgressPercent(0.456f)).isEqualTo(45)
        assertThat(ClangdTestScreenSupport.buildProgressPercent(1.7f)).isEqualTo(100)
    }

    @Test
    fun buildBatchSuiteOptions_shouldProduceStableUniqueIdsAndPreserveOrder() {
        val suites = listOf(
            TestSuiteConfig(
                name = "Basic",
                scenarios = listOf(LspTestScenario.INITIALIZE, LspTestScenario.COMPLETION)
            ),
            TestSuiteConfig(
                name = "Navigation",
                scenarios = listOf(LspTestScenario.DEFINITION, LspTestScenario.DECLARATION),
                iterations = 3
            )
        )

        val options = ClangdTestScreenSupport.buildBatchSuiteOptions(suites)

        assertThat(options.map { it.id }).containsExactly(
            "initialize-completion#0",
            "definition-declaration#1"
        ).inOrder()
        assertThat(options.map { it.config.name }).containsExactly("Basic", "Navigation").inOrder()
    }

    @Test
    fun resolveBatchSuite_shouldFallbackToFirstSuiteWhenIdUnknown() {
        val options = ClangdTestScreenSupport.buildBatchSuiteOptions(
            listOf(
                TestSuiteConfig(
                    name = "Basic",
                    scenarios = listOf(LspTestScenario.INITIALIZE, LspTestScenario.COMPLETION)
                ),
                TestSuiteConfig(
                    name = "Semantic",
                    scenarios = listOf(LspTestScenario.SEMANTIC_TOKENS_FULL)
                )
            )
        )

        val resolved = ClangdTestScreenSupport.resolveBatchSuite(
            options = options,
            selectedId = "missing-suite"
        )

        assertThat(resolved.id).isEqualTo(options.first().id)
        assertThat(resolved.config.name).isEqualTo("Basic")
    }

    @Test
    fun templateSections_shouldPreserveCategoryAndTemplateOrder() {
        val sections = ClangdTestScreenSupport.templateSections(
            listOf(
                LspJsonTemplate.DOCUMENT_SYMBOL,
                LspJsonTemplate.REFERENCES,
                LspJsonTemplate.WORKSPACE_SYMBOL,
                LspJsonTemplate.CODE_ACTION
            )
        )

        assertThat(sections.map { it.category }).containsExactly(
            TemplateCategory.SYMBOL,
            TemplateCategory.DOCUMENT,
            TemplateCategory.CODE_ACTION
        ).inOrder()
        assertThat(sections[0].templates).containsExactly(
            LspJsonTemplate.DOCUMENT_SYMBOL,
            LspJsonTemplate.WORKSPACE_SYMBOL
        ).inOrder()
        assertThat(sections[1].templates).containsExactly(LspJsonTemplate.REFERENCES)
        assertThat(sections[2].templates).containsExactly(LspJsonTemplate.CODE_ACTION)
    }

    @Test
    fun templateSections_shouldRejectEmptyTemplateList() {
        val error = runCatching {
            ClangdTestScreenSupport.templateSections(emptyList())
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error?.message).contains("must not be empty")
    }

    @Test
    fun resolveTemplateSelection_shouldFallbackWhenTemplateUnavailable() {
        val resolved = ClangdTestScreenSupport.resolveTemplateSelection(
            selectedTemplate = LspJsonTemplate.MEMORY_USAGE,
            templates = listOf(LspJsonTemplate.REFERENCES, LspJsonTemplate.CODE_ACTION)
        )

        assertThat(resolved).isEqualTo(LspJsonTemplate.REFERENCES)
        assertThat(ClangdTestScreenSupport.resolveTemplateSelection(null)).isNull()
    }

    @Test
    fun buildTemplateJson_shouldUseParsedParamsForGenerator() {
        var capturedTemplate: LspJsonTemplate? = null
        var capturedParams: LspTestParams? = null

        val json = ClangdTestScreenSupport.buildTemplateJson(
            template = LspJsonTemplate.RENAME,
            fileUri = "file:///tmp/main.cpp",
            line = "bad",
            column = "9",
            generateJson = { template, params ->
                capturedTemplate = template
                capturedParams = params
                """{"template":"${template.name}","line":${params.line},"column":${params.column}}"""
            }
        )

        assertThat(capturedTemplate).isEqualTo(LspJsonTemplate.RENAME)
        assertThat(capturedParams?.fileUri).isEqualTo("file:///tmp/main.cpp")
        assertThat(capturedParams?.line).isEqualTo(0)
        assertThat(capturedParams?.column).isEqualTo(9)
        assertThat(json).isEqualTo("""{"template":"RENAME","line":0,"column":9}""")
    }

    @Test
    fun resolveResultTone_shouldMapSuccessAndFailureConsistently() {
        assertThat(ClangdTestScreenSupport.resolveResultTone(success = true))
            .isEqualTo(ClangdResultTone.SUCCESS)
        assertThat(ClangdTestScreenSupport.resolveResultTone(success = false))
            .isEqualTo(ClangdResultTone.FAILURE)
    }

    @Test
    fun buildBenchmarkMetrics_shouldFormatValuesAndClampSuccessRate() {
        val metrics = ClangdTestScreenSupport.buildBenchmarkMetrics(
            BenchmarkResult(
                scenario = LspTestScenario.COMPLETION,
                iterations = 10,
                minTimeMs = 5,
                maxTimeMs = 25,
                avgTimeMs = 12.345,
                medianTimeMs = 11,
                p95TimeMs = 20,
                successRate = 1.2
            )
        )

        assertThat(metrics.avgTimeLabel).isEqualTo("12.35ms")
        assertThat(metrics.p95TimeLabel).isEqualTo("20ms")
        assertThat(metrics.successRateLabel).isEqualTo("100.0%")
    }

    @Test
    fun buildBatchMetrics_shouldTreatEightyPercentAsHealthyBoundary() {
        val metrics = ClangdTestScreenSupport.buildBatchMetrics(
            BatchTestResult(
                totalTests = 5,
                passedTests = 4,
                failedTests = 1,
                skippedTests = 0,
                totalDurationMs = 321,
                results = emptyList()
            )
        )

        assertThat(metrics.tone).isEqualTo(ClangdResultTone.SUCCESS)
        assertThat(metrics.totalTestsLabel).isEqualTo("5")
        assertThat(metrics.passedTestsLabel).isEqualTo("4")
        assertThat(metrics.failedTestsLabel).isEqualTo("1")
        assertThat(metrics.durationLabel).isEqualTo("321ms")
        assertThat(metrics.successRateLabel).isEqualTo("80.0%")
    }

    @Test
    fun buildBatchMetrics_shouldFlagLowSuccessRateAsFailure() {
        val metrics = ClangdTestScreenSupport.buildBatchMetrics(
            BatchTestResult(
                totalTests = 4,
                passedTests = 3,
                failedTests = 1,
                skippedTests = 0,
                totalDurationMs = 99,
                results = emptyList()
            )
        )

        assertThat(metrics.tone).isEqualTo(ClangdResultTone.FAILURE)
        assertThat(metrics.successRateLabel).isEqualTo("75.0%")
    }

    @Test
    fun buildBatchSuiteOptions_shouldRejectEmptySuiteList() {
        val error = runCatching {
            ClangdTestScreenSupport.buildBatchSuiteOptions(emptyList())
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error?.message).contains("must not be empty")
    }

    @Test
    fun buildValidationOutcome_shouldEmitValidSuccessJsonWithoutErrorField() {
        val validationResult = LspTestManager.LspValidationResult(
            isValidJson = true,
            hasJsonRpc = true,
            hasId = true,
            hasMethod = true,
            hasParams = true,
            methodMatches = true
        )

        val outcome = ClangdTestScreenSupport.buildValidationOutcome(
            scenario = LspTestScenario.COMPLETION,
            validationResult = validationResult
        )
        val json = Json.parseToJsonElement(outcome.responseJson).jsonObject

        assertThat(outcome.testStatus).isEqualTo(TestStatus.SUCCESS)
        assertThat(outcome.errorMessage).isNull()
        assertThat(outcome.success).isTrue()
        assertThat(json.getValue("validationMode").jsonPrimitive.content)
            .isEqualTo("JSON_FORMAT_VALIDATION")
        assertThat(json.getValue("method").jsonPrimitive.content)
            .isEqualTo(LspTestScenario.COMPLETION.method)
        assertThat(json.getValue("result").jsonPrimitive.content).isEqualTo("PASS")
        assertThat(json.containsKey("error")).isFalse()

        val validation = json.getValue("validation").jsonObject
        assertThat(validation.getValue("isValidJson").jsonPrimitive.boolean).isTrue()
        assertThat(validation.getValue("hasJsonRpc").jsonPrimitive.boolean).isTrue()
        assertThat(validation.getValue("hasId").jsonPrimitive.boolean).isTrue()
        assertThat(validation.getValue("hasMethod").jsonPrimitive.boolean).isTrue()
        assertThat(validation.getValue("hasParams").jsonPrimitive.boolean).isTrue()
        assertThat(validation.getValue("methodMatches").jsonPrimitive.boolean).isTrue()
    }

    @Test
    fun buildValidationOutcome_shouldEmitValidFailureJsonAndErrorStatus() {
        val validationResult = LspTestManager.LspValidationResult(
            isValidJson = true,
            hasJsonRpc = true,
            hasId = false,
            hasMethod = true,
            hasParams = false,
            methodMatches = false,
            errorMessage = "method mismatch"
        )

        val outcome = ClangdTestScreenSupport.buildValidationOutcome(
            scenario = LspTestScenario.HOVER,
            validationResult = validationResult
        )
        val json = Json.parseToJsonElement(outcome.responseJson).jsonObject

        assertThat(outcome.testStatus).isEqualTo(TestStatus.ERROR)
        assertThat(outcome.errorMessage).isEqualTo("method mismatch")
        assertThat(outcome.success).isFalse()
        assertThat(json.getValue("result").jsonPrimitive.content).isEqualTo("FAIL")
        assertThat(json.getValue("error").jsonPrimitive.content).isEqualTo("method mismatch")
        assertThat(json.getValue("validation").jsonObject.getValue("methodMatches").jsonPrimitive.boolean)
            .isFalse()
        assertThat(json.getValue("validation").jsonObject.getValue("hasParams").jsonPrimitive.boolean)
            .isFalse()
    }

    @Test
    fun buildHistoryResult_shouldPreserveSuccessAndFailureStates() {
        val successOutcome = ClangdTestExecutionOutcome(
            responseJson = """{"ok":true}""",
            testStatus = TestStatus.SUCCESS
        )
        val failureOutcome = ClangdTestExecutionOutcome(
            responseJson = """{"ok":false}""",
            testStatus = TestStatus.ERROR,
            errorMessage = "validation failed"
        )

        val successResult = ClangdTestScreenSupport.buildHistoryResult(
            timestamp = 100L,
            scenario = LspTestScenario.COMPLETION,
            requestJson = """{"id":1}""",
            outcome = successOutcome,
            durationMs = 20L
        )
        val failureResult = ClangdTestScreenSupport.buildHistoryResult(
            timestamp = 200L,
            scenario = LspTestScenario.HOVER,
            requestJson = """{"id":2}""",
            outcome = failureOutcome,
            durationMs = 30L
        )

        assertThat(successResult.success).isTrue()
        assertThat(successResult.errorMessage).isNull()
        assertThat(successResult.responseJson).isEqualTo("""{"ok":true}""")
        assertThat(failureResult.success).isFalse()
        assertThat(failureResult.errorMessage).isEqualTo("validation failed")
        assertThat(failureResult.responseJson).isEqualTo("""{"ok":false}""")
    }

    @Test
    fun buildRealLspUnavailableOutcome_shouldReturnParsableErrorJson() {
        val reason = "No matching editor tab is open for the requested file URI."
        val outcome = ClangdTestScreenSupport.buildRealLspUnavailableOutcome(reason)
        val json = Json.parseToJsonElement(outcome.responseJson).jsonObject

        assertThat(outcome.testStatus).isEqualTo(TestStatus.ERROR)
        assertThat(outcome.errorMessage).isEqualTo(reason)
        assertThat(outcome.success).isFalse()
        assertThat(json.getValue("mode").jsonPrimitive.content).isEqualTo("REAL_LSP")
        assertThat(json.getValue("result").jsonPrimitive.content).isEqualTo("UNAVAILABLE")
        assertThat(json.getValue("error").jsonPrimitive.content)
            .isEqualTo(reason)
        assertThat(json.getValue("hint").jsonPrimitive.content)
            .contains("editor LSP status")
    }

    @Test
    fun buildEditorFixtureFileUri_shouldResolveClangdWorkspaceMainFile() {
        val workspaceDir = Files.createTempDirectory("clangd-editor-fixture").toFile()

        try {
            val uri = ClangdTestScreenSupport.buildEditorFixtureFileUri(
                workspaceDir = workspaceDir,
                fixtures = DevEditorTestCatalog.clangd.fixturesProvider(),
                activeFixtureIndex = DevEditorTestCatalog.clangd.activeFixtureIndex
            )
            val normalizedWorkspace = workspaceDir.absolutePath.replace(File.separatorChar, '/')

            assertThat(uri).startsWith("file:///")
            assertThat(uri).endsWith("/src/main.cpp")
            assertThat(uri).contains(normalizedWorkspace)
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    @Test
    fun clangdCatalogEntry_shouldRegisterOnceAndExposeExpectedFixtures() {
        val entry = DevEditorTestCatalog.clangd
        val fixturePaths = entry.fixturesProvider().map { it.relativePath }

        assertThat(entry.registryId).isEqualTo(DevTestIds.Clangd)
        assertThat(fixturePaths).containsExactly(
            "src/main.cpp",
            "include/math_utils.h",
            "src/math_utils.cpp",
            "compile_commands.json",
            ".clangd"
        ).inOrder()
        assertThat(DevEditorTestCatalog.editorBackedEntries.map { it.registryId })
            .contains(DevTestIds.Clangd)
        assertThat(DevTestRegistry.getAllTests().count { it.id == DevTestIds.Clangd }).isEqualTo(1)
    }
}

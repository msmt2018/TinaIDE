package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.editorlsp.CompletionFetchResult
import com.wuxianggujun.tinaide.core.editorlsp.CompletionItem
import com.wuxianggujun.tinaide.core.editorlsp.CompletionTextEdit
import com.wuxianggujun.tinaide.core.editorlsp.SemanticToken
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.core.lsp.LocationItem
import com.wuxianggujun.tinaide.core.textengine.Position
import com.wuxianggujun.tinaide.testing.lsp.*
import com.wuxianggujun.tinaide.ui.compose.components.EditorStatus
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.state.editor.EditorContainerState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

internal data class ClangdTestExecutionOutcome(
    val responseJson: String,
    val testStatus: TestStatus,
    val errorMessage: String? = null
) {
    val success: Boolean
        get() = testStatus != TestStatus.ERROR
}

internal data class ClangdBatchSuiteOption(
    val id: String,
    val config: TestSuiteConfig
)

internal enum class ClangdResultTone {
    SUCCESS,
    FAILURE,
}

internal data class ClangdTemplateSection(
    val category: TemplateCategory,
    val templates: List<LspJsonTemplate>
)

@Suppress("ktlint:standard:property-naming")
internal data class ClangdBenchmarkMetrics(
    val avgTimeLabel: String,
    val p95TimeLabel: String,
    val successRateLabel: String
)

internal data class ClangdBatchMetrics(
    val tone: ClangdResultTone,
    val totalTestsLabel: String,
    val passedTestsLabel: String,
    val failedTestsLabel: String,
    val durationLabel: String,
    val successRateLabel: String
)

internal object ClangdTestScreenSupport {
    private val prettyJson = Json {
        prettyPrint = true
    }
    private val benchmarkCategories = setOf(TestCategory.BASIC, TestCategory.NAVIGATION)
    private const val HEALTHY_BATCH_SUCCESS_RATE = 0.8

    fun shouldShowParamsSection(scenario: LspTestScenario): Boolean = scenario != LspTestScenario.INITIALIZE && scenario != LspTestScenario.CUSTOM

    fun shouldShowTriggerCharField(scenario: LspTestScenario): Boolean = scenario == LspTestScenario.COMPLETION

    fun shouldShowCustomJsonSection(scenario: LspTestScenario): Boolean = scenario == LspTestScenario.CUSTOM

    fun benchmarkScenarios(): List<LspTestScenario> = LspTestScenario.entries.filter { it.category in benchmarkCategories }

    fun resolveBenchmarkScenario(selectedScenario: LspTestScenario): LspTestScenario {
        val scenarios = benchmarkScenarios()
        return selectedScenario.takeIf { it in scenarios } ?: scenarios.first()
    }

    fun buildTestParams(
        fileUri: String,
        line: String,
        column: String,
        triggerChar: String = "",
        customJson: String = ""
    ): LspTestParams = LspTestParams(
        fileUri = fileUri,
        line = line.toIntOrNull() ?: 0,
        column = column.toIntOrNull() ?: 0,
        triggerChar = triggerChar,
        customJson = customJson
    )

    fun parseBenchmarkIterations(
        input: String,
        fallback: Int = 10
    ): Int {
        val parsedValue = input.trim().toIntOrNull()
        return if (parsedValue != null && parsedValue > 0) {
            parsedValue
        } else {
            fallback
        }
    }

    fun buildProgressPercent(progress: Float): Int = (progress.coerceIn(0f, 1f) * 100).toInt()

    fun buildBatchSuiteOptions(
        suites: List<TestSuiteConfig>
    ): List<ClangdBatchSuiteOption> {
        require(suites.isNotEmpty()) {
            "Clangd batch test suites must not be empty"
        }
        return suites.mapIndexed { index, suite ->
            ClangdBatchSuiteOption(
                id = buildBatchSuiteId(suite, index),
                config = suite
            )
        }
    }

    fun resolveBatchSuite(
        options: List<ClangdBatchSuiteOption>,
        selectedId: String
    ): ClangdBatchSuiteOption {
        val availableOptions = requireBatchSuiteOptions(options)
        return availableOptions.firstOrNull { it.id == selectedId } ?: availableOptions.first()
    }

    fun templateSections(
        templates: List<LspJsonTemplate> = LspJsonTemplate.entries.toList()
    ): List<ClangdTemplateSection> {
        require(templates.isNotEmpty()) {
            "Clangd template list must not be empty"
        }
        return templates
            .groupBy { it.category }
            .map { (category, groupedTemplates) ->
                ClangdTemplateSection(
                    category = category,
                    templates = groupedTemplates
                )
            }
    }

    fun resolveTemplateSelection(
        selectedTemplate: LspJsonTemplate?,
        templates: List<LspJsonTemplate> = LspJsonTemplate.entries.toList()
    ): LspJsonTemplate? {
        if (selectedTemplate == null) {
            return null
        }
        return selectedTemplate.takeIf { it in templates } ?: templates.firstOrNull()
    }

    fun buildTemplateJson(
        template: LspJsonTemplate,
        fileUri: String,
        line: String,
        column: String,
        generateJson: (LspJsonTemplate, LspTestParams) -> String
    ): String = generateJson(
        template,
        buildTestParams(
            fileUri = fileUri,
            line = line,
            column = column
        )
    )

    fun resolveResultTone(success: Boolean): ClangdResultTone = if (success) ClangdResultTone.SUCCESS else ClangdResultTone.FAILURE

    fun buildBenchmarkMetrics(result: BenchmarkResult): ClangdBenchmarkMetrics = ClangdBenchmarkMetrics(
        avgTimeLabel = String.format(Locale.US, "%.2fms", result.avgTimeMs),
        p95TimeLabel = formatDurationMs(result.p95TimeMs),
        successRateLabel = formatSuccessRate(result.successRate)
    )

    fun buildBatchMetrics(result: BatchTestResult): ClangdBatchMetrics = ClangdBatchMetrics(
        tone = if (result.successRate >= HEALTHY_BATCH_SUCCESS_RATE) {
            ClangdResultTone.SUCCESS
        } else {
            ClangdResultTone.FAILURE
        },
        totalTestsLabel = result.totalTests.toString(),
        passedTestsLabel = result.passedTests.toString(),
        failedTestsLabel = result.failedTests.toString(),
        durationLabel = formatDurationMs(result.totalDurationMs),
        successRateLabel = formatSuccessRate(result.successRate)
    )

    fun buildValidationOutcome(
        scenario: LspTestScenario,
        validationResult: LspTestManager.LspValidationResult
    ): ClangdTestExecutionOutcome {
        val responseJson = prettyJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("validationMode", "JSON_FORMAT_VALIDATION")
                put("method", scenario.method)
                put(
                    "validation",
                    buildJsonObject {
                        put("isValidJson", validationResult.isValidJson)
                        put("hasJsonRpc", validationResult.hasJsonRpc)
                        put("hasId", validationResult.hasId)
                        put("hasMethod", validationResult.hasMethod)
                        put("hasParams", validationResult.hasParams)
                        put("methodMatches", validationResult.methodMatches)
                    }
                )
                put("result", if (validationResult.isFullyValid) "PASS" else "FAIL")
                validationResult.errorMessage?.let { put("error", it) }
            }
        )

        return ClangdTestExecutionOutcome(
            responseJson = responseJson,
            testStatus = if (validationResult.isFullyValid) TestStatus.SUCCESS else TestStatus.ERROR,
            errorMessage = validationResult.errorMessage
        )
    }

    fun buildRealLspUnavailableOutcome(reason: String): ClangdTestExecutionOutcome {
        val responseJson = prettyJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("mode", "REAL_LSP")
                put("result", "UNAVAILABLE")
                put("error", reason)
                put("hint", "Open a C/C++ fixture and wait until the editor LSP status is Ready.")
            }
        )

        return ClangdTestExecutionOutcome(
            responseJson = responseJson,
            testStatus = TestStatus.ERROR,
            errorMessage = reason
        )
    }

    fun buildRealLspCompletionOutcome(
        scenario: LspTestScenario,
        result: CompletionFetchResult
    ): ClangdTestExecutionOutcome = when (result) {
        is CompletionFetchResult.Success -> {
            val responseJson = prettyJson.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("mode", "REAL_LSP")
                    put("method", scenario.method)
                    put("result", "PASS")
                    put("itemCount", result.items.size)
                    put(
                        "items",
                        buildJsonArray {
                            result.items.take(50).forEach { item ->
                                addCompletionItem(item)
                            }
                        }
                    )
                    if (result.items.size > 50) {
                        put("truncated", true)
                    }
                }
            )
            ClangdTestExecutionOutcome(responseJson = responseJson, testStatus = TestStatus.SUCCESS)
        }

        is CompletionFetchResult.TransientFailure -> buildRealLspUnavailableOutcome(
            reason = result.reason ?: "Completion request failed before receiving an LSP response."
        )
    }

    fun buildRealLspHoverOutcome(markdown: String?): ClangdTestExecutionOutcome {
        val hasHover = markdown?.isNotBlank() == true
        val responseJson = prettyJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("mode", "REAL_LSP")
                put("method", LspTestScenario.HOVER.method)
                put("result", if (hasHover) "PASS" else "EMPTY")
                put("markdown", markdown.orEmpty())
            }
        )
        return ClangdTestExecutionOutcome(
            responseJson = responseJson,
            testStatus = if (hasHover) TestStatus.SUCCESS else TestStatus.ERROR,
            errorMessage = if (hasHover) null else "Hover response was empty."
        )
    }

    fun buildRealLspLocationsOutcome(
        scenario: LspTestScenario,
        locations: List<LocationItem>
    ): ClangdTestExecutionOutcome {
        val hasLocations = locations.isNotEmpty()
        val responseJson = prettyJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("mode", "REAL_LSP")
                put("method", scenario.method)
                put("result", if (hasLocations) "PASS" else "EMPTY")
                put("locationCount", locations.size)
                put(
                    "locations",
                    buildJsonArray {
                        locations.take(50).forEach { location ->
                            add(
                                buildJsonObject {
                                    put("uri", location.uri)
                                    put("filePath", location.filePath)
                                    put("fileName", location.fileName)
                                    put("line", location.line)
                                    put("column", location.column)
                                    put("endLine", location.endLine)
                                    put("endColumn", location.endColumn)
                                    location.previewText?.let { put("previewText", it) }
                                }
                            )
                        }
                    }
                )
                if (locations.size > 50) {
                    put("truncated", true)
                }
            }
        )
        return ClangdTestExecutionOutcome(
            responseJson = responseJson,
            testStatus = if (hasLocations) TestStatus.SUCCESS else TestStatus.ERROR,
            errorMessage = if (hasLocations) null else "LSP returned no locations."
        )
    }

    fun buildRealLspSemanticTokensOutcome(
        scenario: LspTestScenario,
        tokens: List<SemanticToken>
    ): ClangdTestExecutionOutcome {
        val hasTokens = tokens.isNotEmpty()
        val responseJson = prettyJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("mode", "REAL_LSP")
                put("method", scenario.method)
                put("result", if (hasTokens) "PASS" else "EMPTY")
                put("tokenCount", tokens.size)
                put(
                    "tokens",
                    buildJsonArray {
                        tokens.take(80).forEach { token ->
                            add(
                                buildJsonObject {
                                    put("line", token.line)
                                    put("startColumn", token.startColumn)
                                    put("length", token.length)
                                    put("tokenType", token.tokenType)
                                    put("tokenModifiers", token.tokenModifiers.joinToString())
                                }
                            )
                        }
                    }
                )
                if (tokens.size > 80) {
                    put("truncated", true)
                }
            }
        )
        return ClangdTestExecutionOutcome(
            responseJson = responseJson,
            testStatus = if (hasTokens) TestStatus.SUCCESS else TestStatus.ERROR,
            errorMessage = if (hasTokens) null else "LSP returned no semantic tokens."
        )
    }

    fun buildRealLspCustomOutcome(requestJson: String): ClangdTestExecutionOutcome = buildRealLspUnavailableOutcome(
        reason = "Custom JSON requests cannot be sent through the editor-state LSP facade yet. Generated request was ${requestJson.length} characters."
    )

    suspend fun runRealLspScenario(
        editorState: EditorContainerState,
        scenario: LspTestScenario,
        params: LspTestParams,
        requestJson: String
    ): ClangdTestExecutionOutcome {
        return try {
            val tab = resolveTargetTab(editorState, params.fileUri)
                ?: return buildRealLspUnavailableOutcome("No matching editor tab is open for the requested file URI.")
            val status = editorState.getLspStatus(tab.id)
            if (!status.isInteractiveLspStatus()) {
                return buildRealLspUnavailableOutcome("Active editor LSP status is $status, expected Ready or Busy.")
            }

            val text = editorState.readActiveTabText().orEmpty()
            val lineCount = text.lineSequence().count().coerceAtLeast(1)
            val fullDocumentLines = 0 until lineCount
            val requestedLine = params.line.coerceIn(0, lineCount - 1)
            val requestedColumn = params.column.coerceAtLeast(0)
            val position = Position(requestedLine, requestedColumn)

            when (scenario) {
                LspTestScenario.COMPLETION,
                LspTestScenario.BENCHMARK_COMPLETION -> buildRealLspCompletionOutcome(
                    scenario = scenario,
                    result = editorState.requestLspCompletion(
                        tabId = tab.id,
                        position = position,
                        triggerChar = params.triggerChar.firstOrNull()
                    )
                )

                LspTestScenario.HOVER,
                LspTestScenario.BENCHMARK_HOVER -> buildRealLspHoverOutcome(
                    markdown = editorState.requestLspHoverMarkdown(
                        tabId = tab.id,
                        line = requestedLine,
                        column = requestedColumn
                    )
                )

                LspTestScenario.DEFINITION -> buildRealLspLocationsOutcome(
                    scenario = scenario,
                    locations = editorState.gotoDefinition(tab.id, requestedLine, requestedColumn)
                )

                LspTestScenario.TYPE_DEFINITION -> buildRealLspLocationsOutcome(
                    scenario = scenario,
                    locations = editorState.gotoTypeDefinition(tab.id, requestedLine, requestedColumn)
                )

                LspTestScenario.IMPLEMENTATION -> buildRealLspLocationsOutcome(
                    scenario = scenario,
                    locations = editorState.gotoImplementation(tab.id, requestedLine, requestedColumn)
                )

                LspTestScenario.SEMANTIC_TOKENS_FULL -> buildRealLspSemanticTokensOutcome(
                    scenario = scenario,
                    tokens = editorState.requestLspSemanticTokens(
                        tabId = tab.id,
                        visibleLines = fullDocumentLines,
                        documentVersion = System.nanoTime()
                    )
                )

                LspTestScenario.SEMANTIC_TOKENS_RANGE -> buildRealLspSemanticTokensOutcome(
                    scenario = scenario,
                    tokens = editorState.requestLspSemanticTokens(
                        tabId = tab.id,
                        visibleLines = requestedLine..requestedLine,
                        documentVersion = System.nanoTime()
                    )
                )

                LspTestScenario.CUSTOM -> buildRealLspCustomOutcome(requestJson)

                LspTestScenario.INITIALIZE,
                LspTestScenario.DECLARATION,
                LspTestScenario.INLAY_HINT,
                LspTestScenario.SELECTION_RANGE,
                LspTestScenario.DID_OPEN,
                LspTestScenario.DID_CHANGE,
                LspTestScenario.DID_CLOSE -> buildRealLspUnavailableOutcome(
                    reason = "${scenario.method} is not supported by the editor-state LSP facade yet."
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            buildRealLspUnavailableOutcome(
                reason = e.message ?: e::class.java.simpleName
            )
        }
    }

    fun buildHistoryResult(
        timestamp: Long,
        scenario: LspTestScenario,
        requestJson: String,
        outcome: ClangdTestExecutionOutcome,
        durationMs: Long
    ): LspTestResult = LspTestResult(
        timestamp = timestamp,
        scenario = scenario,
        requestJson = requestJson,
        responseJson = outcome.responseJson,
        errorMessage = outcome.errorMessage,
        durationMs = durationMs,
        success = outcome.success
    )

    fun buildEditorFixtureFileUri(
        workspaceDir: File,
        fixtures: List<DevEditorFixture>,
        activeFixtureIndex: Int
    ): String {
        require(fixtures.isNotEmpty()) {
            "Clangd editor fixtures must not be empty"
        }
        val normalizedIndex = DevEditorTestHostSupport.resolveActiveFixtureIndex(
            fixtures = fixtures,
            requestedIndex = activeFixtureIndex
        ) ?: 0
        return buildWorkspaceFileUri(
            workspaceDir = workspaceDir,
            relativePath = fixtures[normalizedIndex].relativePath
        )
    }

    private fun requireBatchSuiteOptions(
        options: List<ClangdBatchSuiteOption>
    ): List<ClangdBatchSuiteOption> {
        require(options.isNotEmpty()) {
            "Clangd batch test suites must not be empty"
        }
        return options
    }

    private fun buildBatchSuiteId(
        suite: TestSuiteConfig,
        index: Int
    ): String {
        val scenarioKey = suite.scenarios
            .joinToString(separator = "-") { it.name.lowercase() }
            .ifEmpty { "empty" }
        return "$scenarioKey#$index"
    }

    private fun formatDurationMs(durationMs: Long): String = "${durationMs}ms"

    private fun formatSuccessRate(successRate: Double): String = String.format(Locale.US, "%.1f%%", successRate.coerceIn(0.0, 1.0) * 100)

    private fun buildWorkspaceFileUri(
        workspaceDir: File,
        relativePath: String
    ): String {
        val normalizedPath = File(workspaceDir, relativePath)
            .absolutePath
            .replace(File.separatorChar, '/')
        return if (normalizedPath.startsWith("/")) {
            "file://$normalizedPath"
        } else {
            "file:///$normalizedPath"
        }
    }

    private fun resolveTargetTab(
        editorState: EditorContainerState,
        requestedFileUri: String
    ): com.wuxianggujun.tinaide.ui.compose.components.editor.EditorTabState? {
        val activeIndex = editorState.activeTabIndex
            .takeIf { it in editorState.tabs.indices }
            ?: return null
        val requestedUri = requestedFileUri.trim()
        val targetIndex = if (requestedUri.isBlank()) {
            activeIndex
        } else {
            editorState.tabs.indexOfFirst { tab -> buildFileUri(tab.file) == requestedUri }
                .takeIf { it >= 0 }
                ?: return null
        }
        if (targetIndex != activeIndex) {
            editorState.selectTab(targetIndex)
        }
        return editorState.tabs.getOrNull(targetIndex)
    }

    private fun buildFileUri(file: File): String {
        val normalizedPath = file.absolutePath.replace(File.separatorChar, '/')
        return if (normalizedPath.startsWith("/")) {
            "file://$normalizedPath"
        } else {
            "file:///$normalizedPath"
        }
    }

    private fun EditorStatus.isInteractiveLspStatus(): Boolean = this == EditorStatus.Ready || this == EditorStatus.Busy

    private fun kotlinx.serialization.json.JsonArrayBuilder.addCompletionItem(item: CompletionItem) {
        add(
            buildJsonObject {
                put("label", item.label)
                put("kind", item.kind.name)
                put("source", item.source.name)
                item.detail?.let { put("detail", it) }
                item.documentation?.let { put("documentation", it) }
                item.insertText?.let { put("insertText", it) }
                item.snippetText?.let { put("snippetText", it) }
                item.sortText?.let { put("sortText", it) }
                item.filterText?.let { put("filterText", it) }
                item.textEdit?.let { edit ->
                    put("textEdit", edit.toJsonObject())
                }
                if (item.additionalTextEdits.isNotEmpty()) {
                    put(
                        "additionalTextEdits",
                        buildJsonArray {
                            item.additionalTextEdits.forEach { edit -> add(edit.toJsonObject()) }
                        }
                    )
                }
            }
        )
    }

    private fun CompletionTextEdit.toJsonObject(): JsonObject = buildJsonObject {
        put("startLine", startLine)
        put("startColumn", startColumn)
        put("endLine", endLine)
        put("endColumn", endColumn)
        put("newText", newText)
    }
}

/**
 * Clangd LSP 测试页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClangdTestScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scenario = remember { DevEditorTestCatalog.clangd }
    val title = stringResource(scenario.titleRes)
    val description = stringResource(scenario.descriptionRes)
    val fixtures = remember { scenario.fixturesProvider() }
    val testManager = remember(appContext) { LspTestManager(appContext) }
    val testHistory by testManager.testHistory.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 测试状态
    var selectedScenario by remember { mutableStateOf(LspTestScenario.COMPLETION) }
    var testStatus by remember { mutableStateOf(TestStatus.IDLE) }

    // 测试模式：true = 仅生成JSON（验证格式），false = 发送到真实LSP（需要LSP连接）
    var generateOnlyMode by remember { mutableStateOf(true) }

    // 测试参数
    var fileUri by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("10") }
    var column by remember { mutableStateOf("5") }
    var triggerChar by remember { mutableStateOf(".") }
    var customJson by remember { mutableStateOf("") }

    // 测试结果显示
    var currentRequestJson by remember { mutableStateOf("") }
    var currentResponseJson by remember { mutableStateOf("") }
    var currentError by remember { mutableStateOf("") }

    val runSelectedTest: (EditorContainerState) -> Unit = { editorState ->
        Timber.tag("ClangdTestScreen").d("========== Test button clicked ==========")
        Timber.tag("ClangdTestScreen").d("Mode: ${if (generateOnlyMode) "GENERATE_ONLY" else "SEND_TO_LSP"}")
        Timber.tag("ClangdTestScreen").d("Scenario: ${selectedScenario.getDisplayName(context)}")
        Timber.tag("ClangdTestScreen").d("File URI: $fileUri")
        Timber.tag("ClangdTestScreen").d("Position: line=$line, column=$column")

        val scenarioUnderTest = selectedScenario
        testStatus = TestStatus.SENDING
        val params = ClangdTestScreenSupport.buildTestParams(
            fileUri = fileUri,
            line = line,
            column = column,
            triggerChar = triggerChar,
            customJson = customJson
        )
        val requestJson = testManager.generateTestRequest(scenarioUnderTest, params)
        currentRequestJson = requestJson
        currentError = ""

        Timber.tag("ClangdTestScreen").d("Generated request JSON:")
        Timber.tag("ClangdTestScreen").d(requestJson)

        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val outcome = if (generateOnlyMode) {
                Timber.tag("ClangdTestScreen").d("Generate-only mode: validating JSON format")
                delay(100)
                ClangdTestScreenSupport.buildValidationOutcome(
                    scenario = scenarioUnderTest,
                    validationResult = testManager.validateLspJson(
                        requestJson,
                        scenarioUnderTest
                    )
                )
            } else {
                Timber.tag("ClangdTestScreen").d("Real LSP mode: dispatching request through editor state")
                ClangdTestScreenSupport.runRealLspScenario(
                    editorState = editorState,
                    scenario = scenarioUnderTest,
                    params = params,
                    requestJson = requestJson
                )
            }

            currentResponseJson = outcome.responseJson
            testStatus = outcome.testStatus
            currentError = outcome.errorMessage.orEmpty()

            if (generateOnlyMode) {
                Toast.makeText(
                    context,
                    if (outcome.success) {
                        context.getString(Strings.clangd_test_validation_passed)
                    } else {
                        context.getString(
                            Strings.clangd_test_validation_failed,
                            outcome.errorMessage ?: ""
                        )
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    if (outcome.success) {
                        Strings.clangd_test_real_lsp_request_finished.strOr(context)
                    } else {
                        Strings.clangd_test_real_lsp_request_failed.strOr(
                            context,
                            outcome.errorMessage.orEmpty()
                        )
                    },
                    if (outcome.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
            }

            val duration = System.currentTimeMillis() - startTime
            testManager.recordTestResult(
                ClangdTestScreenSupport.buildHistoryResult(
                    timestamp = System.currentTimeMillis(),
                    scenario = scenarioUnderTest,
                    requestJson = requestJson,
                    outcome = outcome,
                    durationMs = duration
                )
            )
            Timber.tag("ClangdTestScreen").d("Test finished in ${duration}ms")
        }
        Unit
    }
    val clearResults = {
        Timber.tag("ClangdTestScreen").d("Clear history clicked")
        testManager.clearHistory()
        currentRequestJson = ""
        currentResponseJson = ""
        currentError = ""
        testStatus = TestStatus.IDLE
    }

    DevEditorTestHost(
        workspaceKey = scenario.workspaceKey,
        title = title,
        fixtures = fixtures,
        onNavigateBack = onNavigateBack,
        activeFixtureIndex = scenario.activeFixtureIndex,
        headerContent = { workspaceDir, editorState ->
            LaunchedEffect(workspaceDir, fixtures, scenario.activeFixtureIndex) {
                fileUri = ClangdTestScreenSupport.buildEditorFixtureFileUri(
                    workspaceDir = workspaceDir,
                    fixtures = fixtures,
                    activeFixtureIndex = scenario.activeFixtureIndex
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 可滚动的配置内容
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        // 场景选择（使用紧凑的横向布局）
                        Text(
                            text = stringResource(Strings.clangd_test_scenario),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // 紧凑的场景选择列表
                        LspTestScenario.entries.forEach { scenario ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedScenario = scenario }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedScenario == scenario,
                                    onClick = { selectedScenario = scenario },
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = scenario.getDisplayName(context),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // 参数配置（紧凑版）
                        if (ClangdTestScreenSupport.shouldShowParamsSection(selectedScenario)) {
                            Text(
                                text = stringResource(Strings.clangd_test_params),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = fileUri,
                                onValueChange = { fileUri = it },
                                label = { Text(stringResource(Strings.clangd_test_file_uri)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = line,
                                    onValueChange = { line = it },
                                    label = { Text(stringResource(Strings.clangd_test_line)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = column,
                                    onValueChange = { column = it },
                                    label = { Text(stringResource(Strings.clangd_test_column)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            if (ClangdTestScreenSupport.shouldShowTriggerCharField(selectedScenario)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = triggerChar,
                                    onValueChange = { triggerChar = it },
                                    label = { Text(stringResource(Strings.clangd_test_trigger_char)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        // 自定义 JSON 输入
                        if (ClangdTestScreenSupport.shouldShowCustomJsonSection(selectedScenario)) {
                            // 模板选择器
                            var showTemplateDialog by remember { mutableStateOf(false) }
                            var selectedTemplate by remember { mutableStateOf<LspJsonTemplate?>(null) }
                            val effectiveSelectedTemplate = remember(selectedTemplate) {
                                ClangdTestScreenSupport.resolveTemplateSelection(selectedTemplate)
                            }

                            Text(
                                text = stringResource(Strings.clangd_test_custom_json_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 模板选择按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showTemplateDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = effectiveSelectedTemplate?.getDisplayName(context)
                                            ?: stringResource(Strings.clangd_test_select_template),
                                        maxLines = 1
                                    )
                                }

                                // 应用模板按钮
                                if (effectiveSelectedTemplate != null) {
                                    Button(
                                        onClick = {
                                            customJson = ClangdTestScreenSupport.buildTemplateJson(
                                                template = effectiveSelectedTemplate,
                                                fileUri = fileUri,
                                                line = line,
                                                column = column,
                                                generateJson = testManager::generateTemplateJson
                                            )
                                        }
                                    ) {
                                        Text(stringResource(Strings.btn_apply))
                                    }
                                }
                            }

                            // 模板选择对话框
                            if (showTemplateDialog) {
                                TemplateSelectionDialog(
                                    onDismiss = { showTemplateDialog = false },
                                    onTemplateSelected = { template ->
                                        selectedTemplate = template
                                        showTemplateDialog = false
                                        customJson = ClangdTestScreenSupport.buildTemplateJson(
                                            template = template,
                                            fileUri = fileUri,
                                            line = line,
                                            column = column,
                                            generateJson = testManager::generateTemplateJson
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 参数配置（用于模板）
                            Text(
                                text = stringResource(Strings.clangd_test_template_params),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = fileUri,
                                onValueChange = { fileUri = it },
                                label = { Text(stringResource(Strings.clangd_test_file_uri)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = line,
                                    onValueChange = { line = it },
                                    label = { Text(stringResource(Strings.clangd_test_line)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = column,
                                    onValueChange = { column = it },
                                    label = { Text(stringResource(Strings.clangd_test_column)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = customJson,
                                onValueChange = { customJson = it },
                                label = { Text(stringResource(Strings.clangd_test_custom_json_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    } // 结束可滚动的配置内容 Column

                    // 固定在底部的按钮区域
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { runSelectedTest(editorState) },
                            modifier = Modifier.weight(1f),
                            enabled = testStatus != TestStatus.SENDING
                        ) {
                            if (testStatus == TestStatus.SENDING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                when {
                                    testStatus == TestStatus.SENDING -> stringResource(Strings.clangd_test_status_sending)
                                    generateOnlyMode -> stringResource(Strings.clangd_test_generate_json)
                                    else -> stringResource(Strings.clangd_test_send_request)
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = clearResults,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(Strings.clangd_test_clear_history))
                        }
                    }
                }
            }
        },
        footerContent = { _, _ ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(4f / 3f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Tab 切换
                    var selectedTab by remember { mutableIntStateOf(0) }
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(Strings.clangd_test_request)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(Strings.clangd_test_response)) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("${stringResource(Strings.clangd_test_history)} (${testHistory.size})") }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text(stringResource(Strings.clangd_test_tab_benchmark)) }
                        )
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text(stringResource(Strings.clangd_test_tab_batch)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (selectedTab) {
                        0 -> {
                            // 请求 JSON
                            JsonDisplayCard(
                                title = stringResource(Strings.clangd_test_request),
                                jsonContent = currentRequestJson,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        1 -> {
                            // 响应 JSON
                            if (currentError.isNotEmpty()) {
                                ErrorDisplayCard(
                                    error = currentError,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                JsonDisplayCard(
                                    title = stringResource(Strings.clangd_test_response),
                                    jsonContent = currentResponseJson,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        2 -> {
                            // 历史记录
                            if (testHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(Strings.clangd_test_no_history),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(testHistory) { result ->
                                        TestHistoryItem(result = result)
                                    }
                                }
                            }
                        }
                        3 -> {
                            // 性能基准测试
                            BenchmarkTestTab(
                                testManager = testManager,
                                params = ClangdTestScreenSupport.buildTestParams(
                                    fileUri = fileUri,
                                    line = line,
                                    column = column
                                )
                            )
                        }
                        4 -> {
                            // 批量测试套件
                            BatchTestTab(
                                testManager = testManager,
                                params = ClangdTestScreenSupport.buildTestParams(
                                    fileUri = fileUri,
                                    line = line,
                                    column = column
                                )
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * JSON 显示卡片
 */
@Composable
fun JsonDisplayCard(
    title: String,
    jsonContent: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (jsonContent.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Strings.clangd_test_click_send_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = jsonContent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * 错误显示卡片
 */
@Composable
fun ErrorDisplayCard(
    error: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = error,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

/**
 * 测试历史记录项
 */
@Composable
fun TestHistoryItem(result: LspTestResult) {
    val context = LocalContext.current
    val tone = remember(result.success) {
        ClangdTestScreenSupport.resolveResultTone(result.success)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tone == ClangdResultTone.SUCCESS) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.scenario.getDisplayName(context),
                    fontWeight = FontWeight.Bold,
                    color = if (tone == ClangdResultTone.SUCCESS) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Text(
                    text = result.getFormattedTime(),
                    fontSize = 12.sp,
                    color = if (tone == ClangdResultTone.SUCCESS) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.getStatusText(context),
                fontSize = 12.sp,
                color = if (tone == ClangdResultTone.SUCCESS) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

/**
 * 模板选择对话框
 */
@Composable
fun TemplateSelectionDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (LspJsonTemplate) -> Unit
) {
    val context = LocalContext.current
    val templateSections = remember {
        ClangdTestScreenSupport.templateSections()
    }

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TinaDialogTitleText(stringResource(Strings.clangd_test_select_template_dialog_title))
        },
        text = {
            TinaDialogContentColumn {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templateSections.forEach { section ->
                        item {
                            Text(
                                text = section.category.getDisplayName(context),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(section.templates) { template ->
                            TemplateItem(
                                template = template,
                                onClick = { onTemplateSelected(template) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_cancel),
                onClick = onDismiss
            )
        }
    )
}

/**
 * 模板列表项
 */
@Composable
fun TemplateItem(
    template: LspJsonTemplate,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.getDisplayName(context),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = template.method,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = template.getDescription(context),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 性能基准测试标签页
 */
@Composable
fun BenchmarkTestTab(
    testManager: LspTestManager,
    params: LspTestParams
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val benchmarkResults by testManager.benchmarkResults.collectAsState()
    val isRunning by testManager.isRunning.collectAsState()
    val testProgress by testManager.testProgress.collectAsState()

    val benchmarkScenarios = remember { ClangdTestScreenSupport.benchmarkScenarios() }
    var selectedScenario by remember {
        mutableStateOf(ClangdTestScreenSupport.resolveBenchmarkScenario(LspTestScenario.COMPLETION))
    }
    var iterations by remember { mutableStateOf("10") }
    val effectiveSelectedScenario = remember(selectedScenario) {
        ClangdTestScreenSupport.resolveBenchmarkScenario(selectedScenario)
    }
    val progressPercent = ClangdTestScreenSupport.buildProgressPercent(testProgress)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Strings.clangd_test_benchmark_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 场景选择
        Text(
            text = stringResource(Strings.clangd_test_scenario),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        benchmarkScenarios.forEach { scenario ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedScenario = scenario }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = effectiveSelectedScenario == scenario,
                    onClick = { selectedScenario = scenario }
                )
                Text(text = scenario.getDisplayName(context))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 迭代次数
        OutlinedTextField(
            value = iterations,
            onValueChange = { iterations = it },
            label = { Text(stringResource(Strings.clangd_test_benchmark_iterations)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 运行按钮
        Button(
            onClick = {
                val iterCount = ClangdTestScreenSupport.parseBenchmarkIterations(iterations)
                coroutineScope.launch {
                    try {
                        testManager.runBenchmark(effectiveSelectedScenario, params, iterCount)
                        Toast.makeText(context, Strings.clangd_test_toast_benchmark_done.strOr(context), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, Strings.clangd_test_toast_failed.strOr(context, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${stringResource(Strings.clangd_test_benchmark_running)} $progressPercent%")
            } else {
                Text(stringResource(Strings.clangd_test_benchmark_run))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 结果显示
        if (benchmarkResults.isNotEmpty()) {
            Text(
                text = stringResource(Strings.clangd_test_benchmark_results),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            benchmarkResults.forEach { result ->
                BenchmarkResultCard(result = result)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 基准测试结果卡片
 */
@Composable
fun BenchmarkResultCard(result: BenchmarkResult) {
    val context = LocalContext.current
    val metrics = remember(result) {
        ClangdTestScreenSupport.buildBenchmarkMetrics(result)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = result.scenario.getDisplayName(context),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(Strings.clangd_test_benchmark_avg),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = metrics.avgTimeLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column {
                    Text(
                        text = stringResource(Strings.clangd_test_benchmark_p95),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = metrics.p95TimeLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column {
                    Text(
                        text = stringResource(Strings.clangd_test_benchmark_success_rate),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = metrics.successRateLabel,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 批量测试标签页
 */
@Composable
fun BatchTestTab(
    testManager: LspTestManager,
    params: LspTestParams
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val batchTestResult by testManager.batchTestResult.collectAsState()
    val isRunning by testManager.isRunning.collectAsState()
    val testProgress by testManager.testProgress.collectAsState()

    val suiteOptions = remember(context) {
        ClangdTestScreenSupport.buildBatchSuiteOptions(
            PredefinedTestSuites.getAllSuites(context)
        )
    }
    var selectedSuiteId by remember { mutableStateOf(suiteOptions.first().id) }
    var currentTestName by remember { mutableStateOf("") }
    val selectedSuite = remember(suiteOptions, selectedSuiteId) {
        ClangdTestScreenSupport.resolveBatchSuite(
            options = suiteOptions,
            selectedId = selectedSuiteId
        )
    }
    val progressPercent = ClangdTestScreenSupport.buildProgressPercent(testProgress)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(Strings.clangd_test_batch_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 测试套件选择
        Text(
            text = stringResource(Strings.clangd_test_batch_select_suite),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        suiteOptions.forEach { suiteOption ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedSuiteId = suiteOption.id }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedSuiteId == suiteOption.id,
                    onClick = { selectedSuiteId = suiteOption.id }
                )
                Column {
                    Text(text = suiteOption.config.name)
                    Text(
                        text = stringResource(
                            Strings.clangd_test_suite_test_count,
                            suiteOption.config.scenarios.size
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 运行按钮
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        testManager.runTestSuite(
                            config = selectedSuite.config,
                            params = params,
                            onProgress = { progress, testName ->
                                currentTestName = testName
                            }
                        )
                        Toast.makeText(context, Strings.clangd_test_toast_batch_done.strOr(context), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, Strings.clangd_test_toast_failed.strOr(context, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${stringResource(Strings.clangd_test_batch_running)} $progressPercent%")
            } else {
                Text(stringResource(Strings.clangd_test_batch_run))
            }
        }

        if (isRunning && currentTestName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Strings.clangd_test_current_test, currentTestName),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 结果显示
        batchTestResult?.let { result ->
            Text(
                text = stringResource(Strings.clangd_test_batch_results),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            BatchTestResultCard(result = result)
        }
    }
}

/**
 * 批量测试结果卡片
 */
@Composable
fun BatchTestResultCard(result: BatchTestResult) {
    val metrics = remember(result) {
        ClangdTestScreenSupport.buildBatchMetrics(result)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (metrics.tone == ClangdResultTone.SUCCESS) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(Strings.clangd_test_batch_total),
                        fontSize = 12.sp
                    )
                    Text(
                        text = metrics.totalTestsLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Column {
                    Text(
                        text = stringResource(Strings.clangd_test_batch_passed),
                        fontSize = 12.sp
                    )
                    Text(
                        text = metrics.passedTestsLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = stringResource(Strings.clangd_test_batch_failed),
                        fontSize = 12.sp
                    )
                    Text(
                        text = metrics.failedTestsLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stringResource(Strings.clangd_test_batch_duration)}: ${metrics.durationLabel}",
                fontSize = 12.sp
            )

            Text(
                text = stringResource(
                    Strings.clangd_test_success_rate_label,
                    metrics.successRateLabel.removeSuffix("%")
                ),
                fontSize = 12.sp
            )
        }
    }
}

package com.wuxianggujun.tinaide.testing.lsp

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class LspTestManagerTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication().applicationContext as Context

    @Test
    fun generateTestRequest_shouldProduceValidJsonForBuiltInScenarios() {
        val manager = LspTestManager(context)
        val params = defaultParams(customJson = """{"jsonrpc":"2.0","method":"custom"}""")

        LspTestScenario.entries
            .filter { it != LspTestScenario.CUSTOM }
            .forEach { scenario ->
                val requestJson = manager.generateTestRequest(scenario, params)
                val validation = manager.validateLspJson(requestJson, scenario)

                assertThat(validation.isFullyValid).isTrue()
                assertThat(validation.hasJsonRpc).isTrue()
                assertThat(validation.hasMethod).isTrue()
                if (scenario in notificationScenarios) {
                    assertThat(validation.hasId).isFalse()
                } else {
                    assertThat(validation.hasId).isTrue()
                }
            }
    }

    @Test
    fun generateTemplateJson_shouldKeepMethodAndJsonRpcStable() {
        val manager = LspTestManager(context)
        val params = defaultParams()
        val templates = listOf(
            LspJsonTemplate.REFERENCES,
            LspJsonTemplate.DID_OPEN,
            LspJsonTemplate.MEMORY_USAGE,
            LspJsonTemplate.SYMBOL_INFO
        )

        templates.forEach { template ->
            val json = JSONObject(manager.generateTemplateJson(template, params))

            assertThat(json.getString("jsonrpc")).isEqualTo("2.0")
            assertThat(json.getString("method")).isEqualTo(template.method)
        }
    }

    @Test
    fun runTestSuite_shouldStopAfterFirstFailureWhenConfigured() = runBlocking {
        val manager = LspTestManager(context)
        val progressNames = mutableListOf<String>()
        val config = TestSuiteConfig(
            name = "stop-on-first-failure",
            scenarios = listOf(
                LspTestScenario.COMPLETION,
                LspTestScenario.CUSTOM,
                LspTestScenario.HOVER
            ),
            iterations = 1,
            delayBetweenTests = 0,
            stopOnFirstFailure = true
        )

        val result = manager.runTestSuite(
            config = config,
            params = defaultParams(customJson = "{"),
            onProgress = { _, name -> progressNames += name }
        )

        assertThat(result.totalTests).isEqualTo(2)
        assertThat(result.passedTests).isEqualTo(1)
        assertThat(result.failedTests).isEqualTo(1)
        assertThat(result.results.map { it.scenario }).containsExactly(
            LspTestScenario.COMPLETION,
            LspTestScenario.CUSTOM
        ).inOrder()
        assertThat(progressNames).containsExactly(
            LspTestScenario.COMPLETION.getDisplayName(context),
            LspTestScenario.CUSTOM.getDisplayName(context)
        ).inOrder()
    }

    @Test
    fun recordTestResult_shouldKeepNewestEntriesAndClearState() = runBlocking {
        val manager = LspTestManager(context)

        repeat(55) { index ->
            manager.recordTestResult(
                LspTestResult(
                    timestamp = index.toLong(),
                    scenario = LspTestScenario.COMPLETION,
                    requestJson = """{"id":$index}""",
                    responseJson = null,
                    errorMessage = null,
                    durationMs = index.toLong(),
                    success = index % 2 == 0
                )
            )
        }

        assertThat(manager.getHistoryCount()).isEqualTo(50)
        assertThat(manager.testHistory.value.first().timestamp).isEqualTo(54L)
        assertThat(manager.testHistory.value.last().timestamp).isEqualTo(5L)

        manager.runBenchmark(
            scenario = LspTestScenario.COMPLETION,
            params = defaultParams(),
            iterations = 1
        )
        manager.runTestSuite(
            config = TestSuiteConfig(
                name = "single-pass",
                scenarios = listOf(LspTestScenario.HOVER),
                iterations = 1,
                delayBetweenTests = 0
            ),
            params = defaultParams()
        )

        assertThat(manager.benchmarkResults.value).isNotEmpty()
        assertThat(manager.batchTestResult.value).isNotNull()

        manager.clearHistory()

        assertThat(manager.testHistory.value).isEmpty()
        assertThat(manager.benchmarkResults.value).isEmpty()
        assertThat(manager.batchTestResult.value).isNull()
    }

    @Test
    fun parseDiagnostics_shouldCountSeverityBuckets() {
        val manager = LspTestManager(context)
        val diagnosticsJson = """
            {
              "params": {
                "uri": "file:///workspace/main.cpp",
                "diagnostics": [
                  {
                    "severity": 1,
                    "message": "fatal",
                    "source": "clangd",
                    "range": { "start": { "line": 2, "character": 5 } }
                  },
                  {
                    "severity": 2,
                    "message": "warn",
                    "range": { "start": { "line": 4, "character": 1 } }
                  },
                  {
                    "severity": 4,
                    "message": "hint",
                    "range": { "start": { "line": 8, "character": 0 } }
                  }
                ]
              }
            }
        """.trimIndent()

        val info = manager.parseDiagnostics(diagnosticsJson)

        assertThat(info).isNotNull()
        assertThat(info!!.uri).isEqualTo("file:///workspace/main.cpp")
        assertThat(info.errorCount).isEqualTo(1)
        assertThat(info.warningCount).isEqualTo(1)
        assertThat(info.infoCount).isEqualTo(0)
        assertThat(info.hintCount).isEqualTo(1)
        assertThat(info.totalCount).isEqualTo(3)
        assertThat(info.diagnostics.first().source).isEqualTo("clangd")
    }

    @Test
    fun predefinedSuites_shouldExcludeCustomAndBenchmarkFromFullSuite() {
        val suites = PredefinedTestSuites.getAllSuites(context)
        val fullSuite = PredefinedTestSuites.getFullSuite(context)

        assertThat(suites).hasSize(6)
        assertThat(fullSuite.scenarios).doesNotContain(LspTestScenario.CUSTOM)
        assertThat(fullSuite.scenarios).doesNotContain(LspTestScenario.BENCHMARK_COMPLETION)
        assertThat(fullSuite.scenarios).doesNotContain(LspTestScenario.BENCHMARK_HOVER)
        assertThat(PredefinedTestSuites.getDocumentSyncSuite(context).scenarios).containsExactly(
            LspTestScenario.DID_OPEN,
            LspTestScenario.DID_CHANGE,
            LspTestScenario.DID_CLOSE
        ).inOrder()
    }

    private fun defaultParams(customJson: String = """{"jsonrpc":"2.0","method":"noop"}"""): LspTestParams {
        return LspTestParams(
            fileUri = "file:///workspace/main.cpp",
            line = 4,
            column = 7,
            triggerChar = ".",
            customJson = customJson
        )
    }

    private companion object {
        private val notificationScenarios = setOf(
            LspTestScenario.DID_OPEN,
            LspTestScenario.DID_CHANGE,
            LspTestScenario.DID_CLOSE
        )
    }
}

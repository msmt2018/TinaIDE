package com.wuxianggujun.tinaide.plugin.script.api

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.plugin.PluginConfiguration
import com.wuxianggujun.tinaide.plugin.PluginConfigurationProperty
import com.wuxianggujun.tinaide.plugin.PluginConfigurationStore
import com.wuxianggujun.tinaide.plugin.PluginManifest
import com.wuxianggujun.tinaide.plugin.script.PluginExecutionResult
import com.wuxianggujun.tinaide.plugin.script.PluginPermissionManager
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginRuntime
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class,
)
class ConfigApiModuleRuntimeTest {

    private lateinit var context: Application
    private lateinit var permissionManager: PluginPermissionManager
    private lateinit var store: PluginConfigurationStore
    private val runtimes = mutableListOf<ScriptPluginRuntime>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        permissionManager = PluginPermissionManager.getInstance(context)
        store = PluginConfigurationStore.getInstance(context)
        context.getSharedPreferences("tina_plugin_configuration", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        runtimes.forEach { runtime -> runtime.destroy() }
        permissionManager.revokeAllPermissions("test.config.runtime")
        context.getSharedPreferences("tina_plugin_configuration", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `config api should read defaults and persist declared values`() = runBlocking {
        val manifest = createManifest("test.config.runtime")
        val runtime = createRuntime(manifest)
        val registry = PluginApiRegistry.createDefaultRegistry(context)

        try {
            initializeRuntimeOrSkip(runtime)
            registry.initializeForRuntime(runtime)

            val defineResult = runtime.execute(
                """
                function exercise_config_api()
                  local before = tina.config.get("feature.enabled")
                  local ok_enabled = tina.config.set("feature.enabled", true)
                  local ok_format = tina.config.set("output.format", "json")
                  local bad_format, bad_format_err = tina.config.set("output.format", "xml")
                  local fallback = tina.config.get("missing.key", "fallback")
                  local jobs = tina.config.get("build.jobs")
                  return tostring(before)
                    .. "|" .. tostring(ok_enabled)
                    .. "|" .. tostring(ok_format)
                    .. "|" .. tostring(bad_format)
                    .. "|" .. tostring(tina.config.get("feature.enabled"))
                    .. "|" .. tina.config.get("output.format")
                    .. "|" .. tostring(math.floor(jobs))
                    .. "|" .. tostring(fallback == nil)
                    .. "|" .. tostring(bad_format_err ~= nil)
                end
                """.trimIndent()
            )
            assertThat(defineResult).isInstanceOf(PluginExecutionResult.Success::class.java)

            val result = runtime.callFunction("exercise_config_api")

            assertThat(result).isEqualTo(
                PluginExecutionResult.Success("false|true|true|false|true|json|2|true|true")
            )
            assertThat(store.getValue(manifest, "feature.enabled")).isEqualTo(JsonPrimitive(true))
            assertThat(store.getValue(manifest, "output.format")).isEqualTo(JsonPrimitive("json"))
        } finally {
            registry.cleanup()
        }
    }

    private fun createRuntime(manifest: PluginManifest): ScriptPluginRuntime = ScriptPluginRuntime(
        context = context,
        manifest = manifest,
        pluginDir = File(context.cacheDir, manifest.id).apply { mkdirs() },
        permissionManager = permissionManager,
    ).also(runtimes::add)

    private fun createManifest(pluginId: String): PluginManifest = PluginManifest(
        id = pluginId,
        name = "Config Runtime Test",
        version = "1.0.0",
        type = "script",
        configuration = PluginConfiguration(
            title = "Config Runtime",
            properties = mapOf(
                "feature.enabled" to PluginConfigurationProperty(
                    type = "boolean",
                    default = JsonPrimitive(false),
                ),
                "output.format" to PluginConfigurationProperty(
                    type = "string",
                    default = JsonPrimitive("text"),
                    enumValues = listOf("text", "json"),
                ),
                "build.jobs" to PluginConfigurationProperty(
                    type = "number",
                    default = JsonPrimitive(2),
                ),
            ),
        ),
    )

    private suspend fun initializeRuntimeOrSkip(runtime: ScriptPluginRuntime) {
        try {
            assertThat(runtime.initialize().isSuccess).isTrue()
        } catch (error: LinkageError) {
            assumeNoException("Lua native runtime unavailable in this test environment", error)
        }
    }
}

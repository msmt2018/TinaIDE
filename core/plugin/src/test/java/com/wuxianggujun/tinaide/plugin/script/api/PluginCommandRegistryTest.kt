package com.wuxianggujun.tinaide.plugin.script.api

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.commands.HostCommandInvocation
import com.wuxianggujun.tinaide.plugin.script.PluginExecutionResult
import com.wuxianggujun.tinaide.plugin.script.ScriptPluginRuntime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test

class PluginCommandRegistryTest {

    @Before
    fun setUp() {
        PluginCommandRegistry.clear()
        PluginCommandRegistry.setRuntimeProvider { null }
    }

    @After
    fun tearDown() {
        PluginCommandRegistry.clear()
        PluginCommandRegistry.setRuntimeProvider { null }
    }

    @Test
    fun `register should reject duplicate command ids across plugins`() {
        val first = PluginCommandRegistry.register(
            pluginId = "plugin.one",
            pluginName = "Plugin One",
            commandId = "plugin.sayHello",
            callbackName = "handleHello"
        )
        val second = PluginCommandRegistry.register(
            pluginId = "plugin.two",
            pluginName = "Plugin Two",
            commandId = "plugin.sayHello",
            callbackName = "handleHelloAgain"
        )

        assertThat(first.isSuccess).isTrue()
        assertThat(second.isFailure).isTrue()
        assertThat(second.exceptionOrNull()?.message).contains("already registered")
    }

    @Test
    fun `dispatch should invoke runtime callback with invocation payload`() {
        val runtime = mockk<ScriptPluginRuntime>()
        coEvery { runtime.callFunction("handleHello", any()) } returns PluginExecutionResult.Success(Unit)
        PluginCommandRegistry.setRuntimeProvider { pluginId ->
            if (pluginId == "plugin.one") runtime else null
        }
        PluginCommandRegistry.register(
            pluginId = "plugin.one",
            pluginName = "Plugin One",
            commandId = "plugin.sayHello",
            callbackName = "handleHello",
            title = "Say hello"
        ).getOrThrow()
        val targetFile = File("C:/workspace/src/Main.kt")

        val dispatched = PluginCommandRegistry.dispatch(
            commandId = "plugin.sayHello",
            invocation = HostCommandInvocation(
                file = targetFile,
                isDirectory = false,
                isDirty = true
            )
        )

        assertThat(dispatched).isTrue()
        coVerify(timeout = 1_000, exactly = 1) {
            runtime.callFunction(
                "handleHello",
                match<Map<String, Any?>> { payload ->
                    payload["commandId"] == "plugin.sayHello" &&
                        payload["filePath"] == targetFile.absolutePath &&
                        payload["fileName"] == targetFile.name &&
                        payload["isDirectory"] == false &&
                        payload["isDirty"] == true
                }
            )
        }
    }

    @Test
    fun `unregisterAll should remove all commands from plugin`() {
        PluginCommandRegistry.register(
            pluginId = "plugin.one",
            pluginName = "Plugin One",
            commandId = "plugin.sayHello",
            callbackName = "handleHello"
        ).getOrThrow()
        PluginCommandRegistry.register(
            pluginId = "plugin.one",
            pluginName = "Plugin One",
            commandId = "plugin.sayBye",
            callbackName = "handleBye"
        ).getOrThrow()

        PluginCommandRegistry.unregisterAll("plugin.one")

        assertThat(PluginCommandRegistry.isRegistered("plugin.sayHello")).isFalse()
        assertThat(PluginCommandRegistry.isRegistered("plugin.sayBye")).isFalse()
    }
}

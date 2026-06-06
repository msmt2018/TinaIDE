package com.wuxianggujun.tinaide.ui.compose.screens.main

import android.app.Application
import com.google.common.truth.Truth.assertThat
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
class MainActivityCommandOrderingTest {

    @Test
    fun `orderMainActivityCommands should put pinned commands before recent and category commands`() {
        val context = RuntimeEnvironment.getApplication()
        val commands = listOf(
            command("view.settings", MainActivityCommandCategory.VIEW),
            command("project.build", MainActivityCommandCategory.BUILD),
            command("editor.format", MainActivityCommandCategory.CODE),
        )

        val ordered = orderMainActivityCommands(
            commands = commands,
            context = context,
            pinnedCommandIds = listOf("view.settings"),
            recentCommandIds = listOf("project.build"),
            query = ""
        )

        assertThat(ordered.map(MainActivityCommand::id))
            .containsExactly("view.settings", "project.build", "editor.format")
            .inOrder()
    }

    @Test
    fun `orderMainActivityCommands should filter disabled commands and match plugin source name`() {
        val context = RuntimeEnvironment.getApplication()
        val commands = listOf(
            command("plugin.command", MainActivityCommandCategory.PLUGIN, sourceName = "Plugin Tools"),
            command("disabled.command", MainActivityCommandCategory.CODE, enabled = false),
        )

        val ordered = orderMainActivityCommands(
            commands = commands,
            context = context,
            pinnedCommandIds = emptyList(),
            recentCommandIds = emptyList(),
            query = "tools"
        )

        assertThat(ordered.map(MainActivityCommand::id))
            .containsExactly("plugin.command")
    }

    @Test
    fun `selectMainActivityOverflowCommands should use pinned commands when present`() {
        val commands = listOf(
            command("view.settings", MainActivityCommandCategory.VIEW),
            command("project.build", MainActivityCommandCategory.BUILD),
        )

        val selected = selectMainActivityOverflowCommands(
            commands = commands,
            pinnedCommandIds = listOf("project.build")
        )

        assertThat(selected.map(MainActivityCommand::id))
            .containsExactly("project.build")
    }

    @Test
    fun `selectMainActivityOverflowCommands should cap top bar commands`() {
        val commands = listOf(
            command("first", MainActivityCommandCategory.VIEW),
            command("second", MainActivityCommandCategory.BUILD),
            command("third", MainActivityCommandCategory.CODE),
            command("fourth", MainActivityCommandCategory.FILE),
        )

        val selected = selectMainActivityOverflowCommands(
            commands = commands,
            pinnedCommandIds = listOf("first", "second", "third", "fourth")
        )

        assertThat(selected.map(MainActivityCommand::id))
            .containsExactly("first", "second", "third")
            .inOrder()
    }

    private fun command(
        id: String,
        category: MainActivityCommandCategory,
        enabled: Boolean = true,
        sourceName: String? = null,
    ): MainActivityCommand {
        return MainActivityCommand(
            id = id,
            title = MainActivityCommandText.Literal(id),
            category = category,
            enabled = enabled,
            sourceName = sourceName,
            execute = {}
        )
    }
}

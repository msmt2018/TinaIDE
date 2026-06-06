package com.wuxianggujun.tinaide.ui.compose.screens.main

import android.app.Application
import android.content.Context
import com.google.common.truth.Truth.assertThat
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
    application = Application::class
)
class MainActivityCommandPreferenceStoreTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(MAIN_ACTIVITY_COMMAND_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `togglePinned should keep newest pinned command first and unpin existing command`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.togglePinned("view.settings")
        store.togglePinned("view.terminal")
        store.togglePinned("view.settings")

        assertThat(store.pinnedCommandIdsFlow.value)
            .containsExactly("view.terminal")
            .inOrder()
    }

    @Test
    fun `recordExecuted should keep recent command ids unique and newest first`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.recordExecuted("project.build")
        store.recordExecuted("view.settings")
        store.recordExecuted("project.build")

        assertThat(store.recentCommandIdsFlow.value)
            .containsExactly("project.build", "view.settings")
            .inOrder()
    }

    @Test
    fun `movePinned should reorder pinned commands and ignore boundaries`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.togglePinned("third")
        store.togglePinned("second")
        store.togglePinned("first")
        store.movePinned("second", MainActivityPinnedCommandMoveDirection.UP)
        store.movePinned("third", MainActivityPinnedCommandMoveDirection.DOWN)

        assertThat(store.pinnedCommandIdsFlow.value)
            .containsExactly("second", "first", "third")
            .inOrder()
    }

    @Test
    fun `movePinned should persist reordered pinned commands`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.togglePinned("second")
        store.togglePinned("first")
        store.movePinned("second", MainActivityPinnedCommandMoveDirection.UP)

        val restoredStore = MainActivityCommandPreferenceStore(context)
        assertThat(restoredStore.pinnedCommandIdsFlow.value)
            .containsExactly("second", "first")
            .inOrder()
    }

    @Test
    fun `store should ignore invalid command ids`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.togglePinned("bad\nid")
        store.recordExecuted("bad\rid")

        assertThat(store.pinnedCommandIdsFlow.value).isEmpty()
        assertThat(store.recentCommandIdsFlow.value).isEmpty()
    }

    @Test
    fun `pruneUnavailablePluginCommands should remove disabled plugin commands`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.togglePinned("view.settings")
        store.togglePinned("pluginToolbar:disabledPlugin:editor:format")
        store.togglePinned("pluginToolbar:enabledPlugin:editor:format")
        store.recordExecuted("project.build")
        store.recordExecuted("pluginToolbar:disabledPlugin:editor:format")
        store.recordExecuted("pluginToolbar:enabledPlugin:editor:format")

        store.pruneUnavailablePluginCommands(setOf("enabledPlugin"))

        assertThat(store.pinnedCommandIdsFlow.value)
            .containsExactly("pluginToolbar:enabledPlugin:editor:format", "view.settings")
            .inOrder()
        assertThat(store.recentCommandIdsFlow.value)
            .containsExactly("pluginToolbar:enabledPlugin:editor:format", "project.build")
            .inOrder()
    }

    @Test
    fun `pluginToolbarPluginIdOrNull should parse plugin toolbar command ids`() {
        assertThat("pluginToolbar:plugin.menu:editor:format".pluginToolbarPluginIdOrNull())
            .isEqualTo("plugin.menu")
        assertThat("view.settings".pluginToolbarPluginIdOrNull()).isNull()
        assertThat("pluginToolbar::editor:format".pluginToolbarPluginIdOrNull()).isNull()
    }
}

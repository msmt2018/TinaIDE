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
    fun `store should ignore invalid command ids`() {
        val store = MainActivityCommandPreferenceStore(context)

        store.togglePinned("bad\nid")
        store.recordExecuted("bad\rid")

        assertThat(store.pinnedCommandIdsFlow.value).isEmpty()
        assertThat(store.recentCommandIdsFlow.value).isEmpty()
    }
}

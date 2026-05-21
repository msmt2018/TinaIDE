package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.app.Application
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class LegacyDevTestRedirectActivityTest {

    @Test
    fun legacyActivities_shouldRedirectToUnifiedDeveloperTestRoute() {
        assertRedirectsToDevTest(ThemePreviewTestActivity::class.java, DevTestIds.ThemePreview)
        assertRedirectsToDevTest(TreeSitterTestActivity::class.java, DevTestIds.TreeSitter)
        assertRedirectsToDevTest(EditorScrollTestActivity::class.java, DevTestIds.EditorScroll)
        assertRedirectsToDevTest(ClangdTestActivity::class.java, DevTestIds.Clangd)
    }

    private fun <T : ComponentActivity> assertRedirectsToDevTest(
        activityClass: Class<T>,
        expectedTestId: String
    ) {
        assertThat(DevTestRegistry.findById(expectedTestId)).isNotNull()

        val activity = Robolectric.buildActivity(activityClass).setup().get()
        val startedIntent = shadowOf(activity).nextStartedActivity

        assertThat(startedIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(DevTestActivitySupport.extractInitialTestId(startedIntent))
            .isEqualTo(expectedTestId)
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(startedIntent)).isTrue()
        assertThat(activity.isFinishing).isTrue()
    }
}

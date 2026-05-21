package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    manifest = Config.NONE,
    application = Application::class
)
class DevTestActivitySupportTest {

    @Test
    fun buildStartIntent_shouldPopulateDirectLaunchTargetOnlyForNonBlankIds() {
        val context = RuntimeEnvironment.getApplication().applicationContext

        val plainIntent = DevTestActivitySupport.buildStartIntent(context)
        val blankIntent = DevTestActivitySupport.buildStartIntent(context, "   ")
        val directIntent = DevTestActivitySupport.buildStartIntent(context, DevTestIds.Clangd)
        val directFinishIntent = DevTestActivitySupport.buildStartIntent(
            context = context,
            testId = DevTestIds.TreeSitter,
            finishOnBackIfDirect = true
        )

        assertThat(plainIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(blankIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(directIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(directFinishIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(plainIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(blankIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(directIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(directFinishIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(DevTestActivitySupport.extractInitialTestId(plainIntent)).isNull()
        assertThat(DevTestActivitySupport.extractInitialTestId(blankIntent)).isNull()
        assertThat(DevTestActivitySupport.extractInitialTestId(directIntent)).isEqualTo(DevTestIds.Clangd)
        assertThat(DevTestActivitySupport.extractInitialTestId(directFinishIntent))
            .isEqualTo(DevTestIds.TreeSitter)
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(plainIntent)).isFalse()
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(blankIntent)).isFalse()
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(directIntent)).isFalse()
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(directFinishIntent)).isTrue()
    }

    @Test
    fun buildStartIntent_shouldAvoidAddingNewTaskFlagForActivityContext() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val intent = DevTestActivitySupport.buildStartIntent(activity, DevTestIds.CompilerDiagnostics)

        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
        assertThat(DevTestActivitySupport.extractInitialTestId(intent)).isEqualTo(DevTestIds.CompilerDiagnostics)
    }

    @Test
    fun start_shouldLaunchDeveloperTestActivityWithResolvedIntent() {
        val application = RuntimeEnvironment.getApplication()

        DevTestActivity.start(application, DevTestIds.Clangd)

        val startedIntent = shadowOf(application).nextStartedActivity
        assertThat(startedIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(DevTestActivitySupport.extractInitialTestId(startedIntent)).isEqualTo(DevTestIds.Clangd)
    }

    @Test
    fun start_shouldOpenUnifiedTestingToolsListWhenTestIdMissing() {
        val application = RuntimeEnvironment.getApplication()

        DevTestActivity.start(application)

        val startedIntent = shadowOf(application).nextStartedActivity
        assertThat(startedIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(DevTestActivitySupport.extractInitialTestId(startedIntent)).isNull()
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(startedIntent)).isFalse()
    }

    @Test
    fun start_shouldPreserveFinishOnBackFlagForListOnlyUnifiedRoutes() {
        val application = RuntimeEnvironment.getApplication()

        DevTestActivity.start(
            context = application,
            testId = DevTestIds.CppScrollStress,
            finishOnBackIfDirect = true
        )

        val startedIntent = shadowOf(application).nextStartedActivity
        assertThat(startedIntent.component?.className).isEqualTo(DevTestActivity::class.java.name)
        assertThat(DevTestActivitySupport.extractInitialTestId(startedIntent))
            .isEqualTo(DevTestIds.CppScrollStress)
        assertThat(DevTestActivitySupport.extractFinishOnBackIfDirect(startedIntent)).isTrue()
    }

    @Test
    fun normalizeRequestedTestId_shouldDropUnknownRoutesAndKeepRegisteredEntries() {
        assertThat(DevTestActivitySupport.normalizeRequestedTestId(DevTestIds.CppScrollStress))
            .isEqualTo(DevTestIds.CppScrollStress)
        assertThat(DevTestActivitySupport.normalizeRequestedTestId("missing_test")).isNull()
        assertThat(DevTestActivitySupport.normalizeRequestedTestId("")).isNull()
        assertThat(DevTestActivitySupport.normalizeRequestedTestId("   ")).isNull()
        assertThat(DevTestActivitySupport.normalizeRequestedTestId(null)).isNull()
    }

    @Test
    fun resolveExitEffect_shouldFinishOnlyForDirectLaunchesThatOptIn() {
        val defaultEffect = DevTestActivitySupport.resolveExitEffect(
            currentTestId = DevTestIds.Clangd,
            initialTestId = DevTestIds.Clangd,
            finishOnBackIfDirect = false
        )
        val directFinishEffect = DevTestActivitySupport.resolveExitEffect(
            currentTestId = DevTestIds.Clangd,
            initialTestId = DevTestIds.Clangd,
            finishOnBackIfDirect = true
        )
        val switchedEffect = DevTestActivitySupport.resolveExitEffect(
            currentTestId = DevTestIds.TreeSitter,
            initialTestId = DevTestIds.Clangd,
            finishOnBackIfDirect = true
        )

        assertThat(defaultEffect.finishActivity).isFalse()
        assertThat(defaultEffect.nextTestId).isNull()

        assertThat(directFinishEffect.finishActivity).isTrue()
        assertThat(directFinishEffect.nextTestId).isNull()

        assertThat(switchedEffect.finishActivity).isFalse()
        assertThat(switchedEffect.nextTestId).isNull()
    }

    @Test
    fun resolveCurrentTest_shouldHandleKnownUnknownAndBlankRoutes() {
        val clangd = DevTestActivitySupport.resolveCurrentTest(DevTestIds.Clangd)

        assertThat(clangd?.id).isEqualTo(DevTestIds.Clangd)
        assertThat(clangd?.titleRes).isEqualTo(DevTestRegistry.findById(DevTestIds.Clangd)?.titleRes)
        assertThat(DevTestActivitySupport.resolveCurrentTest(DevTestIds.CompilerDiagnostics)?.id)
            .isEqualTo(DevTestIds.CompilerDiagnostics)
        assertThat(DevTestActivitySupport.resolveCurrentTest("missing_test")).isNull()
        assertThat(DevTestActivitySupport.resolveCurrentTest("")).isNull()
        assertThat(DevTestActivitySupport.resolveCurrentTest("   ")).isNull()
        assertThat(DevTestActivitySupport.resolveCurrentTest(null)).isNull()
    }

    @Test
    fun resolveSelectedTestId_shouldOnlyAllowRegisteredRoutes() {
        assertThat(DevTestActivitySupport.resolveSelectedTestId(DevTestIds.CppScrollStress))
            .isEqualTo(DevTestIds.CppScrollStress)
        assertThat(DevTestActivitySupport.resolveSelectedTestId(DevTestIds.TreeSitter))
            .isEqualTo(DevTestIds.TreeSitter)
        assertThat(DevTestActivitySupport.resolveSelectedTestId("missing_test")).isNull()
    }

    @Test
    fun resolveHostState_shouldExposeListStateForBlankAndUnknownRoutes() {
        val blankState = DevTestActivitySupport.resolveHostState(null)
        val unknownState = DevTestActivitySupport.resolveHostState("missing_test")

        assertThat(blankState.currentTest).isNull()
        assertThat(blankState.interceptBack).isFalse()
        assertThat(blankState.tests.map { it.id })
            .containsExactlyElementsIn(DevTestRegistry.getAllTests().map { it.id })
            .inOrder()

        assertThat(unknownState.currentTest).isNull()
        assertThat(unknownState.interceptBack).isFalse()
        assertThat(unknownState.tests.map { it.id })
            .containsExactlyElementsIn(DevTestRegistry.getAllTests().map { it.id })
            .inOrder()
    }

    @Test
    fun resolveHostState_shouldOpenRegisteredEditorBackedRouteFromUnifiedListSelection() {
        val selectedId = DevTestActivitySupport.resolveSelectedTestId(DevTestIds.CppScrollStress)
        val hostState = DevTestActivitySupport.resolveHostState(selectedId)

        assertThat(selectedId).isEqualTo(DevTestIds.CppScrollStress)
        assertThat(hostState.currentTest?.id).isEqualTo(DevTestIds.CppScrollStress)
        assertThat(hostState.currentTest?.titleRes)
            .isEqualTo(DevTestRegistry.findById(DevTestIds.CppScrollStress)?.titleRes)
        assertThat(hostState.interceptBack).isTrue()
    }

    @Test
    fun resolveCurrentTest_shouldResolveEveryRegisteredDeveloperTestId() {
        val allTests = DevTestRegistry.getAllTests()

        assertThat(allTests.map { it.id }).contains(DevTestIds.CppScrollStress)
        allTests.forEach { test ->
            val resolved = DevTestActivitySupport.resolveCurrentTest(test.id)

            assertThat(resolved).isSameInstanceAs(test)
        }
    }
}

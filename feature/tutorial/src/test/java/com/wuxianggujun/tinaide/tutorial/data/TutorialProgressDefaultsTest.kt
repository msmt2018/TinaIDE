package com.wuxianggujun.tinaide.tutorial.data

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class TutorialProgressDefaultsTest {

    @Test
    fun progress_shouldDefaultToFirstStepWithoutCompletionTimestamp() {
        val before = System.currentTimeMillis()
        val progress = TutorialProgress(
            tutorialId = "intro",
            status = ProgressStatus.IN_PROGRESS,
        )
        val after = System.currentTimeMillis()

        assertThat(progress.currentStepIndex).isEqualTo(0)
        assertThat(progress.completedAt).isNull()
        assertThat(progress.lastAccessedAt).isAtLeast(before)
        assertThat(progress.lastAccessedAt).isAtMost(after)
    }

    @Test
    fun tutorialAndStep_shouldExposeSafeDefaults() {
        val step = TutorialStep(
            id = "step-1",
            targetId = "target-1",
            titleRes = Strings.tutorial_cat_getting_started,
            descriptionRes = Strings.tutorial_cat_getting_started_desc,
        )
        val tutorial = Tutorial(
            id = "intro",
            titleRes = Strings.tutorial_cat_getting_started,
            descriptionRes = Strings.tutorial_cat_getting_started_desc,
            category = TutorialCategory.GETTING_STARTED,
            type = TutorialType.INTERACTIVE,
            estimatedMinutes = 5,
            order = 0,
        )

        assertThat(step.position).isEqualTo(TooltipPosition.AUTO)
        assertThat(step.action).isEqualTo(StepAction.NONE)
        assertThat(step.highlightShape).isEqualTo(HighlightShape.ROUNDED_RECT)
        assertThat(step.highlightPadding).isEqualTo(8)
        assertThat(tutorial.source).isEqualTo(TutorialSource.LOCAL)
        assertThat(tutorial.steps).isEmpty()
        assertThat(tutorial.version).isEqualTo(1)
        assertThat(tutorial.requiredAppVersion).isNull()
    }
}

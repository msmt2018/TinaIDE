package com.wuxianggujun.tinaide.tutorial.repository

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
    application = Application::class,
)
class TutorialRepositoryTest {

    @Test
    fun resolveTutorialByLinkTarget_shouldPreferTutorialEntriesForInternalDocs() {
        val application = RuntimeEnvironment.getApplication() as Application
        val repository = TutorialRepository(application, TutorialProgressStore(application))

        assertThat(repository.resolveTutorialByLinkTarget("build-project.md")?.id)
            .isEqualTo("build_project")
        assertThat(repository.resolveTutorialByLinkTarget("./getting-started.md#overview")?.id)
            .isEqualTo("getting_started")
        assertThat(repository.resolveTutorialByLinkTarget("create-project.md")?.id)
            .isEqualTo("create_project")
        assertThat(repository.resolveTutorialByLinkTarget("plugins-settings.md"))
            .isNull()
        assertThat(repository.resolveTutorialByLinkTarget("https://example.com/build-project.md"))
            .isNull()
    }
}

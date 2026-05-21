package com.wuxianggujun.tinaide.ai.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiModuleTest {

    @Test
    fun `ai module declares loadable koin definitions`() {
        assertThat(aiModule.isLoaded).isTrue()
    }
}

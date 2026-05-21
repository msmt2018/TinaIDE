package com.wuxianggujun.tinaide.ui.workspace.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DependencyInstallModelsTest {

    @Test
    fun dependencyInstallUiState_shouldExposeSafeDefaults() {
        val state = DependencyInstallUiState()

        assertThat(state.installPhase).isEqualTo(InstallPhase.INSTALLING)
        assertThat(state.progress).isEqualTo(0f)
        assertThat(state.packageList).isEmpty()
        assertThat(state.currentPackage).isNull()
        assertThat(state.envReady).isFalse()
        assertThat(state.rootfsHealth.status).isEqualTo(DependencyRootfsHealthStatus.UNKNOWN)
    }

    @Test
    fun dependencyItem_shouldDefaultToPendingWithNoProgress() {
        val item = DependencyItem(name = "clang", version = "18", size = "42 MB")

        assertThat(item.status).isEqualTo(DependencyStatus.PENDING)
        assertThat(item.progress).isEqualTo(0f)
        assertThat(item.statusText).isEmpty()
    }
}

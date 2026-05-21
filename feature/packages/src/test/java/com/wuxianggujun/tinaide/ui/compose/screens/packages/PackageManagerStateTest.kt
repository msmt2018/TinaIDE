package com.wuxianggujun.tinaide.ui.compose.screens.packages

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.packages.model.GUIPackage
import com.wuxianggujun.tinaide.core.packages.model.InstallProgressEvent
import com.wuxianggujun.tinaide.core.packages.model.Platform
import org.junit.Test

class PackageManagerStateTest {

    @Test
    fun uiState_shouldExposeIdleDefaults() {
        val state = PackageManagerUiState()

        assertThat(state.isLoading).isFalse()
        assertThat(state.packages).isEmpty()
        assertThat(state.filteredPackages).isEmpty()
        assertThat(state.installStates).isEmpty()
        assertThat(state.selectedPackageIds).isEmpty()
        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.currentDetailPackage).isNull()
    }

    @Test
    fun dialogState_shouldCarryInstallContext() {
        val installing = PackageDialogState.Installing(
            packageId = "sdl3",
            packageName = "SDL3",
            platform = Platform.ANDROID,
            event = InstallProgressEvent.Preparing("start")
        )
        val confirm = PackageDialogState.UninstallConfirm(
            packageId = "sdl3",
            packageInfo = GUIPackage(id = "sdl3", name = "SDL3"),
            platform = Platform.ANDROID,
            dependentPackages = listOf("demo")
        )

        assertThat(installing.packageName).isEqualTo("SDL3")
        assertThat(confirm.dependentPackages).containsExactly("demo")
    }
}

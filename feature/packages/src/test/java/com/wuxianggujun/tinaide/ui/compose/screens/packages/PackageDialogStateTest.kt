package com.wuxianggujun.tinaide.ui.compose.screens.packages

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.packages.UpdateInfo
import com.wuxianggujun.tinaide.core.packages.model.InstallProgressEvent
import com.wuxianggujun.tinaide.core.packages.model.Platform
import org.junit.Test

class PackageDialogStateTest {

    @Test
    fun batchInstalling_shouldCarrySelectionAndProgressContext() {
        val state = PackageDialogState.BatchInstalling(
            packageIds = listOf("cmake", "ninja"),
            platform = Platform.ANDROID,
            currentIndex = 1,
            totalCount = 2,
            currentPackageName = "Ninja",
            event = InstallProgressEvent.Downloading(downloaded = 50L, total = 100L, speed = 10L)
        )

        assertThat(state.packageIds).containsExactly("cmake", "ninja").inOrder()
        assertThat(state.platform).isEqualTo(Platform.ANDROID)
        assertThat(state.currentIndex).isEqualTo(1)
        assertThat(state.totalCount).isEqualTo(2)
        assertThat(state.currentPackageName).isEqualTo("Ninja")
    }

    @Test
    fun batchUpdateStates_shouldCarryUpdateSummary() {
        val update = UpdateInfo(
            packageId = "cmake",
            packageName = "CMake",
            currentVersion = "3.27",
            newVersion = "3.29",
            platform = Platform.LINUX
        )
        val updating = PackageDialogState.BatchUpdating(
            updates = listOf(update),
            currentIndex = 0,
            totalCount = 1,
            currentPackageName = "CMake",
            event = InstallProgressEvent.Preparing("start")
        )
        val complete = PackageDialogState.BatchUpdateComplete(totalCount = 1)

        assertThat(updating.updates).containsExactly(update)
        assertThat(updating.currentPackageName).isEqualTo("CMake")
        assertThat(complete.totalCount).isEqualTo(1)
    }
}

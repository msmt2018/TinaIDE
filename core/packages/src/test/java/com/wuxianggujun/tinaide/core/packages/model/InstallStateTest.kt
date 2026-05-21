package com.wuxianggujun.tinaide.core.packages.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstallStateTest {

    @Test
    fun packageInstallState_shouldReadAndUpdatePlatformIndependently() {
        val state = PackageInstallState()
            .withPlatform(Platform.LINUX, PlatformInstallState.Installed("1.2.0", installedAt = 100L))
            .withPlatform(Platform.ANDROID, PlatformInstallState.UpdateAvailable("1.0.0", "1.1.0"))

        assertThat((state.forPlatform(Platform.LINUX) as PlatformInstallState.Installed).version)
            .isEqualTo("1.2.0")
        assertThat(state.forPlatform(Platform.ANDROID).isInstalled).isTrue()
        assertThat(PackageInstallState().forPlatform(Platform.LINUX))
            .isSameInstanceAs(PlatformInstallState.NotInstalled)
    }

    @Test
    fun installProgress_shouldClampUnknownTotalToZeroProgress() {
        assertThat(
            InstallProgressEvent.Downloading(downloaded = 40L, total = 100L, speed = 10L).progress
        ).isWithin(0.0001f).of(0.4f)

        assertThat(
            InstallProgressEvent.Downloading(downloaded = 40L, total = 0L, speed = 10L).progress
        ).isEqualTo(0f)
    }

    @Test
    fun displayMessages_shouldIncludeRelevantFailureContext() {
        assertThat(
            InstallError.ChecksumMismatch(expected = "abc", actual = "def").toDisplayMessage()
        ).contains("expected abc")
        assertThat(
            InstallError.DependencyMissing(listOf("sdl3", "curl")).toDisplayMessage()
        ).contains("sdl3, curl")
        assertThat(
            UninstallError.DependentPackages(listOf("demo")).toDisplayMessage()
        ).contains("demo depend on this package")
    }
}

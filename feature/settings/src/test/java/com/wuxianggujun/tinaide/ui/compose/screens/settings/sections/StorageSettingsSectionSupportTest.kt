package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.proot.RootfsDistroRuntime
import com.wuxianggujun.tinaide.core.proot.RootfsPackageManager
import com.wuxianggujun.tinaide.core.proot.RootfsProfile
import com.wuxianggujun.tinaide.core.proot.RootfsSourceType
import com.wuxianggujun.tinaide.ui.compose.screens.settings.RootfsHealthStatus
import com.wuxianggujun.tinaide.ui.compose.screens.settings.RootfsHealthUiState
import org.junit.Test

class StorageSettingsSectionSupportTest {

    @Test
    fun validateInputs_shouldHandleRenameWhitespace() {
        assertThat(StorageSettingsSectionSupport.isRenameInputInvalid("   ")).isTrue()
        assertThat(StorageSettingsSectionSupport.isRenameInputInvalid(" Alpine ")).isFalse()
    }

    @Test
    fun resolveProfiles_shouldReturnExpectedEntries() {
        val linux = testProfile(id = "linux")
        val custom = testProfile(id = "custom", packageManager = RootfsPackageManager.APT)
        val profiles = listOf(custom, linux)

        assertThat(
            StorageSettingsSectionSupport.resolveActiveProfile(profiles, "custom")
        ).isEqualTo(custom)
        assertThat(
            StorageSettingsSectionSupport.resolveActiveProfile(profiles, "missing")
        ).isNull()
        assertThat(
            StorageSettingsSectionSupport.resolveRootfsPathValue("", "默认值")
        ).isEqualTo("默认值")
        assertThat(
            StorageSettingsSectionSupport.resolveRootfsPathValue("/data/rootfs", "默认值")
        ).isEqualTo("/data/rootfs")
    }

    @Test
    fun resolveDistro_shouldLookupById() {
        val distros = listOf(
            testDistroOption(id = "alpine", displayName = "Alpine Linux"),
            testDistroOption(id = "ubuntu", displayName = "Ubuntu"),
        )

        assertThat(
            StorageSettingsSectionSupport.resolveDistro(distros, "ubuntu")?.id
        ).isEqualTo("ubuntu")
        assertThat(
            StorageSettingsSectionSupport.resolveDistro(distros, "missing")
        ).isNull()
        assertThat(
            StorageSettingsSectionSupport.buildDistroOptions(distros).first().second
        ).contains("Alpine Linux")
    }

    @Test
    fun resolveProfileValueAndActions_shouldReflectActiveLinuxDistroState() {
        val activeLinux = testProfile(
            id = "linux",
            packageManager = RootfsPackageManager.APK,
        )
        val custom = testProfile(
            id = "custom",
            packageManager = RootfsPackageManager.PACMAN,
        )

        assertThat(
            StorageSettingsSectionSupport.resolveProfileValue(
                profile = activeLinux,
                activeRootfsProfileId = "linux",
            )
        ).isEqualTo(StorageSettingsValueSpec.LabelRes(Strings.value_current))
        assertThat(
            StorageSettingsSectionSupport.resolveProfileValue(
                profile = custom,
                activeRootfsProfileId = "linux",
            )
        ).isEqualTo(StorageSettingsValueSpec.RawText("pacman"))

        assertThat(
            StorageSettingsSectionSupport.buildProfileActionSpecs(
                profile = activeLinux,
                activeRootfsProfileId = "linux",
            )
        ).containsExactly(
            StorageProfileActionSpec(
                action = StorageProfileAction.Rename,
                labelRes = Strings.action_rename,
            ),
            StorageProfileActionSpec(
                action = StorageProfileAction.Delete,
                labelRes = Strings.btn_delete,
            ),
        ).inOrder()
        assertThat(
            StorageSettingsSectionSupport.buildProfileActionSpecs(
                profile = custom,
                activeRootfsProfileId = "linux",
            )
        ).containsExactly(
            StorageProfileActionSpec(
                action = StorageProfileAction.Switch,
                labelRes = Strings.settings_linux_action_switch,
            ),
            StorageProfileActionSpec(
                action = StorageProfileAction.Rename,
                labelRes = Strings.action_rename,
            ),
            StorageProfileActionSpec(
                action = StorageProfileAction.Delete,
                labelRes = Strings.btn_delete,
            ),
        ).inOrder()
    }

    @Test
    fun rootfsHealthValueAndSubtitle_shouldUseFallbackAndDetail() {
        val emptyHealth = RootfsHealthUiState()
        val readyHealth = RootfsHealthUiState(
            status = RootfsHealthStatus.READY,
            statusText = "可用",
            detailText = "Ubuntu 24.04 · aarch64",
        )

        assertThat(
            StorageSettingsSectionSupport.resolveRootfsHealthValue(emptyHealth, "未检测")
        ).isEqualTo("未检测")
        assertThat(
            StorageSettingsSectionSupport.resolveRootfsHealthSubtitle(emptyHealth)
        ).isNull()
        assertThat(
            StorageSettingsSectionSupport.resolveRootfsHealthValue(readyHealth, "未检测")
        ).isEqualTo("可用")
        assertThat(
            StorageSettingsSectionSupport.resolveRootfsHealthSubtitle(readyHealth)
        ).isEqualTo("Ubuntu 24.04 · aarch64")
    }

    @Test
    fun packageManagerLabel_shouldMapKnownManagers() {
        assertThat(
            StorageSettingsSectionSupport.packageManagerLabel(RootfsPackageManager.APK)
        ).isEqualTo("apk")
        assertThat(
            StorageSettingsSectionSupport.packageManagerLabel(RootfsPackageManager.APT)
        ).isEqualTo("apt")
        assertThat(
            StorageSettingsSectionSupport.packageManagerLabel(RootfsPackageManager.PACMAN)
        ).isEqualTo("pacman")
        assertThat(
            StorageSettingsSectionSupport.packageManagerLabel(RootfsPackageManager.DNF)
        ).isEqualTo("dnf")
        assertThat(
            StorageSettingsSectionSupport.resolvePackageManagerValue(RootfsPackageManager.UNKNOWN)
        ).isEqualTo(StorageSettingsValueSpec.LabelRes(Strings.settings_linux_package_manager_unknown))
    }

    private fun testDistroOption(
        id: String,
        displayName: String,
    ): RootfsDistroRuntime.DistroOption = RootfsDistroRuntime.DistroOption(
        id = id,
        displayName = displayName,
        description = "official rootfs",
        packageManager = RootfsPackageManager.APK,
    )

    private fun testProfile(
        id: String,
        packageManager: RootfsPackageManager = RootfsPackageManager.UNKNOWN,
    ): RootfsProfile = RootfsProfile(
        id = id,
        displayName = id,
        distroId = id,
        distroName = id,
        rootfsPath = "/data/$id",
        sourceType = RootfsSourceType.LINUX_DISTRO,
        packageManager = packageManager,
        createdAt = 1L,
        updatedAt = 2L,
    )
}

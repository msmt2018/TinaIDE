package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.proot.RootfsDistroRuntime
import com.wuxianggujun.tinaide.core.proot.RootfsPackageManager
import com.wuxianggujun.tinaide.core.proot.RootfsProfile
import com.wuxianggujun.tinaide.ui.compose.screens.settings.RootfsHealthUiState

internal sealed interface StorageSettingsValueSpec {
    data class RawText(val value: String) : StorageSettingsValueSpec
    data class LabelRes(
        @param:StringRes @get:StringRes val labelRes: Int
    ) : StorageSettingsValueSpec
}

internal enum class StorageProfileAction(
    @param:StringRes @get:StringRes val labelRes: Int
) {
    Switch(Strings.settings_linux_action_switch),
    Rename(Strings.action_rename),
    Delete(Strings.btn_delete),
}

internal data class StorageProfileActionSpec(
    val action: StorageProfileAction,
    @param:StringRes @get:StringRes val labelRes: Int,
)

internal object StorageSettingsSectionSupport {

    fun isRenameInputInvalid(profileNameInput: String): Boolean = profileNameInput.trim().isEmpty()

    fun resolveActiveProfile(
        profiles: List<RootfsProfile>,
        activeRootfsProfileId: String,
    ): RootfsProfile? = profiles.firstOrNull { it.id == activeRootfsProfileId }

    fun resolveRootfsPathValue(rootfsPath: String, defaultValue: String): String = if (rootfsPath.isBlank()) defaultValue else rootfsPath

    fun buildDistroOptions(
        distros: List<RootfsDistroRuntime.DistroOption>,
    ): List<Pair<String, String>> = distros.map { distro ->
        distro.id to buildString {
            append(distro.displayName)
            append('\n')
            append(distro.description)
        }
    }

    fun resolveDistro(
        distros: List<RootfsDistroRuntime.DistroOption>,
        distroId: String,
    ): RootfsDistroRuntime.DistroOption? = distros.firstOrNull { it.id == distroId }

    fun resolveProfileValue(
        profile: RootfsProfile,
        activeRootfsProfileId: String,
    ): StorageSettingsValueSpec = if (profile.id == activeRootfsProfileId) {
        StorageSettingsValueSpec.LabelRes(Strings.value_current)
    } else {
        resolvePackageManagerValue(profile.packageManager)
    }

    fun buildProfileActionSpecs(
        profile: RootfsProfile,
        activeRootfsProfileId: String,
    ): List<StorageProfileActionSpec> = buildList {
        if (profile.id != activeRootfsProfileId) {
            add(
                StorageProfileActionSpec(
                    action = StorageProfileAction.Switch,
                    labelRes = StorageProfileAction.Switch.labelRes,
                )
            )
        }
        add(
            StorageProfileActionSpec(
                action = StorageProfileAction.Rename,
                labelRes = StorageProfileAction.Rename.labelRes,
            )
        )
        add(
            StorageProfileActionSpec(
                action = StorageProfileAction.Delete,
                labelRes = StorageProfileAction.Delete.labelRes,
            )
        )
    }

    fun resolveRootfsHealthValue(
        health: RootfsHealthUiState,
        fallbackValue: String,
    ): String = health.statusText.takeIf { it.isNotBlank() } ?: fallbackValue

    fun resolveRootfsHealthSubtitle(health: RootfsHealthUiState): String? = health.detailText.takeIf { it.isNotBlank() }

    fun resolvePackageManagerValue(packageManager: RootfsPackageManager): StorageSettingsValueSpec = when (packageManager) {
        RootfsPackageManager.UNKNOWN -> StorageSettingsValueSpec.LabelRes(
            Strings.settings_linux_package_manager_unknown
        )
        else -> StorageSettingsValueSpec.RawText(packageManagerLabel(packageManager))
    }

    fun packageManagerLabel(packageManager: RootfsPackageManager): String = when (packageManager) {
        RootfsPackageManager.APK -> "apk"
        RootfsPackageManager.APT -> "apt"
        RootfsPackageManager.PACMAN -> "pacman"
        RootfsPackageManager.DNF -> "dnf"
        RootfsPackageManager.UNKNOWN -> ""
    }
}

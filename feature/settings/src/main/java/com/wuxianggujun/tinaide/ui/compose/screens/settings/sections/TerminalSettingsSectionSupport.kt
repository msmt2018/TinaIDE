package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.proot.RootfsPackageManager
import com.wuxianggujun.tinaide.core.terminal.BackendMode
import com.wuxianggujun.tinaide.core.terminal.GuestDevPackagesCommandGroupStatus
import com.wuxianggujun.tinaide.core.terminal.ShellAvailabilityInfo
import com.wuxianggujun.tinaide.core.terminal.ShellType
import com.wuxianggujun.tinaide.core.terminal.TerminalBackendType
import java.io.File

internal data class TerminalSettingsTextSpec(
    @param:StringRes @get:StringRes val labelRes: Int,
    val formatArgs: List<Any> = emptyList(),
)

internal data class TerminalSettingsDialogSpec(
    val title: TerminalSettingsTextSpec,
    val message: TerminalSettingsTextSpec,
)

internal sealed interface TerminalSettingsDisplaySpec {
    data class ResourceText(
        @param:StringRes @get:StringRes val labelRes: Int,
    ) : TerminalSettingsDisplaySpec

    data class RawText(val value: String) : TerminalSettingsDisplaySpec
}

internal data class TerminalSettingsOptionSpec(
    val value: String,
    @param:StringRes @get:StringRes val labelRes: Int,
    @param:StringRes @get:StringRes val descriptionRes: Int? = null,
)

internal enum class TerminalSettingsAction(
    @param:StringRes @get:StringRes val labelRes: Int
) {
    RefreshLinuxToolsStatus(Strings.btn_refresh),
    InstallGuestDevPackages(Strings.btn_install),
    ReinstallGuestDevPackages(Strings.settings_linux_action_reinstall_dev_packages),
    ChangeShell(Strings.settings_linux_action_change_shell),
    InstallZsh(Strings.dialog_title_install_zsh),
    ReinstallZsh(Strings.settings_linux_action_reinstall_zsh),
    ChangeLocale(Strings.settings_linux_action_change_locale),
    InstallLocale(Strings.dialog_title_install_locale),
    RebuildLocale(Strings.settings_linux_action_rebuild_locale),
}

internal data class TerminalSettingsActionSpec(
    val action: TerminalSettingsAction,
    @param:StringRes @get:StringRes val labelRes: Int,
)

internal enum class TerminalSettingsFontAction {
    SelectBuiltIn,
    SelectSystemMono,
    UseCustomFont,
    PickCustomFont,
    ChangeCustomFont,
}

internal data class TerminalSettingsFontActionSpec(
    val action: TerminalSettingsFontAction,
    val label: String,
)

internal data class TerminalSettingsCommandGroupDiagnostic(
    val label: String,
    val commands: List<String>,
    val available: Boolean,
)

internal object TerminalSettingsSectionSupport {

    fun buildShellTypeOptions(): List<TerminalSettingsOptionSpec> = listOf(
        TerminalSettingsOptionSpec(
            value = ShellType.AUTO.value,
            labelRes = Strings.shell_type_auto,
            descriptionRes = Strings.shell_type_auto_desc,
        ),
        TerminalSettingsOptionSpec(
            value = ShellType.SH.value,
            labelRes = Strings.shell_type_sh,
            descriptionRes = Strings.shell_type_sh_desc,
        ),
        TerminalSettingsOptionSpec(
            value = ShellType.BASH.value,
            labelRes = Strings.shell_type_bash,
            descriptionRes = Strings.shell_type_bash_desc,
        ),
        TerminalSettingsOptionSpec(
            value = ShellType.ZSH.value,
            labelRes = Strings.shell_type_zsh,
            descriptionRes = Strings.shell_type_zsh_desc,
        ),
    )

    @StringRes
    fun resolveShellTypeLabelRes(type: ShellType): Int = when (type) {
        ShellType.AUTO -> Strings.shell_type_auto
        ShellType.SH -> Strings.shell_type_sh
        ShellType.BASH -> Strings.shell_type_bash
        ShellType.ZSH -> Strings.shell_type_zsh
    }

    @StringRes
    fun resolveShellTypeDescriptionRes(type: ShellType): Int = when (type) {
        ShellType.AUTO -> Strings.shell_type_auto_desc
        ShellType.SH -> Strings.shell_type_sh_desc
        ShellType.BASH -> Strings.shell_type_bash_desc
        ShellType.ZSH -> Strings.shell_type_zsh_desc
    }

    fun buildBackendModeOptions(linuxEnvironmentEnabled: Boolean): List<TerminalSettingsOptionSpec> = listOf(
        TerminalSettingsOptionSpec(
            value = BackendMode.AUTO.value,
            labelRes = Strings.backend_mode_auto,
            descriptionRes = Strings.backend_mode_auto_desc,
        ),
        TerminalSettingsOptionSpec(
            value = BackendMode.PROOT.value,
            labelRes = Strings.backend_mode_proot,
            descriptionRes = Strings.backend_mode_proot_desc,
        ),
        TerminalSettingsOptionSpec(
            value = BackendMode.HOST.value,
            labelRes = Strings.backend_mode_host,
            descriptionRes = Strings.backend_mode_host_desc,
        ),
    ).filter { linuxEnvironmentEnabled || it.value != BackendMode.PROOT.value }

    fun resolveBuiltInFontDisplayName(
        hasBuiltIn: Boolean,
        currentFontName: String,
        builtInNotInstalledText: String,
    ): String = if (hasBuiltIn) {
        currentFontName
    } else {
        builtInNotInstalledText
    }

    fun resolveCustomFontDisplayName(
        customFontPath: String,
        selectFileText: String,
    ): String = if (customFontPath.isNotBlank()) {
        File(customFontPath).name
    } else {
        selectFileText
    }

    fun buildFontFamilyActionSpecs(
        builtInLabel: String,
        builtInDisplayName: String,
        systemMonoLabel: String,
        customLabel: String,
        customDisplayName: String,
        customFontPath: String,
        changeCustomFontLabel: String,
    ): List<TerminalSettingsFontActionSpec> = buildList {
        add(
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.SelectBuiltIn,
                label = "$builtInLabel ($builtInDisplayName)",
            )
        )
        add(
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.SelectSystemMono,
                label = systemMonoLabel,
            )
        )
        add(
            TerminalSettingsFontActionSpec(
                action = if (customFontPath.isBlank()) {
                    TerminalSettingsFontAction.PickCustomFont
                } else {
                    TerminalSettingsFontAction.UseCustomFont
                },
                label = "$customLabel ($customDisplayName)",
            )
        )
        if (customFontPath.isNotBlank()) {
            add(
                TerminalSettingsFontActionSpec(
                    action = TerminalSettingsFontAction.ChangeCustomFont,
                    label = changeCustomFontLabel,
                )
            )
        }
    }

    fun resolveFontSizeDialogSteps(min: Float, max: Float): Int = ((max - min).toInt().coerceAtLeast(0) - 1).coerceAtLeast(0)

    @StringRes
    fun resolveBackendModeLabelRes(mode: BackendMode): Int = when (mode) {
        BackendMode.AUTO -> Strings.backend_mode_auto
        BackendMode.PROOT -> Strings.backend_mode_proot
        BackendMode.HOST -> Strings.backend_mode_host
    }

    @StringRes
    fun resolveBackendModeDescriptionRes(mode: BackendMode): Int = when (mode) {
        BackendMode.AUTO -> Strings.backend_mode_auto_desc
        BackendMode.PROOT -> Strings.backend_mode_proot_desc
        BackendMode.HOST -> Strings.backend_mode_host_desc
    }

    @StringRes
    fun resolvePackageManagerLabelRes(packageManager: RootfsPackageManager): Int = when (packageManager) {
        RootfsPackageManager.APK -> Strings.settings_linux_package_manager_apk
        RootfsPackageManager.APT -> Strings.settings_linux_package_manager_apt
        RootfsPackageManager.PACMAN -> Strings.settings_linux_package_manager_pacman
        RootfsPackageManager.DNF -> Strings.settings_linux_package_manager_dnf
        RootfsPackageManager.UNKNOWN -> Strings.settings_linux_package_manager_unknown
    }

    @StringRes
    fun resolveLinuxToolStatusLabelRes(status: Boolean?, notRequired: Boolean = false): Int = when {
        notRequired -> Strings.settings_linux_tool_status_not_required
        status == null -> Strings.settings_linux_tool_status_checking
        status -> Strings.settings_linux_tool_status_ready
        else -> Strings.settings_linux_tool_status_missing
    }

    fun resolveLinuxToolsHealthText(requiredStates: List<Boolean?>): TerminalSettingsTextSpec {
        val readyCount = requiredStates.count { it == true }
        val totalCount = requiredStates.size
        return when {
            requiredStates.any { it == null } -> {
                TerminalSettingsTextSpec(Strings.settings_linux_tool_status_checking)
            }

            readyCount == totalCount -> {
                TerminalSettingsTextSpec(
                    labelRes = Strings.settings_linux_tools_health_ready,
                    formatArgs = listOf(readyCount, totalCount),
                )
            }

            else -> {
                TerminalSettingsTextSpec(
                    labelRes = Strings.settings_linux_tools_health_attention,
                    formatArgs = listOf(readyCount, totalCount),
                )
            }
        }
    }

    fun buildRequiredLinuxToolStates(
        locale: String,
        devPackagesInstalled: Boolean?,
        zshInstalled: Boolean?,
        localeSupportInstalled: Boolean?,
    ): List<Boolean?> = buildList {
        add(devPackagesInstalled)
        add(zshInstalled)
        if (locale != "C.UTF-8") {
            add(localeSupportInstalled)
        }
    }

    fun resolveGuestDevPackagesGroupLabel(
        status: GuestDevPackagesCommandGroupStatus,
    ): TerminalSettingsDisplaySpec = when (status.commands) {
        listOf("cc", "gcc", "clang") -> {
            TerminalSettingsDisplaySpec.ResourceText(Strings.settings_linux_tool_group_compiler)
        }

        listOf("make") -> TerminalSettingsDisplaySpec.ResourceText(Strings.toolchain_pkg_make)
        listOf("git") -> TerminalSettingsDisplaySpec.ResourceText(Strings.toolchain_pkg_git)
        listOf("curl") -> {
            TerminalSettingsDisplaySpec.ResourceText(Strings.settings_linux_tool_group_curl)
        }

        listOf("cmake") -> TerminalSettingsDisplaySpec.ResourceText(Strings.toolchain_pkg_cmake)
        listOf("pkg-config", "pkgconf") -> {
            TerminalSettingsDisplaySpec.ResourceText(Strings.settings_linux_tool_group_pkg_config)
        }

        else -> TerminalSettingsDisplaySpec.RawText(
            status.commands.joinToString(separator = "/")
        )
    }

    fun buildGuestDevPackagesMissingSummary(missingLabels: List<String>): String? {
        if (missingLabels.isEmpty()) return null
        return missingLabels.joinToString(separator = "、")
    }

    fun buildGuestDevPackagesSubtitle(
        baseDescription: String,
        devPackagesInstalled: Boolean?,
        missingComponentsText: String?,
    ): String {
        if (devPackagesInstalled != false || missingComponentsText.isNullOrBlank()) {
            return baseDescription
        }

        return buildString {
            append(baseDescription)
            append("\n")
            append(missingComponentsText)
        }
    }

    fun buildGuestDevPackagesDiagnosticMessage(
        baseDescription: String,
        packagesToInstallText: String?,
        commandDiagnosticsTitle: String,
        checkingText: String,
        readyText: String,
        missingText: String,
        commandGroupDiagnostics: List<TerminalSettingsCommandGroupDiagnostic>,
    ): String {
        val lines = mutableListOf<String>()
        lines += baseDescription
        lines += ""

        packagesToInstallText
            ?.takeUnless { it.isBlank() }
            ?.let { packagesLine ->
                lines += packagesLine
                lines += ""
            }

        lines += commandDiagnosticsTitle

        if (commandGroupDiagnostics.isEmpty()) {
            lines += checkingText
        } else {
            commandGroupDiagnostics.forEach { diagnostic ->
                val statusText = if (diagnostic.available) readyText else missingText
                lines += "• ${diagnostic.label}（${diagnostic.commands.joinToString(separator = "/")}）：$statusText"
            }
        }

        return lines.joinToString(separator = "\n")
    }

    fun buildLinuxToolsDiagnosticMessage(
        systemNameLabel: String,
        activeProfileName: String,
        packageManagerLabel: String,
        packageManagerName: String,
        guestDevPackagesLabel: String,
        guestDevPackagesStatusText: String,
        guestDevPackagesMissingComponentsText: String?,
        zshLabel: String,
        zshStatusText: String,
        localeSupportLabel: String,
        localeSupportStatusText: String,
        localeSupportDescription: String,
    ): String {
        val lines = mutableListOf<String>()
        lines += "$systemNameLabel：$activeProfileName"
        lines += "$packageManagerLabel：$packageManagerName"
        lines += ""
        lines += "$guestDevPackagesLabel：$guestDevPackagesStatusText"
        guestDevPackagesMissingComponentsText
            ?.takeUnless { it.isBlank() }
            ?.let(lines::add)
        lines += "$zshLabel：$zshStatusText"
        lines += "$localeSupportLabel：$localeSupportStatusText"
        lines += localeSupportDescription
        return lines.joinToString(separator = "\n")
    }

    fun buildLinuxToolsActionSpecs(
        isPRootInstalled: Boolean,
        devPackagesInstalled: Boolean?,
        zshInstalled: Boolean?,
        locale: String,
        localeSupportInstalled: Boolean?,
    ): List<TerminalSettingsActionSpec> {
        return buildList {
            add(
                TerminalSettingsActionSpec(
                    action = TerminalSettingsAction.RefreshLinuxToolsStatus,
                    labelRes = TerminalSettingsAction.RefreshLinuxToolsStatus.labelRes,
                )
            )

            if (!isPRootInstalled) {
                return@buildList
            }

            add(
                TerminalSettingsActionSpec(
                    action = if (devPackagesInstalled == true) {
                        TerminalSettingsAction.ReinstallGuestDevPackages
                    } else {
                        TerminalSettingsAction.InstallGuestDevPackages
                    },
                    labelRes = if (devPackagesInstalled == true) {
                        TerminalSettingsAction.ReinstallGuestDevPackages.labelRes
                    } else {
                        TerminalSettingsAction.InstallGuestDevPackages.labelRes
                    },
                )
            )
            add(
                TerminalSettingsActionSpec(
                    action = if (zshInstalled == true) {
                        TerminalSettingsAction.ChangeShell
                    } else {
                        TerminalSettingsAction.InstallZsh
                    },
                    labelRes = if (zshInstalled == true) {
                        TerminalSettingsAction.ChangeShell.labelRes
                    } else {
                        TerminalSettingsAction.InstallZsh.labelRes
                    },
                )
            )
            add(
                TerminalSettingsActionSpec(
                    action = if (locale != "C.UTF-8" && localeSupportInstalled == false) {
                        TerminalSettingsAction.InstallLocale
                    } else {
                        TerminalSettingsAction.ChangeLocale
                    },
                    labelRes = if (locale != "C.UTF-8" && localeSupportInstalled == false) {
                        TerminalSettingsAction.InstallLocale.labelRes
                    } else {
                        TerminalSettingsAction.ChangeLocale.labelRes
                    },
                )
            )
        }
    }

    fun buildGuestDevPackagesActionSpecs(
        devPackagesInstalled: Boolean?,
    ): List<TerminalSettingsActionSpec> = listOf(
        TerminalSettingsActionSpec(
            action = if (devPackagesInstalled == true) {
                TerminalSettingsAction.ReinstallGuestDevPackages
            } else {
                TerminalSettingsAction.InstallGuestDevPackages
            },
            labelRes = if (devPackagesInstalled == true) {
                TerminalSettingsAction.ReinstallGuestDevPackages.labelRes
            } else {
                TerminalSettingsAction.InstallGuestDevPackages.labelRes
            },
        )
    )

    fun buildZshActionSpecs(): List<TerminalSettingsActionSpec> = listOf(
        TerminalSettingsActionSpec(
            action = TerminalSettingsAction.ChangeShell,
            labelRes = TerminalSettingsAction.ChangeShell.labelRes,
        ),
        TerminalSettingsActionSpec(
            action = TerminalSettingsAction.ReinstallZsh,
            labelRes = TerminalSettingsAction.ReinstallZsh.labelRes,
        ),
    )

    fun buildLocaleActionSpecs(): List<TerminalSettingsActionSpec> = listOf(
        TerminalSettingsActionSpec(
            action = TerminalSettingsAction.ChangeLocale,
            labelRes = TerminalSettingsAction.ChangeLocale.labelRes,
        ),
        TerminalSettingsActionSpec(
            action = TerminalSettingsAction.RebuildLocale,
            labelRes = TerminalSettingsAction.RebuildLocale.labelRes,
        ),
    )

    fun resolveZshInstallDialogSpec(forceReinstall: Boolean): TerminalSettingsDialogSpec = if (forceReinstall) {
        TerminalSettingsDialogSpec(
            title = TerminalSettingsTextSpec(Strings.dialog_title_reinstall_zsh),
            message = TerminalSettingsTextSpec(Strings.dialog_message_reinstall_zsh),
        )
    } else {
        TerminalSettingsDialogSpec(
            title = TerminalSettingsTextSpec(Strings.dialog_title_install_zsh),
            message = TerminalSettingsTextSpec(Strings.dialog_message_install_zsh),
        )
    }

    fun resolveGuestDevPackagesInstallDialogSpec(
        forceReinstall: Boolean,
    ): TerminalSettingsDialogSpec = if (forceReinstall) {
        TerminalSettingsDialogSpec(
            title = TerminalSettingsTextSpec(Strings.dialog_title_reinstall_guest_dev_packages),
            message = TerminalSettingsTextSpec(Strings.dialog_message_reinstall_guest_dev_packages),
        )
    } else {
        TerminalSettingsDialogSpec(
            title = TerminalSettingsTextSpec(Strings.dialog_title_install_guest_dev_packages),
            message = TerminalSettingsTextSpec(Strings.dialog_message_install_guest_dev_packages),
        )
    }

    fun resolveLocaleInstallDialogSpec(
        forceReinstall: Boolean,
        pendingLocale: String?,
        currentLocale: String,
    ): TerminalSettingsDialogSpec = if (forceReinstall) {
        TerminalSettingsDialogSpec(
            title = TerminalSettingsTextSpec(Strings.dialog_title_rebuild_locale_support),
            message = TerminalSettingsTextSpec(
                labelRes = Strings.dialog_message_rebuild_locale_support,
                formatArgs = listOf(pendingLocale ?: currentLocale),
            ),
        )
    } else {
        TerminalSettingsDialogSpec(
            title = TerminalSettingsTextSpec(Strings.dialog_title_install_locale),
            message = TerminalSettingsTextSpec(Strings.dialog_message_install_locale),
        )
    }

    fun resolveLocaleInstallTarget(pendingLocale: String?): String = pendingLocale ?: "C.UTF-8"

    fun resolveSelectedBackendMode(
        linuxEnvironmentEnabled: Boolean,
        backendMode: String,
    ): BackendMode = BackendMode.fromValue(
        if (!linuxEnvironmentEnabled && backendMode == BackendMode.PROOT.value) {
            BackendMode.HOST.value
        } else {
            backendMode
        }
    )

    fun shouldForceHostBackend(
        linuxEnvironmentEnabled: Boolean,
        backendMode: String,
    ): Boolean = !linuxEnvironmentEnabled && backendMode == BackendMode.PROOT.value

    fun buildShellSubtitle(
        baseDescription: String,
        availability: ShellAvailabilityInfo?,
        shellAvailabilityError: String?,
        selectedShell: ShellType,
        backendLabelProvider: (TerminalBackendType) -> String,
        shellLabelProvider: (ShellType) -> String,
        autoResolvedTextProvider: (String) -> String,
        availableTextProvider: (String) -> String,
        probeFailedTextProvider: (String) -> String,
    ): String = buildString {
        append(baseDescription)

        if (availability != null) {
            append("\n")
            append(backendLabelProvider(availability.backend))

            availability.autoResolved
                ?.takeIf { selectedShell == ShellType.AUTO }
                ?.let { resolved ->
                    append("\n")
                    append(autoResolvedTextProvider(shellLabelProvider(resolved)))
                }

            if (availability.availableShells.isNotEmpty()) {
                val order = listOf(ShellType.ZSH, ShellType.BASH, ShellType.SH)
                val availableText = availability.availableShells
                    .sortedBy { order.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                    .joinToString(separator = " / ") { shellLabelProvider(it) }
                append("\n")
                append(availableTextProvider(availableText))
            }
        } else if (shellAvailabilityError != null) {
            append("\n")
            append(probeFailedTextProvider(shellAvailabilityError))
        }
    }
}

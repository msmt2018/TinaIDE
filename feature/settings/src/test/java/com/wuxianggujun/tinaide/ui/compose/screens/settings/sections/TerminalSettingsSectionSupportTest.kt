package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.proot.RootfsPackageManager
import com.wuxianggujun.tinaide.core.terminal.BackendMode
import com.wuxianggujun.tinaide.core.terminal.GuestDevPackagesCommandGroupStatus
import com.wuxianggujun.tinaide.core.terminal.ShellAvailabilityInfo
import com.wuxianggujun.tinaide.core.terminal.ShellType
import com.wuxianggujun.tinaide.core.terminal.TerminalBackendType
import org.junit.Test

class TerminalSettingsSectionSupportTest {

    @Test
    fun buildShellTypeOptionsAndLabelMapping_shouldExposeAllTypesInOrder() {
        assertThat(
            TerminalSettingsSectionSupport.buildShellTypeOptions()
        ).containsExactly(
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
        ).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.resolveShellTypeLabelRes(ShellType.BASH)
        ).isEqualTo(Strings.shell_type_bash)
        assertThat(
            TerminalSettingsSectionSupport.resolveShellTypeDescriptionRes(ShellType.ZSH)
        ).isEqualTo(Strings.shell_type_zsh_desc)
    }

    @Test
    fun resolveFontDisplayNamesAndActions_shouldReflectInstalledAndCustomFonts() {
        assertThat(
            TerminalSettingsSectionSupport.resolveBuiltInFontDisplayName(
                hasBuiltIn = false,
                currentFontName = "JetBrains Mono",
                builtInNotInstalledText = "未安装",
            )
        ).isEqualTo("未安装")
        assertThat(
            TerminalSettingsSectionSupport.resolveBuiltInFontDisplayName(
                hasBuiltIn = true,
                currentFontName = "JetBrains Mono",
                builtInNotInstalledText = "未安装",
            )
        ).isEqualTo("JetBrains Mono")

        assertThat(
            TerminalSettingsSectionSupport.resolveCustomFontDisplayName(
                customFontPath = "",
                selectFileText = "选择文件",
            )
        ).isEqualTo("选择文件")
        assertThat(
            TerminalSettingsSectionSupport.resolveCustomFontDisplayName(
                customFontPath = "C:/fonts/FiraCode.ttf",
                selectFileText = "选择文件",
            )
        ).isEqualTo("FiraCode.ttf")

        assertThat(
            TerminalSettingsSectionSupport.buildFontFamilyActionSpecs(
                builtInLabel = "内置字体",
                builtInDisplayName = "未安装",
                systemMonoLabel = "系统等宽",
                customLabel = "自定义字体",
                customDisplayName = "选择文件",
                customFontPath = "",
                changeCustomFontLabel = "更换自定义字体",
            )
        ).containsExactly(
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.SelectBuiltIn,
                label = "内置字体 (未安装)",
            ),
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.SelectSystemMono,
                label = "系统等宽",
            ),
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.PickCustomFont,
                label = "自定义字体 (选择文件)",
            ),
        ).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.buildFontFamilyActionSpecs(
                builtInLabel = "内置字体",
                builtInDisplayName = "JetBrains Mono",
                systemMonoLabel = "系统等宽",
                customLabel = "自定义字体",
                customDisplayName = "FiraCode.ttf",
                customFontPath = "C:/fonts/FiraCode.ttf",
                changeCustomFontLabel = "更换自定义字体",
            )
        ).containsExactly(
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.SelectBuiltIn,
                label = "内置字体 (JetBrains Mono)",
            ),
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.SelectSystemMono,
                label = "系统等宽",
            ),
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.UseCustomFont,
                label = "自定义字体 (FiraCode.ttf)",
            ),
            TerminalSettingsFontActionSpec(
                action = TerminalSettingsFontAction.ChangeCustomFont,
                label = "更换自定义字体",
            ),
        ).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.resolveFontSizeDialogSteps(12f, 20f)
        ).isEqualTo(7)
        assertThat(
            TerminalSettingsSectionSupport.resolveFontSizeDialogSteps(12f, 12f)
        ).isEqualTo(0)
    }

    @Test
    fun buildBackendModeOptions_shouldHideProotWhenLinuxEnvironmentDisabled() {
        assertThat(
            TerminalSettingsSectionSupport.buildBackendModeOptions(false)
        ).containsExactly(
            TerminalSettingsOptionSpec(
                value = BackendMode.AUTO.value,
                labelRes = Strings.backend_mode_auto,
                descriptionRes = Strings.backend_mode_auto_desc,
            ),
            TerminalSettingsOptionSpec(
                value = BackendMode.HOST.value,
                labelRes = Strings.backend_mode_host,
                descriptionRes = Strings.backend_mode_host_desc,
            ),
        ).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.buildBackendModeOptions(true)
        ).containsExactly(
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
        ).inOrder()
    }

    @Test
    fun resolvePackageManagerAndToolStatus_shouldCoverKnownMappings() {
        assertThat(
            TerminalSettingsSectionSupport.resolvePackageManagerLabelRes(RootfsPackageManager.APK)
        ).isEqualTo(Strings.settings_linux_package_manager_apk)
        assertThat(
            TerminalSettingsSectionSupport.resolvePackageManagerLabelRes(RootfsPackageManager.UNKNOWN)
        ).isEqualTo(Strings.settings_linux_package_manager_unknown)

        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolStatusLabelRes(null)
        ).isEqualTo(Strings.settings_linux_tool_status_checking)
        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolStatusLabelRes(true)
        ).isEqualTo(Strings.settings_linux_tool_status_ready)
        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolStatusLabelRes(false)
        ).isEqualTo(Strings.settings_linux_tool_status_missing)
        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolStatusLabelRes(
                status = false,
                notRequired = true,
            )
        ).isEqualTo(Strings.settings_linux_tool_status_not_required)
    }

    @Test
    fun resolveLinuxToolsHealthAndRequiredStates_shouldReflectLocaleRequirement() {
        assertThat(
            TerminalSettingsSectionSupport.buildRequiredLinuxToolStates(
                locale = "C.UTF-8",
                devPackagesInstalled = true,
                zshInstalled = false,
                localeSupportInstalled = null,
            )
        ).containsExactly(true, false).inOrder()
        assertThat(
            TerminalSettingsSectionSupport.buildRequiredLinuxToolStates(
                locale = "zh_CN.UTF-8",
                devPackagesInstalled = true,
                zshInstalled = false,
                localeSupportInstalled = null,
            )
        ).containsExactly(true, false, null).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolsHealthText(listOf(true, null))
        ).isEqualTo(
            TerminalSettingsTextSpec(Strings.settings_linux_tool_status_checking)
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolsHealthText(listOf(true, true))
        ).isEqualTo(
            TerminalSettingsTextSpec(
                labelRes = Strings.settings_linux_tools_health_ready,
                formatArgs = listOf(2, 2),
            )
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveLinuxToolsHealthText(listOf(true, false, true))
        ).isEqualTo(
            TerminalSettingsTextSpec(
                labelRes = Strings.settings_linux_tools_health_attention,
                formatArgs = listOf(2, 3),
            )
        )
    }

    @Test
    fun resolveGuestDevPackagesGroupAndSubtitle_shouldHandleKnownAndCustomGroups() {
        val compilerStatus = GuestDevPackagesCommandGroupStatus(
            commands = listOf("cc", "gcc", "clang"),
            available = false,
        )
        val customStatus = GuestDevPackagesCommandGroupStatus(
            commands = listOf("foo", "bar"),
            available = false,
        )

        assertThat(
            TerminalSettingsSectionSupport.resolveGuestDevPackagesGroupLabel(compilerStatus)
        ).isEqualTo(
            TerminalSettingsDisplaySpec.ResourceText(Strings.settings_linux_tool_group_compiler)
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveGuestDevPackagesGroupLabel(customStatus)
        ).isEqualTo(
            TerminalSettingsDisplaySpec.RawText("foo/bar")
        )

        val missingSummary = TerminalSettingsSectionSupport.buildGuestDevPackagesMissingSummary(
            listOf("编译器", "cmake")
        )
        assertThat(missingSummary).isEqualTo("编译器、cmake")
        assertThat(
            TerminalSettingsSectionSupport.buildGuestDevPackagesMissingSummary(emptyList())
        ).isNull()

        assertThat(
            TerminalSettingsSectionSupport.buildGuestDevPackagesSubtitle(
                baseDescription = "基础描述",
                devPackagesInstalled = false,
                missingComponentsText = "缺失：$missingSummary",
            )
        ).isEqualTo("基础描述\n缺失：编译器、cmake")
        assertThat(
            TerminalSettingsSectionSupport.buildGuestDevPackagesSubtitle(
                baseDescription = "基础描述",
                devPackagesInstalled = true,
                missingComponentsText = "缺失：$missingSummary",
            )
        ).isEqualTo("基础描述")
    }

    @Test
    fun buildGuestDevPackagesDiagnosticMessage_shouldRenderPackagesAndCommandStates() {
        assertThat(
            TerminalSettingsSectionSupport.buildGuestDevPackagesDiagnosticMessage(
                baseDescription = "开发包说明",
                packagesToInstallText = "待安装：clang, make",
                commandDiagnosticsTitle = "命令诊断",
                checkingText = "检查中",
                readyText = "已就绪",
                missingText = "缺失",
                commandGroupDiagnostics = listOf(
                    TerminalSettingsCommandGroupDiagnostic(
                        label = "编译器",
                        commands = listOf("cc", "gcc", "clang"),
                        available = false,
                    ),
                    TerminalSettingsCommandGroupDiagnostic(
                        label = "make",
                        commands = listOf("make"),
                        available = true,
                    ),
                ),
            )
        ).isEqualTo(
            """
            开发包说明

            待安装：clang, make

            命令诊断
            • 编译器（cc/gcc/clang）：缺失
            • make（make）：已就绪
            """.trimIndent()
        )

        assertThat(
            TerminalSettingsSectionSupport.buildGuestDevPackagesDiagnosticMessage(
                baseDescription = "开发包说明",
                packagesToInstallText = null,
                commandDiagnosticsTitle = "命令诊断",
                checkingText = "检查中",
                readyText = "已就绪",
                missingText = "缺失",
                commandGroupDiagnostics = emptyList(),
            )
        ).isEqualTo(
            """
            开发包说明

            命令诊断
            检查中
            """.trimIndent()
        )
    }

    @Test
    fun buildLinuxToolsDiagnosticMessage_shouldIncludeOptionalMissingComponents() {
        assertThat(
            TerminalSettingsSectionSupport.buildLinuxToolsDiagnosticMessage(
                systemNameLabel = "系统",
                activeProfileName = "Alpine",
                packageManagerLabel = "包管理器",
                packageManagerName = "apk",
                guestDevPackagesLabel = "开发包",
                guestDevPackagesStatusText = "缺失",
                guestDevPackagesMissingComponentsText = "缺失组件：编译器、cmake",
                zshLabel = "zsh",
                zshStatusText = "已就绪",
                localeSupportLabel = "语言支持",
                localeSupportStatusText = "不需要",
                localeSupportDescription = "当前语言：C.UTF-8",
            )
        ).isEqualTo(
            """
            系统：Alpine
            包管理器：apk

            开发包：缺失
            缺失组件：编译器、cmake
            zsh：已就绪
            语言支持：不需要
            当前语言：C.UTF-8
            """.trimIndent()
        )
    }

    @Test
    fun resolveSelectedBackendMode_shouldForceHostOutsideLinuxEnvironment() {
        assertThat(
            TerminalSettingsSectionSupport.resolveSelectedBackendMode(
                linuxEnvironmentEnabled = false,
                backendMode = BackendMode.PROOT.value,
            )
        ).isEqualTo(BackendMode.HOST)
        assertThat(
            TerminalSettingsSectionSupport.resolveSelectedBackendMode(
                linuxEnvironmentEnabled = true,
                backendMode = BackendMode.PROOT.value,
            )
        ).isEqualTo(BackendMode.PROOT)

        assertThat(
            TerminalSettingsSectionSupport.shouldForceHostBackend(
                linuxEnvironmentEnabled = false,
                backendMode = BackendMode.PROOT.value,
            )
        ).isTrue()
        assertThat(
            TerminalSettingsSectionSupport.shouldForceHostBackend(
                linuxEnvironmentEnabled = true,
                backendMode = BackendMode.PROOT.value,
            )
        ).isFalse()
    }

    @Test
    fun buildShellSubtitle_shouldIncludeAvailabilityOrProbeFailure() {
        val subtitle = TerminalSettingsSectionSupport.buildShellSubtitle(
            baseDescription = "Shell 描述",
            availability = ShellAvailabilityInfo(
                backend = TerminalBackendType.PROOT,
                autoResolved = ShellType.BASH,
                availableShells = listOf(ShellType.SH, ShellType.ZSH, ShellType.BASH),
            ),
            shellAvailabilityError = null,
            selectedShell = ShellType.AUTO,
            backendLabelProvider = { backend ->
                when (backend) {
                    TerminalBackendType.PROOT -> "PRoot 后端"
                    TerminalBackendType.HOST -> "Host 后端"
                }
            },
            shellLabelProvider = { shell -> shell.value.uppercase() },
            autoResolvedTextProvider = { label -> "自动解析：$label" },
            availableTextProvider = { labels -> "可用：$labels" },
            probeFailedTextProvider = { error -> "探测失败：$error" },
        )
        assertThat(subtitle).isEqualTo(
            "Shell 描述\nPRoot 后端\n自动解析：BASH\n可用：ZSH / BASH / SH"
        )

        assertThat(
            TerminalSettingsSectionSupport.buildShellSubtitle(
                baseDescription = "Shell 描述",
                availability = null,
                shellAvailabilityError = "boom",
                selectedShell = ShellType.SH,
                backendLabelProvider = { "" },
                shellLabelProvider = { it.value },
                autoResolvedTextProvider = { it },
                availableTextProvider = { it },
                probeFailedTextProvider = { error -> "探测失败：$error" },
            )
        ).isEqualTo("Shell 描述\n探测失败：boom")
    }

    @Test
    fun buildActionSpecs_shouldReflectCurrentTerminalState() {
        assertThat(
            TerminalSettingsSectionSupport.buildLinuxToolsActionSpecs(
                isPRootInstalled = false,
                devPackagesInstalled = false,
                zshInstalled = false,
                locale = "zh_CN.UTF-8",
                localeSupportInstalled = false,
            )
        ).containsExactly(
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.RefreshLinuxToolsStatus,
                labelRes = Strings.btn_refresh,
            )
        ).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.buildLinuxToolsActionSpecs(
                isPRootInstalled = true,
                devPackagesInstalled = true,
                zshInstalled = false,
                locale = "zh_CN.UTF-8",
                localeSupportInstalled = false,
            )
        ).containsExactly(
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.RefreshLinuxToolsStatus,
                labelRes = Strings.btn_refresh,
            ),
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.ReinstallGuestDevPackages,
                labelRes = Strings.settings_linux_action_reinstall_dev_packages,
            ),
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.InstallZsh,
                labelRes = Strings.dialog_title_install_zsh,
            ),
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.InstallLocale,
                labelRes = Strings.dialog_title_install_locale,
            ),
        ).inOrder()

        assertThat(
            TerminalSettingsSectionSupport.buildGuestDevPackagesActionSpecs(true)
        ).containsExactly(
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.ReinstallGuestDevPackages,
                labelRes = Strings.settings_linux_action_reinstall_dev_packages,
            )
        )
        assertThat(
            TerminalSettingsSectionSupport.buildZshActionSpecs()
        ).containsExactly(
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.ChangeShell,
                labelRes = Strings.settings_linux_action_change_shell,
            ),
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.ReinstallZsh,
                labelRes = Strings.settings_linux_action_reinstall_zsh,
            ),
        ).inOrder()
        assertThat(
            TerminalSettingsSectionSupport.buildLocaleActionSpecs()
        ).containsExactly(
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.ChangeLocale,
                labelRes = Strings.settings_linux_action_change_locale,
            ),
            TerminalSettingsActionSpec(
                action = TerminalSettingsAction.RebuildLocale,
                labelRes = Strings.settings_linux_action_rebuild_locale,
            ),
        ).inOrder()
    }

    @Test
    fun resolveInstallDialogSpecs_shouldExposeStableTitlesMessagesAndLocaleTarget() {
        assertThat(
            TerminalSettingsSectionSupport.resolveZshInstallDialogSpec(forceReinstall = false)
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_install_zsh),
                message = TerminalSettingsTextSpec(Strings.dialog_message_install_zsh),
            )
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveZshInstallDialogSpec(forceReinstall = true)
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_reinstall_zsh),
                message = TerminalSettingsTextSpec(Strings.dialog_message_reinstall_zsh),
            )
        )

        assertThat(
            TerminalSettingsSectionSupport.resolveGuestDevPackagesInstallDialogSpec(
                forceReinstall = false
            )
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_install_guest_dev_packages),
                message = TerminalSettingsTextSpec(Strings.dialog_message_install_guest_dev_packages),
            )
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveGuestDevPackagesInstallDialogSpec(
                forceReinstall = true
            )
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_reinstall_guest_dev_packages),
                message = TerminalSettingsTextSpec(
                    Strings.dialog_message_reinstall_guest_dev_packages
                ),
            )
        )

        assertThat(
            TerminalSettingsSectionSupport.resolveLocaleInstallDialogSpec(
                forceReinstall = false,
                pendingLocale = "zh_CN.UTF-8",
                currentLocale = "C.UTF-8",
            )
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_install_locale),
                message = TerminalSettingsTextSpec(Strings.dialog_message_install_locale),
            )
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveLocaleInstallDialogSpec(
                forceReinstall = true,
                pendingLocale = "zh_CN.UTF-8",
                currentLocale = "C.UTF-8",
            )
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_rebuild_locale_support),
                message = TerminalSettingsTextSpec(
                    labelRes = Strings.dialog_message_rebuild_locale_support,
                    formatArgs = listOf("zh_CN.UTF-8"),
                ),
            )
        )
        assertThat(
            TerminalSettingsSectionSupport.resolveLocaleInstallDialogSpec(
                forceReinstall = true,
                pendingLocale = null,
                currentLocale = "ja_JP.UTF-8",
            )
        ).isEqualTo(
            TerminalSettingsDialogSpec(
                title = TerminalSettingsTextSpec(Strings.dialog_title_rebuild_locale_support),
                message = TerminalSettingsTextSpec(
                    labelRes = Strings.dialog_message_rebuild_locale_support,
                    formatArgs = listOf("ja_JP.UTF-8"),
                ),
            )
        )

        assertThat(
            TerminalSettingsSectionSupport.resolveLocaleInstallTarget("zh_CN.UTF-8")
        ).isEqualTo("zh_CN.UTF-8")
        assertThat(
            TerminalSettingsSectionSupport.resolveLocaleInstallTarget(null)
        ).isEqualTo("C.UTF-8")
    }
}

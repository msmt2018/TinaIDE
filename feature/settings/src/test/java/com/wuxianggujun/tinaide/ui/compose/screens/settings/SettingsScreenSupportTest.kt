package com.wuxianggujun.tinaide.ui.compose.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsScreenSupportTest {

    @Test
    fun resolveRouteResolution_shouldKeepDeveloperRouteOnScrollableContent() {
        val resolution = resolveRouteResolution(
            currentRoute = SettingsRoute.Developer,
            hasHelpContent = true,
            hasPackagesContent = true,
            hasAiSettingsContent = true
        )

        assertThat(resolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(resolution.scrollableContent).isEqualTo(SettingsScrollableContent.Developer)
    }

    @Test
    fun resolveRouteResolution_shouldPreferExternalHostsWhenSlotsAvailable() {
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.Help,
                hasHelpContent = true,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.HelpContent)
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.Packages,
                hasHelpContent = false,
                hasPackagesContent = true
            ).host
        ).isEqualTo(SettingsScreenHost.PackagesContent)
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.Ai,
                hasHelpContent = false,
                hasPackagesContent = false,
                hasAiSettingsContent = true
            ).host
        ).isEqualTo(SettingsScreenHost.AiSettingsContent)
    }

    @Test
    fun resolveRouteResolution_shouldKeepPluginAndGitRoutesOnSpecialLayouts() {
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.Git,
                hasHelpContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.GitSpecialLayout)
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.Plugins,
                hasHelpContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.PluginsSpecialLayout)
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.PluginMarketplace,
                hasHelpContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.PluginMarketplaceScreen)
        assertThat(
            resolveRouteResolution(
                currentRoute = SettingsRoute.PluginLog,
                hasHelpContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.PluginLogScreen)
    }

    @Test
    fun resolveRouteResolution_shouldFallbackToScrollablePlaceholderWhenExternalSlotsMissing() {
        val helpResolution = resolveRouteResolution(
            currentRoute = SettingsRoute.Help,
            hasHelpContent = false,
            hasPackagesContent = false
        )
        val packagesResolution = resolveRouteResolution(
            currentRoute = SettingsRoute.Packages,
            hasHelpContent = false,
            hasPackagesContent = false
        )
        val aiResolution = resolveRouteResolution(
            currentRoute = SettingsRoute.Ai,
            hasHelpContent = false,
            hasPackagesContent = false,
            hasAiSettingsContent = false
        )

        assertThat(helpResolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(helpResolution.scrollableContent).isEqualTo(SettingsScrollableContent.Placeholder)
        assertThat(packagesResolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(packagesResolution.scrollableContent).isEqualTo(SettingsScrollableContent.Placeholder)
        assertThat(aiResolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(aiResolution.scrollableContent).isEqualTo(SettingsScrollableContent.Placeholder)
    }

    @Test
    fun shouldShowLinuxEnvironmentInstallPrompt_shouldOnlyTriggerOnFirstEnableWithoutEnvironment() {
        assertThat(
            SettingsScreenSupport.shouldShowLinuxEnvironmentInstallPrompt(
                previousLinuxEnvironmentEnabled = false,
                currentLinuxEnvironmentEnabled = true,
                isEnvironmentReady = false
            )
        ).isTrue()
        assertThat(
            SettingsScreenSupport.shouldShowLinuxEnvironmentInstallPrompt(
                previousLinuxEnvironmentEnabled = false,
                currentLinuxEnvironmentEnabled = true,
                isEnvironmentReady = true
            )
        ).isFalse()
        assertThat(
            SettingsScreenSupport.shouldShowLinuxEnvironmentInstallPrompt(
                previousLinuxEnvironmentEnabled = true,
                currentLinuxEnvironmentEnabled = true,
                isEnvironmentReady = false
            )
        ).isFalse()
        assertThat(
            SettingsScreenSupport.shouldShowLinuxEnvironmentInstallPrompt(
                previousLinuxEnvironmentEnabled = false,
                currentLinuxEnvironmentEnabled = false,
                isEnvironmentReady = false
            )
        ).isFalse()
    }

    private fun resolveRouteResolution(
        currentRoute: SettingsRoute,
        hasHelpContent: Boolean,
        hasPackagesContent: Boolean,
        hasAiSettingsContent: Boolean = false
    ): SettingsScreenRouteResolution = SettingsScreenSupport.resolveRouteResolution(
        currentRoute = currentRoute,
        hasHelpContent = hasHelpContent,
        hasPackagesContent = hasPackagesContent,
        hasAiSettingsContent = hasAiSettingsContent
    )
}

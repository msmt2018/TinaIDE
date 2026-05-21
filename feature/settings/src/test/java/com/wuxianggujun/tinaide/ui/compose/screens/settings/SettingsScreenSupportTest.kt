package com.wuxianggujun.tinaide.ui.compose.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsScreenSupportTest {

    @Test
    fun resolveRouteResolution_shouldKeepDeveloperRouteOnScrollableContent() {
        val resolution = SettingsScreenSupport.resolveRouteResolution(
            currentRoute = SettingsRoute.Developer,
            hasHelpContent = true,
            hasFeedbackContent = true,
            hasPackagesContent = true
        )

        assertThat(resolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(resolution.scrollableContent).isEqualTo(SettingsScrollableContent.Developer)
    }

    @Test
    fun resolveRouteResolution_shouldPreferExternalHostsWhenSlotsAvailable() {
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.Help,
                hasHelpContent = true,
                hasFeedbackContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.HelpContent)
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.Feedback,
                hasHelpContent = false,
                hasFeedbackContent = true,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.FeedbackContent)
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.Packages,
                hasHelpContent = false,
                hasFeedbackContent = false,
                hasPackagesContent = true
            ).host
        ).isEqualTo(SettingsScreenHost.PackagesContent)
    }

    @Test
    fun resolveRouteResolution_shouldKeepPluginAndGitRoutesOnSpecialLayouts() {
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.Git,
                hasHelpContent = false,
                hasFeedbackContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.GitSpecialLayout)
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.Plugins,
                hasHelpContent = false,
                hasFeedbackContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.PluginsSpecialLayout)
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.PluginMarketplace,
                hasHelpContent = false,
                hasFeedbackContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.PluginMarketplaceScreen)
        assertThat(
            SettingsScreenSupport.resolveRouteResolution(
                currentRoute = SettingsRoute.PluginLog,
                hasHelpContent = false,
                hasFeedbackContent = false,
                hasPackagesContent = false
            ).host
        ).isEqualTo(SettingsScreenHost.PluginLogScreen)
    }

    @Test
    fun resolveRouteResolution_shouldFallbackToScrollablePlaceholderWhenExternalSlotsMissing() {
        val helpResolution = SettingsScreenSupport.resolveRouteResolution(
            currentRoute = SettingsRoute.Help,
            hasHelpContent = false,
            hasFeedbackContent = false,
            hasPackagesContent = false
        )
        val feedbackResolution = SettingsScreenSupport.resolveRouteResolution(
            currentRoute = SettingsRoute.Feedback,
            hasHelpContent = false,
            hasFeedbackContent = false,
            hasPackagesContent = false
        )
        val packagesResolution = SettingsScreenSupport.resolveRouteResolution(
            currentRoute = SettingsRoute.Packages,
            hasHelpContent = false,
            hasFeedbackContent = false,
            hasPackagesContent = false
        )

        assertThat(helpResolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(helpResolution.scrollableContent).isEqualTo(SettingsScrollableContent.Placeholder)
        assertThat(feedbackResolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(feedbackResolution.scrollableContent).isEqualTo(SettingsScrollableContent.Placeholder)
        assertThat(packagesResolution.host).isEqualTo(SettingsScreenHost.ScrollableContent)
        assertThat(packagesResolution.scrollableContent).isEqualTo(SettingsScrollableContent.Placeholder)
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
}

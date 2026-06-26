package com.wuxianggujun.tinaide.ui.compose.screens.settings

internal enum class SettingsScreenHost {
    HelpContent,
    FeedbackContent,
    PluginMarketplaceScreen,
    PluginLogScreen,
    StorageCleanupScreen,
    PackagesContent,
    GitSpecialLayout,
    PluginsSpecialLayout,
    ScrollableContent
}

internal enum class SettingsScrollableContent {
    Root,
    Editor,
    Lsp,
    Compiler,
    Project,
    Storage,
    Terminal,
    Appearance,
    Keyboard,
    Developer,
    About,
    Placeholder
}

internal data class SettingsScreenRouteResolution(
    val host: SettingsScreenHost,
    val scrollableContent: SettingsScrollableContent? = null
)

internal object SettingsScreenSupport {
    fun resolveRouteResolution(
        currentRoute: SettingsRoute,
        hasHelpContent: Boolean,
        hasFeedbackContent: Boolean,
        hasPackagesContent: Boolean
    ): SettingsScreenRouteResolution = when {
        currentRoute == SettingsRoute.Help && hasHelpContent -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.HelpContent
        )

        currentRoute == SettingsRoute.Feedback && hasFeedbackContent -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.FeedbackContent
        )

        currentRoute == SettingsRoute.PluginMarketplace -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.PluginMarketplaceScreen
        )

        currentRoute == SettingsRoute.PluginLog -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.PluginLogScreen
        )

        currentRoute == SettingsRoute.StorageCleanup -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.StorageCleanupScreen
        )

        currentRoute == SettingsRoute.Packages && hasPackagesContent -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.PackagesContent
        )

        currentRoute == SettingsRoute.Git -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.GitSpecialLayout
        )

        currentRoute == SettingsRoute.Plugins -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.PluginsSpecialLayout
        )

        else -> SettingsScreenRouteResolution(
            host = SettingsScreenHost.ScrollableContent,
            scrollableContent = resolveScrollableContent(currentRoute)
        )
    }

    fun resolveScrollableContent(currentRoute: SettingsRoute): SettingsScrollableContent = when (currentRoute) {
        SettingsRoute.Root -> SettingsScrollableContent.Root
        SettingsRoute.Editor -> SettingsScrollableContent.Editor
        SettingsRoute.Lsp -> SettingsScrollableContent.Lsp
        SettingsRoute.Compiler -> SettingsScrollableContent.Compiler
        SettingsRoute.Project -> SettingsScrollableContent.Project
        SettingsRoute.Storage -> SettingsScrollableContent.Storage
        SettingsRoute.Terminal -> SettingsScrollableContent.Terminal
        SettingsRoute.Appearance -> SettingsScrollableContent.Appearance
        SettingsRoute.Keyboard -> SettingsScrollableContent.Keyboard
        SettingsRoute.Developer -> SettingsScrollableContent.Developer
        SettingsRoute.About -> SettingsScrollableContent.About
        else -> SettingsScrollableContent.Placeholder
    }

    fun shouldShowLinuxEnvironmentInstallPrompt(
        previousLinuxEnvironmentEnabled: Boolean,
        currentLinuxEnvironmentEnabled: Boolean,
        isEnvironmentReady: Boolean
    ): Boolean = !previousLinuxEnvironmentEnabled &&
        currentLinuxEnvironmentEnabled &&
        !isEnvironmentReady
}

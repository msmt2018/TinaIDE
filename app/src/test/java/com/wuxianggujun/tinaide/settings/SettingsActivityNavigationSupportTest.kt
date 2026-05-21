package com.wuxianggujun.tinaide.settings

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.ui.compose.screens.settings.SettingsRoute
import org.junit.Test

class SettingsActivityNavigationSupportTest {

    @Test
    fun navigateTo_shouldPushCurrentRouteBeforeSwitching() {
        val nextState = SettingsActivityNavigationSupport.navigateTo(
            currentRoute = SettingsRoute.Root,
            routeStack = emptyList(),
            targetRoute = SettingsRoute.Developer
        )

        assertThat(nextState.currentRoute).isEqualTo(SettingsRoute.Developer)
        assertThat(nextState.routeStack).containsExactly(SettingsRoute.Root).inOrder()
    }

    @Test
    fun navigateBack_shouldPopRoutesInReverseOrder() {
        val firstStep = SettingsActivityNavigationSupport.navigateTo(
            currentRoute = SettingsRoute.Root,
            routeStack = emptyList(),
            targetRoute = SettingsRoute.Developer
        )
        val secondStep = SettingsActivityNavigationSupport.navigateTo(
            currentRoute = firstStep.currentRoute,
            routeStack = firstStep.routeStack,
            targetRoute = SettingsRoute.About
        )

        val backToDeveloper = SettingsActivityNavigationSupport.navigateBack(secondStep.routeStack)
        val backToRoot = SettingsActivityNavigationSupport.navigateBack(backToDeveloper.routeStack)

        assertThat(backToDeveloper.shouldFinish).isFalse()
        assertThat(backToDeveloper.currentRoute).isEqualTo(SettingsRoute.Developer)
        assertThat(backToDeveloper.routeStack).containsExactly(SettingsRoute.Root).inOrder()
        assertThat(backToRoot.shouldFinish).isFalse()
        assertThat(backToRoot.currentRoute).isEqualTo(SettingsRoute.Root)
        assertThat(backToRoot.routeStack).isEmpty()
    }

    @Test
    fun navigateBack_shouldRequestFinishWhenNoHistoryRemains() {
        val backResult = SettingsActivityNavigationSupport.navigateBack(emptyList())

        assertThat(backResult.shouldFinish).isTrue()
        assertThat(backResult.currentRoute).isNull()
        assertThat(backResult.routeStack).isEmpty()
    }
}

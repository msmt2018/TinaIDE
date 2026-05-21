package com.wuxianggujun.tinaide.ui.compose.screens.packages

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.packages.model.GUIPackage
import org.junit.Test

class PackageFilterStateTest {

    @Test
    fun filterState_shouldDefaultToBlankSearchQueryAndSupportCopy() {
        val defaultState = PackageFilterState()
        val queriedState = defaultState.copy(searchQuery = "cmake")

        assertThat(defaultState.searchQuery).isEmpty()
        assertThat(queriedState.searchQuery).isEqualTo("cmake")
    }

    @Test
    fun uiStateCopy_shouldCarryFilteredSelectionAndDetailPackage() {
        val cmake = GUIPackage(id = "cmake", name = "CMake")
        val ninja = GUIPackage(id = "ninja", name = "Ninja")

        val state = PackageManagerUiState().copy(
            packages = listOf(cmake, ninja),
            filteredPackages = listOf(cmake),
            selectedPackageIds = setOf("cmake"),
            isSelectionMode = true,
            currentDetailPackage = cmake,
        )

        assertThat(state.packages).containsExactly(cmake, ninja).inOrder()
        assertThat(state.filteredPackages).containsExactly(cmake)
        assertThat(state.selectedPackageIds).containsExactly("cmake")
        assertThat(state.isSelectionMode).isTrue()
        assertThat(state.currentDetailPackage).isEqualTo(cmake)
    }
}

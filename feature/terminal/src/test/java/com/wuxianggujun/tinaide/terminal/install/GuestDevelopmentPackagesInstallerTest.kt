package com.wuxianggujun.tinaide.terminal.install

import com.wuxianggujun.tinaide.core.proot.RootfsPackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestDevelopmentPackagesInstallerTest {

    @Test
    fun `apk plan should include alpine development packages`() {
        val plan = buildDevelopmentPackagesPlan(RootfsPackageManager.APK)

        requireNotNull(plan)
        assertEquals(
            listOf("build-base", "git", "curl", "pkgconf", "cmake"),
            plan.packages
        )
        assertEquals(
            listOf(
                listOf("cc", "gcc", "clang"),
                listOf("make"),
                listOf("git"),
                listOf("curl"),
                listOf("cmake"),
                listOf("pkg-config", "pkgconf"),
            ),
            plan.commandGroups
        )
    }

    @Test
    fun `apt plan should include ubuntu development packages`() {
        val plan = buildDevelopmentPackagesPlan(RootfsPackageManager.APT)

        requireNotNull(plan)
        assertTrue(plan.packages.contains("build-essential"))
        assertTrue(plan.packages.contains("pkg-config"))
        assertTrue(plan.commandGroups.contains(listOf("cc", "gcc", "clang")))
        assertTrue(plan.commandGroups.contains(listOf("pkg-config", "pkgconf")))
    }

    @Test
    fun `dnf plan should include toolchain command verification groups`() {
        val plan = buildDevelopmentPackagesPlan(RootfsPackageManager.DNF)

        requireNotNull(plan)
        assertTrue(plan.packages.contains("gcc"))
        assertTrue(plan.packages.contains("gcc-c++"))
        assertTrue(plan.packages.contains("make"))
        assertEquals(6, plan.commandGroups.size)
    }

    @Test
    fun `unknown package manager should have no plan`() {
        assertNull(buildDevelopmentPackagesPlan(RootfsPackageManager.UNKNOWN))
    }
}

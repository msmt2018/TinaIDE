package com.wuxianggujun.tinaide.core.packages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PackageAbiCompatibilityTest {

    @Test
    fun isCompatible_shouldAllowAbiIndependentPackages() {
        assertThat(PackageAbiCompatibility.isCompatible(null, arrayOf("x86_64"))).isTrue()
        assertThat(PackageAbiCompatibility.isCompatible(emptyList(), arrayOf("x86_64"))).isTrue()
    }

    @Test
    fun isCompatible_shouldMatchAnySupportedDeviceAbi() {
        assertThat(
            PackageAbiCompatibility.isCompatible(
                requiredAbis = listOf("arm64-v8a", "x86_64"),
                supportedAbis = arrayOf("x86_64", "x86")
            )
        ).isTrue()
    }

    @Test
    fun isCompatible_shouldRejectUnsupportedBinaryPackageAbi() {
        assertThat(
            PackageAbiCompatibility.isCompatible(
                requiredAbis = listOf("arm64-v8a"),
                supportedAbis = arrayOf("x86_64", "x86")
            )
        ).isFalse()
    }
}

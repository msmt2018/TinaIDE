package com.wuxianggujun.tinaide.core.compile

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.compile.action.LaunchIntent
import org.junit.Test

class EffectiveBuildConfigResolverTest {

    @Test
    fun `make build keeps run config debug for normal run`() {
        val resolved = EffectiveBuildConfigResolver.resolveBuildTypeAndDebugInfo(
            launch = LaunchIntent.Run(OutputMode.TERMINAL),
            buildSystem = BuildSystem.MAKE,
            cmakeBuildType = CMakeBuildTypeOption.RELEASE,
            configuredBuildType = BuildType.DEBUG
        )

        assertThat(resolved.first).isEqualTo(BuildType.DEBUG)
        assertThat(resolved.second).isTrue()
    }

    @Test
    fun `single file build keeps run config release for build mode`() {
        val resolved = EffectiveBuildConfigResolver.resolveBuildTypeAndDebugInfo(
            launch = LaunchIntent.None,
            buildSystem = BuildSystem.SINGLE_FILE,
            cmakeBuildType = CMakeBuildTypeOption.DEBUG,
            configuredBuildType = BuildType.RELEASE
        )

        assertThat(resolved.first).isEqualTo(BuildType.RELEASE)
        assertThat(resolved.second).isFalse()
    }

    @Test
    fun `cmake still follows cmake build type`() {
        val resolved = EffectiveBuildConfigResolver.resolveBuildTypeAndDebugInfo(
            launch = LaunchIntent.Run(OutputMode.TERMINAL),
            buildSystem = BuildSystem.CMAKE,
            cmakeBuildType = CMakeBuildTypeOption.RELEASE,
            configuredBuildType = BuildType.DEBUG
        )

        assertThat(resolved.first).isEqualTo(BuildType.RELEASE)
        assertThat(resolved.second).isFalse()
    }

    @Test
    fun `debug launch intent always forces debug`() {
        val resolved = EffectiveBuildConfigResolver.resolveBuildTypeAndDebugInfo(
            launch = LaunchIntent.Debug,
            buildSystem = BuildSystem.MAKE,
            cmakeBuildType = CMakeBuildTypeOption.RELEASE,
            configuredBuildType = BuildType.RELEASE
        )

        assertThat(resolved.first).isEqualTo(BuildType.DEBUG)
        assertThat(resolved.second).isTrue()
    }
}

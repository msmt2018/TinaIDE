package com.wuxianggujun.tinaide.core.ndk

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.serialization.JsonSerializer
import org.junit.Test

class InstalledToolchainConfigTest {

    @Test
    fun config_shouldSerializeToolchainInfoWithStableEnumNames() {
        val config = InstalledToolchainConfig(
            activeToolchain = "builtin",
            toolchains = listOf(
                ToolchainInfo(
                    id = "builtin",
                    name = "Builtin",
                    version = "18",
                    type = ToolchainType.BUILTIN,
                    path = "toolchains/builtin",
                    installedAt = 100L
                )
            )
        )

        val encoded = JsonSerializer.default.encodeToString(
            InstalledToolchainConfig.serializer(),
            config
        )
        val decoded = JsonSerializer.default.decodeFromString(
            InstalledToolchainConfig.serializer(),
            encoded
        )

        assertThat(encoded).contains("BUILTIN")
        assertThat(decoded).isEqualTo(config)
    }
}

package com.wuxianggujun.tinaide.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PluginNetworkHostRulesTest {

    @Test
    fun `normalizeDeclaredHost should normalize valid hosts`() {
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("Api.Example.com"))
            .isEqualTo("api.example.com")
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("127.0.0.1"))
            .isEqualTo("127.0.0.1")
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("localhost"))
            .isEqualTo("localhost")
    }

    @Test
    fun `normalizeDeclaredHost should reject unsupported host formats`() {
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("https://api.example.com")).isNull()
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("api.example.com/v1")).isNull()
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("api.example.com:443")).isNull()
        assertThat(PluginNetworkHostRules.normalizeDeclaredHost("*.example.com")).isNull()
    }

    @Test
    fun `findDuplicateDeclaredHosts should collapse case variants`() {
        val duplicates = PluginNetworkHostRules.findDuplicateDeclaredHosts(
            listOf("API.example.com", "api.example.com", "cdn.example.com")
        )

        assertThat(duplicates).containsExactly("api.example.com")
    }

    @Test
    fun `isUrlAllowed should support exact host and subdomain matching`() {
        assertThat(
            PluginNetworkHostRules.isUrlAllowed(
                url = "https://api.example.com/path",
                pluginAllowedHosts = setOf("example.com"),
            )
        ).isTrue()
        assertThat(
            PluginNetworkHostRules.isUrlAllowed(
                url = "https://example.com/path",
                pluginAllowedHosts = setOf("example.com"),
            )
        ).isTrue()
        assertThat(
            PluginNetworkHostRules.isUrlAllowed(
                url = "https://evil-example.com/path",
                pluginAllowedHosts = setOf("example.com"),
            )
        ).isFalse()
    }
}

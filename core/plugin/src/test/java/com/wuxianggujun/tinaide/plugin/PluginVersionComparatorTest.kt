package com.wuxianggujun.tinaide.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PluginVersionComparatorTest {

    @Test
    fun `compare should treat missing patch versions as zero`() {
        assertThat(PluginVersionComparator.compare("1.0", "1.0.0")).isEqualTo(0)
        assertThat(PluginVersionComparator.compare("1.2.1", "1.2")).isGreaterThan(0)
    }

    @Test
    fun `compare should sort numeric segments correctly`() {
        assertThat(PluginVersionComparator.compare("1.10.0", "1.2.0")).isGreaterThan(0)
        assertThat(PluginVersionComparator.compare("2.0.0", "10.0.0")).isLessThan(0)
    }

    @Test
    fun `compare should prefer stable releases over prerelease versions`() {
        assertThat(PluginVersionComparator.compare("1.0.0", "1.0.0-beta")).isGreaterThan(0)
        assertThat(PluginVersionComparator.compare("1.0.0-beta.2", "1.0.0-beta.11")).isLessThan(0)
    }

    @Test
    fun `compare should return null for non comparable versions`() {
        assertThat(PluginVersionComparator.compare("unknown", "1.0.0")).isNull()
        assertThat(PluginVersionComparator.compare("1.0.0", "next")).isNull()
    }
}

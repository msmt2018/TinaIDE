package com.wuxianggujun.tinaide.core.lang

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectPathFiltersTest {

    @Test
    fun isNoisyDirectoryName_shouldMatchNamesCaseInsensitivelyAndKnownPrefixes() {
        assertThat(ProjectPathFilters.isNoisyDirectoryName(".GIT")).isTrue()
        assertThat(ProjectPathFilters.isNoisyDirectoryName("Build")).isTrue()
        assertThat(ProjectPathFilters.isNoisyDirectoryName("cmake-build-debug")).isTrue()
        assertThat(ProjectPathFilters.isNoisyDirectoryName("src")).isFalse()
    }

    @Test
    fun shouldSkipSearchDirectory_shouldSkipHiddenExternalAndNoisyDirectories() {
        assertThat(ProjectPathFilters.shouldSkipSearchDirectory(".idea")).isTrue()
        assertThat(ProjectPathFilters.shouldSkipSearchDirectory("external")).isTrue()
        assertThat(ProjectPathFilters.shouldSkipSearchDirectory("node_modules")).isTrue()
        assertThat(ProjectPathFilters.shouldSkipSearchDirectory("app")).isFalse()
    }

    @Test
    fun syncIgnorePatterns_shouldIncludeDirectoryAndBinaryArtifactRules() {
        assertThat(ProjectPathFilters.SYNC_IGNORE_PATTERNS).contains("build/")
        assertThat(ProjectPathFilters.SYNC_IGNORE_PATTERNS).contains("cmake-build-*/")
        assertThat(ProjectPathFilters.SYNC_IGNORE_PATTERNS).contains("*.so")
        assertThat(ProjectPathFilters.SYNC_IGNORE_PATTERNS).contains("*.apk")
    }
}

package com.wuxianggujun.tinaide.ui.projectlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectListUtilsTest {

    @Test
    fun formatFileSize_shouldUseWholeBinaryUnits() {
        assertThat(formatFileSize(0)).isEqualTo("0 B")
        assertThat(formatFileSize(1023)).isEqualTo("1023 B")
        assertThat(formatFileSize(1024)).isEqualTo("1 KB")
        assertThat(formatFileSize(5L * 1024 * 1024)).isEqualTo("5 MB")
        assertThat(formatFileSize(3L * 1024 * 1024 * 1024)).isEqualTo("3 GB")
    }

    @Test
    fun extractProjectNameFromUrl_shouldSupportHttpsAndSshUrls() {
        assertThat(extractProjectNameFromUrl("https://github.com/user/tina-ide.git"))
            .isEqualTo("tina-ide")
        assertThat(extractProjectNameFromUrl("git@github.com:user/native_plugin.git"))
            .isEqualTo("native_plugin")
        assertThat(extractProjectNameFromUrl(" https://example.com/user/app.demo/ "))
            .isEqualTo("appdemo")
        assertThat(extractProjectNameFromUrl(" ")).isEmpty()
    }

    @Test
    fun isValidGitUrl_shouldAcceptCommonTransportPrefixesOnly() {
        assertThat(isValidGitUrl("https://github.com/user/repo.git")).isTrue()
        assertThat(isValidGitUrl("http://example.com/user/repo")).isTrue()
        assertThat(isValidGitUrl("git@github.com:user/repo.git")).isTrue()
        assertThat(isValidGitUrl("ssh://git@example.com/user/repo.git")).isTrue()
        assertThat(isValidGitUrl("ftp://example.com/user/repo.git")).isFalse()
        assertThat(isValidGitUrl("example.com/user/repo.git")).isFalse()
    }
}

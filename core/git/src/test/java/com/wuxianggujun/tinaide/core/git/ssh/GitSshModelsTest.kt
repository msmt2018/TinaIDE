package com.wuxianggujun.tinaide.core.git.ssh

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GitSshModelsTest {

    @Test
    fun sshModels_shouldExposeSafeDefaults() {
        val key = GitSshKeyMeta(name = "default")
        val state = GitSshState(keys = listOf(key))
        val target = ParsedSshTarget(host = "github.com")

        assertThat(key.type).isEqualTo("ed25519")
        assertThat(key.comment).isNull()
        assertThat(state.defaultKeyName).isNull()
        assertThat(state.hostBindings).isEmpty()
        assertThat(target.port).isNull()
    }

    @Test
    fun hostBinding_shouldPreserveOptionalPort() {
        val binding = GitSshHostBinding(host = "git.example.test", keyName = "work", port = 2222)

        assertThat(binding.host).isEqualTo("git.example.test")
        assertThat(binding.keyName).isEqualTo("work")
        assertThat(binding.port).isEqualTo(2222)
    }
}

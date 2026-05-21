package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.git.GitCredential
import com.wuxianggujun.tinaide.core.git.ssh.GitSshHostBinding
import com.wuxianggujun.tinaide.core.git.ssh.GitSshKeyMeta
import com.wuxianggujun.tinaide.core.i18n.Strings
import java.net.URI

internal data class GitHttpsEditorState(
    val host: String,
    val username: String,
    val token: String,
    val isEdit: Boolean,
)

internal data class GitHttpsCredentialDraft(
    val resolvedHost: String,
    val username: String,
    val token: String,
)

internal data class GitSshBindingEditorState(
    val host: String,
    val keyName: String,
    val port: String,
)

internal object GitSettingsSectionSupport {
    fun createAddHttpsEditorState(): GitHttpsEditorState = GitHttpsEditorState(
        host = "",
        username = "",
        token = "",
        isEdit = false,
    )

    fun createEditHttpsEditorState(credential: GitCredential): GitHttpsEditorState = GitHttpsEditorState(
        host = credential.host,
        username = credential.username,
        token = "",
        isEdit = true,
    )

    fun resolveHttpsCredentialDraft(
        rawHost: String,
        rawUsername: String,
        rawToken: String,
    ): GitHttpsCredentialDraft = GitHttpsCredentialDraft(
        resolvedHost = extractHost(rawHost.trim()),
        username = rawUsername.trim().ifBlank { "oauth2" },
        token = rawToken.trim(),
    )

    fun isHttpsHostInvalid(resolvedHost: String): Boolean = resolvedHost.isBlank()

    fun isNewHttpsTokenMissing(isEdit: Boolean, token: String): Boolean = !isEdit && token.isBlank()

    fun resolveHttpsCredentialToken(
        inputToken: String,
        existingToken: String?,
    ): String? = inputToken.ifBlank { existingToken.orEmpty() }.ifBlank { null }

    fun sortHttpsCredentials(credentials: List<GitCredential>): List<GitCredential> = credentials.sortedBy { it.host.lowercase() }

    fun buildDefaultKeyOptions(
        keys: List<GitSshKeyMeta>,
        noneLabel: String,
    ): List<Pair<String, String>> = (keys.map { it.name to it.name } + ("__none__" to noneLabel)).distinct()

    fun resolveSelectedDefaultKey(selected: String): String? = selected.takeIf { it != "__none__" }

    fun createAddBindingEditorState(
        defaultKeyName: String?,
        keys: List<GitSshKeyMeta>,
    ): GitSshBindingEditorState = GitSshBindingEditorState(
        host = "",
        keyName = defaultKeyName ?: keys.firstOrNull()?.name.orEmpty(),
        port = "",
    )

    fun createEditBindingEditorState(binding: GitSshHostBinding): GitSshBindingEditorState = GitSshBindingEditorState(
        host = binding.host,
        keyName = binding.keyName,
        port = binding.port?.toString().orEmpty(),
    )

    fun clearBindingEditorState(): GitSshBindingEditorState = GitSshBindingEditorState(
        host = "",
        keyName = "",
        port = "",
    )

    @StringRes
    fun resolveBindingDialogTitleRes(isEditing: Boolean): Int = if (isEditing) {
        Strings.git_ssh_binding_edit_title
    } else {
        Strings.git_ssh_binding_add_title
    }

    fun resolveBindingDraft(
        host: String,
        keyName: String,
        port: String,
    ): GitSshHostBinding = GitSshHostBinding(
        host = host.trim(),
        keyName = keyName.trim(),
        port = port.trim().toIntOrNull(),
    )

    fun resolveBindingKeyDisplayValue(
        keyName: String,
        notSelectedLabel: String,
    ): String = keyName.ifBlank { notSelectedLabel }

    fun extractHost(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.contains("://")) {
            val uri = runCatching { URI(trimmed) }.getOrNull()
            return uri?.host?.trim().orEmpty()
        }

        if (trimmed.contains("/") && !trimmed.contains(" ")) {
            return trimmed.substringBefore("/").trim()
        }

        return trimmed
    }

    fun suggestKeyName(lastPathSegment: String?): String {
        val base = lastPathSegment
            ?.substringAfterLast('/')
            ?.trim()
            .orEmpty()
            .removeSuffix(".pub")
            .removeSuffix(".key")
            .removeSuffix(".pem")
            .removeSuffix(".txt")
            .ifBlank { "id_ed25519" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}

package com.wuxianggujun.tinaide.core.git.ssh

/**
 * SSH 密钥元数据
 */
data class GitSshKeyMeta(
    val name: String,
    val type: String = "ed25519",
    val comment: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

/**
 * SSH Host 绑定（指定 host 使用哪个密钥）
 */
data class GitSshHostBinding(
    val host: String,
    val keyName: String,
    val port: Int? = null,
)

/**
 * SSH 持久化状态
 */
data class GitSshState(
    val defaultKeyName: String? = null,
    val keys: List<GitSshKeyMeta> = emptyList(),
    val hostBindings: List<GitSshHostBinding> = emptyList(),
)

/**
 * 解析后的 SSH 目标
 */
data class ParsedSshTarget(
    val host: String,
    val port: Int? = null,
)

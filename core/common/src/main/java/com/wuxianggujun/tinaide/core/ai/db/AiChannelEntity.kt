package com.wuxianggujun.tinaide.core.ai.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BYOK AI 渠道实体。
 *
 * 每一条记录对应一个用户自定义的 AI 供应商配置。
 * apiKey 不存储在表中，改由 [com.wuxianggujun.tinaide.ai.channel.AiChannelApiKeyStore]
 * 加密存放在 EncryptedSharedPreferences。
 */
@Entity(
    tableName = "ai_channels",
    indices = [Index(value = ["last_used_at"], name = "index_ai_channels_last_used_at")]
)
data class AiChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "provider")
    val provider: String,

    @ColumnInfo(name = "base_url")
    val baseUrl: String,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
)

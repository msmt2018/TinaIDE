package com.wuxianggujun.tinaide.core.ai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * BYOK AI 渠道 DAO。
 *
 * 复用的列表排序规则：最近使用在前、同时间戳按创建时间倒序。
 */
@Dao
interface AiChannelDao {

    @Query(
        """
        SELECT * FROM ai_channels
        ORDER BY
            CASE WHEN last_used_at IS NULL THEN 1 ELSE 0 END,
            last_used_at DESC,
            created_at DESC
        """
    )
    fun observeAll(): Flow<List<AiChannelEntity>>

    @Query("SELECT * FROM ai_channels WHERE id = :id")
    suspend fun getById(id: String): AiChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiChannelEntity)

    @Update
    suspend fun update(entity: AiChannelEntity)

    @Query("DELETE FROM ai_channels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE ai_channels SET last_used_at = :timestamp WHERE id = :id")
    suspend fun markUsed(id: String, timestamp: Long)
}

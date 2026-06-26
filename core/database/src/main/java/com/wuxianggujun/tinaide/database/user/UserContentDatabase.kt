package com.wuxianggujun.tinaide.database.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 用户内容数据库（收藏、下载历史）
 *
 * 架构说明：
 * - 数据库实现在 core:database 层
 * - 通过 IUserContentRepository 接口对外暴露（定义在 core:common）
 * - 遵循依赖倒置原则（DIP）
 */
@Database(
    entities = [
        FavoriteEntity::class,
        DownloadHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class UserContentDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        private fun dropLegacyAiTables(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `chat_messages`")
            db.execSQL("DROP TABLE IF EXISTS `conversations`")
            db.execSQL("DROP TABLE IF EXISTS `ai_channels`")
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropLegacyAiTables(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropLegacyAiTables(db)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropLegacyAiTables(db)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropLegacyAiTables(db)
            }
        }

        @Volatile
        private var instanceRef: UserContentDatabase? = null

        fun getInstance(context: Context): UserContentDatabase = instanceRef ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                UserContentDatabase::class.java,
                "user_content_database"
            )
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .build()
            instanceRef = instance
            instance
        }

        /**
         * 用于测试的实例清理
         */
        fun clearInstance() {
            instanceRef = null
        }
    }
}

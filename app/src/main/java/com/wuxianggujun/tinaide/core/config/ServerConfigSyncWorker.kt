package com.wuxianggujun.tinaide.core.config

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * 服务器配置同步 Worker
 *
 * 使用 WorkManager 在后台定期同步服务器配置，确保：
 * 1. 应用在后台时也能获取最新配置
 * 2. 网络恢复后自动同步
 * 3. 遵循系统电量优化策略
 */
class ServerConfigSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServerConfigSyncWorker"
        private const val WORK_NAME = "server_config_sync"

        // 默认同步间隔（分钟）
        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 15L

        // 最小同步间隔（分钟）- WorkManager 限制
        private const val MIN_SYNC_INTERVAL_MINUTES = 15L

        /**
         * 调度定期同步任务
         *
         * @param context 上下文
         * @param intervalMinutes 同步间隔（分钟），最小 15 分钟
         */
        fun schedule(context: Context, intervalMinutes: Long = DEFAULT_SYNC_INTERVAL_MINUTES) {
            val effectiveInterval = intervalMinutes.coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // 需要网络连接
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ServerConfigSyncWorker>(
                effectiveInterval,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // 保留现有任务，避免重复调度
                workRequest
            )

            Timber.tag(TAG).i("Scheduled periodic config sync every $effectiveInterval minutes")
        }

        /**
         * 取消定期同步任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).i("Cancelled periodic config sync")
        }

        /**
         * 根据服务端配置更新同步间隔
         *
         * 如果服务端返回的间隔与当前不同，重新调度任务
         */
        fun updateIntervalIfNeeded(context: Context) {
            val serverIntervalSecs = ServerConfigManager.getCurrentConfig().configRefreshIntervalSecs
            val serverIntervalMinutes = (serverIntervalSecs / 60).coerceAtLeast(MIN_SYNC_INTERVAL_MINUTES)

            // 重新调度（使用 REPLACE 策略更新间隔）
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ServerConfigSyncWorker>(
                serverIntervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // 更新现有任务
                workRequest
            )

            Timber.tag(TAG).d("Updated sync interval to $serverIntervalMinutes minutes (from server: ${serverIntervalSecs}s)")
        }
    }

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Starting config sync work")

        return try {
            val success = ServerConfigManager.syncConfig(applicationContext, force = true)

            if (success) {
                Timber.tag(TAG).i("Config sync completed successfully")
                Result.success()
            } else {
                Timber.tag(TAG).w("Config sync failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Config sync work failed with exception")
            Result.retry()
        }
    }
}

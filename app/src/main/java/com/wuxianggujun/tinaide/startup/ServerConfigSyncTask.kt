package com.wuxianggujun.tinaide.startup

import android.content.Context
import com.wuxianggujun.tinaide.core.config.ServerConfigManager
import com.wuxianggujun.tinaide.core.config.ServerConfigSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 启动时后台同步服务器配置
 *
 * 先加载本地缓存，再从服务器同步最新配置，最后调度 WorkManager 定时同步。
 */
class ServerConfigSyncTask(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    companion object {
        private const val TAG = "ServerConfigSyncTask"
    }

    fun execute() {
        scope.launch(Dispatchers.IO) {
            try {
                ServerConfigManager.loadCachedConfig(context)
                val success = ServerConfigManager.syncConfig(context)
                if (success) {
                    Timber.tag(TAG).i("Server config synced successfully at startup")
                } else {
                    Timber.tag(TAG).w("Failed to sync server config at startup, using cached/default config")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Exception while syncing server config at startup")
            } finally {
                ServerConfigSyncWorker.schedule(context)
            }
        }
    }
}

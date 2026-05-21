package com.wuxianggujun.tinaide.startup

import android.content.Context
import com.wuxianggujun.tinaide.core.packages.BundledPackagesInstaller
import com.wuxianggujun.tinaide.core.packages.store.LocalInstallStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 内置包安装启动任务
 *
 * 在应用启动时自动解压 assets 中的预编译库
 */
class BundledPackagesInstallTask(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BundledPackagesInstallTask"
    }

    fun execute() {
        scope.launch {
            try {
                Timber.tag(TAG).d("Starting bundled packages installation...")

                val installer = BundledPackagesInstaller(
                    context = context,
                    installStateStore = LocalInstallStateStore(context)
                )

                installer.installBundledPackages()

                Timber.tag(TAG).i("Bundled packages installation completed")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to install bundled packages")
            }
        }
    }
}

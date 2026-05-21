package com.wuxianggujun.tinaide.ui

import android.content.Context
import com.wuxianggujun.tinaide.storage.ExternalFileIntents
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class MainActivityExternalFileLauncherDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onInfo: (String) -> Unit,
    private val onError: (String) -> Unit,
) : MainActivityExternalFileLauncher {

    override fun openWithExternalApp(file: File) {
        scope.launch {
            ExternalFileIntents.openWithExternalApp(
                context = context,
                file = file,
                onError = onError,
            )
        }
    }

    override fun shareFileOrDirectory(file: File) {
        scope.launch {
            ExternalFileIntents.shareFileOrDirectory(
                context = context,
                file = file,
                onInfo = onInfo,
                onError = onError,
            )
        }
    }
}

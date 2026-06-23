package com.wuxianggujun.tinaide.core.crash

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 崩溃日志自动上传器。
 *
 * 设计目标：
 * - 只在主进程运行（由调用方保证）；
 * - 启动后立即尝试上传一次，避免只依赖 JobScheduler 被系统延迟；
 * - JobScheduler 仍作为网络不可用、进程被杀、系统限流时的兜底重试；
 * - 默认关闭，用户可在设置中明确开启；
 * - 对已上传 tombstone 做去重，避免重复上报。
 */
object CrashLogAutoUploader {
    private const val TAG = "CrashLogAutoUploader"

    @Volatile
    private var actions = CrashLogAutoUploadActions()

    fun scheduleIfNeeded(context: Context) {
        actions.scheduleIfNeeded(context.applicationContext)
    }

    fun uploadOnStartup(context: Context, scope: CoroutineScope) {
        uploadOnStartup(context, scope, null)
    }

    fun uploadOnStartup(
        context: Context,
        scope: CoroutineScope,
        onCompleted: (() -> Unit)?,
    ) {
        uploadWithSchedulerFallback(
            context = context,
            scope = scope,
            forceScheduleBeforeUpload = false,
            onCompleted = onCompleted,
        )
    }

    fun uploadFromCrashScreen(context: Context, scope: CoroutineScope) {
        uploadFromCrashScreen(context, scope, null)
    }

    fun uploadFromCrashScreen(
        context: Context,
        scope: CoroutineScope,
        onCompleted: (() -> Unit)?,
    ) {
        uploadWithSchedulerFallback(
            context = context,
            scope = scope,
            forceScheduleBeforeUpload = true,
            onCompleted = onCompleted,
        )
    }

    private fun uploadWithSchedulerFallback(
        context: Context,
        scope: CoroutineScope,
        forceScheduleBeforeUpload: Boolean,
        onCompleted: (() -> Unit)?,
    ) {
        val appContext = context.applicationContext
        if (!CrashUploadState.isAutoUploadEnabled(appContext)) {
            Timber.tag(TAG).d("Crash auto-upload is disabled")
            return
        }

        val currentActions = actions
        // 先注册系统兜底任务，再做即时尝试；即使进程马上被杀，Job 仍有机会后续补偿。
        if (forceScheduleBeforeUpload) {
            currentActions.schedule(appContext)
        } else {
            currentActions.scheduleIfNeeded(appContext)
        }

        scope.launch(currentActions.uploadDispatcher) {
            val shouldRetry = runCatching {
                currentActions.uploadPending(appContext)
            }.onFailure { t ->
                Timber.tag(TAG).e(t, "Immediate crash log upload failed")
            }.getOrDefault(true)

            if (shouldRetry) {
                currentActions.scheduleIfNeeded(appContext)
            }

            onCompleted?.invoke()
        }
    }

    internal fun replaceActionsForTesting(testActions: CrashLogAutoUploadActions): AutoCloseable {
        val previous = actions
        actions = testActions
        return AutoCloseable {
            actions = previous
        }
    }
}

internal data class CrashLogAutoUploadActions(
    val scheduleIfNeeded: (Context) -> Unit = { CrashLogUploadScheduler.scheduleIfNeeded(it) },
    val schedule: (Context) -> Unit = { CrashLogUploadScheduler.schedule(it) },
    val uploadPending: suspend (Context) -> Boolean = { CrashLogUploader.uploadPending(it) },
    val uploadDispatcher: CoroutineDispatcher = Dispatchers.IO,
)

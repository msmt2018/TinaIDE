package com.wuxianggujun.tinaide.core.i18n

import android.content.Context
import androidx.annotation.StringRes

/**
 * 全局字符串访问入口（避免到处传 Context）。
 *
 * 约束：
 * - 必须在 Application.onCreate() 尽早调用 [initialize]。
 * - 若调用方已持有更合适的 Context（例如 Activity/Locale 包装 Context），优先使用 [getOr]。
 */
object AppStrings {

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    fun get(@StringRes resId: Int, vararg formatArgs: Any?): String {
        val ctx = checkNotNull(appContext) {
            "AppStrings not initialized. Call AppStrings.initialize(context) in TinaApplication.onCreate()."
        }
        if (formatArgs.isEmpty()) return ctx.getString(resId)
        if (formatArgs.none { it == null }) return ctx.getString(resId, *formatArgs)
        val safeArgs = formatArgs.map { it ?: "" }.toTypedArray()
        return ctx.getString(resId, *safeArgs)
    }

    fun getOr(context: Context?, @StringRes resId: Int, vararg formatArgs: Any?): String {
        val ctx = context ?: appContext
        checkNotNull(ctx) {
            "AppStrings not initialized and Context is null."
        }
        if (formatArgs.isEmpty()) return ctx.getString(resId)
        if (formatArgs.none { it == null }) return ctx.getString(resId, *formatArgs)
        val safeArgs = formatArgs.map { it ?: "" }.toTypedArray()
        return ctx.getString(resId, *safeArgs)
    }

    internal fun applicationContextOrNull(): Context? = appContext
}

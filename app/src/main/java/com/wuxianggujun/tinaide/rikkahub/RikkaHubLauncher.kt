package com.wuxianggujun.tinaide.rikkahub

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import me.rerere.rikkahub.RikkaHubInitializer
import me.rerere.rikkahub.RouteActivity
import timber.log.Timber

object RikkaHubLauncher {
    private const val TAG = "RikkaHubLauncher"

    fun open(context: Context): Boolean {
        val application = context.applicationContext as? Application
        if (application == null) {
            Timber.tag(TAG).w("Cannot open embedded RikkaHub without an Application context")
            Toast.makeText(context, Strings.rikkahub_open_failed.strOr(context), Toast.LENGTH_SHORT).show()
            return false
        }

        return runCatching {
            RikkaHubInitializer.initializeEmbedded(application)
            val intent = Intent(context, RouteActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            Timber.tag(TAG).e(error, "Failed to open embedded RikkaHub")
            Toast.makeText(context, Strings.rikkahub_open_failed.strOr(context), Toast.LENGTH_SHORT).show()
            false
        }
    }
}

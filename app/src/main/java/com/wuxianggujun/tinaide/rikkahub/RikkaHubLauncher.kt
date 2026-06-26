package com.wuxianggujun.tinaide.rikkahub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import timber.log.Timber

object RikkaHubLauncher {
    private const val TAG = "RikkaHubLauncher"

    private val packageCandidates = listOf(
        "me.rerere.rikkahub.debug",
        "me.rerere.rikkahub",
    )

    fun open(context: Context): Boolean {
        val packageManager = context.packageManager
        for (packageName in packageCandidates) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ?: continue

            try {
                context.startActivity(launchIntent)
                return true
            } catch (error: ActivityNotFoundException) {
                Timber.tag(TAG).w(error, "RikkaHub package has no launchable activity: %s", packageName)
            } catch (error: SecurityException) {
                Timber.tag(TAG).w(error, "RikkaHub launch rejected by the system: %s", packageName)
            }
        }

        Toast.makeText(context, Strings.rikkahub_not_installed.strOr(context), Toast.LENGTH_SHORT).show()
        return false
    }
}

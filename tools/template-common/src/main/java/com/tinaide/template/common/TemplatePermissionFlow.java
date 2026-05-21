package com.tinaide.template.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.view.ContextThemeWrapper;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.base.IPermission;
import com.tinaide.template.common.TemplatePermissionResolver.PermissionBuckets;

import java.util.List;

/**
 * 模板公共权限 flow：两阶段（special → dangerous）请求 + 冷却式系统设置引导 + 首次解释对话框。
 *
 * 兼容性与稳定性策略：
 * 1) 先请求特殊权限（MANAGE_EXTERNAL_STORAGE 等），再请求危险权限。
 * 2) 被拒绝后仅在冷却窗口外跳转设置页一次，避免 onResume 无限跳转。
 * 3) 全部授予时清理 redirect 状态 + 首次提示标记，用户未来仍可被正常引导。
 */
public final class TemplatePermissionFlow {

    private static final String TAG = "TemplatePermFlow";
    private static final String PREFS_NAME = "template_permission_state";
    private static final String KEY_LAST_SETTINGS_REDIRECT_AT = "last_settings_redirect_at";
    private static final String KEY_RESTRICTED_PROMPT_SHOWN = "restricted_prompt_shown";
    private static final long SETTINGS_REDIRECT_COOLDOWN_MS = 30_000L;
    private static final long REQUEST_COOLDOWN_MS = 1200L;

    private final Activity activity;
    private final SharedPreferences prefs;

    private boolean requestInFlight;
    private long lastRequestAt;
    private PermissionBuckets buckets;

    public TemplatePermissionFlow(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void advance(String source) {
        if (requestInFlight || activity.isFinishing()) {
            return;
        }

        PermissionBuckets resolved = resolveBuckets();
        List<IPermission> specialDenied = XXPermissions.getDeniedPermissions(activity, resolved.specialPermissions);
        if (!specialDenied.isEmpty()) {
            requestByStep(specialDenied, true, source);
            return;
        }

        List<IPermission> dangerousDenied = XXPermissions.getDeniedPermissions(activity, resolved.dangerousPermissions);
        if (!dangerousDenied.isEmpty()) {
            requestByStep(dangerousDenied, false, source);
            return;
        }

        clearRedirectState();
        Log.i(TAG, "All required permissions granted from " + source);
    }

    private void requestByStep(
        List<IPermission> deniedPermissions,
        boolean specialPermissionStep,
        String source
    ) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRequestAt < REQUEST_COOLDOWN_MS) {
            return;
        }

        requestInFlight = true;
        lastRequestAt = now;
        String step = specialPermissionStep ? "special" : "dangerous";
        Log.i(TAG, "Requesting " + step + " permissions from " + source + ", denied=" + deniedPermissions.size());

        XXPermissions.with(activity)
            .permissions(deniedPermissions)
            .request(new OnPermissionCallback() {
                @Override
                public void onResult(List<IPermission> grantedList, List<IPermission> deniedList) {
                    requestInFlight = false;
                    if (deniedList.isEmpty()) {
                        Log.i(TAG, step + " permissions granted");
                        advance("request-result-" + step);
                        return;
                    }

                    boolean doNotAskAgain = XXPermissions.isDoNotAskAgainPermissions(activity, deniedList);
                    Log.w(
                        TAG,
                        step + " permissions denied, denied=" + deniedList.size()
                            + ", doNotAskAgain=" + doNotAskAgain
                    );

                    if (specialPermissionStep || doNotAskAgain) {
                        maybeRedirectToSettings(deniedList, step);
                    }
                }
            });
    }

    private void maybeRedirectToSettings(List<IPermission> deniedList, String step) {
        long lastRedirectAt = prefs.getLong(KEY_LAST_SETTINGS_REDIRECT_AT, 0L);
        long now = System.currentTimeMillis();
        long sinceLast = now - lastRedirectAt;
        if (lastRedirectAt > 0L && sinceLast >= 0L && sinceLast < SETTINGS_REDIRECT_COOLDOWN_MS) {
            Log.w(TAG, "Skip settings redirect for " + step + ": within cooldown (" + sinceLast + "ms)");
            return;
        }

        boolean promptShown = prefs.getBoolean(KEY_RESTRICTED_PROMPT_SHOWN, false);
        if (!promptShown) {
            prefs.edit().putBoolean(KEY_RESTRICTED_PROMPT_SHOWN, true).apply();
            showRestrictedPrompt(deniedList, step);
            return;
        }

        doRedirect(deniedList, step);
    }

    private void showRestrictedPrompt(List<IPermission> deniedList, String step) {
        Context dialogContext = new ContextThemeWrapper(
            activity,
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        );
        new AlertDialog.Builder(dialogContext)
            .setTitle(R.string.restricted_settings_title)
            .setMessage(R.string.restricted_settings_message)
            .setCancelable(false)
            .setPositiveButton(
                R.string.restricted_settings_go,
                (dialog, which) -> doRedirect(deniedList, step)
            )
            .setNegativeButton(R.string.restricted_settings_cancel, null)
            .show();
    }

    private void doRedirect(List<IPermission> deniedList, String step) {
        prefs.edit()
            .putLong(KEY_LAST_SETTINGS_REDIRECT_AT, System.currentTimeMillis())
            .apply();
        Log.i(TAG, "Redirect to permission settings for " + step);
        XXPermissions.startPermissionActivity(activity, deniedList);
    }

    private void clearRedirectState() {
        prefs.edit()
            .remove(KEY_LAST_SETTINGS_REDIRECT_AT)
            .remove(KEY_RESTRICTED_PROMPT_SHOWN)
            .apply();
    }

    private PermissionBuckets resolveBuckets() {
        if (buckets == null) {
            buckets = TemplatePermissionResolver.resolve(activity);
        }
        return buckets;
    }
}

package com.tinaide.template.common;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.hjq.permissions.permission.PermissionLists;
import com.hjq.permissions.permission.base.IPermission;
import com.hjq.permissions.permission.common.SpecialPermission;
import com.hjq.permissions.permission.dangerous.StandardDangerousPermission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 读取 AndroidManifest 中的 uses-permission 列表，按 SpecialPermission / 普通危险权限分桶，
 * 供模板 Activity 统一调用，避免 NativeActivity 与 SDL3 模板各自重复实现。
 */
public final class TemplatePermissionResolver {

    private static final String TAG = "TemplatePermResolver";

    private TemplatePermissionResolver() {}

    public static PermissionBuckets resolve(Context context) {
        List<IPermission> specialPermissions = new ArrayList<>();
        List<IPermission> dangerousPermissions = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (String permissionName : loadRequestedPermissionNames(context)) {
            if (permissionName == null || !seen.add(permissionName)) {
                continue;
            }
            IPermission permission = toRuntimePermission(permissionName);
            if (permission == null) {
                continue;
            }
            if (permission instanceof SpecialPermission) {
                specialPermissions.add(permission);
            } else {
                dangerousPermissions.add(permission);
            }
        }

        return new PermissionBuckets(specialPermissions, dangerousPermissions);
    }

    private static String[] loadRequestedPermissionNames(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS)
                );
            } else {
                packageInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_PERMISSIONS
                );
            }
            return packageInfo.requestedPermissions != null
                ? packageInfo.requestedPermissions
                : new String[0];
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to read requested permissions", e);
            return new String[0];
        }
    }

    private static IPermission toRuntimePermission(String permissionName) {
        if (permissionName == null || permissionName.isEmpty()) {
            return null;
        }
        switch (permissionName) {
            case android.Manifest.permission.MANAGE_EXTERNAL_STORAGE:
                return PermissionLists.getManageExternalStoragePermission();
            case android.Manifest.permission.REQUEST_INSTALL_PACKAGES:
                return PermissionLists.getRequestInstallPackagesPermission();
            case android.Manifest.permission.SYSTEM_ALERT_WINDOW:
                return PermissionLists.getSystemAlertWindowPermission();
            case android.Manifest.permission.WRITE_SETTINGS:
                return PermissionLists.getWriteSettingsPermission();
            case android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:
                return PermissionLists.getRequestIgnoreBatteryOptimizationsPermission();
            case android.Manifest.permission.SCHEDULE_EXACT_ALARM:
                return PermissionLists.getScheduleExactAlarmPermission();
            case android.Manifest.permission.CAMERA:
                return PermissionLists.getCameraPermission();
            case android.Manifest.permission.READ_EXTERNAL_STORAGE:
                return PermissionLists.getReadExternalStoragePermission();
            case android.Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return PermissionLists.getWriteExternalStoragePermission();
            case android.Manifest.permission.READ_MEDIA_IMAGES:
                return PermissionLists.getReadMediaImagesPermission();
            case android.Manifest.permission.READ_MEDIA_VIDEO:
                return PermissionLists.getReadMediaVideoPermission();
            case android.Manifest.permission.READ_MEDIA_AUDIO:
                return PermissionLists.getReadMediaAudioPermission();
            case android.Manifest.permission.RECORD_AUDIO:
                return PermissionLists.getRecordAudioPermission();
            case android.Manifest.permission.POST_NOTIFICATIONS:
                return PermissionLists.getPostNotificationsPermission();
            default:
                if (!permissionName.startsWith("android.permission.")) {
                    Log.i(TAG, "Skip custom or non-runtime permission: " + permissionName);
                    return null;
                }
                return new StandardDangerousPermission(permissionName, Build.VERSION_CODES.BASE);
        }
    }

    public static final class PermissionBuckets {

        public final List<IPermission> specialPermissions;
        public final List<IPermission> dangerousPermissions;

        PermissionBuckets(List<IPermission> specialPermissions, List<IPermission> dangerousPermissions) {
            this.specialPermissions = Collections.unmodifiableList(new ArrayList<>(specialPermissions));
            this.dangerousPermissions = Collections.unmodifiableList(new ArrayList<>(dangerousPermissions));
        }
    }
}

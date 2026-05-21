package com.tinaide.template.nativeactivity;

import android.app.NativeActivity;
import android.os.Bundle;

import com.tinaide.template.common.TemplatePermissionFlow;

/**
 * 模板专用 NativeActivity。权限 flow 统一由 {@link TemplatePermissionFlow} 驱动。
 */
public class TemplateNativeActivity extends NativeActivity {

    private TemplatePermissionFlow permissionFlow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionFlow = new TemplatePermissionFlow(this);
        permissionFlow.advance("onCreate");
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionFlow.advance("onResume");
    }
}

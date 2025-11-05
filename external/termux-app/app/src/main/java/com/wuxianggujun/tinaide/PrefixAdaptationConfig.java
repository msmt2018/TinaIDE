package com.wuxianggujun.tinaide;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Prefix Adaptation 配置管理
 * 
 * 用于控制路径适配的行为，包括是否启用实验性的 ELF Hook 功能
 */
public final class PrefixAdaptationConfig {
    
    private static final String PREFS_NAME = "prefix_adaptation";
    private static final String KEY_ENABLE_ELF_HOOK = "enable_elf_hook";
    
    private PrefixAdaptationConfig() {}
    
    /**
     * 检查是否启用 ELF Hook
     * 
     * @param context 上下文
     * @return true 启用，false 禁用（默认启用）
     */
    public static boolean isElfHookEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLE_ELF_HOOK, true); // 默认启用
    }
    
    /**
     * 设置是否启用 ELF Hook
     * 
     * @param context 上下文
     * @param enabled true 启用，false 禁用
     */
    public static void setElfHookEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLE_ELF_HOOK, enabled).apply();
        
        // 动态更新 Hook 状态
        if (PrefixHook.isInitialized()) {
            PrefixHook.setEnabled(enabled);
        }
    }
}

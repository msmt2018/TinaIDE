package com.wuxianggujun.tinaide;

import android.content.Context;
import android.util.Log;

/**
 * Termux Prefix Hook (EXPERIMENTAL)
 * 
 * 通过 hook close() 系统调用，在 ELF 文件写入时自动修改硬编码路径。
 * 
 * 这是一个实验性功能，用于补充 PrefixAdaptationManager 的脚本方案。
 * 
 * 工作原理：
 * 1. 拦截 close() 系统调用
 * 2. 检测文件是否为 ELF 格式
 * 3. 检查是否包含旧的 /data/data/com.termux 路径
 * 4. 修改 ELF 文件中的硬编码路径
 * 
 * 优点：
 * - 可以处理二进制文件中的硬编码路径
 * - 一次性修改，无运行时开销
 * 
 * 缺点：
 * - 实现复杂，可能不稳定
 * - 需要充分测试
 * - 默认禁用，需要显式启用
 * 
 * 使用方法：
 * <pre>
 * // 在 TermuxApplication.onCreate() 中初始化
 * String targetPrefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
 * PrefixHook.initialize(this, targetPrefix);
 * 
 * // 如果需要启用（默认禁用）
 * PrefixHook.setEnabled(true);
 * </pre>
 * 
 * 注意：即使不启用此 Hook，PrefixAdaptationManager 的脚本方案仍然会正常工作。
 */
public class PrefixHook {
    private static final String TAG = "PrefixHook";
    private static boolean sInitialized = false;
    private static boolean sLibraryLoaded = false;
    
    /**
     * 初始化 prefix hook
     * 
     * @param context 应用上下文
     * @param targetPrefix 目标 prefix 路径（如 /data/data/com.wuxianggujun.tinaide/files/usr）
     * @return true 成功，false 失败
     */
    public static synchronized boolean initialize(Context context, String targetPrefix) {
        if (sInitialized) {
            Log.d(TAG, "Already initialized");
            return true;
        }
        
        // 加载 native 库
        if (!loadLibrary()) {
            Log.w(TAG, "Failed to load library, hook disabled");
            return false;
        }
        
        try {
            // 调用 native 初始化
            nativeInit(targetPrefix);
            
            sInitialized = true;
            Log.i(TAG, "Prefix hook initialized with target: " + targetPrefix);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize prefix hook", e);
            return false;
        }
    }
    
    /**
     * 启用/禁用 hook
     * 
     * @param enabled true 启用，false 禁用
     */
    public static void setEnabled(boolean enabled) {
        if (!sLibraryLoaded) {
            Log.w(TAG, "Library not loaded");
            return;
        }
        
        try {
            nativeSetEnabled(enabled);
            Log.i(TAG, "Hook " + (enabled ? "enabled" : "disabled"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set hook state", e);
        }
    }
    
    /**
     * 检查 hook 是否已启用
     * 
     * @return true 已启用，false 未启用
     */
    public static boolean isEnabled() {
        if (!sLibraryLoaded) {
            return false;
        }
        
        try {
            return nativeIsEnabled();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get hook state", e);
            return false;
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return sInitialized;
    }
    
    /**
     * 加载 native 库
     */
    private static boolean loadLibrary() {
        if (sLibraryLoaded) {
            return true;
        }
        
        try {
            System.loadLibrary("termux-prefix-hook");
            sLibraryLoaded = true;
            Log.i(TAG, "Loaded libtermux-prefix-hook.so");
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Prefix hook library not available (this is optional): " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Failed to load prefix hook library: " + e.getMessage());
            return false;
        }
    }
    
    // ============ Native 方法 ============
    
    /**
     * Native 初始化方法
     */
    private static native void nativeInit(String targetPrefix);
    
    /**
     * Native 设置启用状态
     */
    private static native void nativeSetEnabled(boolean enabled);
    
    /**
     * Native 获取启用状态
     */
    private static native boolean nativeIsEnabled();
}

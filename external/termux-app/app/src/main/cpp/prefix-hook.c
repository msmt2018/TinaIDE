/**
 * Termux Prefix Hook - close() 系统调用拦截
 * 
 * 功能：在文件关闭时检测并修改 ELF 文件中的硬编码路径
 * 集成到 termux-app 模块中
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <android/log.h>
#include <jni.h>
#include <syscall.h>
#include "elf-patcher.h"

// 直接使用系统调用作为备用
#define __close(fd) syscall(__NR_close, fd)

#define TAG "TermuxPrefixHook"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 原始 close 函数指针
static int (*original_close)(int fd) = NULL;

// 目标路径（需要从 Java 层传入）
static char g_target_prefix[256] = {0};
static const char* OLD_PREFIX = "/data/data/com.termux/files/usr";

// Hook 是否已启用（默认禁用，需要显式启用）
static int g_hook_enabled = 0;
static int g_hook_initialized = 0;

/**
 * 获取文件描述符对应的文件路径
 */
static int get_fd_path(int fd, char* buf, size_t size) {
    char proc_path[64];
    snprintf(proc_path, sizeof(proc_path), "/proc/self/fd/%d", fd);
    ssize_t len = readlink(proc_path, buf, size - 1);
    if (len < 0) return -1;
    buf[len] = '\0';
    return 0;
}

/**
 * 检查文件是否为 ELF 格式
 */
static int is_elf_file(const char* path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return 0;
    
    unsigned char magic[4];
    ssize_t n = read(fd, magic, 4);
    close(fd);
    
    if (n != 4) return 0;
    
    // ELF magic: 0x7F 'E' 'L' 'F'
    return (magic[0] == 0x7F && magic[1] == 'E' && 
            magic[2] == 'L' && magic[3] == 'F');
}

/**
 * 检查文件是否在 PREFIX 目录下
 */
static int is_in_prefix(const char* path) {
    if (!path || strlen(g_target_prefix) == 0) return 0;
    return strncmp(path, g_target_prefix, strlen(g_target_prefix)) == 0;
}

/**
 * 检查文件是否包含旧的 prefix 路径
 */
static int contains_old_prefix(const char* path) {
    FILE* fp = fopen(path, "rb");
    if (!fp) return 0;
    
    // 读取文件内容搜索旧路径
    char buf[4096];
    int found = 0;
    size_t n;
    
    while ((n = fread(buf, 1, sizeof(buf), fp)) > 0) {
        if (memmem(buf, n, OLD_PREFIX, strlen(OLD_PREFIX))) {
            found = 1;
            break;
        }
    }
    
    fclose(fp);
    return found;
}

/**
 * Hook 的 close 函数
 */
int close(int fd) {
    // 如果 hook 未初始化，不应该被调用（理论上不会发生）
    if (!original_close) {
        LOGE("Hook not initialized but close() was called!");
        // 直接使用系统调用作为紧急备用
        return __close(fd);
    }
    
    // 如果 hook 未启用或 fd 无效，直接调用原始函数
    if (!g_hook_enabled || fd < 0) {
        return original_close(fd);
    }
    
    // 获取文件路径
    char path[PATH_MAX];
    if (get_fd_path(fd, path, sizeof(path)) < 0) {
        return original_close(fd);
    }
    
    // 只处理 PREFIX 目录下的文件
    if (!is_in_prefix(path)) {
        return original_close(fd);
    }
    
    // 检查是否为 ELF 文件
    if (!is_elf_file(path)) {
        return original_close(fd);
    }
    
    LOGD("Detected ELF file: %s", path);
    
    // 检查是否包含旧的 prefix
    if (!contains_old_prefix(path)) {
        LOGD("File does not contain old prefix, skipping");
        return original_close(fd);
    }
    
    LOGI("Patching ELF file: %s", path);
    
    // 先关闭文件描述符
    int ret = original_close(fd);
    
    // 修改 ELF 文件
    if (strlen(g_target_prefix) > 0) {
        if (patch_elf_file(path, OLD_PREFIX, g_target_prefix) == 0) {
            LOGI("Successfully patched: %s", path);
        } else {
            LOGE("Failed to patch: %s", path);
        }
    } else {
        LOGE("Target prefix not set!");
    }
    
    return ret;
}

/**
 * 初始化 hook
 */
static void init_prefix_hook(const char* target_prefix) {
    if (g_hook_initialized) {
        LOGD("Hook already initialized");
        return;
    }
    
    if (!target_prefix || strlen(target_prefix) == 0) {
        LOGE("Invalid target prefix");
        return;
    }
    
    strncpy(g_target_prefix, target_prefix, sizeof(g_target_prefix) - 1);
    g_target_prefix[sizeof(g_target_prefix) - 1] = '\0';
    LOGI("Prefix hook initialized with target: %s", g_target_prefix);
    LOGI("Hook is DISABLED by default. Call setEnabled(true) to activate.");
    
    // 获取原始 close 函数
    // 使用 RTLD_NEXT 获取下一个 close 符号（即真正的 libc close）
    original_close = (int (*)(int))dlsym(RTLD_NEXT, "close");
    
    if (!original_close) {
        LOGE("Failed to get original close function via RTLD_NEXT");
        LOGE("Hook cannot be initialized - this is critical!");
        return;
    }
    
    g_hook_initialized = 1;
    LOGI("Hook ready (disabled by default, complements script-based repair)");
}

/**
 * 启用/禁用 hook
 */
static void set_hook_enabled(int enabled) {
    if (!g_hook_initialized) {
        LOGE("Hook not initialized, cannot change state");
        return;
    }
    g_hook_enabled = enabled;
    LOGI("Hook %s", enabled ? "ENABLED (will patch ELF files on close)" : "DISABLED (using script-based repair only)");
}

// ============ JNI 绑定 ============

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_PrefixHook_nativeInit(
    JNIEnv* env, jclass clazz __attribute__((unused)), jstring targetPrefix) {
    
    const char* prefix = (*env)->GetStringUTFChars(env, targetPrefix, NULL);
    if (prefix) {
        init_prefix_hook(prefix);
        (*env)->ReleaseStringUTFChars(env, targetPrefix, prefix);
    }
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_PrefixHook_nativeSetEnabled(
    JNIEnv* env __attribute__((unused)), jclass clazz __attribute__((unused)), jboolean enabled) {
    set_hook_enabled(enabled ? 1 : 0);
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_PrefixHook_nativeIsEnabled(
    JNIEnv* env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    return g_hook_enabled ? JNI_TRUE : JNI_FALSE;
}

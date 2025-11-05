/**
 * Path Rewrite Hook
 * 
 * Hook mkdir/mkdirat 等系统调用，重写 com.termux 路径
 * 这个库通过 LD_PRELOAD 加载，拦截 dpkg 的目录创建操作
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <fcntl.h>
#include <unistd.h>
#include <limits.h>
#include <android/log.h>

#define TAG "PathRewrite"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 原始函数指针
static int (*real_mkdir)(const char *pathname, mode_t mode) = NULL;
static int (*real_mkdirat)(int dirfd, const char *pathname, mode_t mode) = NULL;
static int (*real_open)(const char *pathname, int flags, ...) = NULL;
static int (*real_openat)(int dirfd, const char *pathname, int flags, ...) = NULL;
static int (*real_chmod)(const char *pathname, mode_t mode) = NULL;
static int (*real_chown)(const char *pathname, uid_t owner, gid_t group) = NULL;
static int (*real_lchown)(const char *pathname, uid_t owner, gid_t group) = NULL;
static int (*real_utimes)(const char *pathname, const struct timeval times[2]) = NULL;
static int (*real_utimensat)(int dirfd, const char *pathname, const struct timespec times[2], int flags) = NULL;

// 目标 PREFIX（从环境变量获取）
static char g_prefix[256] = {0};
// App private data dir, e.g. /data/data/<pkg>
static char g_app_data_dir[256] = {0};
static int g_initialized = 0;

/**
 * 初始化
 */
static void init_once(void) {
    if (g_initialized) return;
    
    // 获取 PREFIX 环境变量
    const char* prefix = getenv("PREFIX");
    if (prefix) {
        strncpy(g_prefix, prefix, sizeof(g_prefix) - 1);
        LOGI("Path rewrite initialized with PREFIX: %s", g_prefix);

        // Derive app data dir from PREFIX.
        // Typical PREFIX is: /data/data/<pkg>/files/usr
        // We want:           /data/data/<pkg>
        char tmp[sizeof(g_prefix)];
        strncpy(tmp, g_prefix, sizeof(tmp) - 1);
        tmp[sizeof(tmp) - 1] = '\0';

        // Prefer cutting at "/files/usr"
        char *cut = strstr(tmp, "/files/usr");
        if (cut) {
            *cut = '\0';
        } else {
            // Fallback: strip two path components (..../usr -> ..../files -> ..../<pkg>)
            for (int i = 0; i < 2; i++) {
                char *slash = strrchr(tmp, '/');
                if (slash) *slash = '\0';
            }
        }
        strncpy(g_app_data_dir, tmp, sizeof(g_app_data_dir) - 1);
        g_app_data_dir[sizeof(g_app_data_dir) - 1] = '\0';
        LOGI("App data dir derived: %s", g_app_data_dir);
    } else {
        LOGI("PREFIX not set, path rewrite disabled");
    }
    
    // 获取原始函数
    real_mkdir = dlsym(RTLD_NEXT, "mkdir");
    real_mkdirat = dlsym(RTLD_NEXT, "mkdirat");
    real_open = dlsym(RTLD_NEXT, "open");
    real_openat = dlsym(RTLD_NEXT, "openat");
    real_chmod = dlsym(RTLD_NEXT, "chmod");
    real_chown = dlsym(RTLD_NEXT, "chown");
    real_lchown = dlsym(RTLD_NEXT, "lchown");
    real_utimes = dlsym(RTLD_NEXT, "utimes");
    real_utimensat = dlsym(RTLD_NEXT, "utimensat");
    
    g_initialized = 1;
}

/**
 * 重写路径
 * 将 ./data/data/com.termux 或 data/data/com.termux 重写为 PREFIX
 */
static const char* rewrite_path(const char* path, char* buffer, size_t bufsize) {
    if (!path || strlen(g_prefix) == 0) return path;
    
    // 检查是否包含 com.termux
    if (strstr(path, "com.termux") == NULL) return path;
    
    // 处理 ./data/data/com.termux/files/usr/... → PREFIX/...
    if (strncmp(path, "./data/data/com.termux/files/usr", 33) == 0) {
        snprintf(buffer, bufsize, "%s%s", g_prefix, path + 33);
        LOGD("Rewrite: %s -> %s", path, buffer);
        return buffer;
    }

    // 处理 data/data/com.termux/files/usr/... → PREFIX/...
    if (strncmp(path, "data/data/com.termux/files/usr", 31) == 0) {
        snprintf(buffer, bufsize, "%s%s", g_prefix, path + 31);
        LOGD("Rewrite: %s -> %s", path, buffer);
        return buffer;
    }
    
    // 处理 /data/data/com.termux 开头的绝对路径（包括 .dpkg-new 等后缀）
    // 将其重写到应用 data 目录，保证 dpkg 能顺利 rename/replace
    if (strncmp(path, "/data/data/com.termux", 21) == 0) {
        snprintf(buffer, bufsize, "%s%s", g_app_data_dir, path + 21);
        LOGD("Rewrite(abs): %s -> %s", path, buffer);
        return buffer;
    }
    
    // 处理 ./data/data/com.termux 或 data/data/com.termux 开头的相对路径
    const char* rel_path = path;
    if (strncmp(path, "./", 2) == 0) {
        rel_path = path + 2;
    }
    
    if (strncmp(rel_path, "data/data/com.termux", 20) == 0) {
        // 相对路径：data/data/com.termux[...] → g_app_data_dir + [...]
        snprintf(buffer, bufsize, "%s%s", g_app_data_dir, rel_path + 20);
        LOGD("Rewrite(rel): %s -> %s", path, buffer);
        return buffer;
    }
    
    // 处理 ./data/data 或 ./data
    if (strcmp(path, "./data/data") == 0 || strcmp(path, "./data") == 0 ||
        strcmp(path, "data/data") == 0 || strcmp(path, "data") == 0 ||
        strcmp(path, "/data/data") == 0 || strcmp(path, "/data") == 0) {
        // 这些是高层目录，通常不应该由 dpkg 直接操作，仍然按原逻辑忽略
        LOGD("Block data dir: %s (return success)", path);
        return NULL;
    }
    
    // 如果包含 com.termux 但不匹配上面的规则，记录警告
    LOGD("Unhandled path with com.termux: %s", path);
    return path;
}

// --- Extra hooks to keep dpkg rename workflow consistent ---
static int (*real_rename)(const char *oldpath, const char *newpath) = NULL;
static int (*real_renameat)(int olddirfd, const char *oldpath, int newdirfd, const char *newpath) = NULL;

static void ensure_extra_hooks_loaded(void) {
    if (!real_rename) real_rename = dlsym(RTLD_NEXT, "rename");
    if (!real_renameat) real_renameat = dlsym(RTLD_NEXT, "renameat");
}



/**
 * Hook renameat
 */



/**
 * Hook rename (single implementation)
 */
int rename(const char *oldpath, const char *newpath) {
    init_once();
    ensure_extra_hooks_loaded();
    if (!real_rename) return -1;
    char buf_old[PATH_MAX];
    char buf_new[PATH_MAX];
    const char *rp_old = rewrite_path(oldpath, buf_old, sizeof(buf_old));
    const char *rp_new = rewrite_path(newpath, buf_new, sizeof(buf_new));
    if (!rp_old || !rp_new) return -1;
    if (rp_old != oldpath || rp_new != newpath) {
        LOGI("rename rewritten: %s -> %s , %s -> %s", oldpath, rp_old, newpath, rp_new);
    }
    return real_rename(rp_old, rp_new);
}

/**
 * Hook renameat (single implementation)
 */
int renameat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath) {
    init_once();
    ensure_extra_hooks_loaded();
    if (!real_renameat) return -1;
    char buf_old[PATH_MAX];
    char buf_new[PATH_MAX];
    const char *rp_old = rewrite_path(oldpath, buf_old, sizeof(buf_old));
    const char *rp_new = rewrite_path(newpath, buf_new, sizeof(buf_new));
    if (!rp_old || !rp_new) return -1;
    return real_renameat(olddirfd, rp_old, newdirfd, rp_new);
}

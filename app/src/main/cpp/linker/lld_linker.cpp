// LLD 链接器实现（dlopen/dlclose 版本）
//
// 本实现通过动态加载 liblld_linker.so 来执行链接操作。
// 每次链接完成后 dlclose() 清理 LLD 全局状态，解决 duplicate symbol 问题。
//
// 架构：
// 1. dlopen("liblld_linker.so")
// 2. dlsym 获取函数指针
// 3. 调用链接函数
// 4. dlclose() 清理状态
//
// 如果 dlopen 失败，回退到直接调用（需要 fork 隔离）。

#include "lld_linker.h"
#include "lld_linker_api.h"
#include "../utils/file_utils.h"
#include "../utils/logging.h"

#include <dlfcn.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <signal.h>
#include <poll.h>
#include <errno.h>
#include <cstring>
#include <sstream>
#include <sys/stat.h>
#include <mutex>

namespace tinaide {
namespace linker {

// ============================================================================
// dlopen/dlclose 链接实现
// ============================================================================

namespace {

// 库路径缓存
static std::string g_lldLinkerLibPath;
static std::mutex g_libPathMutex;

// 设置 liblld_linker.so 的路径
void setLldLinkerLibPath(const std::string& path) {
    std::lock_guard<std::mutex> lock(g_libPathMutex);
    g_lldLinkerLibPath = path;
    LOGI("LLD linker lib path set to: %s", path.c_str());
}

// 获取 liblld_linker.so 的路径
std::string getLldLinkerLibPath() {
    std::lock_guard<std::mutex> lock(g_libPathMutex);
    return g_lldLinkerLibPath;
}

// 通过 dlopen/dlclose 执行链接
LinkResult linkWithDlopen(
    const std::vector<std::string>& objPaths,
    const std::string& outputPath,
    const LinkOptions& options,
    bool isShared) {

    LinkResult result;
    std::string libPath = getLldLinkerLibPath();

    if (libPath.empty()) {
        result.errorMessage = "LLD linker library path not set";
        LOGE("LLD linker library path not set");
        return result;
    }

    // 打开库
    LOGI("dlopen: %s", libPath.c_str());
    void* handle = dlopen(libPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        result.errorMessage = std::string("dlopen failed: ") + dlerror();
        LOGE("dlopen failed: %s", dlerror());
        return result;
    }

    // 获取函数指针
    auto apiVersionFn = reinterpret_cast<lld_api_version_fn>(dlsym(handle, "lld_api_version"));
    auto linkSharedFn = reinterpret_cast<lld_link_shared_fn>(dlsym(handle, "lld_link_shared"));
    auto linkExeFn = reinterpret_cast<lld_link_executable_fn>(dlsym(handle, "lld_link_executable"));
    auto freeResultFn = reinterpret_cast<lld_free_result_fn>(dlsym(handle, "lld_free_result"));

    if (!apiVersionFn || !linkSharedFn || !linkExeFn || !freeResultFn) {
        result.errorMessage = std::string("dlsym failed: ") + dlerror();
        LOGE("dlsym failed: %s", dlerror());
        dlclose(handle);
        return result;
    }

    // 检查 API 版本
    int version = apiVersionFn();
    if (version != LLD_LINKER_API_VERSION) {
        result.errorMessage = "LLD linker API version mismatch";
        LOGE("API version mismatch: expected %d, got %d", LLD_LINKER_API_VERSION, version);
        dlclose(handle);
        return result;
    }

    // 准备参数
    std::vector<const char*> objPathPtrs;
    objPathPtrs.reserve(objPaths.size());
    for (const auto& path : objPaths) {
        objPathPtrs.push_back(path.c_str());
    }

    std::vector<const char*> libDirPtrs;
    for (const auto& dir : options.libDirs) {
        if (!dir.empty()) {
            libDirPtrs.push_back(dir.c_str());
        }
    }

    std::vector<const char*> libPtrs;
    for (const auto& lib : options.libs) {
        if (!lib.empty()) {
            libPtrs.push_back(lib.c_str());
        }
    }

    LldLinkOptions lldOptions = {};
    lldOptions.sysroot = options.sysroot.c_str();
    lldOptions.target = options.target.c_str();
    lldOptions.is_cxx = options.isCxx ? 1 : 0;
    lldOptions.extra_lib_dirs = libDirPtrs.empty() ? nullptr : libDirPtrs.data();
    lldOptions.extra_lib_dirs_count = libDirPtrs.size();
    lldOptions.extra_libs = libPtrs.empty() ? nullptr : libPtrs.data();
    lldOptions.extra_libs_count = libPtrs.size();

    // 调用链接函数
    LldLinkResult lldResult = {};
    if (isShared) {
        LOGI("Calling lld_link_shared: %zu objects -> %s", objPaths.size(), outputPath.c_str());
        linkSharedFn(objPathPtrs.data(), objPathPtrs.size(), outputPath.c_str(), &lldOptions, &lldResult);
    } else {
        LOGI("Calling lld_link_executable: %zu objects -> %s", objPaths.size(), outputPath.c_str());
        linkExeFn(objPathPtrs.data(), objPathPtrs.size(), outputPath.c_str(), &lldOptions, &lldResult);
    }

    // 复制结果
    result.success = (lldResult.success != 0);
    result.exitCode = lldResult.exit_code;
    if (lldResult.error_message) {
        result.errorMessage = lldResult.error_message;
    }

    // 释放结果内存
    freeResultFn(&lldResult);

    // 关闭库（清理 LLD 全局状态）
    LOGI("dlclose: clearing LLD global state");
    dlclose(handle);

    if (result.success) {
        LOGI("Link succeeded: %s", outputPath.c_str());
    } else {
        LOGE("Link failed: %s", result.errorMessage.c_str());
    }

    return result;
}

// 验证 sysroot（仅用于错误检查，实际验证在 liblld_linker.so 中进行）
bool validateSysrootBasic(const LinkOptions& options, std::string& errorOut) {
    if (options.sysroot.empty()) {
        errorOut = "Sysroot not specified";
        return false;
    }

    struct stat st;
    if (stat(options.sysroot.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
        errorOut = "Sysroot directory does not exist: " + options.sysroot;
        return false;
    }

    return true;
}

} // anonymous namespace

// ============================================================================
// 公共接口实现
// ============================================================================

// 初始化链接器（设置 liblld_linker.so 路径）
void initLinker(const std::string& nativeLibDir) {
    std::string libPath = nativeLibDir + "/liblld_linker.so";
    setLldLinkerLibPath(libPath);
}

LinkResult linkExecutable(const std::string& objPath, const std::string& exePath,
                          const LinkOptions& options) {
    LOGI("linkExecutable: %s -> %s", objPath.c_str(), exePath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    std::vector<std::string> objPaths = {objPath};
    return linkWithDlopen(objPaths, exePath, options, false);
}

LinkResult linkExecutableMany(const std::vector<std::string>& objPaths,
                              const std::string& exePath,
                              const LinkOptions& options) {
    LOGI("linkExecutableMany: %zu objects -> %s", objPaths.size(), exePath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    return linkWithDlopen(objPaths, exePath, options, false);
}

LinkResult linkSharedLibrary(const std::string& objPath, const std::string& soPath,
                             const LinkOptions& options) {
    LOGI("linkSharedLibrary: %s -> %s", objPath.c_str(), soPath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    std::vector<std::string> objPaths = {objPath};
    return linkWithDlopen(objPaths, soPath, options, true);
}

LinkResult linkSharedLibraryMany(const std::vector<std::string>& objPaths,
                                 const std::string& soPath,
                                 const LinkOptions& options) {
    LOGI("linkSharedLibraryMany: %zu objects -> %s", objPaths.size(), soPath.c_str());

    std::string validationError;
    if (!validateSysrootBasic(options, validationError)) {
        LinkResult result;
        result.errorMessage = validationError;
        return result;
    }

    return linkWithDlopen(objPaths, soPath, options, true);
}

} // namespace linker
} // namespace tinaide

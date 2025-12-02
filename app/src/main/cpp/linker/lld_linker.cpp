// LLD 链接器实现

#include "lld_linker.h"
#include "../utils/file_utils.h"
#include "../utils/logging.h"

#if LLVM_HEADERS_AVAILABLE
#include "llvm/ADT/ArrayRef.h"
#include "llvm/Support/raw_ostream.h"
#endif

#include <sys/stat.h>
#include <sstream>
#include <algorithm>

// LLD 前向声明
#if LLVM_HEADERS_AVAILABLE && defined(LLD_LINK_ENABLED)
namespace llvm {
    template <typename T> class ArrayRef;
    class raw_ostream;
}
namespace lld {
namespace elf {
    bool link(llvm::ArrayRef<const char*>, llvm::raw_ostream&,
              llvm::raw_ostream&, bool, bool);
}
}
#endif

namespace tinaide {
namespace linker {

#if !LLVM_HEADERS_AVAILABLE || !defined(LLD_LINK_ENABLED)

// LLD 不可用时的占位实现
LinkResult linkExecutable(const std::string& objPath, const std::string& exePath,                          const LinkOptions& options) {
    LinkResult result;
    result.success = false;
    result.errorMessage = "UNAVAILABLE: LLD not available";
    return result;
}

LinkResult linkExecutableMany(const std::vector<std::string>& objPaths,
                              const std::string& exePath,
                              const LinkOptions& options) {
    LinkResult result;
    result.success = false;
    result.errorMessage = "UNAVAILABLE: LLD not available";
    return result;
}

LinkResult linkSharedLibrary(const std::string& objPath, const std::string& soPath,
                             const LinkOptions& options) {
    LinkResult result;
    result.success = false;
    result.errorMessage = "UNAVAILABLE: LLD not available";
    return result;
}

LinkResult linkSharedLibraryMany(const std::vector<std::string>& objPaths,
                                 const std::string& soPath,
                                 const LinkOptions& options) {
    LinkResult result;
    result.success = false;
    result.errorMessage = "UNAVAILABLE: LLD not available";
    return result;
}

#else

// 验证 sysroot 库目录和必需文件
static LinkResult validateSysroot(const std::string& sysroot, const std::string& target,                                      bool isCxx, bool isShared) {
    LinkResult result;
    result.success = true;

    std::string tripleBase = utils::deriveTripleBase(target);
    std::string apiLevel = utils::deriveApiLevel(target);
    std::string libDir = sysroot + "/usr/lib/" + tripleBase + "/" + apiLevel;

    // 检查库目录
    if (!utils::dirExists(libDir)) {
        result.success = false;
        result.errorMessage =
            "[TinaIDE] Sysroot library directory missing: " + libDir +
            "\n请先同步嵌入式 NDK 资源："
            "\n 1) ./docker/llvm-build/build-local.ps1 -Abi " +
            (tripleBase.find("aarch64") != std::string::npos ? "arm64-v8a" : "x86_64") +
            " -ApiLevel " + apiLevel +
            "\n 2) ./tools/sync-llvm-build.ps1 -Abi " +
            (tripleBase.find("aarch64") != std::string::npos ? "arm64-v8a" : "x86_64");
        return result;
    }

    // 检查必需文件
    std::vector<std::string> missing;
    std::string crtBegin = libDir + (isShared ? "/crtbegin_so.o" : "/crtbegin_dynamic.o");
    std::string crtEnd = libDir + (isShared ? "/crtend_so.o" : "/crtend_android.o");

    if (!utils::fileExists(crtBegin)) {
        missing.push_back(isShared ? "crtbegin_so.o" : "crtbegin_dynamic.o");
    }
    if (!utils::fileExists(crtEnd)) {
        missing.push_back(isShared ? "crtend_so.o" : "crtend_android.o");
    }

    if (!isShared) {
        if (!utils::fileExists(libDir + "/libc.so")) missing.push_back("libc.so");
        if (!utils::fileExists(libDir + "/libm.so")) missing.push_back("libm.so");        if (!utils::fileExists(libDir + "/liblog.so")) missing.push_back("liblog.so");
        if (!utils::fileExists(libDir + "/libandroid.so")) missing.push_back("libandroid.so");
    }

    // 检查 C++ 运行时
    if (isCxx) {
        std::string libcxxSharedApi = libDir + "/libc++_shared.so";
        std::string libcxxSharedRoot = sysroot + "/usr/lib/" + tripleBase + "/libc++_shared.so";
        if (!utils::fileExists(libcxxSharedApi) && !utils::fileExists(libcxxSharedRoot)) {
            missing.push_back("libc++_shared.so");
        }
    }

    if (!missing.empty()) {
        std::ostringstream oss;
        oss << "[TinaIDE] 链接所需的 NDK stub/crt 缺失于: " << libDir << "\n缺失: ";
        for (size_t i = 0; i < missing.size(); ++i) {
            if (i > 0) oss << ", ";
            oss << missing[i];
        }
        oss << "\n请执行:\n 1) ./docker/llvm-build/build-local.ps1 -Abi "
            << (tripleBase.find("aarch64") != std::string::npos ? "arm64-v8a" : "x86_64")
            << " -ApiLevel " << apiLevel
            << "\n 2) ./tools/sync-llvm-build.ps1 -Abi "
            << (tripleBase.find("aarch64") != std::string::npos ? "arm64-v8a" : "x86_64");
        result.success = false;
        result.errorMessage = oss.str();
    }

    return result;
}

// 构建链接参数（可执行文件）
static std::vector<std::string> buildExecutableArgs(
    const std::vector<std::string>& objPaths,    const std::string& exePath,
    const LinkOptions& options) {
    
    std::vector<std::string> args;
    std::string tripleBase = utils::deriveTripleBase(options.target);
    std::string apiLevel = utils::deriveApiLevel(options.target);
    std::string libDir = options.sysroot + "/usr/lib/" + tripleBase + "/" + apiLevel;
    std::string libDirRoot = options.sysroot + "/usr/lib/" + tripleBase;

    // 基础参数
    args.push_back("ld.lld");
    args.push_back("-pie");
    args.push_back("-z");
    args.push_back("now");
    args.push_back("-z");
    args.push_back("relro");

    // 库搜索路径
    args.push_back("-L");
    args.push_back(libDir);
    args.push_back("-L");
    args.push_back(libDirRoot);

    // 动态链接器
    const char* dynLinker = (tripleBase.find("64") != std::string::npos)
        ? "/system/bin/linker64"
        : "/system/bin/linker";
    args.push_back("-dynamic-linker");
    args.push_back(dynLinker);

    // CRT 启动对象
    std::string crtBegin = libDir + "/crtbegin_dynamic.o";
    args.push_back(crtBegin);

    // 输入的目标文件
    for (const auto& objPath : objPaths) {
        args.push_back(objPath);
    }

    // 输出文件
    args.push_back("-o");
    args.push_back(exePath);

    // C++ 运行时
    if (options.isCxx) {
        args.push_back("-lc++");
    }

    // 系统库
    args.push_back("-lc");
    args.push_back("-lm");
    args.push_back("-llog");
    args.push_back("-landroid");

    // 额外的库搜索路径
    for (const auto& libDir : options.libDirs) {        if (!libDir.empty()) {
            args.push_back("-L");
            args.push_back(libDir);
        }
    }

    // 额外的链接库
    for (const auto& lib : options.libs) {
        if (!lib.empty()) {
            args.push_back("-l" + lib);
        }
    }

    // CRT 结束对象
    std::string crtEnd = libDir + "/crtend_android.o";
    args.push_back(crtEnd);

    return args;
}

// 构建链接参数（共享库）
static std::vector<std::string> buildSharedLibraryArgs(
    const std::vector<std::string>& objPaths,
    const std::string& soPath,
    const LinkOptions& options) {
    
    std::vector<std::string> args;
    std::string tripleBase = utils::deriveTripleBase(options.target);
    std::string apiLevel = utils::deriveApiLevel(options.target);
    std::string libDir = options.sysroot + "/usr/lib/" + tripleBase + "/" + apiLevel;
    std::string libDirRoot = options.sysroot + "/usr/lib/" + tripleBase;

    // 基础参数
    args.push_back("ld.lld");
    args.push_back("-shared");

    // 库搜索路径
    args.push_back("-L");
    args.push_back(libDir);
    args.push_back("-L");
    args.push_back(libDirRoot);

    // CRT 启动对象
    std::string crtBegin = libDir + "/crtbegin_so.o";
    args.push_back(crtBegin);

    // 输入的目标文件
    for (const auto& objPath : objPaths) {
        args.push_back(objPath);
    }

    // 输出文件    args.push_back("-o");
    args.push_back(soPath);

    // C++ 运行时
    if (options.isCxx) {
        args.push_back("-lc++");
    }

    // 系统库
    args.push_back("-lc");
    args.push_back("-lm");
    args.push_back("-llog");
    args.push_back("-landroid");

    // 额外的库搜索路径
    for (const auto& libDir : options.libDirs) {
        if (!libDir.empty()) {
            args.push_back("-L");
            args.push_back(libDir);
        }
    }

    // 额外的链接库
    for (const auto& lib : options.libs) {
        if (!lib.empty()) {
            args.push_back("-l" + lib);
        }
    }

    // CRT 结束对象
    std::string crtEnd = libDir + "/crtend_so.o";
    args.push_back(crtEnd);

    return args;
}

// 执行链接
static LinkResult executeLinker(const std::vector<std::string>& argStrings,
                                 const std::string& outputPath) {
    LinkResult result;

    // 转换为 C 风格参数数组
    std::vector<const char*> args;
    args.reserve(argStrings.size());
    for (const auto& arg : argStrings) {
        args.push_back(arg.c_str());
    }

    // 捕获诊断输出
    std::string diagStr;
    llvm::raw_string_ostream diagStream(diagStr);

    // 调用 LLD
    bool success = lld::elf::link(args, diagStream, diagStream, false, false);
    diagStream.flush();

    result.success = success;    if (!success) {
        result.errorMessage = diagStr.empty() ? "link failed" : diagStr;
        return result;
    }

    // 设置可执行权限
    chmod(outputPath.c_str(), 0755);

    return result;
}

// 公共接口实现

LinkResult linkExecutable(const std::string& objPath, const std::string& exePath,
                          const LinkOptions& options) {
    LOGI("linkExecutable: %s -> %s", objPath.c_str(), exePath.c_str());

    // 验证 sysroot
    auto validation = validateSysroot(options.sysroot, options.target, options.isCxx, false);
    if (!validation.success) {
        return validation;
    }

    // 构建参数
    std::vector<std::string> objPaths = {objPath};
    auto args = buildExecutableArgs(objPaths, exePath, options);

    // 执行链接
    return executeLinker(args, exePath);
}

LinkResult linkExecutableMany(const std::vector<std::string>& objPaths,
                              const std::string& exePath,
                              const LinkOptions& options) {
    LOGI("linkExecutableMany: %zu objects -> %s", objPaths.size(), exePath.c_str());

    // 验证 sysroot
    auto validation = validateSysroot(options.sysroot, options.target, options.isCxx, false);
    if (!validation.success) {
        return validation;
    }

    // 构建参数
    auto args = buildExecutableArgs(objPaths, exePath, options);

    // 执行链接
    return executeLinker(args, exePath);
}

LinkResult linkSharedLibrary(const std::string& objPath, const std::string& soPath,
                             const LinkOptions& options) {
    LOGI("linkSharedLibrary: %s -> %s", objPath.c_str(), soPath.c_str());
    // 验证 sysroot
    auto validation = validateSysroot(options.sysroot, options.target, options.isCxx, true);
    if (!validation.success) {
        return validation;
    }

    // 构建参数
    std::vector<std::string> objPaths = {objPath};
    auto args = buildSharedLibraryArgs(objPaths, soPath, options);

    // 执行链接
    return executeLinker(args, soPath);
}

LinkResult linkSharedLibraryMany(const std::vector<std::string>& objPaths,
                                 const std::string& soPath,
                                 const LinkOptions& options) {
    LOGI("linkSharedLibraryMany: %zu objects -> %s", objPaths.size(), soPath.c_str());

    // 验证 sysroot
    auto validation = validateSysroot(options.sysroot, options.target, options.isCxx, true);
    if (!validation.success) {
        return validation;
    }

    // 构建参数
    auto args = buildSharedLibraryArgs(objPaths, soPath, options);

    // 执行链接
    return executeLinker(args, soPath);
}

#endif // LLVM_HEADERS_AVAILABLE && LLD_LINK_ENABLED

} // namespace linker
} // namespace tinaide

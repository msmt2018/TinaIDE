// JNI & logging
#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <android/log.h>

#if LLVM_HEADERS_AVAILABLE
// Clang/LLVM headers for in-process compilation
#include "clang/Frontend/CompilerInstance.h"
#include "clang/Frontend/CompilerInvocation.h"
#include "clang/Frontend/TextDiagnosticPrinter.h"
#include "clang/FrontendTool/Utils.h"
#include "clang/Basic/Diagnostic.h"
#include "clang/Basic/DiagnosticOptions.h"
#include "llvm/ADT/IntrusiveRefCntPtr.h"
#include "llvm/Support/Host.h"
#include "llvm/Support/TargetSelect.h"
#include "llvm/Support/VirtualFileSystem.h"
#include "llvm/Support/raw_ostream.h"
#endif

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "native_compiler", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  "native_compiler", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "native_compiler", __VA_ARGS__)



extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_getClangVersion(
        JNIEnv* env,
        jclass /*clazz*/) {
    // 未打包 LLVM/Clang 头文件时，返回简化版本字符串以验证 JNI 调用链
    const char* v = "LLVM 17 (runtime libs bundled)";
    LOGI("llvm version (placeholder): %s", v);
    return env->NewStringUTF(v);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_syntaxCheck(
        JNIEnv* env,
        jclass /*clazz*/, jstring jSysroot, jstring jSrc, jstring jTarget, jboolean jIsCxx) {
#if !LLVM_HEADERS_AVAILABLE
    const char* msg = "UNAVAILABLE: syntaxCheck requires LLVM headers (in-process)";
    return env->NewStringUTF(msg);
#else
    auto toStr = [&](jstring s){ const char* c = s? env->GetStringUTFChars(s,nullptr):nullptr; std::string o=c?std::string(c):std::string(); if(c) env->ReleaseStringUTFChars(s,c); return o; };
    const std::string sysroot = toStr(jSysroot);
    const std::string srcPath = toStr(jSrc);
    const std::string target  = toStr(jTarget);
    const bool isCxx = jIsCxx == JNI_TRUE;
    // Derive arch triple without API suffix, e.g. "x86_64-linux-android24" → "x86_64-linux-android"
    auto deriveTripleBase = [&](const std::string& t){
        if (t.empty()) return std::string();
        std::string r = t;
        while (!r.empty() && isdigit(static_cast<unsigned char>(r.back()))) r.pop_back();
        return r;
    };
    const std::string tripleBase = deriveTripleBase(target.empty()? llvm::sys::getDefaultTargetTriple(): target);

    // Build tokens first, then materialize stable argv pointers to avoid dangling char*
    std::vector<std::string> tokens;
    auto push  = [&](std::string s){ tokens.emplace_back(std::move(s)); };
    auto push2 = [&](const char* a, std::string b){ tokens.emplace_back(a); tokens.emplace_back(std::move(b)); };
    push("-cc1");
    push2("-triple", target.empty()? llvm::sys::getDefaultTargetTriple(): target);
    push("-fsyntax-only");
    push("-nobuiltininc");
    push2("-isysroot", sysroot);
    // Provide Clang resource-dir so builtin headers like <stdarg.h> are found under -nobuiltininc
    push2("-resource-dir", sysroot+"/lib/clang/17");
    // Also add the resource include as an internal system include for cc1 to pick it up reliably
    push("-internal-isystem"); push(sysroot+"/lib/clang/17/include");
    push("-x");
    push(isCxx ? std::string("c++") : std::string("c"));
    if (isCxx) push("-std=c++17");
    push("-DANDROID"); push("-D__ANDROID__");
    if(!sysroot.empty()){
        push2("-isystem", sysroot+"/usr/include");
        if (!tripleBase.empty()) push2("-isystem", sysroot+"/usr/include/"+tripleBase);
        push2("-I", sysroot+"/usr/include/c++/v1");
    }
    tokens.emplace_back(srcPath);

    std::vector<const char*> args; args.reserve(tokens.size());
    for (auto& t : tokens) args.push_back(t.c_str());

    llvm::IntrusiveRefCntPtr<clang::DiagnosticOptions> dopt = new clang::DiagnosticOptions();
    std::string diag; llvm::raw_string_ostream os(diag);
    llvm::IntrusiveRefCntPtr<clang::DiagnosticIDs> did(new clang::DiagnosticIDs());
    auto printer = std::make_unique<clang::TextDiagnosticPrinter>(os, &*dopt);
    clang::DiagnosticsEngine diags(did, &*dopt, printer.get(), false);
    std::unique_ptr<clang::CompilerInvocation> CI(new clang::CompilerInvocation());
    if (!clang::CompilerInvocation::CreateFromArgs(*CI, args, diags)) { os.flush(); return env->NewStringUTF(diag.empty()?"create invocation failed":diag.c_str()); }

    clang::CompilerInstance Clang; Clang.setInvocation(std::move(CI));
    Clang.createDiagnostics(printer.release(), true);
    bool ok = clang::ExecuteCompilerInvocation(&Clang);
    os.flush();
    if (!ok) return env->NewStringUTF(diag.empty()?"syntax check failed":diag.c_str());
    return env->NewStringUTF("");
#endif
}

static std::string jstringToUtf8(JNIEnv* env, jstring s) {
    if (!s) return std::string();
    const char* utf = env->GetStringUTFChars(s, nullptr);
    std::string out = utf ? std::string(utf) : std::string();
    if (utf) env->ReleaseStringUTFChars(s, utf);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_emitObj(
        JNIEnv* env,
        jclass /*clazz*/,
        jstring jSysroot,
        jstring jSrc,
        jstring jObjOut,
        jstring jTarget,
        jboolean jIsCxx,
        jobjectArray jFlags,
        jobjectArray jIncludeDirs) {
#if !LLVM_HEADERS_AVAILABLE
    const char* msg = "UNAVAILABLE: LLVM headers not found (run tools/sync-llvm-headers.ps1)";
    return env->NewStringUTF(msg);
#else
    auto toStr = [&](jstring s){ const char* c = s? env->GetStringUTFChars(s,nullptr):nullptr; std::string o=c?std::string(c):std::string(); if(c) env->ReleaseStringUTFChars(s,c); return o; };
    const std::string sysroot = toStr(jSysroot);
    const std::string srcPath = toStr(jSrc);
    const std::string objOut  = toStr(jObjOut);
    const std::string target  = toStr(jTarget);
    const bool isCxx = jIsCxx == JNI_TRUE;
    auto deriveTripleBase = [&](const std::string& t){
        if (t.empty()) return std::string();
        std::string r = t;
        while (!r.empty() && isdigit(static_cast<unsigned char>(r.back()))) r.pop_back();
        return r;
    };
    const std::string tripleBase = deriveTripleBase(target.empty()? llvm::sys::getDefaultTargetTriple(): target);

    // NOTE: Do not call InitializeAll* to avoid unresolved target init symbols
    // in monolithic libLLVM builds that don't export per-target inits.

    // Build tokens then stable argv
    std::vector<std::string> tokens;
    auto push  = [&](std::string s){ tokens.emplace_back(std::move(s)); };
    auto push2 = [&](const char* a, std::string b){ tokens.emplace_back(a); tokens.emplace_back(std::move(b)); };
    push("-cc1");
    push2("-triple", target.empty()? llvm::sys::getDefaultTargetTriple(): target);
    push("-emit-obj"); push("-O2"); push("-nobuiltininc");
    push2("-isysroot", sysroot);
    // Provide Clang resource-dir so builtin headers like <stdarg.h> are found under -nobuiltininc
    push2("-resource-dir", sysroot+"/lib/clang/17");
    // Also add the resource include as an internal system include for cc1 to pick it up reliably
    push("-internal-isystem"); push(sysroot+"/lib/clang/17/include");
    push("-x");
    push(isCxx ? std::string("c++") : std::string("c"));
    if (isCxx) push("-std=c++17");
    push("-DANDROID"); push("-D__ANDROID__");
    if(!sysroot.empty()){
        push2("-isystem", sysroot+"/usr/include");
        if (!tripleBase.empty()) push2("-isystem", sysroot+"/usr/include/"+tripleBase);
        push2("-I", sysroot+"/usr/include/c++/v1");
    }
    if (jIncludeDirs){ jsize n=env->GetArrayLength(jIncludeDirs); for(jsize i=0;i<n;++i){ jstring s=(jstring)env->GetObjectArrayElement(jIncludeDirs,i); std::string p=toStr(s); if(!p.empty()) push2("-I",p); env->DeleteLocalRef(s);} }
    if (jFlags){ jsize n=env->GetArrayLength(jFlags); for(jsize i=0;i<n;++i){ jstring s=(jstring)env->GetObjectArrayElement(jFlags,i); std::string f=toStr(s); if(!f.empty()) push(std::move(f)); env->DeleteLocalRef(s);} }
    push2("-o", objOut); tokens.emplace_back(srcPath);

    std::vector<const char*> args; args.reserve(tokens.size());
    for (auto& t : tokens) args.push_back(t.c_str());

    // Debug only: dump cc1 args to log once per invocation
#ifndef NDEBUG
    {
        std::ostringstream oss; oss << "cc1 args (" << args.size() << "):";
        for (auto* a : args) { oss << " " << (a ? a : "<null>"); }
        LOGI("%s", oss.str().c_str());
    }
#endif

    llvm::IntrusiveRefCntPtr<clang::DiagnosticOptions> dopt = new clang::DiagnosticOptions();
    std::string diag; llvm::raw_string_ostream os(diag);
    llvm::IntrusiveRefCntPtr<clang::DiagnosticIDs> did(new clang::DiagnosticIDs());
    auto printer = std::make_unique<clang::TextDiagnosticPrinter>(os, &*dopt);
    clang::DiagnosticsEngine diags(did, &*dopt, printer.get(), false);
    std::unique_ptr<clang::CompilerInvocation> CI(new clang::CompilerInvocation());
    if (!clang::CompilerInvocation::CreateFromArgs(*CI, args, diags)) { os.flush(); return env->NewStringUTF(diag.empty()?"create invocation failed":diag.c_str()); }

    clang::CompilerInstance Clang; Clang.setInvocation(std::move(CI));
    Clang.createDiagnostics(printer.release(), true);
    bool ok = clang::ExecuteCompilerInvocation(&Clang);
    os.flush();
    if (!ok) return env->NewStringUTF(diag.empty()?"compile failed":diag.c_str());
    return env->NewStringUTF("");
#endif
}

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <cstdint>
#include <mutex>
#include <string>

namespace {

constexpr const char* kTag = "GuiRuntimeJni";

constexpr const char* kRenderSymbol   = "tina_gui_render_argb32";
constexpr const char* kInitSymbol     = "tina_gui_init";
constexpr const char* kShutdownSymbol = "tina_gui_shutdown";
constexpr const char* kTouchSymbol    = "tina_gui_on_touch";
constexpr const char* kKeySymbol      = "tina_gui_on_key";

using RenderFn   = int (*)(int width, int height, uint32_t* pixels, int stride);
using InitFn     = int (*)();
using ShutdownFn = void (*)();
using TouchFn    = void (*)(int action, float x, float y, int pointer_id);
using KeyFn      = void (*)(int keycode, int action);

std::mutex gMutex;
void* gLibraryHandle   = nullptr;
RenderFn   gRenderFn   = nullptr;
InitFn     gInitFn     = nullptr;
ShutdownFn gShutdownFn = nullptr;
TouchFn    gTouchFn    = nullptr;
KeyFn      gKeyFn      = nullptr;
std::string gLastError;

void logInfo(const std::string& message) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", message.c_str());
}

void logError(const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message.c_str());
}

void setLastError(const std::string& message) {
    gLastError = message;
    logError(message);
}

void unloadLocked() {
    if (gShutdownFn != nullptr) {
        gShutdownFn();
    }
    gShutdownFn = nullptr;
    gInitFn     = nullptr;
    gRenderFn   = nullptr;
    gTouchFn    = nullptr;
    gKeyFn      = nullptr;
    if (gLibraryHandle != nullptr) {
        dlclose(gLibraryHandle);
        gLibraryHandle = nullptr;
    }
}

jstring toJStringOrNull(JNIEnv* env, const std::string& value) {
    if (value.empty()) return nullptr;
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_ui_gui_GuiRuntimeBridge_nativeLoadLibrary(
    JNIEnv* env,
    jobject /* thiz */,
    jstring libraryPath
) {
    std::lock_guard<std::mutex> lock(gMutex);
    unloadLocked();

    if (libraryPath == nullptr) {
        setLastError("library path is null");
        return toJStringOrNull(env, gLastError);
    }

    const char* rawPath = env->GetStringUTFChars(libraryPath, nullptr);
    if (rawPath == nullptr) {
        setLastError("failed to decode library path");
        return toJStringOrNull(env, gLastError);
    }
    std::string path(rawPath);
    env->ReleaseStringUTFChars(libraryPath, rawPath);

    dlerror();
    gLibraryHandle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (gLibraryHandle == nullptr) {
        const char* err = dlerror();
        setLastError(std::string("dlopen failed: ") + (err ? err : "unknown error"));
        unloadLocked();
        return toJStringOrNull(env, gLastError);
    }

    dlerror();
    gRenderFn = reinterpret_cast<RenderFn>(dlsym(gLibraryHandle, kRenderSymbol));
    if (gRenderFn == nullptr) {
        const char* err = dlerror();
        setLastError(
            std::string("missing symbol `") + kRenderSymbol + "`: " +
            (err ? err : "not found")
        );
        unloadLocked();
        return toJStringOrNull(env, gLastError);
    }

    gInitFn     = reinterpret_cast<InitFn>(dlsym(gLibraryHandle, kInitSymbol));
    gShutdownFn = reinterpret_cast<ShutdownFn>(dlsym(gLibraryHandle, kShutdownSymbol));
    gTouchFn    = reinterpret_cast<TouchFn>(dlsym(gLibraryHandle, kTouchSymbol));
    gKeyFn      = reinterpret_cast<KeyFn>(dlsym(gLibraryHandle, kKeySymbol));

    logInfo(std::string("Symbols resolved — render: yes")
        + ", init: " + (gInitFn ? "yes" : "no")
        + ", shutdown: " + (gShutdownFn ? "yes" : "no")
        + ", touch: " + (gTouchFn ? "yes" : "no")
        + ", key: " + (gKeyFn ? "yes" : "no")
    );

    if (gInitFn != nullptr) {
        const int initCode = gInitFn();
        if (initCode != 0) {
            setLastError("tina_gui_init failed with code: " + std::to_string(initCode));
            unloadLocked();
            return toJStringOrNull(env, gLastError);
        }
    }

    gLastError.clear();
    logInfo("GUI runtime library loaded: " + path);
    return nullptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_ui_gui_GuiRuntimeBridge_nativeUnloadLibrary(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(gMutex);
    unloadLocked();
    gLastError.clear();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_ui_gui_GuiRuntimeBridge_nativeRenderArgb32(
    JNIEnv* env,
    jobject /* thiz */,
    jint width,
    jint height,
    jintArray pixels
) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gLibraryHandle == nullptr || gRenderFn == nullptr) {
        setLastError("GUI runtime library is not loaded");
        return JNI_FALSE;
    }
    if (width <= 0 || height <= 0 || pixels == nullptr) {
        setLastError("invalid render arguments");
        return JNI_FALSE;
    }

    const jsize expectedSize = width * height;
    if (env->GetArrayLength(pixels) < expectedSize) {
        setLastError("pixel buffer length is too small");
        return JNI_FALSE;
    }

    jint* pixelBuffer = env->GetIntArrayElements(pixels, nullptr);
    if (pixelBuffer == nullptr) {
        setLastError("failed to map pixel buffer");
        return JNI_FALSE;
    }

    const int renderCode = gRenderFn(
        static_cast<int>(width),
        static_cast<int>(height),
        reinterpret_cast<uint32_t*>(pixelBuffer),
        static_cast<int>(width)
    );

    env->ReleaseIntArrayElements(pixels, pixelBuffer, 0);

    if (renderCode < 0) {
        setLastError("tina_gui_render_argb32 failed with code: " + std::to_string(renderCode));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_ui_gui_GuiRuntimeBridge_nativeSendTouchEvent(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint action,
    jfloat x,
    jfloat y,
    jint pointerId
) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gTouchFn != nullptr) {
        gTouchFn(static_cast<int>(action), static_cast<float>(x),
                  static_cast<float>(y), static_cast<int>(pointerId));
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_ui_gui_GuiRuntimeBridge_nativeSendKeyEvent(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint keycode,
    jint action
) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gKeyFn != nullptr) {
        gKeyFn(static_cast<int>(keycode), static_cast<int>(action));
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_ui_gui_GuiRuntimeBridge_nativeGetLastError(
    JNIEnv* env,
    jobject /* thiz */
) {
    std::lock_guard<std::mutex> lock(gMutex);
    return toJStringOrNull(env, gLastError);
}

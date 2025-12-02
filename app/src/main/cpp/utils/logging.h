// 日志工具头文件
// 提供统一的日志宏定义，用于 Android logcat 输出

#ifndef TINAIDE_LOGGING_H
#define TINAIDE_LOGGING_H

#include <android/log.h>

// 日志标签
#define LOG_TAG "TinaIDE"

// 日志宏定义
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#endif // TINAIDE_LOGGING_H

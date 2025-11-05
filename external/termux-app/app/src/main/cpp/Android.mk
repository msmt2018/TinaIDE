LOCAL_PATH:= $(call my-dir)

# Termux Prefix Hook 库（用于 ELF 文件修改）
include $(CLEAR_VARS)
LOCAL_MODULE := termux-prefix-hook
LOCAL_SRC_FILES := prefix-hook.c elf-patcher.c
LOCAL_LDLIBS := -llog -ldl
LOCAL_CFLAGS := -Wall -Wextra -O2
include $(BUILD_SHARED_LIBRARY)

## 已移除 Path Rewrite 库（仅保留 close() 前缀 Hook）

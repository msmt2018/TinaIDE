#define _GNU_SOURCE
#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __ANDROID__
#include <android/api-level.h>
#endif

#include <termux/termux_core__nos__c/v1/android/shell/command/environment/AndroidShellEnvironment.h>
#include <termux/termux_core__nos__c/v1/data/DataUtils.h>
#include <termux/termux_core__nos__c/v1/logger/Logger.h>

static const char* LOG_TAG = "AndroidShellEnvironment";



/** The Android build version sdk. */
static int sAndroidBuildVersionSdk = 0;



int android_buildVersionSdk_get() {
    if (sAndroidBuildVersionSdk > 0) {
        return sAndroidBuildVersionSdk;
    }

    const char* value = getenv(ENV__ANDROID__BUILD_VERSION_SDK);
    if (value != NULL && strlen(value) > 0) {
        char buildVersionSdkString[strlen(value) + 1];
        strcpy(buildVersionSdkString, value);
        int build_version_sdk = stringToInt(buildVersionSdkString, -1,
            LOG_TAG, "Failed to convert '%s' env variable value '%s' to an int", ENV__ANDROID__BUILD_VERSION_SDK, buildVersionSdkString);
        if (build_version_sdk > 0) {
            sAndroidBuildVersionSdk = build_version_sdk;
            return sAndroidBuildVersionSdk;
        }
    }

    #ifdef __ANDROID__
    sAndroidBuildVersionSdk = android_get_device_api_level();
    #endif

    return sAndroidBuildVersionSdk;
}

#define _GNU_SOURCE
#include <errno.h>
#include <libgen.h>
#include <regex.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <linux/limits.h>

#include <sys/stat.h>

#include <termux/termux_core__nos__c/v1/TermuxCoreLibraryConfig.h>
#include <termux/termux_core__nos__c/v1/data/DataUtils.h>
#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/termux/file/TermuxFile.h>
#include <termux/termux_core__nos__c/v1/termux/shell/command/environment/TermuxShellEnvironment.h>
#include <termux/termux_core__nos__c/v1/unix/file/UnixFileUtils.h>
#include <termux/termux_core__nos__c/v1/unix/shell/command/environment/UnixShellEnvironment.h>

static const char* LOG_TAG = "TermuxFile";



static char sTermuxAppDataDirBuffer[TERMUX_APP__DATA_DIR___MAX_LEN];

/** The Termux app data directory path. */
static const char* sTermuxAppDataDir = NULL;


static char sTermuxAppLegacyDataDirBuffer[TERMUX_APP__DATA_DIR___MAX_LEN];

/** The Termux legacy app data directory path. */
static const char* sTermuxAppLegacyDataDir = NULL;



static char sTermuxRootfsDirBuffer[TERMUX__ROOTFS_DIR___MAX_LEN];

/** The Termux rootfs directory path. */
static const char* sTermuxRootfsDir = NULL;



static char sTermuxPrefixDirBuffer[TERMUX__PREFIX_DIR___MAX_LEN];

/** The Termux prefix directory path. */
static const char* sTermuxPrefixDir = NULL;



static char sTermuxTmpDirBuffer[TERMUX__PREFIX__TMP_DIR___MAX_LEN];

/** The Termux tmp directory path. */
static const char* sTermuxTmpDir = NULL;





const char* termuxApp_dataDir_get(const char* logTag) {
    if (sTermuxAppDataDir == NULL) {
        sTermuxAppDataDir = termuxApp_dataDir_getFromEnvOrDefault(logTag,
            sTermuxAppDataDirBuffer, sizeof(sTermuxAppDataDirBuffer));
    }

    return sTermuxAppDataDir;
}

int termuxApp_dataDir_getFromEnv(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnv(LOG_LEVEL__VVERBOSE, logTag,
        "app_data_dir", ENV__TERMUX_APP__DATA_DIR,
        true, TERMUX_APP__DATA_DIR___MAX_LEN, true, true,
        buffer, bufferSize);
}

const char* termuxApp_dataDir_getFromEnvOrDefault(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnvOrDefault(LOG_LEVEL__VVERBOSE, logTag,
        "app_data_dir", ENV__TERMUX_APP__DATA_DIR,
        true, TERMUX_APP__DATA_DIR___MAX_LEN, true, true,
        TERMUX_APP__DATA_DIR, -1,
        buffer, bufferSize);
}


const char* termuxApp_legacyDataDir_get(const char* logTag) {
    if (sTermuxAppLegacyDataDir == NULL) {
        sTermuxAppLegacyDataDir = termuxApp_legacyDataDir_getFromEnvOrDefault(logTag,
            sTermuxAppLegacyDataDirBuffer, sizeof(sTermuxAppLegacyDataDirBuffer));
    }

    return sTermuxAppLegacyDataDir;
}

int termuxApp_legacyDataDir_getFromEnv(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnv(LOG_LEVEL__VVERBOSE, logTag,
        "legacy_app_data_dir", ENV__TERMUX_APP__LEGACY_DATA_DIR,
        true, TERMUX_APP__DATA_DIR___MAX_LEN, true, true,
        buffer, bufferSize);
}

const char* termuxApp_legacyDataDir_getFromEnvOrDefault(const char* logTag, char *buffer, size_t bufferSize) {
    const char*  termuxLegacyAppDataDir = getPathFromEnvOrDefault(LOG_LEVEL__VVERBOSE, logTag,
        "legacy_app_data_dir", ENV__TERMUX_APP__LEGACY_DATA_DIR,
        true, TERMUX_APP__DATA_DIR___MAX_LEN, true, true,
        TERMUX_APP__DATA_DIR, -1,
        buffer, bufferSize);
    if (termuxLegacyAppDataDir == NULL) {
        return NULL;
    }

    // If default path is not for a legacy app data directory path,
    // then convert it into a legacy path by extracting the package
    // name from the `basename` of path found and appending it to
    // `/data/data/`.
    if (!stringStartsWith(termuxLegacyAppDataDir, "/data/data/")) {
        char termuxLegacyAppDataDirCopy[strlen(termuxLegacyAppDataDir) + 1];
        strcpy(termuxLegacyAppDataDirCopy, termuxLegacyAppDataDir);

        termuxLegacyAppDataDir = termuxApp_dataDir_convertToLegacyPath(logTag,
            termuxLegacyAppDataDirCopy, buffer, bufferSize);
        if (termuxLegacyAppDataDir == NULL) {
            return NULL;
        }

        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorVVerbose(logTag, "updated_legacy_app_data_dir: '%s'", termuxLegacyAppDataDir);
        }
    }

    return termuxLegacyAppDataDir;

}


const char* termuxApp_dataDir_convertToLegacyPath(const char* logTag,
    const char *termuxAppDataDir, char *buffer, size_t bufferSize) {
    if (termuxAppDataDir == NULL || *termuxAppDataDir != '/') {
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(logTag, "The app_data_dir '%s' to be converted to legacy path is not an absolute path",
                termuxAppDataDir == NULL ? "" : termuxAppDataDir);
        }
        errno = EINVAL;
        return NULL;
    }

    char *lastPathSeparatorIndex = strrchr(termuxAppDataDir, '/');
    if (!lastPathSeparatorIndex) {
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(logTag, "Failed to find last path separator '/' in app_data_dir '%s' to be converted to legacy path",
                termuxAppDataDir);
        }
        errno = EINVAL;
        return NULL;
    }

    size_t lastPathSeparatorLength = lastPathSeparatorIndex - termuxAppDataDir;
    size_t termuxAppDataDirLength = strlen(termuxAppDataDir);

    if (lastPathSeparatorLength == 0) {
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(logTag, "The last path separator '/' is at rootfs in app_data_dir '%s' to be converted to legacy path",
                termuxAppDataDir);
        }
        errno = EINVAL;
        return NULL;
    }

    // If path does not contain at least one character after last path
    // separator `/`, ideally for package name.
    if ((lastPathSeparatorLength + 1) >= termuxAppDataDirLength) {
      if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(logTag, "No basename found in app_data_dir '%s' to be converted to legacy path",
                termuxAppDataDir);
        }
        errno = EINVAL;
        return NULL;
    }

    // Copy package name from basename.
    const char *src = lastPathSeparatorIndex + 1;
    size_t termuxLegacyAppDataDirLength = 11 + strlen(src);
    if (bufferSize <= termuxLegacyAppDataDirLength) {
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(logTag, "The legacy_app_data_dir '/data/data/%s' with length '%zu' is too long to fit in the buffer with size '%zu'",
                src, termuxLegacyAppDataDirLength, bufferSize);
        }
        errno = ENAMETOOLONG;
        return NULL;
    }

    strcpy(buffer, "/data/data/");
    strcpy(buffer + 11, src);

    return buffer;
}



const char* termux_rootfsDir_get(const char* logTag) {
    if (sTermuxRootfsDir == NULL) {
        sTermuxRootfsDir = termux_rootfsDir_getFromEnvOrDefault(logTag,
            sTermuxRootfsDirBuffer, sizeof(sTermuxRootfsDirBuffer));
    }

    return sTermuxRootfsDir;
}

int termux_rootfsDir_getFromEnv(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnv(LOG_LEVEL__VVERBOSE, logTag,
        "rootfs_dir", ENV__TERMUX__ROOTFS,
        true, TERMUX__ROOTFS_DIR___MAX_LEN, true, true,
        buffer, bufferSize);
}

const char* termux_rootfsDir_getFromEnvOrDefault(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnvOrDefault(LOG_LEVEL__VVERBOSE, logTag,
        "rootfs_dir", ENV__TERMUX__ROOTFS,
        true, TERMUX__ROOTFS_DIR___MAX_LEN, true, true,
        TERMUX__ROOTFS, 3 /* (R_OK | X_OK) */,
        buffer, bufferSize);
}



const char* termux_prefixDir_get(const char* logTag) {
    if (sTermuxPrefixDir == NULL) {
        sTermuxPrefixDir = termux_prefixDir_getFromEnvOrDefault(logTag,
            sTermuxPrefixDirBuffer, sizeof(sTermuxPrefixDirBuffer));
    }

    return sTermuxPrefixDir;
}

int termux_prefixDir_getFromEnv(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnv(LOG_LEVEL__VVERBOSE, logTag,
        "prefix_dir", ENV__TERMUX__PREFIX,
        true, TERMUX__PREFIX_DIR___MAX_LEN, true, true,
        buffer, bufferSize);
}

const char* termux_prefixDir_getFromEnvOrDefault(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnvOrDefault(LOG_LEVEL__VVERBOSE, logTag,
        "prefix_dir", ENV__TERMUX__PREFIX,
        true, TERMUX__PREFIX_DIR___MAX_LEN, true, true,
        TERMUX__PREFIX, 3 /* (R_OK | X_OK) */,
        buffer, bufferSize);
}



const char* termux_tmpDir_get(const char* logTag) {
    if (sTermuxTmpDir == NULL) {
        sTermuxTmpDir = termux_tmpDir_getFromEnvOrDefault(logTag,
            sTermuxTmpDirBuffer, sizeof(sTermuxTmpDirBuffer));
    }

    return sTermuxTmpDir;
}

int termux_tmpDir_getFromEnv(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnv(LOG_LEVEL__VVERBOSE, logTag,
        "tmp_dir", ENV__TMPDIR,
        true, TERMUX__PREFIX__TMP_DIR___MAX_LEN, true, true,
        buffer, bufferSize);
}

const char* termux_tmpDir_getFromEnvOrDefault(const char* logTag, char *buffer, size_t bufferSize) {
    return getPathFromEnvOrDefault(LOG_LEVEL__VVERBOSE, logTag,
        "tmp_dir", ENV__TMPDIR,
        true, TERMUX__PREFIX__TMP_DIR___MAX_LEN, true, true,
        TERMUX__PREFIX__TMP_DIR, -1,
        buffer, bufferSize);
}





char *termuxPrefixPath(const char* logTag, const char *termuxPrefixDir, const char *executablePath,
    char *buffer, size_t bufferSize) {
    size_t executablePathLength = strlen(executablePath);
    if (bufferSize <= executablePathLength) {
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(LOG_TAG, "The original executable path '%s' with length '%zu' to prefix is too long to fit in the buffer with size '%zu'",
                executablePath, executablePathLength, bufferSize);
        }
        errno = ENAMETOOLONG;
        return NULL;
    }

    // If `executablePath` is not an absolute path.
    if (executablePath[0] != '/') {
        strcpy(buffer, executablePath);
        return buffer;
    }

    char termuxBinPath[TERMUX__PREFIX__BIN_DIR___MAX_LEN + 1];

    // If `executablePath` equals with `/bin` or `/usr/bin` (currently not `/xxx/bin`).
    if (strcmp(executablePath, "/bin") == 0 || strcmp(executablePath, "/usr/bin") == 0) {
        if (termuxPrefixDir == NULL) {
            termuxPrefixDir = termux_prefixDir_get(logTag);
            if (termuxPrefixDir == NULL) { return NULL; }
        }

        // If `termuxPrefixDir` equals `/`.
        if (strlen(termuxPrefixDir) == 1 && termuxPrefixDir[0] == '/') {
            strcpy(buffer, executablePath);
            return buffer;
        }

        snprintf(termuxBinPath, sizeof(termuxBinPath), "%s/bin", termuxPrefixDir);
        strcpy(buffer, termuxBinPath);
        return buffer;
    }

    char *binMatchIndex = strstr(executablePath, "/bin/");
    // If `executablePath` starts with `/bin/` or `/xxx/bin`.
    if (binMatchIndex == executablePath || binMatchIndex == (executablePath + 4)) {
        if (termuxPrefixDir == NULL) {
            termuxPrefixDir = termux_prefixDir_get(logTag);
            if (termuxPrefixDir == NULL) { return NULL; }
        }

        // If `termuxPrefixDir` equals `/`.
        if (strlen(termuxPrefixDir) == 1 && termuxPrefixDir[0] == '/') {
            strcpy(buffer, executablePath);
            return buffer;
        }

        int termuxBinPathLength = snprintf(termuxBinPath, sizeof(termuxBinPath),
            "%s/bin/", termuxPrefixDir);

        strcpy(buffer, termuxBinPath);
        char *dest = buffer + termuxBinPathLength;
        // Copy what comes after `/bin/`.
        const char *src = binMatchIndex + 5;
        size_t prefixedPathLen = termuxBinPathLength + strlen(src);
        if (bufferSize <= prefixedPathLen) {
            if (!libtermux_core__nos__c__getIsRunningTests()) {
                logErrorDebug(logTag, "The prefixed_path '%s%s' with length '%zu' is too long to fit in the buffer with size '%zu'",
                    termuxBinPath, src, prefixedPathLen, bufferSize);
            }
            errno = ENAMETOOLONG;
            return NULL;
        }

        strcpy(dest, src);
        return buffer;
    } else {
        strcpy(buffer, executablePath);
        return buffer;
    }
}





int termuxApp_dataDir_isPathUnder(const char* logTag, const char *path,
    const char *termuxAppDataDir, const char *termuxLegacyAppDataDir) {
    if (path == NULL || *path == '\0') {
        return 1;
    }


    const char* realPath;
    char realPathBuffer[PATH_MAX];
    (void)realPathBuffer;
    if (isFdPath(path)) {
        realPath = getRegularFileFdRealPath(logTag, path, realPathBuffer, sizeof(realPathBuffer));
        if (realPath == NULL) {
            return -1;
        }

        path = realPath;
    }


    // Termux app for version `>= 0.119.0` will export
    // `TERMUX_APP__DATA_DIR` with the non-legacy app data directory
    // paths (`/data/user/<user_id>/<package_name>` or
    // `/mnt/expand/<volume_uuid>/user/0/<package_name>`) that
    // are normally returned by Android `ApplicationInfo.dataDir` call.
    // It will also export `TERMUX_APP__LEGACY_DATA_DIR` with the
    // legacy app data directory path (`/data/data/<package_name>`),
    // however, it will only be accessible if app is running on
    // primary user `0`, or if Android vendor does a bind mount for
    // secondary users/profiles as well or if
    // bind mount was done manually on rooted devices with
    // `MountLegacyAppDataDirPaths.java`.
    //
    // The checking if `path` passed to this function is under termux
    // app data directory is done based on following logic, assuming
    // `termuxAppDataDir` and `termuxLegacyAppDataDir` passed
    // are `NULL`. The below `FAIL` condition, like where `termux-exec`
    // was compiled with the default value of a legacy path
    // `/data/data/<package_name>` and a path starting with
    // `/data/user/0/<package_name>` is executed instead
    // of it starting with `/data/data/<package_name>`, would not
    // normally occur as rootfs would be for a legacy path as well,
    // and if solving it is needed, then user should used the app
    // version (like `>= 0.119.0`) where `TERMUX_APP__DATA_DIR`
    // variable is exported automatically.
    // - Executing path under legacy app data directory:
    //   - If `ENV__TERMUX_APP__LEGACY_DATA_DIR` is set and valid:
    //       - The env path will be used as is.
    //   - Else:
    //       - A non-legacy default path will be used after being
    //         converted to a legacy path.
    //       - A legacy default path will be used as is.
    // - Executing path under non-legacy app data directory:
    //   - If `ENV__TERMUX_APP__DATA_DIR` is set and valid:
    //       - The env path will be used as is.
    //   - Else:
    //       - A non-legacy default path will be used as is.
    //       - A legacy default path will be used as is. (FAIL)
    if (stringStartsWith(path, "/data/data/")) {
        if (termuxLegacyAppDataDir == NULL) {
            termuxLegacyAppDataDir = termuxApp_legacyDataDir_get(logTag);
            if (termuxLegacyAppDataDir == NULL) { return -1; }
        }

        termuxAppDataDir = termuxLegacyAppDataDir;
    } else {
        if (termuxAppDataDir == NULL) {
            termuxAppDataDir = termuxApp_dataDir_get(logTag);
            if (termuxAppDataDir == NULL) { return -1; }
        }
    }

    return isPathInDirPath("app_data_dir", path, termuxAppDataDir, true);
}



int termux_rootfsDir_isPathUnder(const char* logTag, const char *path,
    const char *termuxRootfsDir) {
    if (termuxRootfsDir == NULL) {
        termuxRootfsDir = termux_rootfsDir_get(logTag);
        if (termuxRootfsDir == NULL) { return -1; }
    }

    return isPathOrFdPathInDirPath(logTag, "rootfs_dir", path, termuxRootfsDir, true);
}

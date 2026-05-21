#include <fcntl.h>
#include <limits.h>

#include <termux/termux_core__nos__c/v1/termux/file/TermuxFile.h>
#include <termux/termux_core__nos__c/v1/termux/shell/command/environment/TermuxShellEnvironment.h>
#include <termux/termux_core__nos__c/v1/unix/shell/command/environment/UnixShellEnvironment.h>



#define TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1 "/data/user/0/com.foo"
#define TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2 "/data/data/com.foo"

#define TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1 "/data/data/com.foo/foo/rootfs"
#define TERMUX_CORE__TESTS__TERMUX_PREFIX_DIR_1 TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1 "/usr"
#define TERMUX_CORE__TESTS__TERMUX_BIN_DIR_1 TERMUX_CORE__TESTS__TERMUX_PREFIX_DIR_1 "/bin"

#define TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2 "/"
#define TERMUX_CORE__TESTS__TERMUX_PREFIX_DIR_2 TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1 "/usr"
#define TERMUX_CORE__TESTS__TERMUX_BIN_DIR_2 TERMUX_CORE__TESTS__TERMUX_PREFIX_DIR_1 "/bin"



void test__termuxApp_dataDir_getFromEnv();
void test__termux_prefixDir_getFromEnv();
void test__termux_rootfsDir_getFromEnv();
void test__termuxApp_dataDir_convertToLegacyPath();
void test__termuxPrefixPath();
void test__termuxApp_dataDir_isPathUnder();
void test__termux_rootfsDir_isPathUnder();



void TermuxFile_runTests() {
    logVerbose(LOG_TAG, "TermuxFile_runTests()");

    test__termuxApp_dataDir_getFromEnv();
    test__termux_prefixDir_getFromEnv();
    test__termux_rootfsDir_getFromEnv();
    test__termuxApp_dataDir_convertToLegacyPath();
    test__termuxPrefixPath();
    test__termuxApp_dataDir_isPathUnder();
    test__termux_rootfsDir_isPathUnder();

    int__AEqual(0, errno);
}





void test__getTermuxAppDataDirFromEnv__Basic();

void test__termuxApp_dataDir_getFromEnv() {
    logVerbose(LOG_TAG, "test__termuxApp_dataDir_getFromEnv()");

    test__getTermuxAppDataDirFromEnv__Basic();

    int__AEqual(0, errno);
}

void test__getTermuxAppDataDirFromEnv__Basic() {
    logVVerbose(LOG_TAG, "test__getTermuxAppDataDirFromEnv__Basic()");

    size_t envVarLength = strlen(ENV__TERMUX_APP__DATA_DIR) + 1;

    char buffer[TERMUX_APP__DATA_DIR___MAX_LEN];
    size_t bufferSize = sizeof(buffer);

    int result;

    putenv(ENV__TERMUX_APP__DATA_DIR "=" TERMUX_APP__DATA_DIR);
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual(TERMUX_APP__DATA_DIR, buffer);

    putenv(ENV__TERMUX_APP__DATA_DIR "=/");
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual("/", buffer);

    putenv(ENV__TERMUX_APP__DATA_DIR "=/a");
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual("/a", buffer);

    putenv(ENV__TERMUX_APP__DATA_DIR "=a");
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    putenv(ENV__TERMUX_APP__DATA_DIR "=a/");
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    putenv(ENV__TERMUX_APP__DATA_DIR "=a" TERMUX_APP__DATA_DIR);
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    unsetenv(ENV__TERMUX_APP__DATA_DIR);
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    state__ATrue(PATH_MAX >= TERMUX_APP__DATA_DIR___MAX_LEN);

    char rootfsDir1[envVarLength + PATH_MAX];
    snprintf(rootfsDir1, sizeof(rootfsDir1), "%s=/%0*d", ENV__TERMUX_APP__DATA_DIR, TERMUX_APP__DATA_DIR___MAX_LEN - 1, 0);

    putenv(rootfsDir1);
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    char rootfsDir2[envVarLength + PATH_MAX];
    size_t rootfsDir2Length = snprintf(rootfsDir2, sizeof(rootfsDir2), "%s=/%0*d", ENV__TERMUX_APP__DATA_DIR, TERMUX_APP__DATA_DIR___MAX_LEN - 2, 0);

    putenv(rootfsDir2);
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    state__ATrue(rootfsDir2Length - envVarLength == strlen(buffer));

    char buffer1[TERMUX_APP__DATA_DIR___MAX_LEN - 1];
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer1, sizeof(buffer1));
    state__ATrue(result == -1);
    errno = 0;

    char buffer2[TERMUX_APP__DATA_DIR___MAX_LEN - 2];
    result = termuxApp_dataDir_getFromEnv(LOG_TAG, buffer2, sizeof(buffer2));
    state__ATrue(result == -1);
    errno = 0;
}





void test__getTermuxPrefixDirFromEnv__Basic();

void test__termux_prefixDir_getFromEnv() {
    logVerbose(LOG_TAG, "test__termux_prefixDir_getFromEnv()");

    test__getTermuxPrefixDirFromEnv__Basic();

    int__AEqual(0, errno);
}

void test__getTermuxPrefixDirFromEnv__Basic() {
    logVVerbose(LOG_TAG, "test__getTermuxPrefixDirFromEnv__Basic()");

    size_t envVarLength = strlen(ENV__TERMUX__PREFIX) + 1;

    char buffer[TERMUX__PREFIX_DIR___MAX_LEN];
    size_t bufferSize = sizeof(buffer);

    int result;

    putenv(ENV__TERMUX__PREFIX "=" TERMUX__PREFIX);
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual(TERMUX__PREFIX, buffer);

    putenv(ENV__TERMUX__PREFIX "=/");
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual("/", buffer);

    putenv(ENV__TERMUX__PREFIX "=/a");
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual("/a", buffer);

    putenv(ENV__TERMUX__PREFIX "=a");
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    putenv(ENV__TERMUX__PREFIX "=a/");
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    putenv(ENV__TERMUX__PREFIX "=a" TERMUX__PREFIX);
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    unsetenv(ENV__TERMUX__PREFIX);
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    state__ATrue(PATH_MAX >= TERMUX__PREFIX_DIR___MAX_LEN);

    char rootfsDir1[envVarLength + PATH_MAX];
    snprintf(rootfsDir1, sizeof(rootfsDir1), "%s=/%0*d", ENV__TERMUX__PREFIX, TERMUX__PREFIX_DIR___MAX_LEN - 1, 0);

    putenv(rootfsDir1);
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    char rootfsDir2[envVarLength + PATH_MAX];
    size_t rootfsDir2Length = snprintf(rootfsDir2, sizeof(rootfsDir2), "%s=/%0*d", ENV__TERMUX__PREFIX, TERMUX__PREFIX_DIR___MAX_LEN - 2, 0);

    putenv(rootfsDir2);
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    state__ATrue(rootfsDir2Length - envVarLength == strlen(buffer));

    char buffer1[TERMUX__PREFIX_DIR___MAX_LEN - 1];
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer1, sizeof(buffer1));
    state__ATrue(result == -1);
    errno = 0;

    char buffer2[TERMUX__PREFIX_DIR___MAX_LEN - 2];
    result = termux_prefixDir_getFromEnv(LOG_TAG, buffer2, sizeof(buffer2));
    state__ATrue(result == -1);
    errno = 0;
}





void test__getTermuxRootfsDirFromEnv__Basic();

void test__termux_rootfsDir_getFromEnv() {
    logVerbose(LOG_TAG, "test__termux_rootfsDir_getFromEnv()");

    test__getTermuxRootfsDirFromEnv__Basic();

    int__AEqual(0, errno);
}

void test__getTermuxRootfsDirFromEnv__Basic() {
    logVVerbose(LOG_TAG, "test__getTermuxRootfsDirFromEnv__Basic()");

    size_t envVarLength = strlen(ENV__TERMUX__ROOTFS) + 1;

    char buffer[TERMUX__ROOTFS_DIR___MAX_LEN];
    size_t bufferSize = sizeof(buffer);

    int result;

    putenv(ENV__TERMUX__ROOTFS "=" TERMUX__ROOTFS);
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual(TERMUX__ROOTFS, buffer);

    putenv(ENV__TERMUX__ROOTFS "=/");
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual("/", buffer);

    putenv(ENV__TERMUX__ROOTFS "=/a");
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    string__AEqual("/a", buffer);

    putenv(ENV__TERMUX__ROOTFS "=a");
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    putenv(ENV__TERMUX__ROOTFS "=a/");
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    putenv(ENV__TERMUX__ROOTFS "=a" TERMUX__ROOTFS);
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    unsetenv(ENV__TERMUX__ROOTFS);
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    state__ATrue(PATH_MAX >= TERMUX__ROOTFS_DIR___MAX_LEN);

    char rootfsDir1[envVarLength + PATH_MAX];
    snprintf(rootfsDir1, sizeof(rootfsDir1), "%s=/%0*d", ENV__TERMUX__ROOTFS, TERMUX__ROOTFS_DIR___MAX_LEN - 1, 0);

    putenv(rootfsDir1);
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 1);

    char rootfsDir2[envVarLength + PATH_MAX];
    size_t rootfsDir2Length = snprintf(rootfsDir2, sizeof(rootfsDir2), "%s=/%0*d", ENV__TERMUX__ROOTFS, TERMUX__ROOTFS_DIR___MAX_LEN - 2, 0);

    putenv(rootfsDir2);
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer, bufferSize);
    state__ATrue(result == 0);
    state__ATrue(rootfsDir2Length - envVarLength == strlen(buffer));

    char buffer1[TERMUX__ROOTFS_DIR___MAX_LEN - 1];
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer1, sizeof(buffer1));
    state__ATrue(result == -1);
    errno = 0;

    char buffer2[TERMUX__ROOTFS_DIR___MAX_LEN - 2];
    result = termux_rootfsDir_getFromEnv(LOG_TAG, buffer2, sizeof(buffer2));
    state__ATrue(result == -1);
    errno = 0;
}





void test__termuxApp_dataDir_convertToLegacyPath__Basic();

void test__termuxApp_dataDir_convertToLegacyPath() {
    logVerbose(LOG_TAG, "test__termuxApp_dataDir_convertToLegacyPath()");

    test__termuxApp_dataDir_convertToLegacyPath__Basic();

    int__AEqual(0, errno);
}



#define ctaddtlp__AEqual(expected, termuxAppDataDir, buffer, bufferSize)                           \
    if (1) {                                                                                       \
    assertStringEqualsWithError(expected, termuxApp_dataDir_convertToLegacyPath(LOG_TAG, termuxAppDataDir, buffer, bufferSize), \
        LOG_TAG, "%d: termuxApp_dataDir_convertToLegacyPath('%s')", __LINE__, termuxAppDataDir);   \
    errno = 0;                                                                                     \
    } else ((void)0)



void test__termuxApp_dataDir_convertToLegacyPath__Basic() {
    logVVerbose(LOG_TAG, "test__termuxApp_dataDir_convertToLegacyPath__Basic()");

    char buffer[TERMUX_APP__DATA_DIR___MAX_LEN];
    size_t bufferSize = sizeof(buffer);

    ctaddtlp__AEqual(NULL, NULL, buffer, bufferSize);
    ctaddtlp__AEqual(NULL, "a", buffer, bufferSize);
    ctaddtlp__AEqual(NULL, "a/", buffer, bufferSize);
    ctaddtlp__AEqual(NULL, "/", buffer, bufferSize);
    ctaddtlp__AEqual(NULL, "/a", buffer, bufferSize);
    ctaddtlp__AEqual(NULL, "/a/", buffer, bufferSize);
    ctaddtlp__AEqual(NULL, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1 "/", buffer, bufferSize);
    ctaddtlp__AEqual(TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, buffer, bufferSize);

    // `11` is length of `/data/data/`.
    char termuxAppDataDir1[PATH_MAX];
    snprintf(termuxAppDataDir1, sizeof(termuxAppDataDir1), "/data/user/0/%0*d", TERMUX_APP__DATA_DIR___MAX_LEN - 11, 0);
    ctaddtlp__AEqual(NULL, termuxAppDataDir1, buffer, bufferSize);

    char termuxAppDataDir2_1[PATH_MAX];
    snprintf(termuxAppDataDir2_1, sizeof(termuxAppDataDir2_1), "/data/user/0/%0*d", TERMUX_APP__DATA_DIR___MAX_LEN - 11 - 1, 0);
    char termuxAppDataDir2_2[PATH_MAX];
    snprintf(termuxAppDataDir2_2, sizeof(termuxAppDataDir2_2), "/data/data/%0*d", TERMUX_APP__DATA_DIR___MAX_LEN - 11 - 1, 0);
    ctaddtlp__AEqual(termuxAppDataDir2_2, termuxAppDataDir2_1, buffer, bufferSize);

}





void test__termuxPrefixPath__Basic();

void test__termuxPrefixPath() {
    logVerbose(LOG_TAG, "test__termuxPrefixPath()");

    test__termuxPrefixPath__Basic();

    int__AEqual(0, errno);
}



#define tpp__AEqual(expected, termuxPrefixDir, executablePath, buffer, bufferSize)                 \
    if (1) {                                                                                       \
    assertStringEqualsWithError(expected, termuxPrefixPath(LOG_TAG, termuxPrefixDir, executablePath, buffer, bufferSize), \
        LOG_TAG, "%d: termuxPrefixPath('%s', '%s')", __LINE__, termuxPrefixDir , executablePath);  \
    errno = 0;                                                                                     \
    } else ((void)0)



void test__termuxPrefixPath__Basic() {
    logVVerbose(LOG_TAG, "test__termuxPrefixPath__Basic()");

    char buffer[PATH_MAX];

    tpp__AEqual(TERMUX__PREFIX__BIN_DIR, TERMUX__PREFIX, "/bin", buffer, PATH_MAX);
    tpp__AEqual(TERMUX__PREFIX__BIN_DIR "/", TERMUX__PREFIX, "/bin/", buffer, PATH_MAX);
    tpp__AEqual(TERMUX__PREFIX__BIN_DIR, TERMUX__PREFIX, "/usr/bin", buffer, PATH_MAX);
    tpp__AEqual(TERMUX__PREFIX__BIN_DIR "/", TERMUX__PREFIX, "/usr/bin/", buffer, PATH_MAX);
    tpp__AEqual(TERMUX__PREFIX__BIN_DIR "/sh", TERMUX__PREFIX, "/bin/sh", buffer, PATH_MAX);
    tpp__AEqual(TERMUX__PREFIX__BIN_DIR "/sh", TERMUX__PREFIX, "/usr/bin/sh", buffer, PATH_MAX);
    tpp__AEqual("/system/bin/sh", TERMUX__PREFIX, "/system/bin/sh", buffer, PATH_MAX);
    tpp__AEqual("/system/bin/tool", TERMUX__PREFIX, "/system/bin/tool", buffer, PATH_MAX);
    tpp__AEqual(TERMUX__PREFIX__BIN_DIR "/sh", TERMUX__PREFIX, TERMUX__PREFIX__BIN_DIR "/sh", buffer, PATH_MAX);

    tpp__AEqual("./ab/sh", TERMUX__PREFIX, "./ab/sh", buffer, PATH_MAX);

}





void test__termuxApp_dataDir_isPathUnder__Basic();

void test__termuxApp_dataDir_isPathUnder() {
    logVerbose(LOG_TAG, "test__termuxApp_dataDir_isPathUnder()");

    test__termuxApp_dataDir_isPathUnder__Basic();

    int__AEqual(0, errno);
}



#define iputadd__ABool(expected, path, termuxAppDataDir, termuxLegacyAppDataDir)                   \
    if (1) {                                                                                       \
    assertIntWithError(expected ? 0 : 1, termuxApp_dataDir_isPathUnder(LOG_TAG, path, termuxAppDataDir, termuxLegacyAppDataDir), \
        LOG_TAG, "%d: termuxApp_dataDir_isPathUnder('%s', '%s', '%s')", __LINE__, path, termuxAppDataDir, termuxLegacyAppDataDir); \
    errno = 0;                                                                                     \
    } else ((void)0)

#define iputadd__AInt(expected, path, termuxAppDataDir, termuxLegacyAppDataDir)                    \
    if (1) {                                                                                       \
    assertIntWithError(expected, termuxApp_dataDir_isPathUnder(LOG_TAG, path, termuxAppDataDir, termuxLegacyAppDataDir), \
        LOG_TAG, "%d: termuxApp_dataDir_isPathUnder('%s', '%s', '%s')", __LINE__, path, termuxAppDataDir, termuxLegacyAppDataDir); \
    errno = 0;                                                                                     \
    } else ((void)0)



void test__termuxApp_dataDir_isPathUnder__Basic() {
    logVVerbose(LOG_TAG, "test__termuxApp_dataDir_isPathUnder__Basic()");

    bool isTermuxBinDirUnderTermuxAppDataDir = stringStartsWith(TERMUX__PREFIX__BIN_DIR, TERMUX_APP__DATA_DIR "/");
    bool isTermuxAppDataDirALegacyPath = stringStartsWith(TERMUX_APP__DATA_DIR, "/data/data/");

    state__ATrue(strlen(TERMUX_APP__DATA_DIR) < PATH_MAX);
    state__ATrue(strlen(TERMUX__PREFIX__BIN_DIR) < PATH_MAX);


    iputadd__ABool(false, NULL, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(false, "", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(false, "/", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);

    iputadd__ABool(false, "/a", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(false, "/bin/sh", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(false, "/usr/bin/sh", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(false, "/system/bin/sh", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);


    iputadd__ABool(false, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(true, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2 "/bar", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);

    iputadd__ABool(false, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);
    iputadd__ABool(true, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1 "/bar", TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_1, TERMUX_CORE__TESTS__TERMUX_APP_DATA_DIR_2);


    if (isTermuxAppDataDirALegacyPath) {
        iputadd__ABool(false, TERMUX_APP__DATA_DIR, NULL, TERMUX_APP__DATA_DIR);
        iputadd__ABool(true, TERMUX_APP__DATA_DIR "/foo", NULL, TERMUX_APP__DATA_DIR);
        iputadd__ABool(isTermuxBinDirUnderTermuxAppDataDir, TERMUX__PREFIX__BIN_DIR "/sh", NULL, TERMUX_APP__DATA_DIR);
        iputadd__ABool(false, "/" TERMUX__PREFIX__BIN_DIR "/sh", NULL, TERMUX_APP__DATA_DIR);
        iputadd__ABool(false, "/./" TERMUX__PREFIX__BIN_DIR "/sh", NULL, TERMUX_APP__DATA_DIR);
    } else {
        iputadd__ABool(false, TERMUX_APP__DATA_DIR, TERMUX_APP__DATA_DIR, NULL);
        iputadd__ABool(true, TERMUX_APP__DATA_DIR "/foo", TERMUX_APP__DATA_DIR, NULL);
        iputadd__ABool(isTermuxBinDirUnderTermuxAppDataDir, TERMUX__PREFIX__BIN_DIR "/sh", TERMUX_APP__DATA_DIR, NULL);
        iputadd__ABool(false, "/" TERMUX__PREFIX__BIN_DIR "/sh", TERMUX_APP__DATA_DIR, NULL);
        iputadd__ABool(false, "/./" TERMUX__PREFIX__BIN_DIR "/sh", TERMUX_APP__DATA_DIR, NULL);
    }


    if (access(TERMUX__PREFIX__BIN_DIR "/sh", X_OK) == 0) {
        int fd = open(TERMUX__PREFIX__BIN_DIR "/sh", 0);
        state__ATrue(fd != -1);

        char realPath[PATH_MAX];
        ssize_t length = readlink(TERMUX__PREFIX__BIN_DIR "/sh", realPath, sizeof(realPath) - 1);
        state__ATrue(length >= 0);
        realPath[length] = '\0';

        // If not running on device, termux-packages `build-package.sh`
        // `termux_step_override_config_scripts` step creates a
        // symlink from `$TERMUX__PREFIX/bin/sh` to `/bin/sh`.
        bool isPathTermuxBuilderSymlink = isTermuxBinDirUnderTermuxAppDataDir &&
            strcmp(realPath, "/bin/sh") == 0;

        char procFdPath[40];
        snprintf(procFdPath, sizeof(procFdPath), "/proc/self/fd/%d", fd);
        if (isTermuxAppDataDirALegacyPath) {
            iputadd__ABool(isTermuxBinDirUnderTermuxAppDataDir && !isPathTermuxBuilderSymlink,
                procFdPath, NULL, TERMUX_APP__DATA_DIR);
        } else {
            iputadd__ABool(isTermuxBinDirUnderTermuxAppDataDir && !isPathTermuxBuilderSymlink,
                procFdPath, TERMUX_APP__DATA_DIR, NULL);
        }
        close(fd);
    }
    errno = 0;


    if (access("/system/bin/sh", X_OK) == 0) {
        int fd = open("/system/bin/sh", 0);
        state__ATrue(fd != -1);
        char procFdPath[40];
        snprintf(procFdPath, sizeof(procFdPath), "/proc/self/fd/%d", fd);
        if (isTermuxAppDataDirALegacyPath) {
            iputadd__ABool(false, procFdPath, NULL, TERMUX_APP__DATA_DIR);
        } else {
            iputadd__ABool(false, procFdPath, TERMUX_APP__DATA_DIR, NULL);
        }
        close(fd);
    }
    errno = 0;


    if (access(TERMUX__PREFIX__BIN_DIR, X_OK) == 0) {
        // S_ISREG should fail in `getRegularFileFdRealPath()`.
        int fd = open(TERMUX__PREFIX__BIN_DIR, 0);
        state__ATrue(fd != -1);
        char procFdPath[40];
        snprintf(procFdPath, sizeof(procFdPath), "/proc/self/fd/%d", fd);
        if (isTermuxAppDataDirALegacyPath) {
            iputadd__AInt(-1, procFdPath, NULL, TERMUX_APP__DATA_DIR);
        } else {
            iputadd__AInt(-1, procFdPath, TERMUX_APP__DATA_DIR, NULL);
        }
        close(fd);
    }
    errno = 0;

}





void test__termux_rootfsDir_isPathUnder__Basic();

void test__termux_rootfsDir_isPathUnder() {
    logVerbose(LOG_TAG, "test__termux_rootfsDir_isPathUnder()");

    test__termux_rootfsDir_isPathUnder__Basic();

    int__AEqual(0, errno);
}



#define iputrd__ABool(expected, path, termux_rootfs_dir)                                           \
    if (1) {                                                                                       \
    assertIntWithError(expected ? 0 : 1, termux_rootfsDir_isPathUnder(LOG_TAG, path, termux_rootfs_dir), \
        LOG_TAG, "%d: termux_rootfsDir_isPathUnder('%s', '%s')", __LINE__, path, termux_rootfs_dir); \
    errno = 0;                                                                                     \
    } else ((void)0)

#define iputrd__AInt(expected, path, termux_rootfs_dir)                                            \
    if (1) {                                                                                       \
    assertIntWithError(expected, termux_rootfsDir_isPathUnder(LOG_TAG, path, termux_rootfs_dir),   \
        LOG_TAG, "%d: termux_rootfsDir_isPathUnder('%s', '%s')", __LINE__, path, termux_rootfs_dir); \
    errno = 0;                                                                                     \
    } else ((void)0)



void test__termux_rootfsDir_isPathUnder__Basic() {
    logVVerbose(LOG_TAG, "test__termux_rootfsDir_isPathUnder__Basic()");

    state__ATrue(strlen(TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1) < PATH_MAX);
    state__ATrue(strlen(TERMUX_CORE__TESTS__TERMUX_BIN_DIR_1) < PATH_MAX);

    iputrd__ABool(false, NULL, TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);

    iputrd__ABool(false, "/a", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/bin/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/usr/bin/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/system/bin/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);

    iputrd__ABool(false, TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1, TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(true, TERMUX_CORE__TESTS__TERMUX_BIN_DIR_1 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/" TERMUX_CORE__TESTS__TERMUX_BIN_DIR_1 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/./" TERMUX_CORE__TESTS__TERMUX_BIN_DIR_1 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);
    iputrd__ABool(false, "/../" TERMUX_CORE__TESTS__TERMUX_BIN_DIR_1 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_1);



    state__ATrue(strlen(TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2) < PATH_MAX);
    state__ATrue(strlen(TERMUX_CORE__TESTS__TERMUX_BIN_DIR_2) < PATH_MAX);

    iputrd__ABool(false, NULL, TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(false, "", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(false, "/", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);

    iputrd__ABool(false, TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2, TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/a", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/bin/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/usr/bin/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/system/bin/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);

    iputrd__ABool(true, TERMUX_CORE__TESTS__TERMUX_BIN_DIR_2 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/" TERMUX_CORE__TESTS__TERMUX_BIN_DIR_2 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/./" TERMUX_CORE__TESTS__TERMUX_BIN_DIR_2 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);
    iputrd__ABool(true, "/../" TERMUX_CORE__TESTS__TERMUX_BIN_DIR_2 "/sh", TERMUX_CORE__TESTS__TERMUX_ROOTFS_DIR_2);



    bool termuxRootfsIsRootfs = strcmp(TERMUX__ROOTFS, "/") == 0;
    bool termuxRootfsIsSystem = strcmp(TERMUX__ROOTFS, "/system") == 0;
    bool isTermuxBinUnderTermuxRootfs = stringStartsWith(TERMUX__PREFIX__BIN_DIR,
        termuxRootfsIsRootfs ? "/" : TERMUX__ROOTFS "/");

    state__ATrue(strlen(TERMUX__ROOTFS) < PATH_MAX);
    state__ATrue(strlen(TERMUX__PREFIX__BIN_DIR) < PATH_MAX);


    if (access(TERMUX__PREFIX__BIN_DIR "/sh", X_OK) == 0) {
        int fd = open(TERMUX__PREFIX__BIN_DIR "/sh", 0);
        state__ATrue(fd != -1);

        char realPath[PATH_MAX];
        ssize_t length = readlink(TERMUX__PREFIX__BIN_DIR "/sh", realPath, sizeof(realPath) - 1);
        state__ATrue(length >= 0);
        realPath[length] = '\0';

        // If not running on device, termux-packages `build-package.sh`
        // `termux_step_override_config_scripts` step creates a
        // symlink from `$TERMUX__PREFIX/bin/sh` to `/bin/sh`.
        bool isPathTermuxBuilderSymlink = isTermuxBinUnderTermuxRootfs &&
            strcmp(realPath, "/bin/sh") == 0;

        char procFdPath[40];
        snprintf(procFdPath, sizeof(procFdPath), "/proc/self/fd/%d", fd);
        iputrd__ABool(isTermuxBinUnderTermuxRootfs && !isPathTermuxBuilderSymlink,
            procFdPath, TERMUX__ROOTFS);
        close(fd);
    }
    errno = 0;


    if (access("/system/bin/sh", X_OK) == 0) {
        int fd = open("/system/bin/sh", 0);
        state__ATrue(fd != -1);
        char procFdPath[40];
        snprintf(procFdPath, sizeof(procFdPath), "/proc/self/fd/%d", fd);
        iputrd__ABool(termuxRootfsIsRootfs || termuxRootfsIsSystem, procFdPath, TERMUX__ROOTFS);
        close(fd);
    }
    errno = 0;


    if (access(TERMUX__PREFIX__BIN_DIR, X_OK) == 0) {
        // S_ISREG should fail in `getRegularFileFdRealPath()`.
        int fd = open(TERMUX__PREFIX__BIN_DIR, 0);
        state__ATrue(fd != -1);
        char procFdPath[40];
        snprintf(procFdPath, sizeof(procFdPath), "/proc/self/fd/%d", fd);
        iputrd__AInt(-1, procFdPath, TERMUX__ROOTFS);
        close(fd);
    }
    errno = 0;
}

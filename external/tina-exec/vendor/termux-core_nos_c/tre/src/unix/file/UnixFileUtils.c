#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <regex.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <termux/termux_core__nos__c/v1/TermuxCoreLibraryConfig.h>
#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/unix/file/UnixFileUtils.h>

static const char* LOG_TAG = "UnixFileUtils";

#include "Canonicalize.c"



static int sOriginalUmask;


static int make_path(const char* logTag, const char *path, bool userMode, int nmode, int parentMode);



char* absolutizePath(const char *path, char *absolutePath, int bufferSize) {
    if (bufferSize < PATH_MAX) {
        errno = EINVAL;
        return NULL;
    }

    if (path == NULL) {
        errno = EINVAL;
        return NULL;
    }

    size_t pathLength = strlen(path);
    if (pathLength < 1) {
        errno = EINVAL;
        return NULL;
    }

    if (pathLength >= PATH_MAX) {
        errno = ENAMETOOLONG;
        return NULL;
    }

    if (path[0] != '/') {
        char pwd[PATH_MAX];
        if (getcwd(pwd, sizeof(pwd)) == NULL) {
            return NULL;
        }

        size_t pwd_len = strlen(pwd);
        // Following a change in Linux 2.6.36, the pathname returned by the
        // getcwd() system call will be prefixed with the string
        // "(unreachable)" if the current directory is not below the root
        // directory of the current process (e.g., because the process set a
        // new filesystem root using chroot(2) without changing its current
        // directory into the new root).  Such behavior can also be caused
        // by an unprivileged user by changing the current directory into
        // another mount namespace.  When dealing with pathname from
        // untrusted sources, callers of the functions described in this
        // page should consider checking whether the returned pathname
        // starts with '/' or '(' to avoid misinterpreting an unreachable
        // path as a relative pathname.
        // - https://man7.org/linux/man-pages/man3/getcwd.3.html
        if (pwd_len < 1 || pwd[0] != '/') {
            errno = ENOENT;
            return NULL;
        }

        memcpy(absolutePath, pwd, pwd_len);
        if (absolutePath[pwd_len - 1] != '/') {
            absolutePath[pwd_len] = '/';
            pwd_len++;
        }

        size_t absolutePathLength = pwd_len + pathLength;
        if (absolutePathLength >= PATH_MAX) {
            errno = ENAMETOOLONG;
            return NULL;
        }

        memcpy(absolutePath + pwd_len, path, pathLength);
        absolutePath[absolutePathLength] = '\0';
    } else {
        strcpy(absolutePath, path);
    }

    return absolutePath;
}





char* normalizePath(char* path, bool keepEndSeparator, bool removeDoubleDot) {
    return __normalizePath(path, keepEndSeparator, removeDoubleDot);
}




char* removeDupSeparator(char* path, bool keepEndSeparator) {
    if (path == NULL || *path == '\0') {
        return NULL;
    }

    char* in = path;
    char* out = path;
    char prevChar = 0;
    int n = 0;
    for (; *in != '\0'; in++) {
        // Remove duplicate path separators.
        if (!(*in == '/' && prevChar == '/')) {
            *(out++) = *in;
            n++;
        }
        prevChar = *in;
    }
    *out = '\0';

    // Remove the trailing path separator, except when path equals `/`.
    if (!keepEndSeparator && prevChar == '/' && n > 1) {
        *(--out) = '\0';
    }

    return path;
}





bool isFdPath(const char *path) {

    return strstr(path, "/fd/") != NULL && regexMatch(path, REGEX__PROC_FD_PATH, REG_EXTENDED) == 0;

}

char* getRegularFileFdRealPath(const char* logTag, const char *path, char *realPath, size_t bufferSize) {
    char pathString[strlen(path) + 1];
    strcpy(pathString, path);
    char* fdString = basename(pathString);

    int fd = stringToInt(fdString, -1, logTag, "Failed to convert fd string '%s' to fd for fd path '%s'", fdString, path);
    if (fd < 0) {
        return NULL;
    }

    bool isRunningTests = libtermux_core__nos__c__getIsRunningTests();

    if (!isRunningTests) {
        logErrorVVerbose(logTag, "fd_path: '%s', fd: '%d'", path, fd);
    }

    struct stat fdStatbuf;
    int fd_result = fstat(fd, &fdStatbuf);
    if (fd_result < 0) {
        logStrerrorDebug(logTag, "Failed to stat fd '%d' for fd path '%s'", fd, path);
        return NULL;
    }

    ssize_t length = readlink(path, realPath, bufferSize - 1);
    if (length < 0) {
        logStrerrorDebug(logTag, "Failed to get real path for fd path '%s'", path);
        return NULL;
    } else {
        realPath[length] = '\0';
    }

    if (!isRunningTests) {
        logErrorVVerbose(logTag, "real_path: '%s'", realPath);
    }

    // Check if fd is for a regular file.
    // We should log real path first before doing this check.
    if (!S_ISREG(fdStatbuf.st_mode)) {
        if (S_ISDIR(fdStatbuf.st_mode)) {
            errno = EISDIR;
        } else {
            errno = ENXIO;
        }
        if (!isRunningTests) {
            logStrerrorDebug(logTag, "The real path '%s' for fd path '%s' is of type '%d' instead of a regular file",
                realPath, path, fdStatbuf.st_mode & S_IFMT);
        }
        return NULL;
    }

    size_t realPathLength = strlen(realPath);
    if (realPathLength < 1 || realPath[0] != '/') {
        logErrorDebug(logTag, "A non absolute real path '%s' returned for fd path '%s'", realPath, path);
        errno = EIO;
        return NULL;
    }

    struct stat pathStatbuf;
    int path_result = stat(realPath, &pathStatbuf);
    if (path_result < 0) {
        logStrerrorDebug(logTag, "Failed to stat real path '%s' returned for fd path '%s'", realPath, path);
        return NULL;
    }

    // If the original file when fd was opened has now been replaced with a different file.
    if (fdStatbuf.st_dev != pathStatbuf.st_dev || fdStatbuf.st_ino != pathStatbuf.st_ino) {
        logErrorDebug(logTag, "The file at real path '%s' is not for the original fd '%d'", realPath, fd);
        errno = ENXIO;
        return NULL;
    }

    return realPath;
}





bool isPathInDirPath(const char* label, const char* path, const char* dirPath, bool ensureUnder) {
    if (path == NULL || *path != '/' || dirPath == NULL || *dirPath != '/' ) {
        return 1;
    }

    // If root `/`, preserve it as is, otherwise append `/` to dirPath.
    char *dirSubPath;
    bool isRootfs = strcmp(dirPath, "/") == 0;
    if (asprintf(&dirSubPath, isRootfs ? "%s" : "%s/", dirPath) == -1) {
        errno = ENOMEM;
        logStrerrorDebug(LOG_TAG, "asprintf failed while checking if the path '%s' is under %s '%s'", path, label, dirPath);
        return -1;
    }

    int result;
    if (ensureUnder) {
        result = strcmp(dirSubPath, path) != 0 && stringStartsWith(path, dirSubPath) ? 0 : 1;
    } else {
        result = strcmp(dirSubPath, path) == 0 || stringStartsWith(path, dirSubPath) ? 0 : 1;
    }

    free(dirSubPath);

    return result;
}

int isPathOrFdPathInDirPath(const char* logTag, const char* label,
    const char *path, const char *dirPath, bool ensureUnder) {
    if (path == NULL || *path == '\0') {
        return 1;
    }

    if (isFdPath(path)) {
        char realPathBuffer[PATH_MAX];
        (void)realPathBuffer;
        const char* realPath = getRegularFileFdRealPath(logTag, path, realPathBuffer, sizeof(realPathBuffer));
        if (realPath == NULL) {
            return -1;
        }

        return isPathInDirPath(label, realPath, dirPath, ensureUnder);
    } else {
        return isPathInDirPath(label, path, dirPath, ensureUnder);
    }
}







void logInvalidPathError(int errorLogLevel, const char* logTag, const char* label,
    bool ensureAbsolute, size_t envPathMaxLength) {

    if (!libtermux_core__nos__c__getIsRunningTests()) {
        if (envPathMaxLength >= 1) {
            logErrorForLogLevel(errorLogLevel, logTag, "The %s must be set to %s with max length '%d' including the null '\\0' terminator",
                label,
                (ensureAbsolute ? "an absolute unix path starting with a '/'" : "a unix path"),
                envPathMaxLength);
        } else {
            logErrorForLogLevel(errorLogLevel, logTag, "The %s must be set to %s",
                label,
                (ensureAbsolute ? "an absolute unix path starting with a '/'" : "a unix path"));
        }
    }
}



int getPathFromEnv(int errorLogLevel, const char* logTag,
    const char* label, const char * envVariableName,
    bool ensureAbsolute, size_t envPathMaxLength,
    bool normalize, bool removeDoubleDot,
    char *buffer, size_t bufferSize) {
    // FIXME: Check @Beta comment in header doc.

    const char* envPath = getenv(envVariableName);
    size_t envPathLength = envPath != NULL ? strlen(envPath) : 0;

    // If unset.
    if (envPath == NULL || envPathLength <= 0) {
        if (errorLogLevel >= 1 && !libtermux_core__nos__c__getIsRunningTests()) {
            logErrorForLogLevel(errorLogLevel, logTag, "The %s value is not set in the '%s' env variable",
                label, envVariableName);
            logInvalidPathError(errorLogLevel, logTag, label, ensureAbsolute, envPathMaxLength);
        }
        return 1;
    }

    // If not absolute.
    if (ensureAbsolute && envPath[0] != '/') {
        if (errorLogLevel >= 1 && !libtermux_core__nos__c__getIsRunningTests()) {
            logErrorForLogLevel(errorLogLevel, logTag, "The %s value with length '%zu' set in the '%s' env variable is not an absolute path: '%s'",
                label, envPathLength, envVariableName, envPath);
            logInvalidPathError(errorLogLevel, logTag, label, ensureAbsolute, envPathMaxLength);
        }
        return 1;
    }

    // If length exceeds max length.
    if (envPathMaxLength >= 1 && envPathLength >= envPathMaxLength) {
        if (errorLogLevel >= 1 && !libtermux_core__nos__c__getIsRunningTests()) {
                logErrorForLogLevel(errorLogLevel, logTag, "The %s value with length '%zu' set in the '%s' env variable has length >= max length '%d': '%s'",
                    label, envPathLength, envVariableName, envPathMaxLength, envPath);
            logInvalidPathError(errorLogLevel, logTag, label, ensureAbsolute, envPathMaxLength);
        }
        return 1;
    }

    // If length exceeds buffer length.
    if (bufferSize <= envPathLength) {
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorDebug(logTag, "The %s '%s' with length '%zu' set in the '%s' env variable is too long to fit in the buffer with size '%zu'",
                label, envPath, envPathLength, envVariableName, bufferSize);
        }
        errno = EINVAL;
        return -1;
    }

    // If valid.
    strcpy(buffer, envPath);

    if (normalize) {
        normalizePath(buffer, false, removeDoubleDot);
        if (strlen(buffer) < 1) {
            logErrorForLogLevel(errorLogLevel, logTag, "The %s '%s' set in the '%s' env variable with length '%zu' became null after normalization",
                label, envPath, envVariableName, envPathLength);
            return 1;
        }
    }

    return 0;

}

const char* getPathFromEnvOrDefault(int errorLogLevel, const char* logTag,
    const char* label, const char * envVariableName,
    bool ensureAbsolute, size_t envPathMaxLength,
    bool normalize, bool removeDoubleDot,
    const char* defaultPath, int defaultPathAccessCheckType,
    char *buffer, size_t bufferSize) {
    // FIXME: Check @Beta comment in header doc.

    const char *path;
    int result = getPathFromEnv(errorLogLevel, logTag,
        label, envVariableName, ensureAbsolute, envPathMaxLength,
        normalize, removeDoubleDot,
        buffer, bufferSize);
    if (result < 0) {
        return NULL;
    } else if (result == 0 && strlen(buffer) > 0) {
        path = buffer;
        if (!libtermux_core__nos__c__getIsRunningTests()) {
            logErrorVVerbose(logTag, "%s: '%s'", label, path);
        }
        return path;
    }

    // Use default path.
    size_t pathLength = strlen(defaultPath);
    if (defaultPath[0] != '/' || bufferSize <= pathLength) {
        logErrorDebug(logTag, "The default_%s '%s' with length '%zu' must be an absolute path starting with a '/' with max length '%zu'",
            label, defaultPath, pathLength, bufferSize);
        errno = EINVAL;
        return NULL;
    }

    strcpy(buffer, defaultPath);
    if (normalize) {
        normalizePath(buffer, false, removeDoubleDot);
        if (strlen(buffer) < 1) {
            logErrorDebug(logTag, "The default_%s '%s' with length '%zu' became null after normalization",
                label, defaultPath, pathLength);
            errno = EINVAL;
            return NULL;
        }
    }
    path = buffer;
    if (!libtermux_core__nos__c__getIsRunningTests()) {
        logErrorVVerbose(logTag, "default_%s: '%s'", label, path);
    }

    // Check default path access if required.
    if (defaultPathAccessCheckType >= 0) {
        if (defaultPathAccessCheckType == 0) {
            if (access(path, F_OK) != 0) {
                logStrerrorDebug(logTag, "The default_%s '%s' does not exist", label, path);
                return NULL;
            }
        } else if (defaultPathAccessCheckType == 1) {
            if (access(path, R_OK) != 0) {
                logStrerrorDebug(logTag, "The default_%s '%s' is not readable", label, path);
                return NULL;
            }
        } else if (defaultPathAccessCheckType == 2) {
            if (access(path, (R_OK | W_OK)) != 0) {
                logStrerrorDebug(logTag, "The default_%s '%s' is not readable or writable", label, path);
                return NULL;
            }
        } else if (defaultPathAccessCheckType == 3) {
            if (access(path, (R_OK | X_OK)) != 0) {
                logStrerrorDebug(logTag, "The default_%s '%s' is not readable or executable", label, path);
                return NULL;
            }
        } else if (defaultPathAccessCheckType == 4) {
            if (access(path, (R_OK | W_OK | X_OK)) != 0) {
                logStrerrorDebug(logTag, "The default_%s '%s' is not readable, writable or executable", label, path);
                return NULL;
            }
        }
    }

    return path;
}





/**
 * Make all the directories leading up to PATH.
 *
 * - https://github.com/bminor/bash/blob/bash-5.2/examples/loadables/mkdir.c#L51
 */
int createDirectoryFile(const char* logTag, const char *path, bool userMode, int mode) {

    /* Make the new mode */
    sOriginalUmask = umask(0);
    umask(sOriginalUmask);

    int nmode = (S_IRWXU | S_IRWXG | S_IRWXO) & ~sOriginalUmask;
    int parentMode = nmode | (S_IWUSR|S_IXUSR);  /* u+wx */

    /* Adjust new mode based on mode argument */
    nmode &= mode;

    return make_path(logTag, path, userMode, nmode, parentMode);
}

/**
 * Make all the directories leading up to PATH, then create PATH.
 * Note that this changes the process's umask; make sure that all
 * paths leading to a return reset it to ORIGINAL_UMASK.
 *
 * - https://github.com/bminor/bash/blob/bash-5.2/examples/loadables/mkdir.c#L133
 */
static int make_path(const char* logTag, const char *path, bool userMode, int nmode, int parentMode) {
    //int oumask;
    struct stat sb;
    char *p, *npath;

    if (stat(path, &sb) == 0) {
        if (S_ISDIR(sb.st_mode) == 0) {
            logStrerror(logTag, "mkdir: A file already exists at path '%s' but is not a directory", path);
            return -1;
        }

        if (userMode && chmod(path, nmode)) {
            logStrerror(logTag, "mkdir: Failed to set permission for existing path '%s'", path);
            return -1;
        }

        return 0;
    }

    //oumask = umask(0);
    umask(0);

    //npath = savestring(path);    /* So we can write to it. */
    char npathBuffer[strlen(path) + 1];
    strcpy(npathBuffer, path);
    npath = npathBuffer;

    /* Check whether or not we need to do anything with intermediate dirs. */

    /* Skip leading slashes. */
    p = npath;
    while (*p == '/') {
        p++;
    }

    while ((p = strchr(p, '/'))) {
        *p = '\0';

        if (stat(npath, &sb) != 0) {
            if (mkdir(npath, 0)) {
                logStrerror(logTag, "mkdir: Failed to create directory at path '%s'", npath);
                umask(sOriginalUmask);
                return -1;
            }
            if (chmod(npath, parentMode) != 0) {
                logStrerror(logTag, "mkdir: Failed to set permission for path '%s'", npath);
                umask(sOriginalUmask);
                return -1;
            }
        } else if (S_ISDIR(sb.st_mode) == 0) {
            logStrerror(logTag, "mkdir: A file already exists at path '%s' but is not a directory", npath);
            umask(sOriginalUmask);
            return -1;
        }

        *p++ = '/';   /* restore slash */
        while (*p == '/') {
            p++;
        }
    }

    /* Create the final directory component. */
    if (stat(npath, &sb) && mkdir(npath, nmode)) {
        logStrerror(logTag, "mkdir: Failed to create directory at path '%s'", npath);
        umask(sOriginalUmask);
        return -1;
    }

    umask(sOriginalUmask);
    return 0;
}





int closeFD(const char* logTag, const char* label, int fd) {
    if (fd >= 0 &&
        close(fd) == -1) {
        if (errno != EBADF) {
            logStrerror(logTag, "Failed to close %s fd %d", label, fd);
        }
        return -1;
    }
    return 0;
}





int redirectStdFdToDevNull(bool do_stdin, bool do_stdout, bool do_stderr) {
    int dev_null;
    int ret = 0;

    if ((dev_null = open("/dev/null", O_RDWR)) == -1) {
        logStrerror(LOG_TAG, "Failed to open '/dev/null'");
        return -1;
    }

    if (do_stdin && dup2(dev_null, STDIN_FILENO) == -1) {
        logStrerror(LOG_TAG, "Failed to redirect stdin to '/dev/null'");
        ret = -1;
    }

    if (do_stdout && dup2(dev_null, STDOUT_FILENO) == -1) {
        logStrerror(LOG_TAG, "Failed to redirect stdout to '/dev/null'");
        ret = -1;
    }

    if (do_stderr && dup2(dev_null, STDERR_FILENO) == -1) {
        logStrerror(LOG_TAG, "Failed to redirect stderr to '/dev/null'");
        ret = -1;
    }

    if (dev_null > STDERR_FILENO) {
        close(dev_null);
    }

    return ret;
}

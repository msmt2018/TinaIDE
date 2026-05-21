#include <limits.h>

#include <termux/termux_core__nos__c/v1/unix/file/UnixFileUtils.h>



void test__absolutizePath();
void test__normalizePath();



void UnixFileUtils_runTests() {
    logVerbose(LOG_TAG, "UnixFileUtils_runTests()");

    test__absolutizePath();
    test__normalizePath();

    int__AEqual(0, errno);
}





void test__absolutizePath__Basic();

void test__absolutizePath() {
    logVerbose(LOG_TAG, "test__absolutizePath()");

    test__absolutizePath__Basic();

    int__AEqual(0, errno);
}



#define absolutizeP__AEqual(expectedPath, path)                                                    \
    if (1) {                                                                                       \
    char absolutePath[PATH_MAX];                                                                   \
    assertStringEqualsWithError(expectedPath, absolutizePath(path, absolutePath, sizeof(absolutePath)), \
    LOG_TAG, "%d: absolutizePath('%s')", __LINE__, path);                                          \
    errno = 0;                                                                                     \
    } else ((void)0)

#define absolutizeP__ANull(path)                                                                   \
    if (1) {                                                                                       \
    char absolutePath[PATH_MAX];                                                                   \
    assertStringNullWithError(absolutizePath(path, absolutePath, sizeof(absolutePath)),            \
    LOG_TAG, "%d: absolutizePath('%s')", __LINE__, path);                                          \
    errno = 0;                                                                                     \
    } else ((void)0)



void test__absolutizePath__Basic() {
    logVVerbose(LOG_TAG, "test__absolutizePath__Basic()");

    char expected[PATH_MAX];
    char cwd[PATH_MAX];

    state__ATrue(getcwd(cwd, sizeof(cwd)) != NULL);

    // Ensure enough space for 25 char binary under TERMUX__PREFIX__BIN_DIR for tests.
    state__ATrue(strlen(cwd) + strlen(TERMUX__PREFIX__BIN_DIR "/xxxxxxxxxxxxxxxxxxxxxxxxx") < PATH_MAX);


    absolutizeP__ANull(NULL);
    absolutizeP__ANull("");

    {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "/%0*d", PATH_MAX - 2, 0);
    absolutizeP__AEqual(path, path);
    }

    {
    char path[PATH_MAX + 1];
    snprintf(path, sizeof(path), "/%0*d", PATH_MAX - 1, 0);
    absolutizeP__ANull(path);
    }

    {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%0*d", PATH_MAX - 1, 0);
    absolutizeP__ANull(path);
    }

    {
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "%0*d", PATH_MAX - 2, 0);
    state__ATrue(strlen(cwd) >= 1);
    absolutizeP__ANull(path);
    }


    snprintf(expected, sizeof(expected), "%s/path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "path/to/binary");

    snprintf(expected, sizeof(expected), "%s/path/to/../binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "path/to/../binary");


    snprintf(expected, sizeof(expected), "%s/../path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "../path/to/binary");

    snprintf(expected, sizeof(expected), "%s/../../path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "../../path/to/binary");


    snprintf(expected, sizeof(expected), "%s/./path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "./path/to/binary");

    snprintf(expected, sizeof(expected), "%s/././path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "././path/to/binary");


    snprintf(expected, sizeof(expected), "%s/.././path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, ".././path/to/binary");

    snprintf(expected, sizeof(expected), "%s/./../path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "./../path/to/binary");


    {
    char path[PATH_MAX];
    snprintf(expected, sizeof(expected), "%s/path/to/binary", cwd) < 0 ? abort() : (void)0;
    snprintf(path, sizeof(path), "%s/path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, path);
    }


    {
    char path[PATH_MAX];
    snprintf(expected, sizeof(expected), "%s/../path/to/binary", cwd) < 0 ? abort() : (void)0;
    snprintf(path, sizeof(path), "%s/../path/to/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, path);
    }


    snprintf(expected, sizeof(expected), "%s/binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, "binary");

    {
    char path[PATH_MAX];
    snprintf(expected, sizeof(expected), "%s///binary", cwd) < 0 ? abort() : (void)0;
    snprintf(path, sizeof(path), "%s///binary", cwd) < 0 ? abort() : (void)0;
    absolutizeP__AEqual(expected, path);
    }

}





void test__normalizePath__KeepEndSeparator();
void test__normalizePath__NoEndSeparator();
void test__normalizePath__SingleDots();
void test__normalizePath__DoubleDots();
void test__normalizePath__FromMethodDocs();
void test__normalizePath__BinaryPaths();

/**
 * Test normalization of file paths.
 *
 * Most of the tests were taken and modified from following under Apache-2.0 license.
 * - https://github.com/apache/commons-io/blob/51894ebab6d260962caacd9e33394226bbf891c4/src/test/java/org/apache/commons/io/FilenameUtilsTest.java#L758
 * - https://github.com/apache/commons-io/blob/master/LICENSE.txt
 */
void test__normalizePath() {
    logVerbose(LOG_TAG, "test__normalizePath()");

    test__normalizePath__KeepEndSeparator();
    test__normalizePath__NoEndSeparator();
    test__normalizePath__SingleDots();
    test__normalizePath__DoubleDots();
    test__normalizePath__FromMethodDocs();
    test__normalizePath__BinaryPaths();

    int__AEqual(0, errno);
}



#define normalizeP__AEqual(expectedPath, pathParam, keepEndSeparator, removeDoubleDot)             \
    if (1) {                                                                                       \
    const char* path = pathParam;                                                                  \
    if (path != NULL) {                                                                            \
        char inputPath[PATH_MAX];                                                                  \
        strncpy(inputPath, path, sizeof(inputPath) - 1);                                           \
        assertStringEqualsWithError(expectedPath, normalizePath(inputPath, keepEndSeparator, removeDoubleDot), \
        LOG_TAG, "%d: normalizePath('%s', %s, %s)", __LINE__, inputPath, #keepEndSeparator, #removeDoubleDot); \
    } else {                                                                                       \
        assertStringEqualsWithError(expectedPath, normalizePath(NULL, keepEndSeparator, removeDoubleDot), \
        LOG_TAG, "%d: normalizePath(null, %s, %s)", __LINE__, #keepEndSeparator, #removeDoubleDot); \
    }                                                                                              \
    } else ((void)0)

#define normalizeP__ANull(pathParam, keepEndSeparator, removeDoubleDot)                            \
    if (1) {                                                                                       \
    const char* path = pathParam;                                                                  \
    if (path != NULL) {                                                                            \
        char inputPath[PATH_MAX];                                                                  \
        strncpy(inputPath, path, sizeof(inputPath) - 1);                                           \
        assertStringNullWithError(normalizePath(inputPath, keepEndSeparator, removeDoubleDot),     \
        LOG_TAG, "%d: normalizePath('%s', %s, %s)", __LINE__, inputPath, #keepEndSeparator, #removeDoubleDot); \
    } else {                                                                                       \
        assertStringNullWithError(normalizePath(NULL, keepEndSeparator, removeDoubleDot),          \
        LOG_TAG, "%d: normalizePath(null, %s, %s)", __LINE__, #keepEndSeparator, #removeDoubleDot); \
    }                                                                                              \
    } else ((void)0)

#define normalizeP__AEqual__KES(expected, path) normalizeP__AEqual(expected, path, true, true)
#define normalizeP__ANull__KES(path) normalizeP__ANull(path, true, true)

#define normalizeP__AEqual__NES(expected, path) normalizeP__AEqual(expected, path, false, true)
#define normalizeP__ANull__NES(path) normalizeP__ANull(path, false, true)



void test__normalizePath__KeepEndSeparator() {
    logVVerbose(LOG_TAG, "test__normalizePath__KeepEndSeparator()");

    normalizeP__ANull(NULL, true, true);
    normalizeP__ANull__KES("");

    normalizeP__AEqual__KES("/", "/");
    normalizeP__AEqual__KES("/", "//");
    normalizeP__AEqual__KES("/", "///");
    normalizeP__AEqual__KES("/", "////");

    normalizeP__AEqual__KES("/a", "/a");
    normalizeP__AEqual__KES("/a", "//a");
    normalizeP__AEqual__KES("/a/", "//a/");
    normalizeP__AEqual__KES("/a/", "//a//");
    normalizeP__AEqual__KES("/a/", "//a///");
    normalizeP__AEqual__KES("/a/", "///a///");
    normalizeP__AEqual__KES("/a/b", "///a/b");
    normalizeP__AEqual__KES("/a/b/", "///a/b/");
    normalizeP__AEqual__KES("/a/b/", "///a/b//");
    normalizeP__AEqual__KES("/a/b/", "///a//b//");
    normalizeP__AEqual__KES("/a/b/", "///a//b///");
    normalizeP__AEqual__KES("/a/b/", "///a///b///");
    normalizeP__AEqual__KES("a/", "a//");
    normalizeP__AEqual__KES("a/", "a///");
    normalizeP__AEqual__KES("a/", "a///");
    normalizeP__AEqual__KES("a/b", "a/b");
    normalizeP__AEqual__KES("a/b/", "a/b/");
    normalizeP__AEqual__KES("a/b/", "a/b//");
    normalizeP__AEqual__KES("a/b/", "a//b//");
    normalizeP__AEqual__KES("a/b/", "a//b///");
    normalizeP__AEqual__KES("a/b/", "a///b///");
    normalizeP__AEqual__KES("a/b/c", "a/b//c");
    normalizeP__AEqual__KES("/a/b/c", "/a/b//c");
    normalizeP__AEqual__KES("/a/b/c/", "/a/b//c/");
    normalizeP__AEqual__KES("/a/b/c/d/", "///a//b///c/d///");
    normalizeP__AEqual__KES("/a/b/c/d", "///a//b///c/d");
    normalizeP__AEqual__KES("/a/b/c/d/", "//a//b///c/d//");

    normalizeP__AEqual__KES("~", "~");
    normalizeP__AEqual__KES("~/", "~/");
    normalizeP__AEqual__KES("~/a", "~/a");
    normalizeP__AEqual__KES("~/a/", "~/a/");
    normalizeP__AEqual__KES("~/a/b/d", "~/a/b//d");
    normalizeP__AEqual__KES("~user/a", "~user/a");
    normalizeP__AEqual__KES("~user/a/", "~user/a/");
    normalizeP__AEqual__KES("~user/a/b/d", "~user/a/b//d");
    normalizeP__AEqual__KES("~user/", "~user/");
    normalizeP__AEqual__KES("~user", "~user");
    normalizeP__AEqual__KES("~user/a/b/c.txt", "~user/a/b/c.txt");

    normalizeP__AEqual__KES("C:/a", "C:/a");
    normalizeP__AEqual__KES("C:/a/", "C:/a/");
    normalizeP__AEqual__KES("C:/a/b/d", "C:/a/b//d");
    normalizeP__AEqual__KES("C:/", "C:/");
    normalizeP__AEqual__KES("C:a", "C:a");
    normalizeP__AEqual__KES("C:a/", "C:a/");
    normalizeP__AEqual__KES("C:a/b/d", "C:a/b//d");
    normalizeP__AEqual__KES("C:", "C:");
    normalizeP__AEqual__KES("C:/a/b/c.txt", "C:/a/b/c.txt");

    normalizeP__AEqual__KES("/server/a", "//server/a");
    normalizeP__AEqual__KES("/server/a/", "//server/a/");
    normalizeP__AEqual__KES("/server/a/b/d", "//server/a/b//d");
    normalizeP__AEqual__KES("/server/", "//server/");
    normalizeP__AEqual__KES("/server/a/b/c.txt", "//server/a/b/c.txt");
    normalizeP__AEqual__KES("/-server/a/b/c.txt", "/-server/a/b/c.txt");

    normalizeP__AEqual__KES(":", ":");
    normalizeP__AEqual__KES("1:", "1:");
    normalizeP__AEqual__KES("1:a", "1:a");
    normalizeP__AEqual__KES("1:/a/b/c.txt", "1:/a/b/c.txt");
    normalizeP__AEqual__KES("/a/b/c.txt", "///a/b/c.txt");
    normalizeP__AEqual__KES("a/b/c.txt", "a/b/c.txt");
    normalizeP__AEqual__KES("/a/b/c.txt", "/a/b/c.txt");
    normalizeP__AEqual__KES("~/a/b/c.txt", "~/a/b/c.txt");
    normalizeP__AEqual__KES("/127.0.0.1/a/b/c.txt", "//127.0.0.1/a/b/c.txt");
    normalizeP__AEqual__KES("/::1/a/b/c.txt", "//::1/a/b/c.txt");
    normalizeP__AEqual__KES("/1::/a/b/c.txt", "//1::/a/b/c.txt");
    normalizeP__AEqual__KES("/server.example.org/a/b/c.txt", "//server.example.org/a/b/c.txt");
    normalizeP__AEqual__KES("/server.sub.example.org/a/b/c.txt", "/server.sub.example.org/a/b/c.txt");
    normalizeP__AEqual__KES("/server./a/b/c.txt", "//server./a/b/c.txt");
    normalizeP__AEqual__KES("/1::127.0.0.1/a/b/c.txt", "//1::127.0.0.1/a/b/c.txt");

    // Not a valid IPv4 addresses but technically a valid "reg-name"s according to RFC1034.
    normalizeP__AEqual__KES("/127.0.0.256/a/b/c.txt", "//127.0.0.256/a/b/c.txt");
    normalizeP__AEqual__KES("/127.0.0.01/a/b/c.txt", "//127.0.0.01/a/b/c.txt");

    normalizeP__AEqual__KES("/::1::2/a/b/c.txt", "//::1::2/a/b/c.txt");
    normalizeP__AEqual__KES("/:1/a/b/c.txt", "//:1/a/b/c.txt");
    normalizeP__AEqual__KES("/1:/a/b/c.txt", "//1:/a/b/c.txt");
    normalizeP__AEqual__KES("/1:2:3:4:5:6:7:8:9/a/b/c.txt", "//1:2:3:4:5:6:7:8:9/a/b/c.txt");
    normalizeP__AEqual__KES("/g:2:3:4:5:6:7:8/a/b/c.txt", "//g:2:3:4:5:6:7:8/a/b/c.txt");
    normalizeP__AEqual__KES("/1ffff:2:3:4:5:6:7:8/a/b/c.txt", "//1ffff:2:3:4:5:6:7:8/a/b/c.txt");
    normalizeP__AEqual__KES("/1:2/a/b/c.txt", "//1:2/a/b/c.txt");
}

void test__normalizePath__NoEndSeparator() {
    logVVerbose(LOG_TAG, "test__normalizePath__NoEndSeparator()");

    normalizeP__ANull(NULL, false, true);
    normalizeP__ANull__NES("");

    normalizeP__AEqual__NES("/", "/");
    normalizeP__AEqual__NES("/", "//");
    normalizeP__AEqual__NES("/", "///");
    normalizeP__AEqual__NES("/", "////");

    normalizeP__AEqual__NES("/a", "/a");
    normalizeP__AEqual__NES("/a", "//a");
    normalizeP__AEqual__NES("/a", "//a/");
    normalizeP__AEqual__NES("/a", "//a//");
    normalizeP__AEqual__NES("/a", "//a///");
    normalizeP__AEqual__NES("/a", "///a///");
    normalizeP__AEqual__NES("/a/b", "///a/b");
    normalizeP__AEqual__NES("/a/b", "///a/b/");
    normalizeP__AEqual__NES("/a/b", "///a/b//");
    normalizeP__AEqual__NES("/a/b", "///a//b//");
    normalizeP__AEqual__NES("/a/b", "///a//b///");
    normalizeP__AEqual__NES("/a/b", "///a///b///");
    normalizeP__AEqual__NES("a", "a//");
    normalizeP__AEqual__NES("a", "a///");
    normalizeP__AEqual__NES("a", "a///");
    normalizeP__AEqual__NES("a/b", "a/b");
    normalizeP__AEqual__NES("a/b", "a/b/");
    normalizeP__AEqual__NES("a/b", "a/b//");
    normalizeP__AEqual__NES("a/b", "a//b//");
    normalizeP__AEqual__NES("a/b", "a//b///");
    normalizeP__AEqual__NES("a/b", "a///b///");
    normalizeP__AEqual__NES("a/b/c", "a/b//c");
    normalizeP__AEqual__NES("/a/b/c", "/a/b//c");
    normalizeP__AEqual__NES("/a/b/c", "/a/b//c/");
    normalizeP__AEqual__NES("/a/b/c/d", "///a//b///c/d///");
    normalizeP__AEqual__NES("/a/b/c/d", "///a//b///c/d");
    normalizeP__AEqual__NES("/a/b/c/d", "//a//b///c/d//");

    normalizeP__AEqual__NES("~", "~");
    normalizeP__AEqual__NES("~", "~/");
    normalizeP__AEqual__NES("~user/a", "~user/a");
    normalizeP__AEqual__NES("~user/a", "~user/a/");
    normalizeP__AEqual__NES("~user/a/b/d", "~user/a/b//d/");
    normalizeP__AEqual__NES("~user", "~user/");
    normalizeP__AEqual__NES("~user", "~user");
    normalizeP__AEqual__NES("~/a", "~/a");
    normalizeP__AEqual__NES("~/a", "~/a/");
    normalizeP__AEqual__NES("~/a/b/d", "~/a/b//d/");
    normalizeP__AEqual__NES("~/a/b/c.txt", "~/a/b/c.txt/");
    normalizeP__AEqual__NES("~user/a/b/c.txt", "~user/a/b/c.txt/");

    normalizeP__AEqual__NES("C:/a", "C:/a");
    normalizeP__AEqual__NES("C:/a", "C:/a/");
    normalizeP__AEqual__NES("C:/a/b/d", "C:/a/b//d/");
    normalizeP__AEqual__NES("C:", "C://");
    normalizeP__AEqual__NES("C:a", "C:a");
    normalizeP__AEqual__NES("C:a", "C:a/");
    normalizeP__AEqual__NES("C:a/b/d", "C:a/b//d/");
    normalizeP__AEqual__NES("C:", "C:/");
    normalizeP__AEqual__NES("C:/a/b/c.txt", "C:/a/b/c.txt/");
    normalizeP__AEqual__NES("C:/a/b/c.txt", "C://a//b//c.txt/");

    normalizeP__AEqual__NES("/server/a", "//server/a");
    normalizeP__AEqual__NES("/server/a", "//server/a/");
    normalizeP__AEqual__NES("/server/a/b/d", "//server/a/b//d/");
    normalizeP__AEqual__NES("/server", "//server/");
    normalizeP__AEqual__NES("/server/a/b/c.txt", "//server/a/b/c.txt/");

    normalizeP__AEqual__NES(":", ":");
    normalizeP__AEqual__NES("1:", "1:");
    normalizeP__AEqual__NES("1:a", "1:a");
    normalizeP__AEqual__NES("1:/a/b/c.txt", "1:/a/b/c.txt");
    normalizeP__AEqual__NES("a/b/c.txt", "a/b/c.txt/");
    normalizeP__AEqual__NES("/a/b/c.txt", "/a/b/c.txt/");
    normalizeP__AEqual__NES("/a/b/c.txt", "///a/b/c.txt");
}

void test__normalizePath__SingleDots() {
    logVVerbose(LOG_TAG, "test__normalizePath__SingleDots()");

    normalizeP__ANull__KES(".");
    normalizeP__ANull__KES("./");
    normalizeP__AEqual__KES("/", "/.");
    normalizeP__AEqual__KES("/", "/./");
    normalizeP__ANull__KES("././");
    normalizeP__AEqual__KES("/", "/./.");

    normalizeP__AEqual__KES("/a", "/././a");
    normalizeP__AEqual__KES("/b/", "/./b/./");
    normalizeP__AEqual__KES("/b", "/./b/.");
    normalizeP__AEqual__KES("c/", "./c/./");
    normalizeP__AEqual__KES("c", "./c/.");
    normalizeP__AEqual__KES("c/", "././c/./");
    normalizeP__AEqual__KES("c", "././c/.");
    normalizeP__AEqual__KES("c/", "././c/././");
    normalizeP__AEqual__KES("c", "././c/./.");
    normalizeP__AEqual__KES("a/b", "a/b/././.");
    normalizeP__AEqual__KES("a/b/", "a/b/./././");
    normalizeP__AEqual__KES("a/", "./a/");
    normalizeP__AEqual__KES("a", "./a");
    normalizeP__AEqual__KES("/a/b/", "/a/b/./././");
    normalizeP__AEqual__KES("/a/b", "/a/b/././.");
    normalizeP__AEqual__KES("/a", "/./a");
    normalizeP__AEqual__KES("/", "/./");
    normalizeP__AEqual__KES("/", "/.");

    normalizeP__AEqual__KES("~/a/b/", "~/a/b/./././");
    normalizeP__AEqual__KES("~/a/b", "~/a/b/././.");
    normalizeP__AEqual__KES("~/a", "~/./a");
    normalizeP__AEqual__KES("~/", "~/./");
    normalizeP__AEqual__KES("~", "~/.");
    normalizeP__AEqual__KES("~/", "~/");
    normalizeP__AEqual__KES("~user/a/b/", "~user/a/b/./././");
    normalizeP__AEqual__KES("~user/a/b", "~user/a/b/././.");
    normalizeP__AEqual__KES("~user/a", "~user/./a");
    normalizeP__AEqual__KES("~user/", "~user/./");
    normalizeP__AEqual__KES("~user", "~user/.");

    normalizeP__AEqual__KES("C:/a/b/", "C:/a/b/./././");
    normalizeP__AEqual__KES("C:/a/b", "C:/a/b/././.");
    normalizeP__AEqual__KES("C:/a", "C:/./a");
    normalizeP__AEqual__KES("C:/", "C:/./");
    normalizeP__AEqual__KES("C:", "C:/.");
    normalizeP__AEqual__KES("C:a/b/", "C:a/b/./././");
    normalizeP__AEqual__KES("C:a/b", "C:a/b/././.");
    normalizeP__AEqual__KES("C:./a", "C:./a");
    normalizeP__AEqual__KES("C:./", "C:./");
    normalizeP__AEqual__KES("C:.", "C:.");

    normalizeP__AEqual__KES("/server/a/b/", "//server/a/b/./././");
    normalizeP__AEqual__KES("/server/a/b", "//server/a/b/././.");
    normalizeP__AEqual__KES("/server/a", "//server/./a");
    normalizeP__AEqual__KES("/server/", "//server/./");
    normalizeP__AEqual__KES("/server", "//server/.");

    normalizeP__AEqual__KES("/a/b/c.txt", "/./a/b/c.txt");
    normalizeP__AEqual__KES("/127.0.0.256/a/b/c.txt", "//./127.0.0.256/./a/b/c.txt");



    normalizeP__ANull__NES(".");
    normalizeP__ANull__NES("./");
    normalizeP__AEqual__NES("/", "/.");
    normalizeP__AEqual__NES("/", "/./");
    normalizeP__ANull__NES("././");
    normalizeP__AEqual__NES("/", "/./.");

    normalizeP__AEqual__NES("/a", "/././a/");
    normalizeP__AEqual__NES("/b", "/./b/./");
    normalizeP__AEqual__NES("/b", "/./b/.");
    normalizeP__AEqual__NES("c", "./c/./");
    normalizeP__AEqual__NES("c", "./c/.");
    normalizeP__AEqual__NES("c", "././c/./");
    normalizeP__AEqual__NES("c", "././c/.");
    normalizeP__AEqual__NES("c", "././c/././");
    normalizeP__AEqual__NES("c", "././c/./.");
    normalizeP__AEqual__NES("/", "/./.");
    normalizeP__AEqual__NES("/", "/.");
    normalizeP__AEqual__NES("a/b", "a/b/./././");
    normalizeP__AEqual__NES("a/b", "a/b/./././/");
    normalizeP__AEqual__NES("a", "./a//");
    normalizeP__AEqual__NES("a", "./a/");
    normalizeP__AEqual__NES("/a/b", "/a/b/./././");
    normalizeP__AEqual__NES("/a/b", "/a/b/./././");
    normalizeP__AEqual__NES("/a", "/./a/");
    normalizeP__AEqual__NES("/", "/./");
    normalizeP__AEqual__NES("/", "/./");

    normalizeP__AEqual__NES("~/a/b", "~/a/b/./././");
    normalizeP__AEqual__NES("~/a/b", "~/a/b/./././");
    normalizeP__AEqual__NES("~/a", "~/./a/");
    normalizeP__AEqual__NES("~", "~/./");
    normalizeP__AEqual__NES("~", "~/./");
    normalizeP__AEqual__NES("~user/a/b", "~user/a/b/./././");
    normalizeP__AEqual__NES("~user/a/b", "~user/a/b/././.");
    normalizeP__AEqual__NES("~user/a", "~user/./a/");
    normalizeP__AEqual__NES("~user", "~user/./");
    normalizeP__AEqual__NES("~user", "~user/./");

    normalizeP__AEqual__NES("C:/a/b", "C:/a/b/./././");
    normalizeP__AEqual__NES("C:/a/b", "C:/a/b/././.");
    normalizeP__AEqual__NES("C:/a", "C:/./a/");
    normalizeP__AEqual__NES("C:", "C:/./");
    normalizeP__AEqual__NES("C:", "C:/./");
    normalizeP__AEqual__NES("C:a/b", "C:a/b/./././");
    normalizeP__AEqual__NES("C:a/b", "C:a/b/././.");
    normalizeP__AEqual__NES("C:./a", "C:./a/");
    normalizeP__AEqual__NES("C:.", "C:./");
    normalizeP__AEqual__NES("C:.", "C:.");

    normalizeP__AEqual__NES("/server/a/b", "//server/a/b/./././");
    normalizeP__AEqual__NES("/server/a/b", "//server/a/b/././.");
    normalizeP__AEqual__NES("/server/a", "//server/./a/");
    normalizeP__AEqual__NES("/server", "//server/./");
    normalizeP__AEqual__NES("/server", "//server/.");

    normalizeP__AEqual__NES("/a/b/c.txt", "/./a/b/c.txt");
    normalizeP__AEqual__NES("/127.0.0.256/a/b/c.txt", "//./127.0.0.256/./a/b/c.txt");
}

void test__normalizePath__DoubleDots() {
    logVVerbose(LOG_TAG, "test__normalizePath__DoubleDots()");

    normalizeP__ANull__KES("..");
    normalizeP__ANull__KES("../");
    normalizeP__AEqual__KES("/", "/..");
    normalizeP__AEqual__KES("/", "/../");
    normalizeP__AEqual__KES("/", "/../../");

    normalizeP__AEqual__KES("/a", "/../a");
    normalizeP__AEqual__KES("/a", "/../a/.");
    normalizeP__ANull__KES("../");
    normalizeP__ANull__KES("../a");
    normalizeP__ANull__KES("../a/.");

    normalizeP__ANull__KES("./../a");
    normalizeP__ANull__KES("././../a");
    normalizeP__ANull__KES("././.././a");

    normalizeP__ANull__KES("a/../../b");
    normalizeP__AEqual__KES("b", "a/./../b");
    normalizeP__AEqual__KES("b", "a/./.././b");

    normalizeP__AEqual__KES("a/c", "a/b/../c");
    normalizeP__AEqual__KES("c", "a/b/../../c");
    normalizeP__AEqual__KES("c/", "a/b/../../c/");
    normalizeP__ANull__KES("a/b/../../../c");
    normalizeP__AEqual__KES("a", "a/b/..");
    normalizeP__AEqual__KES("a/", "a/b/../");
    normalizeP__ANull__KES("a/b/../..");
    normalizeP__ANull__KES("a/b/../../");
    normalizeP__ANull__KES("a/b/../../..");
    normalizeP__AEqual__KES("a/d", "a/b/../c/../d");
    normalizeP__AEqual__KES("a/d/", "a/b/../c/../d/");
    normalizeP__AEqual__KES("/a/c", "/a/b/../c");
    normalizeP__AEqual__KES("/c", "/a/b/../../c");
    normalizeP__AEqual__KES("/c", "/a/b/../../../c");
    normalizeP__AEqual__KES("/a", "/a/b/..");
    normalizeP__AEqual__KES("/", "/a/b/../..");
    normalizeP__AEqual__KES("/", "/a/b/../../..");
    normalizeP__AEqual__KES("/a/d", "/a/b/../c/../d");
    normalizeP__AEqual__KES("/a", "/../a");

    normalizeP__ANull__KES("~/..");
    normalizeP__ANull__KES("~/../");
    normalizeP__ANull__KES("~/../a");
    normalizeP__ANull__KES("~/../a/.");
    normalizeP__AEqual__KES("~/a/c", "~/a/b/../c");
    normalizeP__AEqual__KES("~/c", "~/a/b/../../c");
    normalizeP__ANull__KES("~/a/b/../../../c");
    normalizeP__AEqual__KES("~/a", "~/a/b/..");
    normalizeP__AEqual__KES("~/", "~/a/b/../../");
    normalizeP__AEqual__KES("~", "~/a/b/../..");
    normalizeP__ANull__KES("~/a/b/../../..");
    normalizeP__AEqual__KES("~/a/d", "~/a/b/../c/../d");
    normalizeP__ANull__KES("~/../a");
    normalizeP__ANull__KES("~/..");
    normalizeP__AEqual__KES("~user/a/c", "~user/a/b/../c");
    normalizeP__AEqual__KES("~user/c", "~user/a/b/../../c");
    normalizeP__ANull__KES("~user/a/b/../../../c");
    normalizeP__AEqual__KES("~user/a", "~user/a/b/..");
    normalizeP__AEqual__KES("~user", "~user/a/b/../..");
    normalizeP__ANull__KES("~user/a/b/../../..");
    normalizeP__AEqual__KES("~user/a/d", "~user/a/b/../c/../d");
    normalizeP__ANull__KES("~user/../a");
    normalizeP__ANull__KES("~user/..");

    normalizeP__AEqual__KES("C:/a/c", "C:/a/b/../c");
    normalizeP__AEqual__KES("C:/c", "C:/a/b/../../c");
    normalizeP__ANull__KES("C:/a/b/../../../../c");
    normalizeP__AEqual__KES("c", "C:/a/b/../../../c");
    normalizeP__AEqual__KES("C:/a", "C:/a/b/..");
    normalizeP__AEqual__KES("C:", "C:/a/b/../..");
    normalizeP__ANull__KES("C:/a/b/../../..");
    normalizeP__AEqual__KES("C:/a/d", "C:/a/b/../c/../d");
    normalizeP__AEqual__KES("a", "C:/../a");
    normalizeP__ANull__KES("C:/..");
    normalizeP__AEqual__KES("C:a/c", "C:a/b/../c");
    normalizeP__AEqual__KES("c", "C:a/b/../../c");
    normalizeP__ANull__KES("C:a/b/../../../c");
    normalizeP__AEqual__KES("C:a", "C:a/b/..");
    normalizeP__ANull__KES("C:a/b/../..");
    normalizeP__ANull__KES("C:a/b/../../..");
    normalizeP__AEqual__KES("C:a/d", "C:a/b/../c/../d");
    normalizeP__AEqual__KES("C:../a", "C:../a");
    normalizeP__AEqual__KES("C:..", "C:..");

    normalizeP__AEqual__KES("/server/a/c", "//server/a/b/../c");
    normalizeP__AEqual__KES("/server/c", "//server/a/b/../../c");
    normalizeP__AEqual__KES("/c", "/server/a/b/../../../../c");
    normalizeP__AEqual__KES("/c", "//server/a/b/../../../c");
    normalizeP__AEqual__KES("/server/a", "//server/a/b/..");
    normalizeP__AEqual__KES("/server", "//server/a/b/../..");
    normalizeP__AEqual__KES("/", "//server/a/b/../../..");
    normalizeP__AEqual__KES("/server/a/d", "//server/a/b/../c/../d");
    normalizeP__AEqual__KES("/a", "//server/../a");
    normalizeP__AEqual__KES("/", "//server/../..");
    normalizeP__AEqual__KES("/", "//server/..");
    normalizeP__AEqual__KES("/b/c/g", "/a/../../../../b/c/d/e/f/../../../g");
    normalizeP__AEqual__KES("/g", "/a/../../../../b/c/d/e/f/../../../../../g");

    normalizeP__AEqual__KES("/a/b/c.txt", "//../a/b/c.txt");
    normalizeP__AEqual__KES("/127.0..1/a/b/c.txt", "//127.0..1/a/b/c.txt");
    normalizeP__AEqual__KES("/foo", "//../foo");
    normalizeP__AEqual__KES("/foo", "//../foo");



    normalizeP__ANull__NES("..");
    normalizeP__ANull__NES("../");
    normalizeP__AEqual__NES("/", "/..");
    normalizeP__AEqual__NES("/", "/../");
    normalizeP__AEqual__NES("/", "/../../");

    normalizeP__AEqual__NES("/a", "/../a/");
    normalizeP__AEqual__NES("/a", "/../a/./");
    normalizeP__ANull__NES("../a");
    normalizeP__ANull__NES("../a/.");
    normalizeP__ANull__NES("../a");

    normalizeP__ANull__NES("./../a");
    normalizeP__ANull__NES("././../a");
    normalizeP__ANull__NES("././.././a");

    normalizeP__ANull__NES("a/../../b");
    normalizeP__AEqual__NES("b", "a/./../b/");
    normalizeP__AEqual__NES("b", "a/./.././b/");

    normalizeP__AEqual__NES("a/c", "a/b/../c/");
    normalizeP__AEqual__NES("c", "a/b/../../c");
    normalizeP__AEqual__NES("c", "a/b/../../c/");
    normalizeP__ANull__NES("a/b/../../../c/");
    normalizeP__AEqual__NES("a", "a/b/..");
    normalizeP__AEqual__NES("a", "a/b/../");
    normalizeP__ANull__NES("a/b/../..");
    normalizeP__ANull__NES("a/b/../../");
    normalizeP__ANull__NES("a/b/../../../");
    normalizeP__AEqual__NES("a/d", "a/b/../c/../d");
    normalizeP__AEqual__NES("a/d", "a/b/../c/../d/");
    normalizeP__AEqual__NES("/a/c", "/a/b/../c/");
    normalizeP__AEqual__NES("/c", "/a/b/../../c/");
    normalizeP__AEqual__NES("/c", "/a/b/../../../c/");
    normalizeP__AEqual__NES("/a", "/a/b/../");
    normalizeP__AEqual__NES("/", "/a/b/../../");
    normalizeP__AEqual__NES("/", "/a/b/../../../");
    normalizeP__AEqual__NES("/a/d", "/a/b/../c/../d/");
    normalizeP__AEqual__NES("/a", "/../a/");

    normalizeP__ANull__NES("~/..");
    normalizeP__ANull__NES("~/../");
    normalizeP__ANull__NES("~/../a");
    normalizeP__ANull__NES("~/../a/.");
    normalizeP__AEqual__NES("~/a/c", "~/a/b/../c/");
    normalizeP__AEqual__NES("~/c", "~/a/b/../../c/");
    normalizeP__ANull__NES("~/a/b/../../../c/");
    normalizeP__AEqual__NES("~/a", "~/a/b/../");
    normalizeP__AEqual__NES("~", "~/a/b/../../");
    normalizeP__AEqual__NES("~", "~/a/b/../..");
    normalizeP__ANull__NES("~/a/b/../../../");
    normalizeP__AEqual__NES("~/a/d", "~/a/b/../c/../d/");
    normalizeP__ANull__NES("~/../a/");
    normalizeP__ANull__NES("~/..");
    normalizeP__ANull__NES("~/../");
    normalizeP__AEqual__NES("~user/a/c", "~user/a/b/../c/");
    normalizeP__AEqual__NES("~user/c", "~user/a/b/../../c/");
    normalizeP__ANull__NES("~user/a/b/../../../c/");
    normalizeP__AEqual__NES("~user/a", "~user/a/b/../");
    normalizeP__AEqual__NES("~user", "~user/a/b/../../");
    normalizeP__ANull__NES("~user/a/b/../../../");
    normalizeP__AEqual__NES("~user/a/d", "~user/a/b/../c/../d/");
    normalizeP__ANull__NES("~user/../a/");
    normalizeP__ANull__NES("~user/../");

    normalizeP__AEqual__NES("C:/a/c", "C:/a/b/../c/");
    normalizeP__AEqual__NES("C:/c", "C:/a/b/../../c/");
    normalizeP__AEqual__NES("c", "C:/a/b/../../../c/");
    normalizeP__AEqual__NES("C:/a", "C:/a/b/../");
    normalizeP__AEqual__NES("C:", "C:/a/b/../../");
    normalizeP__ANull__NES("C:/a/b/../../../");
    normalizeP__AEqual__NES("C:/a/d", "C:/a/b/../c/../d/");
    normalizeP__AEqual__NES("a", "C:/../a/");
    normalizeP__ANull__NES("C:/../");
    normalizeP__AEqual__NES("C:a/c", "C:a/b/../c/");
    normalizeP__AEqual__NES("c", "C:a/b/../../c/");
    normalizeP__ANull__NES("C:a/b/../../../c/");
    normalizeP__AEqual__NES("C:a", "C:a/b/../");
    normalizeP__ANull__NES("C:a/b/../../");
    normalizeP__ANull__NES("C:a/b/../../../");
    normalizeP__AEqual__NES("C:a/d", "C:a/b/../c/../d/");
    normalizeP__AEqual__NES("C:../a", "C:../a/");
    normalizeP__AEqual__NES("C:..", "C:../");

    normalizeP__AEqual__NES("/server/a/c", "//server/a/b/../c/");
    normalizeP__AEqual__NES("/server/c", "//server/a/b/../../c/");
    normalizeP__AEqual__NES("/c", "//server/a/b/../../../../c/");
    normalizeP__AEqual__NES("/c", "//server/a/b/../../../c/");
    normalizeP__AEqual__NES("/server/a", "//server/a/b/../");
    normalizeP__AEqual__NES("/server", "//server/a/b/../../");
    normalizeP__AEqual__NES("/", "//server/a/b/../../../");
    normalizeP__AEqual__NES("/server/a/d", "//server/a/b/../c/../d/");
    normalizeP__AEqual__NES("/a", "//server/../a");
    normalizeP__AEqual__NES("/", "//server/../../");
    normalizeP__AEqual__NES("/", "//server/../");
}

void test__normalizePath__FromMethodDocs() {
    logVVerbose(LOG_TAG, "test__normalizePath__FromMethodDocs()");

    normalizeP__ANull(NULL, false, true);
    normalizeP__ANull__NES("");
    normalizeP__AEqual__NES("/", "/");
    normalizeP__AEqual__NES("/", "//");

    normalizeP__AEqual__NES(":", ":");
    normalizeP__AEqual__NES("1:/a/b/c.txt", "1:/a/b/c.txt");
    normalizeP__AEqual__NES("/a/b/c/d", "///a//b///c/d///");
    normalizeP__AEqual__NES("/foo", "/foo//");
    normalizeP__AEqual__NES("/foo", "/foo/./");
    normalizeP__AEqual__NES("/foo/bar", "//foo//./bar");
    normalizeP__AEqual__NES("~", "~");
    normalizeP__AEqual__NES("~", "~/");
    normalizeP__AEqual__NES("~user", "~user/");
    normalizeP__AEqual__NES("~user", "~user");
    normalizeP__AEqual__NES("~user/a", "~user/a");

    normalizeP__ANull__NES(".");
    normalizeP__ANull__NES("./");
    normalizeP__AEqual__NES("/", "/.");
    normalizeP__AEqual__NES("/", "/./");
    normalizeP__ANull__NES("././");
    normalizeP__AEqual__NES("/", "/./.");
    normalizeP__AEqual__NES("/a", "/././a");
    normalizeP__AEqual__NES("/b", "/./b/./");
    normalizeP__AEqual__NES("c", "././c/./");

    normalizeP__ANull__NES("..");
    normalizeP__ANull__NES("../");
    normalizeP__AEqual__NES("/", "/..");
    normalizeP__AEqual__NES("/", "/../");
    normalizeP__AEqual__NES("/a", "/../a");
    normalizeP__ANull__NES("../a/.");
    normalizeP__ANull__NES("../a");
    normalizeP__AEqual__NES("/a", "/a/b/..");
    normalizeP__AEqual__NES("/", "/a/b/../..");
    normalizeP__AEqual__NES("/", "/a/b/../../..");
    normalizeP__AEqual__NES("/c", "/a/b/../../../c");
    normalizeP__ANull__NES("./../a");
    normalizeP__ANull__NES("././../a");
    normalizeP__ANull__NES("././.././a");
    normalizeP__ANull__NES("a/../../b");
    normalizeP__AEqual__NES("b", "a/./../b");
    normalizeP__AEqual__NES("b", "a/./.././b");
    normalizeP__ANull__NES("foo/../../bar");
    normalizeP__AEqual__NES("/bar", "/foo/../bar");
    normalizeP__AEqual__NES("/bar", "/foo/../bar/");
    normalizeP__AEqual__NES("/baz", "/foo/../bar/../baz");
    normalizeP__AEqual__NES("foo", "foo/bar/..");
    normalizeP__AEqual__NES("bar", "foo/../bar");
    normalizeP__ANull__NES("~/..");
    normalizeP__ANull__NES("~/../");
    normalizeP__ANull__NES("~/../a/.");
    normalizeP__ANull__NES("~/..");
    normalizeP__AEqual__NES("~/bar", "~/foo/../bar/");
    normalizeP__AEqual__NES("C:/bar", "C:/foo/../bar");
    normalizeP__AEqual__NES("/server/bar", "//server/foo/../bar");
    normalizeP__AEqual__NES("/bar", "//server/../bar");
}

void test__normalizePath__BinaryPaths() {
    logVVerbose(LOG_TAG, "test__normalizePath__BinaryPaths()");

    normalizeP__AEqual__NES("binary", "binary");
    normalizeP__AEqual__NES("binary", "binary/");
    normalizeP__AEqual__NES("/binary", "//binary//");
    normalizeP__AEqual__NES("/binary", "/../binary");
    normalizeP__AEqual__NES("/binary", "/../../binary");
    normalizeP__ANull__NES("../binary");

    normalizeP__AEqual__NES("path/to/binary", "path/to/binary");
    normalizeP__AEqual__NES("path/to/binary", "path/to/binary/");
    normalizeP__AEqual__NES("path/to/binary", "path//to//binary");

    normalizeP__AEqual__NES("path/path/to/binary", "path/./path/to/binary");
    normalizeP__AEqual__NES("/path/to/binary", "/././path/to/binary");
    normalizeP__AEqual__NES("path/to/binary", "././path/to/binary");

    normalizeP__AEqual__NES("path/to/binary", "path/../path/to/binary");
    normalizeP__AEqual__NES("/path/to/binary", "/path/../../path/to/binary");
    normalizeP__ANull__NES("path/../../path/to/binary");

    normalizeP__AEqual__NES("path/to/binary", "path/.././path/to/binary");
    normalizeP__AEqual__NES("path/to/binary", "path/./../path/to/binary");
    normalizeP__AEqual__NES("/path/to/binary", "/path/../../path/to/binary");
    normalizeP__AEqual__NES("/path/to/binary", "/path/./../../path/to/binary");
    normalizeP__AEqual__NES("/path/to/binary", "/path/../.././path/to/binary");
    normalizeP__AEqual__NES("/path/to/binary", "/path/./../.././path/to/binary");
    normalizeP__ANull__NES("path/./../../path/to/binary");
    normalizeP__ANull__NES("path/./../.././path/to/binary");

    normalizeP__AEqual__NES("/usr/bin/sh", "/../../../../../../../../../../../../usr/./bin/../bin/sh");
    normalizeP__ANull__NES("../../../../../../../../../../../../usr/./bin/../bin/sh");

    normalizeP__AEqual__NES(TERMUX__PREFIX__BIN_DIR "/sh", TERMUX__PREFIX__BIN_DIR "/sh");
    normalizeP__AEqual__NES(TERMUX__PREFIX__BIN_DIR "/sh", TERMUX__PREFIX__BIN_DIR "/../bin/sh");
}

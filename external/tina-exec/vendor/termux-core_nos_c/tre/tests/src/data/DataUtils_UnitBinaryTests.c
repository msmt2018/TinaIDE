#include <termux/termux_core__nos__c/v1/data/DataUtils.h>



void test__stringStartsWith();
void test__stringEndsWith();



void DataUtils_runTests() {
    logVerbose(LOG_TAG, "DataUtils_runTests()");

    test__stringStartsWith();
    test__stringEndsWith();

    int__AEqual(0, errno);
}





void test__stringStartsWith__Basic();

void test__stringStartsWith() {
    logVerbose(LOG_TAG, "test__stringStartsWith()");

    test__stringStartsWith__Basic();

    int__AEqual(0, errno);
}



#define ssw__ATrue(string, prefix)                                                                 \
    if (1) {                                                                                       \
    assertTrueWithError(stringStartsWith(string, prefix),                                          \
        LOG_TAG, "%d: stringStartsWith('%s', '%s')", __LINE__, string , prefix);                   \
    assertIntWithError(0, errno,                                                                   \
        LOG_TAG, "%d: %s()", __LINE__, __FUNCTION__);                                              \
    } else ((void)0)

#define ssw__AFalse(string, prefix)                                                                \
    if (1) {                                                                                       \
    assertFalseWithError(stringStartsWith(string, prefix),                                         \
        LOG_TAG, "%d: stringStartsWith('%s', '%s')", __LINE__, string , prefix);                   \
    assertIntWithError(0, errno,                                                                   \
        LOG_TAG, "%d: %s()", __LINE__, __FUNCTION__);                                              \
    } else ((void)0)



void test__stringStartsWith__Basic() {
    logVVerbose(LOG_TAG, "test__stringStartsWith__Basic()");

    ssw__AFalse(NULL, "/path");
    ssw__AFalse("/path", NULL);
    ssw__AFalse(NULL, NULL);
    ssw__AFalse("", "");
    ssw__AFalse("/", "");
    ssw__AFalse("", "/");
    ssw__AFalse("", " ");
    ssw__AFalse(" ", "");
    ssw__ATrue(" ", " ");
    ssw__AFalse("", "\0");
    ssw__AFalse("\0", "");
    ssw__AFalse("\0", "\0");
    ssw__AFalse("\0", "a");
    ssw__AFalse("a", "\0");

    ssw__ATrue("/path/to/file", "/path");
    ssw__AFalse("/path", "/path/to/file");

    ssw__ATrue("/path", "/");
    ssw__ATrue("/path", "/p");
    ssw__ATrue("/path/to/file", "/");
    ssw__ATrue("/path/to/file", "/p");
    ssw__ATrue("/path/to/file", "/path/");
    ssw__ATrue("/path/to/file", "/path/to");
    ssw__ATrue("/path/to/file", "/path/to/");
    ssw__ATrue("/path/to/file", "/path/to/file");
    ssw__AFalse("/path/to/file", "/path/to/file/");

}





void test__stringEndsWith__Basic();

void test__stringEndsWith() {
    logVerbose(LOG_TAG, "test__stringEndsWith()");

    test__stringEndsWith__Basic();

    int__AEqual(0, errno);
}



#define sew__ATrue(string, prefix)                                                                 \
    if (1) {                                                                                       \
    assertTrueWithError(stringEndsWith(string, prefix),                                            \
        LOG_TAG, "%d: stringEndsWith('%s', '%s')", __LINE__, string , prefix);                     \
    } else ((void)0)

#define sew__AFalse(string, prefix)                                                                \
    if (1) {                                                                                       \
    assertFalseWithError(stringEndsWith(string, prefix),                                           \
        LOG_TAG, "%d: stringEndsWith('%s', '%s')", __LINE__, string , prefix);                     \
    } else ((void)0)



void test__stringEndsWith__Basic() {
    logVVerbose(LOG_TAG, "test__stringEndsWith__Basic()");

    sew__AFalse(NULL, "/path");
    sew__AFalse("/path", NULL);
    sew__AFalse(NULL, NULL);
    sew__AFalse("", "");
    sew__AFalse("/", "");
    sew__AFalse("", "/");
    sew__AFalse("", " ");
    sew__AFalse(" ", "");
    sew__ATrue(" ", " ");
    sew__AFalse("", "\0");
    sew__AFalse("\0", "");
    sew__AFalse("\0", "\0");
    sew__AFalse("\0", "a");
    sew__AFalse("a", "\0");

    sew__ATrue("/path/to/file", "/file");
    sew__AFalse("/file", "/path/to/file");

    sew__ATrue("/path", "h");
    sew__ATrue("/path", "/path");
    sew__AFalse("/path", "hh");
    sew__ATrue("/path/to/file", "/file");
    sew__ATrue("/path/to/file", "to/file");
    sew__ATrue("/path/to/file", "/to/file");
    sew__ATrue("/path/to/file", "path/to/file");
    sew__ATrue("/path/to/file", "/path/to/file");

}

#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/stat.h>
#include <sys/types.h>

#include <termux/termux_core__nos__c/v1/TermuxCoreLibraryConfig.h>
#include <termux/termux_core__nos__c/v1/data/AssertUtils.h>
#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/termux/file/TermuxFile.h>

#include <termux/termux_core__nos__c/v1/termux/shell/command/environment/termux_core/TermuxCoreShellEnvironment.h>



static const char* LOG_TAG = "ub-tests";


static void init();
static void initLogger();
static void runTests();



#include "data/DataUtils_UnitBinaryTests.c"
#include "unix/file/UnixFileUtils_UnitBinaryTests.c"
#include "termux/file/TermuxFile_UnitBinaryTests.c"



__attribute__((visibility("default")))
int main() {
    init();

    logVVerbose(LOG_TAG, "main()");

    runTests();

    return 0;
}



static void init() {
    errno = 0;

    libtermux_core__nos__c__setIsRunningTests(true);

    initLogger();
}

static void initLogger() {
    setDefaultLogTagAndPrefix("lib" TERMUX__LNAME "-core_c");
    setCurrentLogLevel(termuxCore_tests_logLevel_get());
    setLogFormatMode(LOG_FORMAT_MODE__TAG_AND_MESSAGE);
}



void runTests() {

    logDebug(LOG_TAG, "runTests(start)");

    DataUtils_runTests();
    UnixFileUtils_runTests(); // Should be run before Android/Termux file tests.
    TermuxFile_runTests();

    logDebug(LOG_TAG, "runTests(end)");

}

#define _GNU_SOURCE
#include <errno.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/termux/shell/command/environment/TermuxShellEnvironment.h>
#include <termux/termux_core__nos__c/v1/termux/shell/command/environment/termux_core/TermuxCoreShellEnvironment.h>
#include <termux/termux_core__nos__c/v1/unix/shell/command/environment/UnixShellEnvironment.h>



int termuxCore_tests_logLevel_get() {
    return getLogLevelFromEnv(ENV__TERMUX_CORE__TESTS__LOG_LEVEL);
}

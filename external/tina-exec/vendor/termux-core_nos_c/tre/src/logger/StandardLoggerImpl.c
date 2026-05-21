#define _GNU_SOURCE
#include <stdbool.h>
#include <stdio.h>
#include <unistd.h>

#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/logger/StandardLoggerImpl.h>

const struct ILogger sStandardLoggerImpl = {
    .printMessage = printMessageToStdStream,
};



void printMessageToStdStream(bool logOnStderr, const char *message) {
    fprintf(logOnStderr ? stderr : stdout, "%s", message);

    if (!logOnStderr) {
        fflush(stdout);
    }
}

#define _GNU_SOURCE
#include <errno.h>
#include <regex.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <termux/termux_core__nos__c/v1/data/DataUtils.h>
#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/unix/os/selinux/UnixSeLinuxUtils.h>

bool getSeProcessContextFromEnv(const char* logTag, const char* var, char buffer[], size_t bufferSize) {
    const char* value = getenv(var);
    if (value == NULL) {
        return false;
    }

    size_t valueLength = strlen(value);
    if (valueLength < 1) {
        return false;
    }

    if (regexMatch(value, REGEX__PROCESS_CONTEXT, REG_EXTENDED) != 0) {
        logErrorVVerbose(logTag, "Ignoring invalid se_process_context value set in '%s' env variable: '%s'",
            var, value);
        return false;
    }


    if (bufferSize <= valueLength) {
        logErrorDebug(logTag, "The se_process_context '%s' with length '%zu' is too long to fit in the buffer with size '%zu'",
            value, valueLength, bufferSize);
        errno = EINVAL;
        return -1;
    }

    strcpy(buffer, value);

    return true;
}

bool getSeProcessContextFromFile(const char* logTag, char buffer[], size_t bufferSize) {
    FILE *fp = fopen("/proc/self/attr/current", "r");
    if (fp == NULL) {
        logErrorVVerbose(logTag, "Failed to open '/proc/self/attr/current' to read se_process_context: '%d'", errno);
        return false;
    }

    if (fgets(buffer, bufferSize, fp) != NULL) {
        if (bufferSize > 0 && buffer[bufferSize-1] == '\n') {
            buffer[--bufferSize] = '\0';
        }
    } else {
        fclose(fp);
        logErrorVVerbose(logTag, "Failed to read se_process_context from '/proc/self/attr/current': '%d'", errno);
        return false;
    }

    fclose(fp);
    return true;
}

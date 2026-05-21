#define _GNU_SOURCE
#include <stdarg.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include <termux/termux_core__nos__c/v1/data/AssertUtils.h>

void assertBoolWithError(bool expected, bool actual, const char* logTag, const char *fmt, ...) {
    if (actual != expected) {
        logError(logTag, "FAILED: expected: %s, actual: %s", expected ? "true" : "false", actual ? "true" : "false");
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertBool(bool expected, bool actual, const char* logTag) {
    assertBoolWithError(expected, actual, logTag, NULL);
}



void assertFalseWithError(bool actual, const char* logTag, const char *fmt, ...) {
    if (actual != false) {
        logError(logTag, "FAILED: expected: false, actual: true");
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertFalse(bool actual, const char* logTag) {
    assertFalseWithError(actual, logTag, NULL);
}



void assertTrueWithError(bool actual, const char* logTag, const char *fmt, ...) {
    if (actual != true) {
        logError(logTag, "FAILED: expected: true, actual: false");
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertTrue(bool actual, const char* logTag) {
    assertTrueWithError(actual, logTag, NULL);
}





void assertIntWithError(int expected, int actual, const char* logTag, const char *fmt, ...) {
    if (actual != expected) {
        logError(logTag, "FAILED: expected: %d, actual: %d", expected, actual);
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertInt(int expected, int actual, const char* logTag) {
    assertIntWithError(expected, actual, logTag, NULL);
}





bool stringEqualsRegardingNull(const char *expected, const char *actual) {
    if (expected == NULL) {
        return actual == NULL;
    }

    return actual != NULL && strcmp(actual, expected) == 0;
}


void assertStringEqualsWithError(const char *expected, const char *actual,
    const char* logTag, const char *fmt, ...) {
    if (!stringEqualsRegardingNull(expected, actual)) {
        if (expected == NULL) {
            logError(logTag, "FAILED: expected: null, actual: '%s'", actual);
        } else if (actual == NULL) {
            logError(logTag, "FAILED: expected: '%s', actual: null", expected);
        } else {
            logError(logTag, "FAILED: expected: '%s', actual: '%s'", expected, actual);
        }
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertStringEquals(const char *expected, const char *actual, const char* logTag) {
    assertStringEqualsWithError(expected, actual, logTag, NULL);
}




void assertStringNullWithError(const char *actual, const char* logTag, const char *fmt, ...) {
    if (actual != NULL) {
        logError(logTag, "FAILED: expected: null, actual: '%s'", actual);
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertStringNull(const char *actual, const char* logTag) {
    assertStringNullWithError(actual, logTag, NULL);
}



void assertStringNotNullWithError(const char *actual, const char* logTag, const char *fmt, ...) {
    if (actual == NULL) {
        logError(logTag, "FAILED: expected: not_null, actual: '%s'", actual);
        if (fmt != NULL) {
            va_list args;
            va_start(args, fmt);
            logMessage(LOG_PRIORITY__ERROR, logTag, fmt, args);
            va_end(args);
        }
        exit(1);
    }
}

void assertStringNotNull(const char *actual, const char* logTag) {
    assertStringNotNullWithError(actual, logTag, NULL);
}

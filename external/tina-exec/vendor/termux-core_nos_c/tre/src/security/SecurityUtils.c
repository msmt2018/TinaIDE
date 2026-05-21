#define _GNU_SOURCE
#include <errno.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>

#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/security/SecurityUtils.h>

char * generateUuid(const char* logTag, char *buffer, size_t bufferSize) {
    char uuid[64 + 4 + 1];

    sprintf(uuid, "%x%x-%x-%x-%x-%x%x%x",
        /* 64-bit Hex number */
        arc4random(), arc4random(),
        /* 32-bit Hex number */
        (uint32_t) getpid(),
        /* 32-bit Hex number of the form 4xxx (4 is the UUID version) */
        ((arc4random() & 0x0fff) | 0x4000),
        /* 32-bit Hex number in the range [0x8000, 0xbfff] */
        arc4random() % 0x3fff + 0x8000,
        /* 96-bit Hex number */
        arc4random(), arc4random(), arc4random());

    size_t uuidLength = strlen(uuid);

    if (bufferSize <= uuidLength) {
        logErrorDebug(logTag, "The uuid '%s' with length '%zu' is too long to fit in the buffer with size '%zu'",
            uuid, uuidLength, bufferSize);
        errno = EINVAL;
        return NULL;
    }

    strcpy(buffer, uuid);

    return buffer;
}

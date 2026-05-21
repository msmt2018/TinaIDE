#define _GNU_SOURCE
#include <signal.h>
#include <string.h>

#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/unix/os/process/UnixSignalUtils.h>



sighandler_t setSignalHandler(const char* logTag,
    int signum, sighandler_t saHandler, sigset_t saMask, int saFlags) {
    struct sigaction sa, osa;

    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = saHandler;
    sa.sa_mask = saMask;
    sa.sa_flags = saFlags;

    if (sigaction(signum, &sa, &osa) == -1) {
        logStrerror(logTag, "Failed to set %d signal handler", signum);
        return SIG_ERR;
    }

    return osa.sa_handler;
}

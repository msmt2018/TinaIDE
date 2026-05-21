#include <stdbool.h>

#include <termux/termux_core__nos__c/v1/TermuxCoreLibraryConfig.h>

static bool sIsRunningTests = false;



bool libtermux_core__nos__c__getIsRunningTests() {
    return sIsRunningTests;
}

void libtermux_core__nos__c__setIsRunningTests(bool isRunningTests) {
    sIsRunningTests = isRunningTests;
}

#define _GNU_SOURCE
#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <termux/termux_core__nos__c/v1/data/DataUtils.h>
#include <termux/termux_core__nos__c/v1/unix/shell/command/environment/UnixShellEnvironment.h>



bool getBoolEnvValue(const char *name, bool def) {
    const char* value = getenv(name);
    if (value == NULL || strlen(value) < 1) {
        return def;
    } else if (strcmp(value, "1") == 0 || strcmp(value, "true") == 0 ||
               strcmp(value, "on") == 0 || strcmp(value, "yes") == 0 || strcmp(value, "y") == 0) {
        return true;
    } else if (strcmp(value, "0") == 0 || strcmp(value, "false") == 0 ||
               strcmp(value, "off") == 0 || strcmp(value, "no") == 0 || strcmp(value, "n") == 0) {
        return false;
    }
    return def;
}



bool areVarsInEnv(char *const *envp, const char *vars[], int n) {
    int envLength = 0;
    while (envp[envLength] != NULL) {
        for (int i = 0; i < n; i++) {
            if (stringStartsWith(envp[envLength], vars[i])) {
                return true;
            }
        }
        envLength++;
    }
    return false;
}

bool areEmptyVarsInEnv(char *const *envp, const char *vars[], int n) {
    int envLength = 0;
    while (envp[envLength] != NULL) {
        for (int i = 0; i < n; i++) {
            if (strcmp(envp[envLength], vars[i]) == 0) {
                return true;
            }
        }
        envLength++;
    }
    return false;
}

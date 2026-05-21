#define _GNU_SOURCE
#include <stdarg.h>
#include <unistd.h>

#include <termux/termux_exec__nos__c/v1/termux/api/termux_exec/service/ld_preload/direct/exec/ExecIntercept.h>
#include <termux/termux_exec__nos__c/v1/termux/api/termux_exec/service/ld_preload/direct/exec/ExecVariantsIntercept.h>
#include <termux/termux_exec__nos__c/v1/termux/os/process/termux_exec/TermuxExecProcess.h>

#define TINA_EXEC_DIRECT_VERSION_STRING "libtina_exec_direct_ld_preload version=" TERMUX_EXEC_PKG__VERSION " owner=" TERMUX__REPOS_HOST_ORG_NAME " project=tina-exec"

static void tinaExec_directLdPreload_initProcess(void) {
    termuxExec_process_initProcess(TINA_EXEC_DIRECT_VERSION_STRING, NULL);
}

__attribute__((visibility("default")))
int execl(const char *name, const char *arg, ...) {
    tinaExec_directLdPreload_initProcess();

    va_list ap;
    va_start(ap, arg);
    int result = execlIntercept(true, ExecL, name, arg, ap);
    va_end(ap);
    return result;
}

__attribute__((visibility("default")))
int execlp(const char *name, const char *arg, ...) {
    tinaExec_directLdPreload_initProcess();

    va_list ap;
    va_start(ap, arg);
    int result = execlIntercept(true, ExecLP, name, arg, ap);
    va_end(ap);
    return result;
}

__attribute__((visibility("default")))
int execle(const char *name, const char *arg, ...) {
    tinaExec_directLdPreload_initProcess();

    va_list ap;
    va_start(ap, arg);
    int result = execlIntercept(true, ExecLE, name, arg, ap);
    va_end(ap);
    return result;
}

__attribute__((visibility("default")))
int execv(const char *name, char *const *argv) {
    tinaExec_directLdPreload_initProcess();
    return execvIntercept(true, name, argv);
}

__attribute__((visibility("default")))
int execvp(const char *name, char *const *argv) {
    tinaExec_directLdPreload_initProcess();
    return execvpIntercept(true, name, argv);
}

__attribute__((visibility("default")))
int execvpe(const char *name, char *const *argv, char *const *envp) {
    tinaExec_directLdPreload_initProcess();
    return execvpeIntercept(true, name, argv, envp);
}

__attribute__((visibility("default")))
int fexecve(int fd, char *const *argv, char *const *envp) {
    tinaExec_directLdPreload_initProcess();
    return fexecveIntercept(true, fd, argv, envp);
}

__attribute__((visibility("default")))
int execve(const char *name, char *const argv[], char *const envp[]) {
    tinaExec_directLdPreload_initProcess();
    return execveIntercept(true, name, argv, envp);
}

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/types.h>
#include <sys/wait.h>

#include <linux/limits.h>

#include <termux/termux_core__nos__c/v1/unix/file/UnixFileUtils.h>
#include <termux/termux_core__nos__c/v1/logger/Logger.h>
#include <termux/termux_core__nos__c/v1/unix/os/process/UnixForkUtils.h>



int forkChild(ForkInfo *info) {
    pid_t cpid, w;


    // Create pipe for capturing child stdout and stderr.
    if (pipe(info->pipeFds) == -1) {
        logStrerror(info->parentLogTag, "pipe() failed");
        return -1;
    }

    cpid = fork();
    if (cpid == -1) {
        logStrerror(info->parentLogTag, "fork() failed");
        exit(1);
    }

    // If in child.
    if (cpid == 0) {
        info->isChild = true;

        updateLogPid();

        if (info->onChildFork != NULL) {
            info->onChildFork(info);
        }


        if (redirectStdFdToDevNull(info->redirectChildStdinToDevNull,
                info->redirectChildStdoutToDevNull, info->redirectChildStderrToDevNull) == -1) {
            logStrerror(info->childLogTag, "Child: Failed to redirect std fd to '/dev/null'");
            exitForkWithError(info, 1);
        }


        // Copy original stdout/stderr in case need to restore.
        if ((info->stdoutFd = dup(STDOUT_FILENO)) == -1) {
            logStrerror(info->childLogTag, "Child: Failed to copy stdout fd");
            exitForkWithError(info, 1);
        }

        if ((info->stderrFd = dup(STDERR_FILENO)) == -1) {
            logStrerror(info->childLogTag, "Child: Failed to copy stderr fd");
            exitForkWithError(info, 1);
        }


        // Redirect stdout/stderr to pipe -> send output to parent.
        if (dup2(info->pipeFds[1], STDOUT_FILENO) == -1) {
            logStrerror(info->childLogTag, "Child: Failed to redirect stdout to parent");
            exitForkWithError(info, 1);
        }

        if (dup2(info->pipeFds[1], STDERR_FILENO) == -1) {
            logStrerror(info->childLogTag, "Child: Failed to redirect stderr to parent");
            exitForkWithError(info, 1);
        }


        // Close both pipe ends (pipeFds[0] belongs to parent,
        // pipeFds[1] no longer needed after dup2).
        close(info->pipeFds[0]);
        close(info->pipeFds[1]);
        info->pipeFds[0] = info->pipeFds[1] = -1;

        // Set no buffering for stdout/stderr.
        if (setvbuf(stdout, NULL, _IONBF, 0) != 0) {
            logStrerror(info->childLogTag, "Child: Failed to set no buffering for stdout");
            exitForkWithError(info, 1);
        }
        if (setvbuf(stderr, NULL, _IONBF, 0) != 0) {
            logStrerror(info->childLogTag, "Child: Failed to set no buffering for stderr");
            exitForkWithError(info, 1);
        }

        // Let caller child logic continue.
        return 0;
    }
    // If in parent.
    else {
        info->isChild = false;

        char *buffer = NULL;
        long unsigned int bufferSize;
        long unsigned int bufferData;
        long unsigned int bufferFree;

        // Close child's side of pipe.
        close(info->pipeFds[1]);

        // Open parent's side of pipe for reading.
        info->pipeFile = fdopen(info->pipeFds[0], "r");
        if (info->pipeFile == NULL) {
            logStrerror(info->parentLogTag, "Parent: Failed to open pipe for read child output");
            exitForkWithError(info, 1);
        }

        // Allocate buffer to store child's output.
        bufferSize = info->outputInitialBuffer;
        bufferData = 0;
        bufferFree = bufferSize;
        buffer = (char *) malloc(bufferSize);
        if (buffer == NULL) {
            logStrerror(info->parentLogTag, "Parent: Failed to allocate initial buffer to store child output");
            exitForkWithError(info, 1);
        }

        // Read child output until EOF.
        while (!feof(info->pipeFile)) {
            int n = fread(buffer + bufferData, 1, bufferFree, info->pipeFile);
            if (n <= 0 && ferror(info->pipeFile) != 0) {
                logStrerror(info->parentLogTag, "Parent: Failed to read from pipe of child output");
                exitForkWithError(info, 1);
            }

            bufferData += n;
            bufferFree -= n;
            if (bufferFree <= 0) {
                bufferFree += bufferSize;
                bufferSize *= 2;
                char *newbuf = (char *) realloc(buffer, bufferSize);
                if (newbuf == NULL) {
                    logStrerror(info->parentLogTag, "Parent: Failed to reallocate buffer to store child output");
                    exitForkWithError(info, 1);
                }
                buffer = newbuf;
            }
        }
        if (bufferData > 0 && buffer[bufferData-1] == '\n') // Remove trailing newline.
            bufferData--;
        buffer[bufferData++] = '\0';

        // Resize buffer to final size.
        bufferSize = bufferData;
        bufferFree = 0;
        char *newbuf = (char *) realloc(buffer, bufferSize);
        if (newbuf == NULL) {
            logStrerror(info->parentLogTag, "Parent: Failed to reallocate buffer to store final child output");
            exitForkWithError(info, 1);
        }
        buffer = newbuf;

        // Wait for child to finish.
        // If in parent, wait for child to exit.
        w = waitpid(cpid, &info->status, WUNTRACED | WCONTINUED);
        if (w == -1) {
            logStrerror(info->parentLogTag, "Parent: waitpid() failed");
            exit(1);
        }

        // Close pipe file and pipe.
        fclose(info->pipeFile);
        close(info->pipeFds[0]);

        // Return results using provided references.
        if (info->returnOutput) {
            info->output = buffer;
        } else {
            free(buffer);
        }

        // Let caller parent logic continue.
        info->exitCode = WEXITSTATUS(info->status);
        return 0;
    }
}

void exitForkWithError(ForkInfo *info, int exitCode) {
    cleanupFork(info);
    if (!info->isChild && info->cpid != -1 && info->exitCode == -1) {
        kill(info->cpid, SIGKILL);
    }
    exit(exitCode);
}

void cleanupFork(ForkInfo *info) {
    if (info->stdoutFd != -1) {
        close(info->stdoutFd);
        info->stdoutFd = -1;
    }

    if (info->stderrFd != -1) {
        close(info->stderrFd);
        info->stderrFd = -1;
    }

    if (info->pipeFds[0] != -1) {
        close(info->pipeFds[0]);
        info->pipeFds[0] = -1;
    }

    if (info->pipeFds[1] != -1) {
        close(info->pipeFds[1]);
        info->pipeFds[1] = -1;
    }

    if (info->pipeFile != NULL) {
        fclose(info->pipeFile);
        info->pipeFile = NULL;
    }

    if (info->output != NULL) {
        free(info->output);
        info->output = NULL;
    }
}

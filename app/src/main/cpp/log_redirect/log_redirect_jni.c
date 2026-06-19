#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DEFAULT_LOG_TAG "TINA_USER_OUTPUT"
#define MAX_LOG_TAG_LENGTH 63
#define MAX_LINE_LENGTH 3072

typedef struct {
    int read_fd;
    int priority;
    char tag[MAX_LOG_TAG_LENGTH + 1];
} reader_args_t;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_started = 0;
static int g_original_stdout = -1;
static int g_original_stderr = -1;

static void close_fd(int *fd) {
    if (*fd >= 0) {
        close(*fd);
        *fd = -1;
    }
}

static void flush_line(int priority, const char *tag, char *line, size_t *length) {
    if (*length == 0) return;
    line[*length] = '\0';
    __android_log_write(priority, tag, line);
    *length = 0;
}

static void *reader_thread(void *raw_args) {
    reader_args_t *args = (reader_args_t *)raw_args;
    char buffer[512];
    char line[MAX_LINE_LENGTH + 1];
    size_t line_length = 0;

    for (;;) {
        ssize_t read_count = read(args->read_fd, buffer, sizeof(buffer));
        if (read_count < 0 && errno == EINTR) {
            continue;
        }
        if (read_count <= 0) break;

        for (ssize_t i = 0; i < read_count; ++i) {
            char ch = buffer[i];
            if (ch == '\r') continue;
            if (ch == '\n') {
                flush_line(args->priority, args->tag, line, &line_length);
                continue;
            }
            line[line_length++] = ch;
            if (line_length >= MAX_LINE_LENGTH) {
                flush_line(args->priority, args->tag, line, &line_length);
            }
        }
    }

    flush_line(args->priority, args->tag, line, &line_length);
    close(args->read_fd);
    free(args);
    return NULL;
}

static int create_pipe(int pipe_fd[2]) {
    if (pipe(pipe_fd) != 0) return -1;
    fcntl(pipe_fd[0], F_SETFD, FD_CLOEXEC);
    fcntl(pipe_fd[1], F_SETFD, FD_CLOEXEC);
    return 0;
}

static int start_reader(int read_fd, int priority, const char *tag) {
    reader_args_t *args = (reader_args_t *)calloc(1, sizeof(reader_args_t));
    if (args == NULL) return -1;

    args->read_fd = read_fd;
    args->priority = priority;
    strncpy(args->tag, tag, MAX_LOG_TAG_LENGTH);
    args->tag[MAX_LOG_TAG_LENGTH] = '\0';

    pthread_t thread;
    int result = pthread_create(&thread, NULL, reader_thread, args);
    if (result != 0) {
        free(args);
        return -1;
    }
    pthread_detach(thread);
    return 0;
}

static int redirect_fd(int target_fd, int pipe_fd[2], int priority, const char *tag) {
    if (create_pipe(pipe_fd) != 0) return -1;
    if (start_reader(pipe_fd[0], priority, tag) != 0) {
        close_fd(&pipe_fd[0]);
        close_fd(&pipe_fd[1]);
        return -1;
    }
    if (dup2(pipe_fd[1], target_fd) < 0) {
        close_fd(&pipe_fd[1]);
        return -1;
    }
    close_fd(&pipe_fd[1]);
    return 0;
}

static void restore_streams_locked(void) {
    fflush(stdout);
    fflush(stderr);

    if (g_original_stdout >= 0) {
        dup2(g_original_stdout, STDOUT_FILENO);
        close_fd(&g_original_stdout);
    }
    if (g_original_stderr >= 0) {
        dup2(g_original_stderr, STDERR_FILENO);
        close_fd(&g_original_stderr);
    }
    g_started = 0;
}

JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_ui_runtime_NativeStdStreamRedirect_nativeStart(
    JNIEnv *env,
    jobject thiz,
    jstring tag
) {
    (void)thiz;

    pthread_mutex_lock(&g_lock);
    if (g_started) {
        pthread_mutex_unlock(&g_lock);
        return JNI_TRUE;
    }

    char log_tag[MAX_LOG_TAG_LENGTH + 1] = DEFAULT_LOG_TAG;
    if (tag != NULL) {
        const char *utf_tag = (*env)->GetStringUTFChars(env, tag, NULL);
        if (utf_tag != NULL && utf_tag[0] != '\0') {
            strncpy(log_tag, utf_tag, MAX_LOG_TAG_LENGTH);
            log_tag[MAX_LOG_TAG_LENGTH] = '\0';
        }
        if (utf_tag != NULL) {
            (*env)->ReleaseStringUTFChars(env, tag, utf_tag);
        }
    }

    fflush(stdout);
    fflush(stderr);

    g_original_stdout = dup(STDOUT_FILENO);
    g_original_stderr = dup(STDERR_FILENO);
    if (g_original_stdout < 0 || g_original_stderr < 0) {
        restore_streams_locked();
        pthread_mutex_unlock(&g_lock);
        return JNI_FALSE;
    }

    int stdout_pipe[2] = {-1, -1};
    int stderr_pipe[2] = {-1, -1};
    if (redirect_fd(STDOUT_FILENO, stdout_pipe, ANDROID_LOG_INFO, log_tag) != 0 ||
        redirect_fd(STDERR_FILENO, stderr_pipe, ANDROID_LOG_ERROR, log_tag) != 0) {
        restore_streams_locked();
        pthread_mutex_unlock(&g_lock);
        return JNI_FALSE;
    }

    setvbuf(stdout, NULL, _IOLBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    g_started = 1;
    pthread_mutex_unlock(&g_lock);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_ui_runtime_NativeStdStreamRedirect_nativeStop(
    JNIEnv *env,
    jobject thiz
) {
    (void)env;
    (void)thiz;

    pthread_mutex_lock(&g_lock);
    restore_streams_locked();
    pthread_mutex_unlock(&g_lock);
}

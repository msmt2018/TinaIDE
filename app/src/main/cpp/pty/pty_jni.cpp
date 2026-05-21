#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <termios.h>

namespace {

struct PtyProcess {
    pid_t pid = -1;
    int master_fd = -1;
    bool running = false;
};

int set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) return -1;
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

int open_master() {
    return open("/dev/ptmx", O_RDWR | O_NOCTTY | O_CLOEXEC);
}

int grant_unlock_pts(int master) {
    if (grantpt(master) != 0) return -1;
    if (unlockpt(master) != 0) return -1;
    return 0;
}

int open_slave(int master) {
    char *name = ptsname(master);
    if (!name) return -1;
    return open(name, O_RDWR | O_NOCTTY | O_CLOEXEC);
}

/**
 * 设置终端为规范模式（canonical mode）并启用回显
 * 这是交互式 shell 的标准设置
 */
void setup_terminal(int fd) {
    struct termios tio;
    if (tcgetattr(fd, &tio) < 0) return;

    // 输入模式：启用信号字符、规范模式下的特殊处理
    tio.c_iflag |= ICRNL | IXON | IXANY | IMAXBEL | IUTF8;
    tio.c_iflag &= ~(IXOFF | ISTRIP | INLCR | IGNCR);

    // 输出模式：输出处理、CR 转 NL
    tio.c_oflag |= OPOST | ONLCR;
    tio.c_oflag &= ~(OCRNL | ONOCR | ONLRET);

    // 控制模式
    tio.c_cflag |= CS8 | CREAD;
    tio.c_cflag &= ~(PARENB | CSTOPB);

    // 本地模式：规范模式、回显、信号处理
    tio.c_lflag |= ISIG | ICANON | ECHO | ECHOE | ECHOK | ECHOCTL | ECHOKE | IEXTEN;
    tio.c_lflag &= ~(ECHONL | NOFLSH | TOSTOP);

    // 特殊字符
    tio.c_cc[VINTR] = 0x03;     // Ctrl+C
    tio.c_cc[VQUIT] = 0x1c;     // Ctrl+backslash
    tio.c_cc[VERASE] = 0x7f;    // DEL
    tio.c_cc[VKILL] = 0x15;     // Ctrl+U
    tio.c_cc[VEOF] = 0x04;      // Ctrl+D
    tio.c_cc[VSUSP] = 0x1a;     // Ctrl+Z
    tio.c_cc[VREPRINT] = 0x12;  // Ctrl+R
    tio.c_cc[VWERASE] = 0x17;   // Ctrl+W
    tio.c_cc[VLNEXT] = 0x16;    // Ctrl+V

    tcsetattr(fd, TCSANOW, &tio);
}

char **to_cstr_array(JNIEnv *env, jobjectArray arr, int &out_len) {
    if (!arr) {
        out_len = 0;
        return nullptr;
    }
    jsize len = env->GetArrayLength(arr);
    out_len = static_cast<int>(len);
    char **result = static_cast<char **>(calloc(len + 1, sizeof(char *)));
    for (jsize i = 0; i < len; i++) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        const char *utf = env->GetStringUTFChars(item, nullptr);
        result[i] = strdup(utf);
        env->ReleaseStringUTFChars(item, utf);
        env->DeleteLocalRef(item);
    }
    result[len] = nullptr;
    return result;
}

void free_cstr_array(char **arr) {
    if (!arr) return;
    for (int i = 0; arr[i] != nullptr; i++) {
        free(arr[i]);
    }
    free(arr);
}

bool update_running(PtyProcess *pty) {
    // NOTE:
    // Do NOT call waitpid(..., WNOHANG) here.
    // It would reap the child and make nativeWaitFor() return -1 (ECHILD),
    // and it can also prevent draining remaining PTY output after the child exits.
    if (!pty || !pty->running || pty->pid <= 0) return false;
    if (kill(pty->pid, 0) == 0) return true;
    if (errno == ESRCH) {
        pty->running = false;
        return false;
    }
    // For EPERM or other errors, conservatively treat it as running.
    return true;
}

void destroy_pty(PtyProcess *pty) {
    if (!pty) return;
    if (pty->pid > 0) {
        // kill process group (child is session leader)
        kill(-pty->pid, SIGKILL);
        kill(pty->pid, SIGKILL);
    }
    if (pty->master_fd >= 0) {
        close(pty->master_fd);
        pty->master_fd = -1;
    }
    pty->running = false;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeCreate(
    JNIEnv *env,
    jclass,
    jstring cmd,
    jobjectArray args,
    jobjectArray envp,
    jint rows,
    jint cols
) {
    const char *cmd_cstr = env->GetStringUTFChars(cmd, nullptr);

    int arg_len = 0;
    char **arg_tail = to_cstr_array(env, args, arg_len);

    int env_len = 0;
    char **env_cstr = to_cstr_array(env, envp, env_len);

    int master = open_master();
    if (master < 0) {
        env->ReleaseStringUTFChars(cmd, cmd_cstr);
        free_cstr_array(arg_tail);
        free_cstr_array(env_cstr);
        return 0;
    }
    if (grant_unlock_pts(master) != 0) {
        close(master);
        env->ReleaseStringUTFChars(cmd, cmd_cstr);
        free_cstr_array(arg_tail);
        free_cstr_array(env_cstr);
        return 0;
    }

    int slave = open_slave(master);
    if (slave < 0) {
        close(master);
        env->ReleaseStringUTFChars(cmd, cmd_cstr);
        free_cstr_array(arg_tail);
        free_cstr_array(env_cstr);
        return 0;
    }

    struct winsize ws = {};
    ws.ws_row = static_cast<unsigned short>(rows);
    ws.ws_col = static_cast<unsigned short>(cols);
    ioctl(slave, TIOCSWINSZ, &ws);

    pid_t pid = fork();
    if (pid < 0) {
        close(slave);
        close(master);
        env->ReleaseStringUTFChars(cmd, cmd_cstr);
        free_cstr_array(arg_tail);
        free_cstr_array(env_cstr);
        return 0;
    }

    if (pid == 0) {
        setsid();
        ioctl(slave, TIOCSCTTY, 0);

        // 设置正确的终端属性（回显、规范模式等）
        setup_terminal(slave);

        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);

        if (slave > STDERR_FILENO) close(slave);
        close(master);

        int argv_len = arg_len + 2;
        char **argv = static_cast<char **>(calloc(argv_len, sizeof(char *)));
        argv[0] = strdup(cmd_cstr);
        for (int i = 0; i < arg_len; i++) {
            argv[i + 1] = arg_tail[i] ? strdup(arg_tail[i]) : nullptr;
        }
        argv[arg_len + 1] = nullptr;

        if (env_cstr && env_len > 0) {
            execve(cmd_cstr, argv, env_cstr);
        } else {
            execv(cmd_cstr, argv);
        }
        _exit(127);
    }

    close(slave);
    set_nonblocking(master);

    auto *pty = new PtyProcess();
    pty->pid = pid;
    pty->master_fd = master;
    pty->running = true;

    env->ReleaseStringUTFChars(cmd, cmd_cstr);
    free_cstr_array(arg_tail);
    free_cstr_array(env_cstr);

    return reinterpret_cast<jlong>(pty);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeRead(
    JNIEnv *env,
    jclass,
    jlong ptr,
    jbyteArray buffer,
    jint offset,
    jint length
) {
    auto *pty = reinterpret_cast<PtyProcess *>(ptr);
    if (!pty || pty->master_fd < 0) return -1;
    if (length <= 0) return 0;

    auto *tmp = static_cast<jbyte *>(malloc(static_cast<size_t>(length)));
    if (!tmp) return -1;

    ssize_t n = read(pty->master_fd, tmp, static_cast<size_t>(length));
    if (n == 0) {
        // EOF: PTY slave closed and all buffered data drained.
        free(tmp);
        return -1;
    }
    if (n < 0) {
        int err = errno;
        free(tmp);
        if (err == EAGAIN || err == EWOULDBLOCK) return 0;
        return -1;
    }

    env->SetByteArrayRegion(buffer, offset, static_cast<jsize>(n), tmp);
    free(tmp);
    return static_cast<jint>(n);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeWrite(
    JNIEnv *env,
    jclass,
    jlong ptr,
    jbyteArray data,
    jint offset,
    jint length
) {
    auto *pty = reinterpret_cast<PtyProcess *>(ptr);
    if (!pty || pty->master_fd < 0) return -1;
    if (length <= 0) return 0;

    auto *tmp = static_cast<jbyte *>(malloc(static_cast<size_t>(length)));
    if (!tmp) return -1;
    env->GetByteArrayRegion(data, offset, length, tmp);

    ssize_t n = write(pty->master_fd, tmp, static_cast<size_t>(length));
    free(tmp);

    if (n < 0) {
        int err = errno;
        if (err == EAGAIN || err == EWOULDBLOCK) return 0;
        return -1;
    }
    return static_cast<jint>(n);
}

extern "C" JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeResize(
    JNIEnv *,
    jclass,
    jlong ptr,
    jint rows,
    jint cols
) {
    auto *pty = reinterpret_cast<PtyProcess *>(ptr);
    if (!pty || pty->master_fd < 0) return;

    struct winsize ws = {};
    ws.ws_row = static_cast<unsigned short>(rows);
    ws.ws_col = static_cast<unsigned short>(cols);
    ioctl(pty->master_fd, TIOCSWINSZ, &ws);

    if (pty->pid > 0) {
        kill(-pty->pid, SIGWINCH);
        kill(pty->pid, SIGWINCH);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeWaitFor(
    JNIEnv *,
    jclass,
    jlong ptr
) {
    auto *pty = reinterpret_cast<PtyProcess *>(ptr);
    if (!pty || pty->pid <= 0) return -1;

    int status = 0;
    pid_t r = waitpid(pty->pid, &status, 0);
    if (r != pty->pid) {
        pty->running = false;
        return -1;
    }
    pty->running = false;

    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeDestroy(
    JNIEnv *,
    jclass,
    jlong ptr
) {
    auto *pty = reinterpret_cast<PtyProcess *>(ptr);
    if (!pty) return;
    destroy_pty(pty);
    delete pty;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_terminal_process_PtyProcess_nativeIsRunning(
    JNIEnv *,
    jclass,
    jlong ptr
) {
    auto *pty = reinterpret_cast<PtyProcess *>(ptr);
    return update_running(pty) ? JNI_TRUE : JNI_FALSE;
}

#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/wait.h>

#define TAG "ExecTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_debug_ExecutableTest_testNativeExec(
    JNIEnv* env, jobject /* this */, jstring jPath) {
    
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    
    LOGI("=== Native Exec 测试 ===");
    LOGI("路径: %s", path);
    
    // 测试 1: access() 检查
    if (access(path, F_OK) != 0) {
        LOGE("文件不存在: %s", strerror(errno));
    } else {
        LOGI("✅ 文件存在");
    }
    
    if (access(path, X_OK) != 0) {
        LOGE("❌ 没有执行权限: %s", strerror(errno));
    } else {
        LOGI("✅ 有执行权限");
    }
    
    // 测试 2: fork + execve
    pid_t pid = fork();
    if (pid == 0) {
        // 子进程
        char* argv[] = {(char*)path, (char*)"--version", nullptr};
        char* envp[] = {nullptr};
        
        execve(path, argv, envp);
        
        // 如果到这里，说明 execve 失败了
        LOGE("❌ execve 失败: %s (errno=%d)", strerror(errno), errno);
        _exit(127);
    } else if (pid > 0) {
        // 父进程
        int status;
        waitpid(pid, &status, 0);
        
        if (WIFEXITED(status)) {
            int exitCode = WEXITSTATUS(status);
            if (exitCode == 127) {
                LOGE("❌ 子进程 execve 失败");
            } else {
                LOGI("✅ 子进程执行成功，退出码: %d", exitCode);
            }
        }
    } else {
        LOGE("❌ fork 失败: %s", strerror(errno));
    }
    
    // 测试 3: posix_spawn
    LOGI("测试 posix_spawn...");
    pid_t spawn_pid;
    char* spawn_argv[] = {(char*)path, (char*)"--version", nullptr};
    char* spawn_envp[] = {nullptr};
    
    int spawn_result = posix_spawn(&spawn_pid, path, nullptr, nullptr, 
                                    spawn_argv, spawn_envp);
    
    if (spawn_result != 0) {
        LOGE("❌ posix_spawn 失败: %s (errno=%d)", strerror(spawn_result), spawn_result);
    } else {
        LOGI("✅ posix_spawn 成功，pid=%d", spawn_pid);
        int status;
        waitpid(spawn_pid, &status, 0);
        LOGI("子进程退出码: %d", WEXITSTATUS(status));
    }
    
    env->ReleaseStringUTFChars(jPath, path);
    
    return env->NewStringUTF("测试完成，查看 logcat");
}

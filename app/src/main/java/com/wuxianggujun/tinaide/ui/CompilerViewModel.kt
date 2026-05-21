package com.wuxianggujun.tinaide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wuxianggujun.tinaide.core.compile.BuildSystem
import com.wuxianggujun.tinaide.core.compile.BuildSystemDetector
import com.wuxianggujun.tinaide.core.compile.CompileProjectUseCase
import com.wuxianggujun.tinaide.core.compile.ProcessManager
import com.wuxianggujun.tinaide.core.compile.RunConfigurationManager
import com.wuxianggujun.tinaide.file.IProjectContext
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 编译 ViewModel（按 AI 方案）
 *
 * 负责：
 * - 调用 CompileProjectUseCase 执行编译
 * - 暴露编译进度与结果事件
 * - 支持取消编译与停止进程
 */
class CompilerViewModel(
    private val compileUseCase: CompileProjectUseCase,
    private val projectContext: IProjectContext,
    private val processManager: ProcessManager
) : ViewModel() {

    private val _progress = MutableStateFlow<CompileProjectUseCase.CompileProgress?>(null)
    val progress: StateFlow<CompileProjectUseCase.CompileProgress?> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<CompileEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CompileEvent> = _events.asSharedFlow()

    private var compileJob: Job? = null

    fun compile(
        operation: CompileProjectUseCase.Operation = CompileProjectUseCase.Operation.forRun()
    ) {
        launchOperation {
            compileUseCase.execute(
                operation = operation,
                onProgress = { p -> _progress.value = p }
            )
        }
    }

    fun reconfigureCMake() {
        runCMakeMaintenance(CompileProjectUseCase.Action.CMAKE_RECONFIGURE)
    }

    fun clearCMakeBuildDirectory() {
        runCMakeMaintenance(CompileProjectUseCase.Action.CMAKE_CLEAR_BUILD_DIRECTORY)
    }

    fun clearAndReconfigureCMake() {
        runCMakeMaintenance(CompileProjectUseCase.Action.CMAKE_CLEAR_AND_RECONFIGURE)
    }

    private fun runCMakeMaintenance(action: CompileProjectUseCase.Action) {
        launchOperation {
            compileUseCase.executeCMakeMaintenance(action)
        }
    }

    private fun launchOperation(
        operation: suspend () -> CompileProjectUseCase.Result
    ) {
        compileJob?.cancel()
        val job = viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is CompileProjectUseCase.Result.Success -> {
                        _events.emit(CompileEvent.Success(result.report))
                    }

                    is CompileProjectUseCase.Result.Error -> {
                        _events.emit(
                            CompileEvent.Error(
                                action = result.action,
                                message = result.userMessage,
                                throwable = result.throwable
                            )
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 操作被取消，不发送任何事件
            }
        }
        compileJob = job
        processManager.setCurrentRunJob(job)
    }

    /**
     * 获取当前项目的运行配置管理器
     */
    fun getRunConfigurationManager(): RunConfigurationManager {
        val project = projectContext.getCurrentProject() ?: return RunConfigurationManager()
        return RunConfigurationManager.load(project.rootPath)
    }

    /**
     * 保存运行配置管理器
     */
    fun saveRunConfigurationManager(manager: RunConfigurationManager): Boolean {
        val project = projectContext.getCurrentProject() ?: return false
        return RunConfigurationManager.save(project.rootPath, manager)
    }

    /**
     * 获取当前选中的运行配置
     */
    fun getRunConfiguration() = getRunConfigurationManager().selectedConfig

    /**
     * 获取当前项目的构建系统类型
     */
    fun detectBuildSystem(): BuildSystem {
        val project = projectContext.getCurrentProject() ?: return BuildSystem.UNKNOWN
        return BuildSystemDetector.detect(File(project.rootPath))
    }

    /**
     * 获取当前项目的可用构建目标（仅 CMake 项目有效）
     */
    suspend fun getAvailableTargets() = compileUseCase.getAvailableTargets()

    fun cancelCompile() {
        compileJob?.cancel()
        compileJob = null
    }

    /**
     * 停止当前正在运行的程序
     */
    fun stopRunningProgram() {
        // 先取消编译任务
        compileJob?.cancel()
        compileJob = null
        // 然后停止进程（直接使用 ProcessManager）
        if (processManager.isRunning()) {
            processManager.stopCurrentProcess()
        }
    }

    /**
     * 强制停止当前正在运行的程序
     */
    fun forceStopRunningProgram() {
        // 先取消编译任务
        compileJob?.cancel()
        compileJob = null
        // 然后强制停止进程
        if (processManager.isRunning()) {
            processManager.forceStopCurrentProcess()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 被清理时，取消编译任务
        compileJob?.cancel()
        compileJob = null
        // 注意：不再在这里停止进程，因为用户可能希望进程继续运行
        // 进程会在下次运行时自动停止，或者用户手动停止
    }
}

sealed class CompileEvent {
    data class Success(val report: CompileProjectUseCase.Report) : CompileEvent()
    data class Error(
        val action: CompileProjectUseCase.Action,
        val message: String,
        val throwable: Throwable?
    ) : CompileEvent()
}

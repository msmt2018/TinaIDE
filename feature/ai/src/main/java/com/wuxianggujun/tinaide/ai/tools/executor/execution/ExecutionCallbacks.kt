package com.wuxianggujun.tinaide.ai.tools.executor.execution

/**
 * 执行工具执行器的回调接口
 */
interface ExecutionCallbacks {
    /**
     * 运行项目
     */
    fun runProject(request: RunRequest): ExecutionResult

    /**
     * 运行测试
     */
    fun runTests(request: TestRequest): ExecutionResult

    /**
     * 构建项目
     */
    fun buildProject(request: BuildRequest): ExecutionResult

    /**
     * 停止运行
     */
    fun stopExecution(executionId: String): Boolean

    /**
     * 获取执行状态
     */
    fun getExecutionStatus(executionId: String): ExecutionStatus?

    /**
     * 获取执行输出
     */
    fun getExecutionOutput(executionId: String): ExecutionOutputResult?

    /**
     * 获取构建错误
     * @param executionId 可选的执行ID，如果提供则获取该次构建的错误，否则获取当前所有错误
     */
    fun getBuildErrors(executionId: String? = null): BuildErrorsResult

    /**
     * 跳转到运行输出界面
     */
    fun navigateToRunOutput()

    /**
     * 跳转到构建日志界面
     */
    fun navigateToBuildLog()
}

/**
 * 运行请求
 */
data class RunRequest(
    val configuration: String? = null,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap()
)

/**
 * 测试请求
 */
data class TestRequest(
    val testClass: String? = null,
    val testMethod: String? = null,
    val testPackage: String? = null,
    val arguments: List<String> = emptyList()
)

/**
 * 构建请求
 */
data class BuildRequest(
    val clean: Boolean = false,
    val rebuild: Boolean = false,
    val target: String? = null
)

/**
 * 执行结果
 */
data class ExecutionResult(
    val executionId: String,
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
    val duration: Long,
    val status: ExecutionStatus
)

/**
 * 执行输出结果
 */
data class ExecutionOutputResult(
    val executionId: String,
    val output: String,
    val errorOutput: String,
    val status: ExecutionStatus,
    val exitCode: Int
)

/**
 * 构建错误结果
 */
data class BuildErrorsResult(
    val hasErrors: Boolean,
    val errorCount: Int,
    val warningCount: Int,
    val errors: List<BuildError>
)

/**
 * 构建错误
 */
data class BuildError(
    val file: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val severity: ErrorSeverity
)

/**
 * 错误严重性
 */
enum class ErrorSeverity {
    ERROR,
    WARNING,
    INFO
}

/**
 * 执行状态
 */
enum class ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT
}

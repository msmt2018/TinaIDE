package com.wuxianggujun.tinaide.core.lsp

import android.net.Uri
import android.util.Log
import com.wuxianggujun.tinaide.lsp.NativeLspService
import com.wuxianggujun.tinaide.lsp.model.CompletionResult
import com.wuxianggujun.tinaide.lsp.model.HoverResult
import com.wuxianggujun.tinaide.lsp.model.Location
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 面向编辑器的 Native LSP 请求桥接层。
 *
 * 当前支持 Hover / Definition / References 三种请求，并在内部确保
 * NativeLspService 已初始化、请求串行化以及取消后释放资源。
 */
object NativeLspRequestBridge {

    private const val TAG = "NativeLspRequestBridge"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class WorkerState<T> {
        var job: Job? = null
        var identity: String? = null
        var requestId: Long = 0
        val callbacks = mutableListOf<(T?) -> Unit>()
    }

    private val hoverWorkers = ConcurrentHashMap<String, WorkerState<HoverResult?>>()
    private val completionWorkers = ConcurrentHashMap<String, WorkerState<CompletionResult?>>()
    private val definitionJobs = ConcurrentHashMap<String, Job>()
    private val referenceJobs = ConcurrentHashMap<String, Job>()

    fun requestHover(
        filePath: String,
        line: Int,
        column: Int,
        workDir: String?,
        onResult: (HoverResult?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        scheduleWorker(
            workers = hoverWorkers,
            key = fileUri,
            label = "Hover",
            identity = buildIdentity(filePath, line, column),
            block = {
            if (!ensureNativeClient(workDir)) return@scheduleWorker null
            NativeLspService.requestHoverAsync(fileUri, line, column)
            },
            onResult = onResult
        )
    }

    fun requestCompletion(
        filePath: String,
        line: Int,
        column: Int,
        workDir: String?,
        onResult: (CompletionResult?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        scheduleWorker(
            workers = completionWorkers,
            key = fileUri,
            label = "Completion",
            identity = buildIdentity(filePath, line, column),
            block = {
            if (!ensureNativeClient(workDir)) return@scheduleWorker null
            NativeLspDocumentBridge.flushPendingSync(filePath)
            NativeLspService.requestCompletionAsync(fileUri, line, column)
            },
            onResult = onResult
        )
    }

    fun requestDefinition(
        filePath: String,
        line: Int,
        column: Int,
        workDir: String?,
        onResult: (List<Location>?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        val key = buildKey(fileUri, line, column)
        launchRequest(
            key = key,
            jobs = definitionJobs,
            workDir = workDir,
            methodLabel = "Definition",
            request = { NativeLspService.requestDefinitionAsync(fileUri, line, column) },
            onResult = onResult
        )
    }

    fun requestReferences(
        filePath: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
        workDir: String?,
        onResult: (List<Location>?) -> Unit
    ) {
        val fileUri = buildUri(filePath)
        val key = buildKey(fileUri, line, column)
        launchRequest(
            key = key,
            jobs = referenceJobs,
            workDir = workDir,
            methodLabel = "References",
            request = {
                NativeLspService.requestReferencesAsync(
                    fileUri = fileUri,
                    line = line,
                    character = column,
                    includeDeclaration = includeDeclaration
                )
            },
            onResult = onResult
        )
    }

    private fun buildUri(filePath: String): String = Uri.fromFile(File(filePath)).toString()

    private fun buildKey(fileUri: String, line: Int, column: Int): String =
        "$fileUri:$line:$column"

    private fun buildIdentity(filePath: String, line: Int, column: Int): String {
        val version = NativeLspDocumentBridge.currentVersion(filePath) ?: -1
        return "$line:$column:$version"
    }

    private fun <T> scheduleWorker(
        workers: ConcurrentHashMap<String, WorkerState<T>>,
        key: String,
        label: String,
        identity: String,
        block: suspend () -> T?,
        onResult: (T?) -> Unit
    ) {
        val state = workers.computeIfAbsent(key) { WorkerState<T>() }
        val previousJob: Job?
        val requestId: Long
        synchronized(state) {
            if (state.job?.isActive == true && state.identity == identity) {
                state.callbacks.add(onResult)
                Log.d(TAG, "$label request deduped for key=$key identity=$identity")
                return
            }
            previousJob = state.job
            state.identity = identity
            state.requestId += 1
            requestId = state.requestId
            state.callbacks.clear()
            state.callbacks.add(onResult)
        }
        previousJob?.cancel()
        val job = scope.launch {
            val result = try {
                block()
            } catch (cancelled: CancellationException) {
                Log.d(TAG, "$label request cancelled for key=$key")
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "$label request failed for key=$key", t)
                null
            }
            withContext(Dispatchers.Main) {
                val callbacks = synchronized(state) {
                    if (state.requestId != requestId) {
                        state.callbacks.clear()
                        null
                    } else {
                        state.callbacks.toList().also { state.callbacks.clear() }
                    }
                }
                if (callbacks.isNullOrEmpty()) {
                    Log.d(TAG, "$label result dropped for stale request key=$key id=$requestId")
                    return@withContext
                }
                if (result == null) {
                    Log.d(TAG, "$label result is null for key=$key")
                } else {
                    Log.d(TAG, "$label result ready for key=$key")
                }
                callbacks.forEach { callback -> callback(result) }
            }
        }
        synchronized(state) { state.job = job }
        job.invokeOnCompletion {
            val shouldRemove = synchronized(state) {
                if (state.job === job) {
                    state.job = null
                    state.identity = null
                    state.callbacks.clear()
                    true
                } else {
                    false
                }
            }
            if (shouldRemove) {
                workers.remove(key)
            }
        }
    }

    private fun <T> launchRequest(
        key: String,
        jobs: ConcurrentHashMap<String, Job>,
        workDir: String?,
        methodLabel: String,
        request: suspend () -> T?,
        onResult: (T?) -> Unit
    ) {
        jobs[key]?.cancel()
        jobs[key] = scope.launch {
            try {
                Log.d(TAG, "Launching $methodLabel request key=$key workDir=$workDir")
                if (!ensureNativeClient(workDir)) {
                    Log.w(TAG, "Native client unavailable for $methodLabel key=$key")
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }
                val result = runCatching { request() }
                    .onFailure {
                        if (it !is kotlinx.coroutines.CancellationException) {
                            Log.e(TAG, "Native $methodLabel request failed", it)
                        } else {
                            Log.d(TAG, "Native $methodLabel request cancelled for $key")
                        }
                    }
                    .getOrNull()
                withContext(Dispatchers.Main) {
                    if (result == null) {
                        Log.d(TAG, "$methodLabel result is null for key=$key")
                    } else {
                        Log.d(TAG, "$methodLabel result ready for key=$key")
                    }
                    onResult(result)
                }
            } finally {
                jobs.remove(key)
            }
        }
    }

    private suspend fun ensureNativeClient(workDir: String?): Boolean {
        if (NativeLspService.nativeIsInitialized()) {
            return true
        }
        val initialized = NativeLspService.initialize(workDir = workDir ?: "/")
        if (!initialized) {
            Log.w(TAG, "NativeLspService initialize failed for workDir=$workDir")
        }
        return initialized
    }
}

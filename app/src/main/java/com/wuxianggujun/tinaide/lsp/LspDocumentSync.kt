package com.wuxianggujun.tinaide.lsp

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * LSP 文档同步
 * 
 * 将 CodeEditor 的文本更新同步到 LspService，确保 clangd 拥有最新文档。
 */
object LspDocumentSync {

    private const val TAG = "LspDocumentSync"
    private const val SYNC_DELAY_MS = 300L

    private val sessions = ConcurrentHashMap<String, Session>()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    @Volatile private var hasSeenInitialization = false

    init {
        LspService.addInitializationListener { initialized ->
            if (initialized) {
                if (hasSeenInitialization) {
                    // clangd 重启后，重新发送所有文档
                    sessions.values.forEach { it.resendAfterRestart() }
                } else {
                    hasSeenInitialization = true
                }
            } else {
                hasSeenInitialization = false
            }
        }
    }

    /**
     * 绑定编辑器到 LSP 文档同步
     */
    fun bind(context: Context, editor: CodeEditor, filePath: String, projectPath: String?): Handle? {
        LspHealthMonitor.start(context)
        val absolutePath = File(filePath).absolutePath
        
        val existingSession = sessions[absolutePath]
        if (existingSession != null) {
            Log.d(TAG, "Reusing existing session for $absolutePath")
            return Handle(absolutePath)
        }

        val session = Session(
            context = context.applicationContext,
            editor = editor,
            filePath = absolutePath,
            projectPath = projectPath ?: File(absolutePath).parent
        )

        sessions[absolutePath] = session
        session.start()
        return Handle(absolutePath)
    }

    /**
     * 解绑文档
     */
    fun unbind(filePath: String) {
        val absolutePath = File(filePath).absolutePath
        sessions.remove(absolutePath)?.dispose()
    }

    /**
     * 立即同步待处理的更改
     */
    suspend fun flushPendingSync(filePath: String) {
        val absolutePath = File(filePath).absolutePath
        sessions[absolutePath]?.flushPendingSync()
    }

    /**
     * 获取当前文档版本
     */
    fun currentVersion(filePath: String): Int? {
        val absolutePath = File(filePath).absolutePath
        return sessions[absolutePath]?.currentVersion()
    }

    /**
     * 文档句柄
     */
    class Handle internal constructor(private val key: String) {
        fun dispose() = unbind(key)
    }

    /**
     * 文档会话
     */
    private class Session(
        private val context: Context,
        private val editor: CodeEditor,
        private val filePath: String,
        private val projectPath: String?
    ) {
        private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val fileUri = Uri.fromFile(File(filePath)).toString()
        private var subscription: SubscriptionReceipt<ContentChangeEvent>? = null
        private var pendingSync: Job? = null
        
        @Volatile private var opened = false
        @Volatile private var disposed = false
        @Volatile private var version = 1
        @Volatile private var lastSnapshot: String? = null
        
        private val clangdPath: String? = LspBinaryResolver.resolve(context).also { resolved ->
            if (!resolved.isNullOrBlank()) {
                LspService.setClangdBinary(resolved)
            }
        }

        fun start() {
            if (clangdPath.isNullOrBlank()) {
                Log.w(TAG, "clangd binary not found, LSP features disabled for $filePath")
                return
            }
            
            workerScope.launch {
                if (!ensureInitialized()) {
                    Log.w(TAG, "LSP not available, skip $filePath")
                    return@launch
                }
                
                val content = readEditorText()
                Log.d(TAG, "Document opened: $fileUri")
                
                val result = runCatching { LspService.didOpenDocument(fileUri, content) }
                if (result.isFailure) {
                    Log.e(TAG, "Failed to send didOpen", result.exceptionOrNull())
                    return@launch
                }
                
                lastSnapshot = content
                LspRequestDispatcher.notifyDocumentChange(filePath)
                opened = true
                registerListeners()
                Log.i(TAG, "LSP synced document: $filePath")
            }
        }

        private suspend fun registerListeners() {
            withContext(Dispatchers.Main) {
                subscription = editor.subscribeEvent<ContentChangeEvent> { _, _ ->
                    scheduleSync()
                }
            }
        }

        private fun scheduleSync() {
            if (disposed || !opened) return
            pendingSync?.cancel()
            pendingSync = workerScope.launch {
                delay(SYNC_DELAY_MS)
                sendSnapshot()
            }
        }

        suspend fun flushPendingSync() {
            if (disposed || !opened) return
            pendingSync?.cancel()
            sendSnapshot()
            delay(100) // 给 clangd 时间处理
        }

        fun resendAfterRestart() {
            if (disposed || !opened) return
            workerScope.launch {
                if (!ensureInitialized()) {
                    Log.w(TAG, "LSP not available when resending $fileUri")
                    return@launch
                }
                
                val snapshot = lastSnapshot ?: readEditorText()
                Log.d(TAG, "Resending snapshot after restart: $fileUri")
                
                val result = runCatching { LspService.didOpenDocument(fileUri, snapshot) }
                if (result.isFailure) {
                    Log.e(TAG, "Failed to resend didOpen for $fileUri", result.exceptionOrNull())
                    return@launch
                }
                
                version = 1
                lastSnapshot = snapshot
                LspRequestDispatcher.notifyDocumentChange(filePath)
            }
        }

        private suspend fun sendSnapshot() {
            val snapshot = readEditorText()
            if (snapshot == lastSnapshot) {
                Log.d(TAG, "No content changes for $fileUri, skip didChange")
                return
            }
            
            val nextVersion = ++version
            Log.d(TAG, "Document changed: $fileUri, version=$nextVersion")
            
            val result = runCatching { LspService.didChangeDocument(fileUri, snapshot, nextVersion) }
            if (result.isFailure) {
                Log.e(TAG, "Failed to send didChange", result.exceptionOrNull())
            } else {
                lastSnapshot = snapshot
                LspRequestDispatcher.notifyDocumentChange(filePath)
            }
        }

        private suspend fun readEditorText(): String = withContext(Dispatchers.Main) {
            editor.text.toString()
        }

        fun currentVersion(): Int? = if (opened && !disposed) version else null

        private suspend fun ensureInitialized(): Boolean {
            if (LspService.isInitialized) return true
            
            val workDir = projectPath ?: "/"
            val requestedClangdPath = clangdPath 
                ?: LspService.getClangdBinary() 
                ?: LspService.defaultClangdPath()
                
            Log.i(TAG, "LSP initializing: clangd=$requestedClangdPath, workDir=$workDir")
            val result = LspService.initialize(clangdPath = requestedClangdPath, workDir = workDir)
            
            if (result) {
                Log.i(TAG, "LSP initialized successfully")
            } else {
                Log.e(TAG, "LSP initialization failed")
            }
            return result
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            pendingSync?.cancel()
            
            subscription?.let { receipt ->
                mainScope.launch { receipt.unsubscribe() }
            }
            
            workerScope.launch {
                if (opened) {
                    Log.d(TAG, "Document closed: $fileUri")
                    runCatching { LspService.didCloseDocument(fileUri) }
                        .onFailure { Log.w(TAG, "Failed to send didClose", it) }
                }
            }.invokeOnCompletion {
                workerScope.cancel()
            }
            
            LspResultCache.invalidateFile(filePath)
        }
    }
}

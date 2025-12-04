package com.wuxianggujun.tinaide.core.lsp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log
import com.wuxianggujun.tinaide.lsp.NativeLspService

/**
 * 监听 Native LSP 运行状况并在必要时给出即时反馈，帮助定位 transport/clangd 异常。
 */
object NativeLspHealthMonitor : NativeLspService.HealthListener {

    private const val TAG = "NativeLspHealthMonitor"

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var appContext: Context? = null

    fun start(context: Context) {
        if (appContext != null) {
            return
        }
        appContext = context.applicationContext
        NativeLspService.addHealthListener(this)
    }

    override fun onHealthEvent(event: NativeLspService.HealthEvent) {
        val ctx = appContext ?: return
        val toastText = when (event.type) {
            NativeLspService.HealthEventType.INIT_FAILURE ->
                "Native LSP 初始化失败：${event.message}"
            NativeLspService.HealthEventType.CHANNEL_ERROR ->
                "Native LSP 通信异常：${event.message}"
            NativeLspService.HealthEventType.TRANSPORT_ERROR ->
                "Native LSP 传输异常：${event.message}"
            NativeLspService.HealthEventType.CLANGD_EXIT ->
                "clangd 进程已退出：${event.message}"
        }
        Log.w(TAG, "Health event ${event.type}: ${event.message}")
        mainHandler.post {
            Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
        }
    }
}

package com.wuxianggujun.tinaide.output

import android.content.Context
import android.content.Intent
import com.wuxianggujun.tinaide.core.ServiceLocator
import com.wuxianggujun.tinaide.core.config.IConfigManager
import com.wuxianggujun.tinaide.core.config.ConfigKeys
import com.wuxianggujun.tinaide.core.get
import java.util.concurrent.CopyOnWriteArrayList
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 输出管理器实现
 */
class OutputManager(private val context: Context) : IOutputManager {

    private val bufferLock = Any()
    private val channelBuffers = EnumMap<IOutputManager.OutputChannel, StringBuilder>(IOutputManager.OutputChannel::class.java)
    private val maxBufferChars = 1024 * 1024 // 1MB 级别的字符数
    private val listeners = CopyOnWriteArrayList<IOutputManager.OutputListener>()
    private var outputMode = IOutputManager.OutputMode.ACTIVITY
    
    init {
        // 从配置读取输出模式
        try {
            val configManager = ServiceLocator.get<IConfigManager>()
            val savedMode = configManager.get(ConfigKeys.OutputMode)
            outputMode = IOutputManager.OutputMode.valueOf(savedMode)
        } catch (e: Exception) {
            outputMode = IOutputManager.OutputMode.ACTIVITY
        }
    }
    
    override fun appendOutput(text: String, channel: IOutputManager.OutputChannel) {
        synchronized(bufferLock) {
            val buffer = channelBuffers.getOrPut(channel) { StringBuilder() }
            buffer.append(text)
            if (buffer.length > maxBufferChars) {
                val keepStart = buffer.length - maxBufferChars / 2
                buffer.delete(0, maxOf(0, keepStart))
            }
        }
        listeners.forEach { it.onOutputAppended(text, channel) }
    }
    
    override fun clearOutput(channel: IOutputManager.OutputChannel) {
        synchronized(bufferLock) {
            channelBuffers[channel]?.setLength(0)
        }
        listeners.forEach { it.onOutputCleared(channel) }
    }
    
    override fun getOutput(channel: IOutputManager.OutputChannel): String {
        synchronized(bufferLock) {
            return channelBuffers[channel]?.toString() ?: ""
        }
    }
    
    override fun setOutputMode(mode: IOutputManager.OutputMode) {
        outputMode = mode
        // 保存到配置
        try {
            val configManager = ServiceLocator.get<IConfigManager>()
            configManager.set(ConfigKeys.OutputMode, mode.name)
        } catch (e: Exception) {
            // 忽略配置保存失败
        }
    }
    
    override fun getOutputMode(): IOutputManager.OutputMode {
        return outputMode
    }
    
    override fun showOutput() {
        when (outputMode) {
            IOutputManager.OutputMode.ACTIVITY -> launchOutputActivity()
            IOutputManager.OutputMode.BOTTOM_PANEL -> {
                // 由底部面板自行消费
            }
        }
    }

    private fun launchOutputActivity() {
        val intent = Intent(context, OutputActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

        if (OutputActivityVisibilityTracker.isVisible()) {
            // Activity 已经在前台，仅需 bringToFront
            context.startActivity(intent)
            return
        }

        if (!OutputActivityVisibilityTracker.tryLaunch()) {
            // 已经有一次启动在途，忽略本次请求避免重复创建
            return
        }

        context.startActivity(intent)
    }
    
    override fun addOutputListener(listener: IOutputManager.OutputListener) {
        listeners.add(listener)
    }
    
    override fun removeOutputListener(listener: IOutputManager.OutputListener) {
        listeners.remove(listener)
    }
    
    companion object {
        private const val TAG = "OutputManager"
        
        @Volatile
        private var logTextView: LogTextView? = null
        
        fun setLogView(view: LogTextView?) {
            logTextView = view
        }
        
        fun appendLog(level: LogLevel, message: String) {
            logTextView?.appendLog(level, message)
        }

        internal fun notifyOutputActivityStarted() {
            OutputActivityVisibilityTracker.onActivityStarted()
        }

        internal fun notifyOutputActivityDestroyed() {
            OutputActivityVisibilityTracker.onActivityDestroyed()
        }
    }
}

private object OutputActivityVisibilityTracker {
    private val visible = AtomicBoolean(false)
    private val launchInFlight = AtomicBoolean(false)

    fun tryLaunch(): Boolean = launchInFlight.compareAndSet(false, true)

    fun isVisible(): Boolean = visible.get()

    fun onActivityStarted() {
        visible.set(true)
        launchInFlight.set(false)
    }

    fun onActivityDestroyed() {
        visible.set(false)
        launchInFlight.set(false)
    }
}

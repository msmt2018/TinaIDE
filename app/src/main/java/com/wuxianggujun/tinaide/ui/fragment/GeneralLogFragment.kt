package com.wuxianggujun.tinaide.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.wuxianggujun.tinaide.R
import com.wuxianggujun.tinaide.databinding.TabGeneralLogBinding
import com.wuxianggujun.tinaide.lsp.LspService
import com.wuxianggujun.tinaide.output.LogLevel
import com.wuxianggujun.tinaide.ui.BottomLogBuffer

/**
 * 通用日志 Fragment
 *
 * 功能：
 * - 显示 LSP 和其他系统日志
 * - 显示 LSP 连接状态
 * - 支持暂停/恢复、清除、过滤日志
 */
class GeneralLogFragment : Fragment() {

    private var _binding: TabGeneralLogBinding? = null
    private val binding get() = _binding!!

    private var logListener: BottomLogBuffer.LogListener? = null
    private var initListener: LspService.InitializationListener? = null
    private var healthListener: LspService.HealthListener? = null

    /** 日志是否暂停 */
    private var isPaused = false

    companion object {
        fun newInstance(): GeneralLogFragment {
            return GeneralLogFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TabGeneralLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupLspStatus()
        bindLogs()
    }

    override fun onResume() {
        super.onResume()
        // 当 Fragment 可见时，通知 LogTextView 刷新待处理的日志
        if (!isPaused) {
            _binding?.generalLogView?.onBecomeVisible()
        }
    }

    override fun onPause() {
        super.onPause()
        // 当 Fragment 不可见时，暂停 UI 更新
        _binding?.generalLogView?.onBecomeInvisible()
    }

    private fun setupToolbar() {
        // 暂停/恢复按钮
        binding.btnPause.setOnClickListener {
            togglePause()
        }

        // 清空日志
        binding.btnClear.setOnClickListener {
            clearLog()
        }

        // 过滤日志
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }
    }

    /**
     * 切换暂停/恢复状态
     */
    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            // 暂停：更换图标为播放，暂停日志更新
            binding.btnPause.setIconResource(R.drawable.ic_play)
            binding.btnPause.contentDescription = "恢复"
            _binding?.generalLogView?.onBecomeInvisible()
        } else {
            // 恢复：更换图标为暂停，恢复日志更新
            binding.btnPause.setIconResource(R.drawable.ic_pause)
            binding.btnPause.contentDescription = "暂停"
            _binding?.generalLogView?.onBecomeVisible()
        }
    }

    /**
     * 显示过滤对话框
     */
    private fun showFilterDialog() {
        // TODO: 实现日志过滤对话框
        // 可以按日志级别（INFO/WARN/ERROR）或关键字过滤
        binding.generalLogView.appendLog(LogLevel.INFO, "过滤功能开发中...")
    }

    private fun setupLspStatus() {
        initListener = LspService.InitializationListener { isInitialized ->
            binding.root.post {
                val message = if (isInitialized) "LSP 已连接" else "LSP 未连接"
                updateLspStatus(isInitialized, message)
            }
        }

        healthListener = LspService.HealthListener { type, message ->
            binding.root.post {
                updateLspStatus(false, "LSP 异常: $message")
            }
        }

        initListener?.let { LspService.addInitializationListener(it) }
        healthListener?.let { LspService.addHealthListener(it) }

        // 初始化状态
        val initialized = LspService.isInitialized
        val message = if (initialized) "LSP 已连接" else "LSP 未连接"
        updateLspStatus(initialized, message)
    }

    private fun bindLogs() {
        logListener = BottomLogBuffer.LogListener { entry ->
            if (!isPaused) {
                binding.generalLogView.post {
                    binding.generalLogView.appendLog(entry.level, entry.timestamp, entry.tag, entry.message)
                }
            }
        }
        logListener?.let { listener ->
            BottomLogBuffer.replayTo(listener)
            BottomLogBuffer.addListener(listener)
        }
    }

    /**
     * 追加日志
     */
    fun appendLog(level: LogLevel, message: String) {
        if (!isPaused) {
            binding.generalLogView.appendLog(level, message)
        }
    }

    /**
     * 清空日志
     */
    fun clearLog() {
        BottomLogBuffer.clear()
        binding.generalLogView.clearLog()
    }

    /**
     * 获取日志内容
     */
    fun getLogContent(): String {
        return binding.generalLogView.getLogContent()
    }

    /**
     * 更新 LSP 状态
     */
    fun updateLspStatus(connected: Boolean, message: String) {
        val context = binding.root.context
        val color = if (connected) {
            ContextCompat.getColor(context, R.color.lspStatusConnected)
        } else {
            ContextCompat.getColor(context, R.color.lspStatusDisconnected)
        }

        binding.lspStatusIndicator.backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)
        binding.tvLspStatus.text = message
    }

    /**
     * 通知可见性变化（由 BottomPanelManager 调用）
     * 当底部面板收起时暂停日志刷新，展开时恢复
     */
    fun notifyVisibilityChanged(visible: Boolean) {
        if (visible && !isPaused) {
            _binding?.generalLogView?.onBecomeVisible()
        } else {
            _binding?.generalLogView?.onBecomeInvisible()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        logListener?.let { BottomLogBuffer.removeListener(it) }
        logListener = null

        initListener?.let { LspService.removeInitializationListener(it) }
        healthListener?.let { LspService.removeHealthListener(it) }

        _binding = null
    }
}

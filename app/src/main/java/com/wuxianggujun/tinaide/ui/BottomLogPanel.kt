package com.wuxianggujun.tinaide.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.wuxianggujun.tinaide.R
import com.wuxianggujun.tinaide.databinding.BottomSheetLogPanelBinding
import com.wuxianggujun.tinaide.lsp.LspDebugPanel
import com.wuxianggujun.tinaide.lsp.NativeLspService
import com.wuxianggujun.tinaide.output.LogLevel
import com.wuxianggujun.tinaide.output.OutputManager

/**
 * 底部日志面板管理器
 * 
 * 功能：
 * - 可拖动展开/收起
 * - 显示编译日志和 LSP 调试日志
 * - 工具栏快捷操作
 * - LSP 状态指示
 */
class BottomLogPanel(
    private val container: ViewGroup,
    private val onCompile: () -> Unit = {},
    private val onStop: () -> Unit = {}
) {
    
    private val binding: BottomSheetLogPanelBinding
    private val bottomSheetBehavior: BottomSheetBehavior<*>
    
    init {
        // 加载布局
        binding = BottomSheetLogPanelBinding.inflate(
            LayoutInflater.from(container.context),
            container,
            true
        )
        
        // 初始化 BottomSheet 行为
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet).apply {
            peekHeight = 56.dpToPx()
            isHideable = false
            isFitToContents = false
            halfExpandedRatio = 0.5f
            state = BottomSheetBehavior.STATE_COLLAPSED
        }
        
        setupToolbar()
        setupLspStatus()
        
        // 将日志输出重定向到面板
        OutputManager.setLogView(binding.logView)
    }
    
    private fun setupToolbar() {
        binding.btnCompile.setOnClickListener {
            onCompile()
            expand()
        }
        
        binding.btnStop.setOnClickListener {
            onStop()
        }
        
        binding.btnClearLog.setOnClickListener {
            binding.logView.clearLog()
        }
        
        binding.btnSaveLog.setOnClickListener {
            // TODO: 实现保存日志功能
            binding.logView.appendLog(LogLevel.INFO, "保存日志功能开发中...")
        }
        
        // 点击拖动手柄切换展开/收起
        binding.dragHandle.setOnClickListener {
            toggle()
        }
    }
    
    private fun setupLspStatus() {
        // 监听 LSP 健康状态
        NativeLspService.addHealthListener(NativeLspService.HealthListener { event ->
            updateLspStatus(
                connected = false,
                message = "LSP Error: ${event.message}"
            )
        })
        
        // 初始状态
        if (NativeLspService.nativeIsInitialized()) {
            updateLspStatus(connected = true, message = "LSP 已连接")
        } else {
            updateLspStatus(connected = false, message = "LSP 未连接")
        }
    }
    
    fun updateLspStatus(connected: Boolean, message: String) {
        val context = binding.root.context
        val color = if (connected) {
            ContextCompat.getColor(context, R.color.lsp_status_connected)
        } else {
            ContextCompat.getColor(context, R.color.lsp_status_disconnected)
        }
        
        binding.lspStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        binding.tvLspStatus.text = message
    }
    
    fun appendLog(level: LogLevel, message: String) {
        binding.logView.appendLog(level, message)
    }
    
    fun clearLog() {
        binding.logView.clearLog()
    }
    
    fun expand() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    
    fun collapse() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
    
    fun halfExpand() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
    }
    
    fun toggle() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> halfExpand()
            BottomSheetBehavior.STATE_HALF_EXPANDED -> expand()
            BottomSheetBehavior.STATE_EXPANDED -> collapse()
            else -> collapse()
        }
    }
    
    fun isExpanded(): Boolean {
        return bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
    }
    
    private fun Int.dpToPx(): Int {
        val density = container.context.resources.displayMetrics.density
        return (this * density).toInt()
    }
}

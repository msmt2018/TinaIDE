package com.wuxianggujun.tinaide.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wuxianggujun.tinaide.databinding.TabBuildLogBinding
import com.wuxianggujun.tinaide.output.LogLevel

/**
 * 构建日志 Fragment
 *
 * 功能：
 * - 显示 C++ 编译过程的日志
 * - 无工具栏（编译/停止等操作由 BottomPanelManager 统一管理）
 */
class BuildLogFragment : Fragment() {

    private var _binding: TabBuildLogBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(): BuildLogFragment {
            return BuildLogFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TabBuildLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        _binding?.buildLogView?.onBecomeVisible()
    }

    override fun onPause() {
        super.onPause()
        _binding?.buildLogView?.onBecomeInvisible()
    }

    /**
     * 追加日志
     */
    fun appendLog(level: LogLevel, message: String) {
        binding.buildLogView.appendLog(level, message)
    }

    /**
     * 清空日志
     */
    fun clearLog() {
        binding.buildLogView.clearLog()
    }

    /**
     * 获取日志内容
     */
    fun getLogContent(): String {
        return binding.buildLogView.getLogContent()
    }

    /**
     * 通知可见性变化（由 BottomPanelManager 调用）
     * 当底部面板收起时暂停日志刷新，展开时恢复
     */
    fun notifyVisibilityChanged(visible: Boolean) {
        if (visible) {
            _binding?.buildLogView?.onBecomeVisible()
        } else {
            _binding?.buildLogView?.onBecomeInvisible()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

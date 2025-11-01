package com.wuxianggujun.tinaide

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.wuxianggujun.tinaide.termux.BootstrapInstaller
import java.io.File
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 适配系统栏内边距
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 查找 TerminalView 并绑定最小实现的 Client
        val terminalView = findViewById<TerminalView>(R.id.terminal_view)
        terminalView.setTerminalViewClient(object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: android.view.MotionEvent) {}
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent): Boolean = false
            override fun onLongPress(event: android.view.MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        })

        // 构建一个最小 TerminalSessionClient，用于刷新绘制等回调
        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.invalidate()
            }
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession?) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) { terminalView.invalidate() }
            override fun onTerminalCursorStateChange(state: Boolean) { terminalView.invalidate() }
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String, message: String) {}
            override fun logWarn(tag: String, message: String) {}
            override fun logInfo(tag: String, message: String) {}
            override fun logDebug(tag: String, message: String) {}
            override fun logVerbose(tag: String, message: String) {}
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
            override fun logStackTrace(tag: String, e: Exception) {}
        }

        // 安装/检测离线 Termux 环境（assets/bootstrap 下放置对应 ABI 的 bootstrap 包）
        val install = BootstrapInstaller.installIfNeeded(this)
        if (!install.installed) {
            Toast.makeText(this, install.message ?: "Missing bootstrap in assets/bootstrap", Toast.LENGTH_LONG).show()
        }
        // 准备 Termux 环境变量与 shell 路径
        val shellPath = BootstrapInstaller.resolveShell(this)
        val cwd = File(filesDir, "home").absolutePath
        val args = arrayOf("-l")
        val env = BootstrapInstaller.buildEnv(this)

        val session = TerminalSession(
            shellPath,
            cwd,
            args,
            env,
            /* transcriptRows = */ 10000,
            sessionClient
        )

        terminalView.attachSession(session)
    }
}

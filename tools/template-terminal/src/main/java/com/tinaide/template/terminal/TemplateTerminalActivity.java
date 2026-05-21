package com.tinaide.template.terminal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.tinaide.template.common.TemplatePermissionFlow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 终端模板 Activity：从 assets 提取可执行文件，设置最小运行环境后拉起 Termux 终端。
 */
public final class TemplateTerminalActivity extends Activity {

    private static final String TAG = "TemplateTerminal";
    private static final String EXECUTABLE_ASSET_PATH = "tinaide_terminal/executable.bin";
    private static final String EXECUTABLE_FILE_NAME = "entry.bin";
    private static final String APP_NAME_PLACEHOLDER_PREFIX = "TINAIDE_APP_NAME_PLACEHOLDER";
    private static final int CURSOR_BLINK_RATE = 600;
    private static final int COLOR_BACKGROUND = Color.parseColor("#090B0D");
    private static final int COLOR_TOOLBAR_BACKGROUND = Color.parseColor("#111418");
    private static final int COLOR_DIVIDER = Color.parseColor("#20252B");
    private static final int COLOR_TERMINAL_BACKGROUND = Color.BLACK;
    private static final int COLOR_TITLE = Color.parseColor("#F3F6F8");
    private static final int COLOR_HINT = Color.parseColor("#94A0AA");
    private static final int COLOR_STATUS_RUNNING_TEXT = Color.parseColor("#D9FBE8");
    private static final int COLOR_STATUS_RUNNING_BACKGROUND = Color.parseColor("#183126");
    private static final int COLOR_STATUS_RUNNING_STROKE = Color.parseColor("#2B7A57");
    private static final int COLOR_STATUS_FINISHED_TEXT = Color.parseColor("#FFE8C2");
    private static final int COLOR_STATUS_FINISHED_BACKGROUND = Color.parseColor("#382413");
    private static final int COLOR_STATUS_FINISHED_STROKE = Color.parseColor("#B9782B");

    private TemplatePermissionFlow permissionFlow;
    private TerminalView terminalView;
    private TerminalSession terminalSession;
    private LinearLayout toolbarView;
    private TextView toolbarTitleView;
    private TextView toolbarSubtitleView;
    private TextView toolbarStatusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_TERMINAL_BACKGROUND);

        permissionFlow = new TemplatePermissionFlow(this);

        try {
            File executableFile = stageExecutable();
            TemplateTerminalViewClient viewClient = new TemplateTerminalViewClient(this);
            terminalView = createTerminalView(viewClient);
            View contentView = createContentView(terminalView);
            setContentView(contentView);
            contentView.requestApplyInsets();
            updateToolbarState(true);

            terminalSession = createTerminalSession(executableFile);
            terminalView.attachSession(terminalSession);
            permissionFlow.advance("onCreate");
        } catch (Exception error) {
            Log.e(TAG, "Failed to start terminal template", error);
            Toast.makeText(
                this,
                getString(R.string.terminal_template_launch_failed, resolveErrorMessage(error)),
                Toast.LENGTH_LONG
            ).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionFlow.advance("onResume");
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerState(true, false);
        }
    }

    @Override
    protected void onPause() {
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerState(false, false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (terminalSession != null) {
            terminalSession.finishIfRunning();
        }
        super.onDestroy();
    }

    private TerminalView createTerminalView(TemplateTerminalViewClient viewClient) {
        TerminalView view = new TerminalView(this, null);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setKeepScreenOn(true);
        view.setTextSize(Math.round(14f * getResources().getDisplayMetrics().scaledDensity));
        // Termux TerminalView 会在这里通过 mClient 打日志，必须先挂上 client 再设闪烁速率。
        viewClient.attach(view);
        view.setTerminalViewClient(viewClient);
        view.setTerminalCursorBlinkerRate(CURSOR_BLINK_RATE);
        view.setBackgroundColor(COLOR_TERMINAL_BACKGROUND);
        return view;
    }

    private LinearLayout createContentView(TerminalView view) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setFitsSystemWindows(false);

        root.addView(createToolbarView(), createToolbarLayoutParams());
        root.addView(createDividerView());
        root.addView(createTerminalContainer(view), createTerminalLayoutParams());
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            if (toolbarView != null) {
                toolbarView.setPadding(dp(16), topInset + dp(10), dp(16), dp(10));
            }
            v.setPadding(0, 0, 0, bottomInset);
            return insets;
        });
        return root;
    }

    private LinearLayout.LayoutParams createToolbarLayoutParams() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private View createToolbarView() {
        toolbarView = new LinearLayout(this);
        LinearLayout toolbar = toolbarView;
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setBackgroundColor(COLOR_TOOLBAR_BACKGROUND);
        toolbar.setPadding(dp(16), dp(10), dp(16), dp(10));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setLayoutParams(
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        );

        toolbarTitleView = new TextView(this);
        toolbarTitleView.setText(resolveToolbarTitle());
        toolbarTitleView.setTextColor(COLOR_TITLE);
        toolbarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        toolbarTitleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        toolbarTitleView.setSingleLine(true);
        toolbarTitleView.setEllipsize(TextUtils.TruncateAt.END);

        toolbarSubtitleView = new TextView(this);
        toolbarSubtitleView.setTextColor(COLOR_HINT);
        toolbarSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        toolbarSubtitleView.setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL);
        toolbarSubtitleView.setSingleLine(true);
        toolbarSubtitleView.setEllipsize(TextUtils.TruncateAt.END);
        toolbarSubtitleView.setPadding(0, dp(3), 0, 0);

        titleColumn.addView(toolbarTitleView);
        titleColumn.addView(toolbarSubtitleView);

        toolbarStatusView = new TextView(this);
        toolbarStatusView.setGravity(Gravity.CENTER);
        toolbarStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        toolbarStatusView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        toolbarStatusView.setPadding(dp(10), dp(5), dp(10), dp(5));
        toolbarStatusView.setMinWidth(dp(68));
        topRow.addView(titleColumn);
        topRow.addView(toolbarStatusView);

        toolbar.addView(topRow);
        return toolbar;
    }

    private View createDividerView() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_DIVIDER);
        divider.setLayoutParams(
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        );
        return divider;
    }

    private FrameLayout createTerminalContainer(TerminalView view) {
        FrameLayout container = new FrameLayout(this);
        container.setBackground(createTerminalContainerBackground());
        container.addView(
            view,
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        );
        return container;
    }

    private LinearLayout.LayoutParams createTerminalLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        );
        return params;
    }

    private TerminalSession createTerminalSession(File executableFile) {
        File homeDir = new File(getFilesDir(), "terminal-home");
        if (!homeDir.exists() && !homeDir.mkdirs()) {
            throw new IllegalStateException("Failed to create home directory");
        }

        String[] command = buildCommand(executableFile);
        String[] env = buildEnvironment(homeDir);
        return new TerminalSession(
            command[0],
            homeDir.getAbsolutePath(),
            command,
            env,
            null,
            new TemplateTerminalSessionClient(this)
        );
    }

    private File stageExecutable() throws IOException {
        File payloadDir = new File(getFilesDir(), "terminal-payload");
        if (!payloadDir.exists() && !payloadDir.mkdirs()) {
            throw new IOException("Failed to create payload directory");
        }

        File outputFile = new File(payloadDir, EXECUTABLE_FILE_NAME);
        try (
            InputStream input = getAssets().open(EXECUTABLE_ASSET_PATH);
            FileOutputStream output = new FileOutputStream(outputFile, false)
        ) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        }

        outputFile.setReadable(true, true);
        outputFile.setWritable(true, true);
        outputFile.setExecutable(true, true);
        try {
            Os.chmod(outputFile.getAbsolutePath(), 0700);
        } catch (Exception error) {
            Log.w(TAG, "chmod failed, fallback to File#setExecutable", error);
        }
        return outputFile;
    }

    private String[] buildCommand(File executableFile) {
        if (isShebangScript(executableFile)) {
            return new String[] {
                "/system/bin/sh",
                executableFile.getAbsolutePath()
            };
        }
        return new String[] {
            resolveSystemLinker(),
            executableFile.getAbsolutePath()
        };
    }

    private String[] buildEnvironment(File homeDir) {
        List<String> environment = new ArrayList<>();
        environment.add("HOME=" + homeDir.getAbsolutePath());
        environment.add("PWD=" + homeDir.getAbsolutePath());
        environment.add("TMPDIR=" + getCacheDir().getAbsolutePath());
        environment.add("PATH=/system/bin:/system/xbin");
        environment.add("TERM=xterm-256color");
        environment.add("LANG=C.UTF-8");
        environment.add(
            "LD_LIBRARY_PATH=" + getApplicationInfo().nativeLibraryDir + ":/system/lib64:/system/lib"
        );
        return environment.toArray(new String[0]);
    }

    private String resolveSystemLinker() {
        String[] candidates = new String[] {
            "/system/bin/linker64",
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker",
            "/apex/com.android.runtime/bin/linker"
        };
        for (String candidate : candidates) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return "/system/bin/linker64";
    }

    private boolean isShebangScript(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.read() == '#'
                && input.read() == '!';
        } catch (IOException ignored) {
            return false;
        }
    }

    private String resolveErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private GradientDrawable createTerminalContainerBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_TERMINAL_BACKGROUND);
        return background;
    }

    private GradientDrawable createStatusBackground(boolean running) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(999));
        background.setColor(
            running ? COLOR_STATUS_RUNNING_BACKGROUND : COLOR_STATUS_FINISHED_BACKGROUND
        );
        background.setStroke(
            dp(1),
            running ? COLOR_STATUS_RUNNING_STROKE : COLOR_STATUS_FINISHED_STROKE
        );
        return background;
    }

    private void updateToolbarState(boolean running) {
        if (toolbarTitleView == null || toolbarSubtitleView == null || toolbarStatusView == null) {
            return;
        }
        toolbarTitleView.setText(resolveToolbarTitle());
        toolbarSubtitleView.setText(
            running
                ? R.string.terminal_template_toolbar_hint_running
                : R.string.terminal_template_toolbar_hint_finished
        );
        toolbarStatusView.setText(
            running
                ? R.string.terminal_template_status_running
                : R.string.terminal_template_status_finished
        );
        toolbarStatusView.setTextColor(
            running ? COLOR_STATUS_RUNNING_TEXT : COLOR_STATUS_FINISHED_TEXT
        );
        toolbarStatusView.setBackground(createStatusBackground(running));
    }

    private String resolveToolbarTitle() {
        CharSequence label = getApplicationInfo().loadLabel(getPackageManager());
        String title = label != null ? label.toString().trim() : "";
        if (title.isEmpty() || title.startsWith(APP_NAME_PLACEHOLDER_PREFIX)) {
            return getString(R.string.terminal_template_default_title);
        }
        return title;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class TemplateTerminalSessionClient implements TerminalSessionClient {
        private final Context context;
        private final ClipboardManager clipboardManager;

        TemplateTerminalSessionClient(Context context) {
            this.context = context;
            clipboardManager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        }

        @Override
        public void onTextChanged(TerminalSession changedSession) {}

        @Override
        public void onTitleChanged(TerminalSession changedSession) {}

        @Override
        public void onSessionFinished(TerminalSession finishedSession) {
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    updateToolbarState(false);
                }
            });
        }

        @Override
        public void onCopyTextToClipboard(TerminalSession session, String text) {
            if (text == null) return;
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Terminal", text));
        }

        @Override
        public void onPasteTextFromClipboard(TerminalSession session) {
            if (session == null) return;
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() <= 0) return;
            CharSequence text = clipData.getItemAt(0).coerceToText(context);
            if (text != null && session.getEmulator() != null) {
                session.getEmulator().paste(text.toString());
            }
        }

        @Override
        public void onBell(TerminalSession session) {}

        @Override
        public void onColorsChanged(TerminalSession session) {}

        @Override
        public void onSessionCustomOsc(TerminalSession session, int code, String text) {}

        @Override
        public void onTerminalCursorStateChange(boolean state) {}

        @Override
        public void setTerminalShellPid(TerminalSession session, int pid) {}

        @Override
        public Integer getTerminalCursorStyle() {
            return 0;
        }

        @Override
        public void logError(String tag, String message) {
            Log.e(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logWarn(String tag, String message) {
            Log.w(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logInfo(String tag, String message) {
            Log.i(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logDebug(String tag, String message) {
            Log.d(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logVerbose(String tag, String message) {
            Log.v(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logStackTraceWithMessage(String tag, String message, Exception error) {
            Log.e(tag != null ? tag : TAG, message != null ? message : "", error);
        }

        @Override
        public void logStackTrace(String tag, Exception error) {
            Log.e(tag != null ? tag : TAG, "Exception", error);
        }
    }

    private static final class TemplateTerminalViewClient implements TerminalViewClient {
        private final InputMethodManager inputMethodManager;
        private TerminalView terminalView;

        TemplateTerminalViewClient(Context context) {
            inputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        }

        void attach(TerminalView view) {
            terminalView = view;
        }

        @Override
        public float onScale(float scale) {
            return scale;
        }

        @Override
        public void onSingleTapUp(MotionEvent e) {
            if (terminalView == null) return;
            terminalView.requestFocus();
            inputMethodManager.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
        }

        @Override
        public boolean shouldBackButtonBeMappedToEscape() {
            return false;
        }

        @Override
        public boolean shouldEnforceCharBasedInput() {
            return false;
        }

        @Override
        public boolean shouldUseCtrlSpaceWorkaround() {
            return false;
        }

        @Override
        public boolean isTerminalViewSelected() {
            return true;
        }

        @Override
        public void copyModeChanged(boolean copyMode) {}

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
            return false;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent e) {
            return false;
        }

        @Override
        public boolean onLongPress(MotionEvent event) {
            return false;
        }

        @Override
        public boolean readControlKey() {
            return false;
        }

        @Override
        public boolean readAltKey() {
            return false;
        }

        @Override
        public boolean readShiftKey() {
            return false;
        }

        @Override
        public boolean readFnKey() {
            return false;
        }

        @Override
        public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
            return false;
        }

        @Override
        public void onEmulatorSet() {
            if (terminalView != null) {
                terminalView.setTerminalCursorBlinkerState(true, true);
            }
        }

        @Override
        public void logError(String tag, String message) {
            Log.e(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logWarn(String tag, String message) {
            Log.w(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logInfo(String tag, String message) {
            Log.i(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logDebug(String tag, String message) {
            Log.d(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logVerbose(String tag, String message) {
            Log.v(tag != null ? tag : TAG, message != null ? message : "");
        }

        @Override
        public void logStackTraceWithMessage(String tag, String message, Exception error) {
            Log.e(tag != null ? tag : TAG, message != null ? message : "", error);
        }

        @Override
        public void logStackTrace(String tag, Exception error) {
            Log.e(tag != null ? tag : TAG, "Exception", error);
        }
    }
}

package com.wuxianggujun.tinaide.ui.gui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wuxianggujun.tinaide.core.compile.GuiOrientation
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.ui.compose.components.FloatingOverlay
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaOverlayPanelSurface
import com.wuxianggujun.tinaide.ui.compose.components.TinaPanelSegmentButton
import com.wuxianggujun.tinaide.ui.runtime.NativeLaunchEnvironment
import com.wuxianggujun.tinaide.ui.theme.TinaIDETheme
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * GUI 宿主：加载用户共享库，并轮询 `tina_gui_render_argb32` 输出图像帧。
 */
class GuiHostActivity : ComponentActivity() {
    private var launchEnvironmentOwnerId: String? = null

    companion object {
        const val EXTRA_LIBRARY_PATH = "extra_library_path"
        const val EXTRA_PRELOAD_LIBRARY_PATHS = "extra_preload_library_paths"
        const val EXTRA_GUI_ORIENTATION = "extra_gui_orientation"
        const val EXTRA_ENABLE_FLOATING_LOG = "extra_enable_floating_log"

        fun createIntent(
            context: Context,
            libraryPath: String,
            preloadLibraryPaths: List<String> = emptyList(),
            guiOrientation: GuiOrientation = GuiOrientation.AUTO,
            enableFloatingLog: Boolean = false,
            launchEnvironment: Map<String, String> = emptyMap(),
        ): Intent = Intent(context, GuiHostActivity::class.java).apply {
            putExtra(EXTRA_LIBRARY_PATH, libraryPath)
            putStringArrayListExtra(
                EXTRA_PRELOAD_LIBRARY_PATHS,
                ArrayList(preloadLibraryPaths)
            )
            putExtra(EXTRA_GUI_ORIENTATION, guiOrientation.name)
            putExtra(EXTRA_ENABLE_FLOATING_LOG, enableFloatingLog)
            NativeLaunchEnvironment.putIntoIntent(this, launchEnvironment)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val libraryPath = intent.getStringExtra(EXTRA_LIBRARY_PATH).orEmpty().trim()
        val preloadLibraryPaths = intent.getStringArrayListExtra(EXTRA_PRELOAD_LIBRARY_PATHS)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
        val libraryValidationError = validateLibraryPath(libraryPath)
        if (libraryValidationError != null) {
            Toast.makeText(this, libraryValidationError, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        launchEnvironmentOwnerId = "${javaClass.simpleName}@${System.identityHashCode(this)}"
        NativeLaunchEnvironment.apply(
            ownerId = launchEnvironmentOwnerId!!,
            environment = NativeLaunchEnvironment.readFromIntent(intent),
        )
        applyGuiOrientation()

        val enableFloatingLog = intent.getBooleanExtra(EXTRA_ENABLE_FLOATING_LOG, false)

        setContent {
            TinaIDETheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    GuiHostScreen(
                        libraryPath = libraryPath,
                        preloadLibraryPaths = preloadLibraryPaths,
                        onBack = { finish() }
                    )
                    FloatingOverlay(
                        enableFloatingLog = enableFloatingLog,
                        onExit = { finish() }
                    )
                }
            }
        }
    }

    private fun validateLibraryPath(libraryPath: String): String? {
        if (libraryPath.isBlank()) {
            return Strings.gui_host_error_missing_library.strOr(this)
        }
        val libraryFile = File(libraryPath)
        if (!libraryFile.isFile) {
            return Strings.sdl_runtime_error_main_library_invalid.strOr(this, libraryPath)
        }
        if (!libraryFile.name.endsWith(".so", ignoreCase = true)) {
            return Strings.gui_runtime_invalid_shared_library.strOr(this, libraryPath)
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        launchEnvironmentOwnerId?.let(NativeLaunchEnvironment::clear)
        launchEnvironmentOwnerId = null
    }

    private fun applyGuiOrientation() {
        val orientationName = intent.getStringExtra(EXTRA_GUI_ORIENTATION) ?: return
        val orientation = runCatching { GuiOrientation.valueOf(orientationName) }
            .getOrDefault(GuiOrientation.AUTO)
        requestedOrientation = when (orientation) {
            GuiOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            GuiOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            GuiOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
}

@Composable
private fun GuiHostScreen(
    libraryPath: String,
    preloadLibraryPaths: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var renderSize by remember { mutableStateOf(IntSize(640, 360)) }
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frameVersion by remember { mutableIntStateOf(0) }
    var fps by remember { mutableStateOf(0f) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var libraryLoaded by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    DisposableEffect(Unit) {
        onDispose {
            GuiRuntimeBridge.unloadLibrary()
        }
    }

    LaunchedEffect(libraryPath, preloadLibraryPaths) {
        errorText = null
        val preloadError = preloadLibraryPaths.firstNotNullOfOrNull { preloadPath ->
            runCatching {
                System.load(preloadPath)
                null
            }.getOrElse { throwable ->
                throwable.message ?: throwable.javaClass.simpleName
            }
        }
        if (preloadError != null) {
            errorText = preloadError
            libraryLoaded = false
            return@LaunchedEffect
        }

        val loadError = GuiRuntimeBridge.loadLibrary(libraryPath)
        if (loadError != null) {
            errorText = loadError
            libraryLoaded = false
        } else {
            libraryLoaded = true
        }
    }

    LaunchedEffect(libraryLoaded, renderSize) {
        if (!libraryLoaded) return@LaunchedEffect

        val width = renderSize.width.coerceIn(1, 1280)
        val height = renderSize.height.coerceIn(1, 720)
        val pixels = IntArray(width * height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        frameBitmap = bitmap
        frameVersion = 0

        var frameCount = 0
        var windowStart = SystemClock.elapsedRealtime()

        while (isActive) {
            val ok = GuiRuntimeBridge.renderArgb32(width, height, pixels)
            if (!ok) {
                errorText = GuiRuntimeBridge.lastError()
                    ?: Strings.gui_host_status_error.strOr(context)
                break
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            frameVersion += 1
            frameCount += 1

            val now = SystemClock.elapsedRealtime()
            if (now - windowStart >= 1000L) {
                fps = frameCount * 1000f / (now - windowStart).coerceAtLeast(1L)
                frameCount = 0
                windowStart = now
            }
            delay(16L)
        }
    }

    val isError = !errorText.isNullOrBlank()
    val isLoading = !isError && frameBitmap == null
    val statusText = when {
        isError -> stringResource(Strings.gui_host_status_error)
        isLoading -> stringResource(Strings.gui_host_status_loading)
        else -> stringResource(Strings.gui_host_status_running)
    }
    val statusContainerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
        isLoading -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
    }
    val statusContentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isLoading -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        TinaDialogContentColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TinaCustomDialogHeader(
                title = stringResource(Strings.gui_host_title),
                leadingContent = {
                    GuiHostActionButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.btn_cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                trailingContent = {
                    GuiHostStatusBadge(
                        text = statusText,
                        containerColor = statusContainerColor,
                        contentColor = statusContentColor
                    )
                }
            )

            GuiHostSectionSurface {
                Text(
                    text = stringResource(Strings.gui_host_library_path_label, libraryPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isError) {
                    val bitmap = frameBitmap
                    Text(
                        text = if (bitmap == null) {
                            stringResource(Strings.gui_host_status_loading)
                        } else {
                            stringResource(
                                Strings.gui_host_frame_stats,
                                String.format(Locale.US, "%.1f", fps),
                                bitmap.width,
                                bitmap.height
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bitmap == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            if (isError) {
                GuiHostSectionSurface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
                ) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(Strings.gui_host_render_contract_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                    )
                }
            }

            TinaOverlayPanelSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.Black,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .onSizeChanged { size ->
                            if (size.width > 0 && size.height > 0) {
                                renderSize = size
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = frameBitmap
                    when {
                        isError -> {
                            GuiHostRenderStateCard(
                                title = stringResource(Strings.gui_host_status_error),
                                description = errorText.orEmpty(),
                                supportingText = stringResource(Strings.gui_host_render_contract_hint),
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        bitmap == null -> {
                            GuiHostRenderStateCard(
                                title = stringResource(Strings.gui_host_status_loading),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                showLoading = true
                            )
                        }

                        else -> {
                            AndroidView(
                                factory = { viewContext ->
                                    @android.annotation.SuppressLint("ClickableViewAccessibility")
                                    val view = ImageView(viewContext).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        isFocusable = true
                                        isFocusableInTouchMode = true
                                        setOnTouchListener { _, event ->
                                            val action = when (event.actionMasked) {
                                                MotionEvent.ACTION_DOWN,
                                                MotionEvent.ACTION_POINTER_DOWN -> 0
                                                MotionEvent.ACTION_UP,
                                                MotionEvent.ACTION_POINTER_UP -> 1
                                                MotionEvent.ACTION_MOVE -> 2
                                                else -> return@setOnTouchListener false
                                            }
                                            val pointerIndex = event.actionIndex
                                            GuiRuntimeBridge.sendTouchEvent(
                                                action = action,
                                                x = event.getX(pointerIndex),
                                                y = event.getY(pointerIndex),
                                                pointerId = event.getPointerId(pointerIndex)
                                            )
                                            true
                                        }
                                        setOnKeyListener { _, keyCode, event ->
                                            val keyAction = when (event.action) {
                                                KeyEvent.ACTION_DOWN -> 0
                                                KeyEvent.ACTION_UP -> 1
                                                else -> return@setOnKeyListener false
                                            }
                                            GuiRuntimeBridge.sendKeyEvent(keyCode, keyAction)
                                            true
                                        }
                                    }
                                    view
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { imageView ->
                                    frameVersion
                                    imageView.setImageBitmap(bitmap)
                                    imageView.contentDescription =
                                        context.getString(Strings.gui_host_image_content_desc)
                                    imageView.invalidate()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuiHostActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    TinaPanelSegmentButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        minHeight = 36.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(0.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun GuiHostStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

@Composable
private fun GuiHostSectionSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    TinaOverlayPanelSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        TinaDialogContentColumn(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun GuiHostRenderStateCard(
    title: String,
    description: String? = null,
    supportingText: String? = null,
    containerColor: Color,
    contentColor: Color,
    showLoading: Boolean = false
) {
    GuiHostSectionSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        color = containerColor,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = contentColor,
                    strokeWidth = 3.dp
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )

            description?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            supportingText?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

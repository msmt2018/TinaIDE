package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface GuiPreviewManualPathOutcome {
    data object EmptyPath : GuiPreviewManualPathOutcome

    data class FileMissing(
        val normalizedPath: String
    ) : GuiPreviewManualPathOutcome

    data class ReadyToLoad(
        val normalizedPath: String
    ) : GuiPreviewManualPathOutcome
}

internal object GuiPreviewTestScreenSupport {
    fun validateManualPath(
        input: String,
        fileExists: (String) -> Boolean
    ): GuiPreviewManualPathOutcome {
        val normalizedPath = input.trim()
        if (normalizedPath.isEmpty()) {
            return GuiPreviewManualPathOutcome.EmptyPath
        }
        if (!fileExists(normalizedPath)) {
            return GuiPreviewManualPathOutcome.FileMissing(normalizedPath)
        }
        return GuiPreviewManualPathOutcome.ReadyToLoad(normalizedPath)
    }
}

/**
 * GUI 图像预览测试页面（开发者选项）
 *
 * 可用于快速验证 SDL3/其他 GUI 库渲染结果（图片形式）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuiPreviewTestScreen(onNavigateBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val navigateBack = onNavigateBack ?: {
        (context as? ComponentActivity)?.finish()
        Unit
    }
    val coroutineScope = rememberCoroutineScope()

    var manualPath by rememberSaveable { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun showBitmap(bitmap: Bitmap?, source: String) {
        if (bitmap == null) {
            errorText = context.getString(Strings.gui_preview_test_error_decode_failed)
            return
        }
        previewBitmap = bitmap
        sourceText = source
        errorText = null
    }

    fun loadImage(sourceLabel: String, loader: suspend () -> Bitmap?) {
        coroutineScope.launch {
            isLoading = true
            errorText = null
            val result = runCatching {
                withContext(Dispatchers.IO) { loader() }
            }
            isLoading = false
            result.onSuccess { bitmap ->
                showBitmap(bitmap, sourceLabel)
            }.onFailure { throwable ->
                val reason = throwable.message
                    ?: throwable::class.simpleName
                    ?: context.getString(Strings.gui_preview_test_error_unknown)
                errorText = context.getString(Strings.gui_preview_test_error_load_failed, reason)
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        loadImage(
            sourceLabel = context.getString(
                Strings.gui_preview_test_source_document,
                uri.lastPathSegment ?: uri.toString()
            ),
            loader = { loadBitmapFromUri(context, uri) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Strings.gui_preview_test_title)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.gui_preview_test_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Strings.gui_preview_test_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { pickImageLauncher.launch(arrayOf("image/*")) }
                ) {
                    Text(stringResource(Strings.gui_preview_test_pick_image))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        loadImage(
                            sourceLabel = context.getString(Strings.gui_preview_test_source_demo),
                            loader = { createDemoGuiBitmap() }
                        )
                    }
                ) {
                    Text(stringResource(Strings.gui_preview_test_generate_demo))
                }
            }

            OutlinedTextField(
                value = manualPath,
                onValueChange = { manualPath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Strings.gui_preview_test_path_label)) },
                placeholder = { Text(stringResource(Strings.gui_preview_test_path_placeholder)) },
                singleLine = true
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when (
                        val outcome = GuiPreviewTestScreenSupport.validateManualPath(
                            input = manualPath,
                            fileExists = { File(it).exists() }
                        )
                    ) {
                        GuiPreviewManualPathOutcome.EmptyPath -> {
                            errorText = context.getString(Strings.gui_preview_test_error_empty_path)
                        }
                        is GuiPreviewManualPathOutcome.FileMissing -> {
                            errorText = context.getString(Strings.gui_preview_test_error_file_not_found)
                        }
                        is GuiPreviewManualPathOutcome.ReadyToLoad -> {
                            loadImage(
                                sourceLabel = context.getString(
                                    Strings.gui_preview_test_source_path,
                                    outcome.normalizedPath
                                ),
                                loader = { loadBitmapFromPath(outcome.normalizedPath) }
                            )
                        }
                    }
                }
            ) {
                Text(stringResource(Strings.gui_preview_test_load_path))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> CircularProgressIndicator()
                        previewBitmap != null -> Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(Strings.gui_preview_test_image_content_desc),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        else -> Text(
                            text = stringResource(Strings.gui_preview_test_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            previewBitmap?.let { bitmap ->
                Text(
                    text = stringResource(
                        Strings.gui_preview_test_image_info,
                        sourceText ?: "-",
                        bitmap.width,
                        bitmap.height
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = context.contentResolver.openInputStream(uri).use { input ->
    if (input == null) {
        throw IOException("Input stream is null")
    }
    BitmapFactory.decodeStream(input)
}

private fun loadBitmapFromPath(path: String): Bitmap? = BitmapFactory.decodeFile(path)

private fun createDemoGuiBitmap(width: Int = 1280, height: Int = 720): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 30, 43)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

    val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(39, 51, 72)
    }
    canvas.drawRoundRect(32f, 32f, width - 32f, height - 32f, 24f, 24f, panelPaint)

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(77, 148, 255)
    }
    canvas.drawRoundRect(56f, 56f, width - 56f, 136f, 16f, 16f, headerPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        isFakeBoldText = true
    }
    canvas.drawText("SDL3 / GUI Preview", 84f, 108f, titlePaint)

    val chartAxisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(136, 156, 190)
        strokeWidth = 5f
    }
    canvas.drawLine(110f, height - 140f, width - 110f, height - 140f, chartAxisPaint)
    canvas.drawLine(110f, height - 140f, 110f, 220f, chartAxisPaint)

    val plotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(79, 226, 164)
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }
    val points = floatArrayOf(
        140f, height - 200f,
        280f, height - 260f,
        420f, height - 240f,
        560f, height - 330f,
        700f, height - 300f,
        840f, height - 390f,
        980f, height - 360f,
        1120f, height - 430f
    )
    for (index in 0 until points.size - 2 step 2) {
        canvas.drawLine(
            points[index],
            points[index + 1],
            points[index + 2],
            points[index + 3],
            plotPaint
        )
    }

    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 204, 102)
    }
    for (index in points.indices step 2) {
        canvas.drawCircle(points[index], points[index + 1], 9f, dotPaint)
    }

    val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 186, 215)
        textSize = 30f
    }
    canvas.drawText("Render FPS: 60", 84f, height - 70f, statusPaint)
    canvas.drawText("Backend: SDL3", width - 350f, height - 70f, statusPaint)

    return bitmap
}

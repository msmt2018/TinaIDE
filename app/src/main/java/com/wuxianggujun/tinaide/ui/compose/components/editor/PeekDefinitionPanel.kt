package com.wuxianggujun.tinaide.ui.compose.components.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.lsp.LocationItem
import com.wuxianggujun.tinaide.ui.compose.state.editor.PeekDefinitionPanelState
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PEEK_CONTEXT_BEFORE_LINES = 4
private const val PEEK_CONTEXT_AFTER_LINES = 10
private const val PEEK_MAX_LINE_LENGTH = 260

@Composable
internal fun PeekDefinitionPanel(
    panelState: PeekDefinitionPanelState,
    onLocationSelected: (LocationItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locations = panelState.locations
    var selectedIndex by remember(panelState.ownerTabId, locations) { mutableIntStateOf(0) }
    val selectedLocation = locations.getOrNull(selectedIndex)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PeekDefinitionHeader(
                title = panelState.title,
                selectedIndex = selectedIndex,
                locationsCount = locations.size,
                onPrevious = {
                    selectedIndex = (selectedIndex - 1).floorMod(locations.size)
                },
                onNext = {
                    selectedIndex = (selectedIndex + 1).floorMod(locations.size)
                },
                onDismiss = onDismiss
            )

            when {
                panelState.isLoading -> PeekDefinitionLoading()
                locations.isEmpty() -> PeekDefinitionMessage(stringResource(Strings.lsp_no_results))
                selectedLocation != null -> {
                    PeekDefinitionLocationMeta(selectedLocation)
                    PeekDefinitionCodeSnippet(selectedLocation)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onLocationSelected(selectedLocation) }) {
                            Text(stringResource(Strings.btn_goto))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekDefinitionHeader(
    title: String,
    selectedIndex: Int,
    locationsCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (locationsCount > 0) {
                Text(
                    text = stringResource(Strings.lsp_location_found_count, locationsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (locationsCount > 1) {
            Text(
                text = "${selectedIndex + 1}/$locationsCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Strings.wizard_btn_previous)
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(Strings.wizard_btn_next)
                )
            }
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Strings.btn_close)
            )
        }
    }
}

@Composable
private fun PeekDefinitionLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(Strings.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeekDefinitionMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeekDefinitionLocationMeta(location: LocationItem) {
    val line = (location.line + 1).coerceAtLeast(1)
    val column = (location.column + 1).coerceAtLeast(1)
    Text(
        text = "${location.fileName}  $line:$column",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PeekDefinitionCodeSnippet(location: LocationItem) {
    val snippetState by produceState<PeekSnippetState>(PeekSnippetState.Loading, location) {
        value = withContext(Dispatchers.IO) { buildPeekSnippet(location) }
    }

    when (val snippet = snippetState) {
        PeekSnippetState.Loading -> PeekDefinitionLoading()
        PeekSnippetState.Error -> PeekDefinitionMessage(stringResource(Strings.editor_load_failed))
        is PeekSnippetState.Success -> PeekDefinitionCodeLines(snippet.lines)
    }
}

@Composable
private fun PeekDefinitionCodeLines(lines: List<PeekCodeLine>) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(codeBackground)
            .verticalScroll(verticalScroll)
            .horizontalScroll(horizontalScroll)
            .padding(vertical = 8.dp)
    ) {
        lines.forEach { line ->
            PeekDefinitionCodeLine(line)
        }
    }
}

@Composable
private fun PeekDefinitionCodeLine(line: PeekCodeLine) {
    val rowBackground = if (line.isTarget) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .background(rowBackground)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = (line.number + 1).toString().padStart(4),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = line.text.ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

private fun buildPeekSnippet(location: LocationItem): PeekSnippetState {
    val file = File(location.filePath)
    if (!file.isFile) return PeekSnippetState.Error

    val targetEndLine = max(location.line, location.endLine)
    val startLine = (location.line - PEEK_CONTEXT_BEFORE_LINES).coerceAtLeast(0)
    val endLine = targetEndLine + PEEK_CONTEXT_AFTER_LINES
    val maxLines = (endLine - startLine + 1).coerceAtLeast(1)
    val charset = runCatching { FileEncodingDetector.detectCharset(file) }.getOrDefault(Charsets.UTF_8)

    val lines = runCatching {
        file.bufferedReader(charset).useLines { sequence ->
            sequence
                .drop(startLine)
                .take(maxLines)
                .mapIndexed { offset, text ->
                    val lineNumber = startLine + offset
                    PeekCodeLine(
                        number = lineNumber,
                        text = text.trimEnd().limitCodeLineLength(),
                        isTarget = lineNumber in location.line..targetEndLine
                    )
                }
                .toList()
        }
    }.getOrElse {
        return PeekSnippetState.Error
    }

    return if (lines.isEmpty()) {
        PeekSnippetState.Error
    } else {
        PeekSnippetState.Success(lines)
    }
}

private fun String.limitCodeLineLength(): String {
    if (length <= PEEK_MAX_LINE_LENGTH) return this
    return take(PEEK_MAX_LINE_LENGTH) + "…"
}

private fun Int.floorMod(modulus: Int): Int {
    if (modulus <= 0) return 0
    return ((this % modulus) + modulus) % modulus
}

private sealed interface PeekSnippetState {
    object Loading : PeekSnippetState
    object Error : PeekSnippetState
    data class Success(val lines: List<PeekCodeLine>) : PeekSnippetState
}

private data class PeekCodeLine(
    val number: Int,
    val text: String,
    val isTarget: Boolean
)

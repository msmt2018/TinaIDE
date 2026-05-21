package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.lsp.LocationItem
import java.io.File
import kotlinx.coroutines.delay

/**
 * 位置列表对话框
 *
 * 用于展示 Go to Definition / Find References / Go to Implementation 等导航结果。
 * 单结果时由调用方直接跳转，多结果时弹出此对话框供用户选择。
 */
@Composable
fun LocationListDialog(
    title: String,
    locations: List<LocationItem>,
    isLoading: Boolean,
    onLocationClick: (LocationItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val expandedByFile = remember { mutableStateMapOf<String, Boolean>() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isLoading, locations.isNotEmpty()) {
        if (!isLoading && locations.isNotEmpty()) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val filteredLocations by remember(locations, query) {
        derivedStateOf {
            val needle = query.trim()
            if (needle.isBlank()) {
                locations
            } else {
                locations.filter { item ->
                    item.fileName.contains(needle, ignoreCase = true) ||
                        item.filePath.contains(needle, ignoreCase = true) ||
                        (item.previewText?.contains(needle, ignoreCase = true) == true)
                }
            }
        }
    }

    val groupedByFile by remember(filteredLocations) {
        derivedStateOf {
            filteredLocations
                .groupBy { it.filePath }
                .toList()
                .sortedBy { it.first.lowercase() }
        }
    }

    TinaAlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        title = {
            TinaDialogTitleText(title)
        },
        text = {
            when {
                isLoading -> {
                    TinaDialogContentColumn {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                locations.isEmpty() -> {
                    TinaDialogContentColumn {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Strings.lsp_no_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    val enableFolding = query.trim().isBlank()
                    TinaDialogContentColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(Strings.lsp_location_search_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            )
                        )

                        Text(
                            text = stringResource(
                                Strings.lsp_location_found_count,
                                filteredLocations.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )

                        TinaDialogCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                groupedByFile.forEach { (filePath, itemsInFile) ->
                                    val fileName =
                                        itemsInFile.firstOrNull()?.fileName ?: File(filePath).name
                                    val expanded = if (enableFolding) {
                                        expandedByFile[filePath] ?: true
                                    } else {
                                        true
                                    }

                                    item(key = "header|$filePath") {
                                        LocationFileHeaderRow(
                                            fileName = fileName,
                                            filePath = filePath,
                                            count = itemsInFile.size,
                                            expanded = expanded,
                                            enableToggle = enableFolding,
                                            onToggle = { expandedByFile[filePath] = !expanded }
                                        )
                                    }

                                    if (expanded) {
                                        items(itemsInFile) { location ->
                                            LocationListItem(
                                                location = location,
                                                onClick = {
                                                    keyboardController?.hide()
                                                    onLocationClick(location)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_close),
                onClick = {
                    keyboardController?.hide()
                    onDismiss()
                }
            )
        },
        modifier = modifier
    )
}

@Composable
private fun LocationListItem(
    location: LocationItem,
    onClick: () -> Unit
) {
    val line = (location.line + 1).coerceAtLeast(1)
    val column = (location.column + 1).coerceAtLeast(1)
    val posText = "$line:$column"
    val previewText = location.previewText?.takeIf { it.isNotBlank() }

    TinaDialogCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        color = MaterialTheme.colorScheme.surface,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = previewText ?: location.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = if (previewText != null) {
                        "${location.fileName}  $posText"
                    } else {
                        posText
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
private fun LocationFileHeaderRow(
    fileName: String,
    filePath: String,
    count: Int,
    expanded: Boolean,
    enableToggle: Boolean,
    onToggle: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    val dirLabel = remember(filePath) { File(filePath).parent }.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(TinaShapes.CardCorner))
            .background(bg)
            .clickable(enabled = enableToggle, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val arrow = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight
        Icon(
            imageVector = arrow,
            contentDescription = null,
            tint = fg.copy(alpha = if (enableToggle) 1f else 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dirLabel.isNotBlank()) {
                Text(
                    text = dirLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

package com.wuxianggujun.tinaide.ui.compose.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuxianggujun.tinaide.core.editor.BookmarkInfo
import com.wuxianggujun.tinaide.core.editor.IBookmarkRepository
import com.wuxianggujun.tinaide.core.i18n.Drawables
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import com.wuxianggujun.tinaide.ui.compose.icons.rememberTinaPainter
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun BookmarksContent(
    projectRootPath: String?,
    bookmarkRepository: IBookmarkRepository,
    onNavigate: (filePath: String, line: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (projectRootPath.isNullOrBlank()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Strings.bookmarks_no_project),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val root = projectRootPath!!
    val bookmarks by bookmarkRepository.bookmarksFlow(root).collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingTarget by remember { mutableStateOf<BookmarkInfo?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // 当 editingTarget 改变时，同步更新 editingText
    LaunchedEffect(editingTarget) {
        editingTarget?.let { target ->
            editingText = target.note
        }
    }

    // 当 editingTarget 改变时，同步更新 editingText
    editingTarget?.let { target ->
        TinaInputDialog(
            title = stringResource(Strings.bookmark_edit_note),
            value = editingText,
            onValueChange = { editingText = it },
            placeholder = stringResource(Strings.bookmark_note_placeholder),
            onConfirm = {
                val note = editingText
                scope.launch {
                    bookmarkRepository.updateNote(root, target.filePath, target.line, note)
                }
                editingTarget = null
                editingText = ""
            },
            onDismiss = {
                editingTarget = null
                editingText = ""
            }
        )
    }

    if (showClearAllDialog) {
        TinaConfirmDialog(
            title = stringResource(Strings.bookmark_clear_all_title),
            message = stringResource(Strings.bookmark_clear_all_confirm),
            onConfirm = {
                showClearAllDialog = false
                scope.launch {
                    bookmarkRepository.clearAll(root)
                }
            },
            onDismiss = { showClearAllDialog = false },
            isDanger = true
        )
    }

    if (bookmarks.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Strings.bookmarks_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TinaOverlayPanelSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TinaPanelSegmentButton(
                    onClick = {
                        scope.launch {
                            val removed = bookmarkRepository.pruneMissingFiles(root)
                            Toast.makeText(
                                context,
                                Strings.toast_bookmark_pruned.strOr(context, removed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    minHeight = 32.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = stringResource(Strings.bookmark_prune_missing)
                    )
                }

                Spacer(modifier = Modifier.size(4.dp))

                TinaPanelSegmentButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier.size(32.dp),
                    minHeight = 32.dp,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = stringResource(Strings.bookmark_clear_all),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bookmarks, key = { "${it.filePath}:${it.line}" }) { bm ->
                BookmarkRow(
                    projectRootPath = root,
                    bookmark = bm,
                    onClick = { onNavigate(bm.filePath, bm.line) },
                    onEditNote = {
                        editingTarget = bm
                    },
                    onRemove = {
                        scope.launch { bookmarkRepository.remove(root, bm.filePath, bm.line) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    projectRootPath: String,
    bookmark: BookmarkInfo,
    onClick: () -> Unit,
    onEditNote: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPath = remember(projectRootPath, bookmark.filePath) {
        val file = File(bookmark.filePath)
        val root = File(projectRootPath)
        runCatching { file.relativeTo(root).path }.getOrDefault(file.name)
    }

    TinaDialogSelectableCard(
        selected = false,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        unselectedColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        unselectedBorder = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayPath,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(Strings.bookmark_location, bookmark.line + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TinaPanelSegmentButton(
                onClick = onEditNote,
                modifier = Modifier.size(30.dp),
                minHeight = 30.dp,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    painter = rememberTinaPainter(Drawables.ic_edit),
                    contentDescription = stringResource(Strings.bookmark_edit_note)
                )
            }

            Spacer(modifier = Modifier.size(6.dp))

            TinaPanelSegmentButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp),
                minHeight = 30.dp,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    painter = rememberTinaPainter(Drawables.ic_delete),
                    contentDescription = stringResource(Strings.bookmark_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (bookmark.note.isNotBlank()) {
            Text(
                text = bookmark.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

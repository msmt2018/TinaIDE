package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.storage.CleanupCategory
import com.wuxianggujun.tinaide.storage.StorageCleanupManager.CleanupNode
import com.wuxianggujun.tinaide.ui.compose.components.TinaConfirmDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaTopBar
import com.wuxianggujun.tinaide.ui.compose.screens.settings.CheckState
import com.wuxianggujun.tinaide.ui.compose.screens.settings.StorageCleanupSelectionResolver
import com.wuxianggujun.tinaide.ui.compose.screens.settings.StorageCleanupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanupDetailScreen(
    category: CleanupCategory,
    viewModel: StorageCleanupViewModel,
    onBack: () -> Unit
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var showCleanConfirm by remember { mutableStateOf(false) }

    val selectedCount = StorageCleanupSelectionResolver.selectedCount(
        items = detail.items,
        childrenByParent = detail.childrenByParent,
        selectedPaths = detail.selectedPaths,
    )
    val selectedBytes = StorageCleanupSelectionResolver.selectedBytes(
        items = detail.items,
        childrenByParent = detail.childrenByParent,
        selectedPaths = detail.selectedPaths,
    )
    val totalSelectable = detail.items.size
    val canCleanSelected = !detail.isDeleting && selectedCount > 0
    val allTopSelected = totalSelectable > 0 && detail.items.all { it.absolutePath in detail.selectedPaths }

    Scaffold(
        topBar = {
            TinaTopBar(
                title = stringResource(categoryTitleRes(category)),
                onNavigateBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            if (allTopSelected) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAllTopLevel()
                            }
                        },
                        enabled = totalSelectable > 0 && !detail.isDeleting,
                    ) {
                        Icon(
                            imageVector = if (allTopSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                            contentDescription = stringResource(
                                if (allTopSelected) {
                                    Strings.storage_cleanup_detail_clear_selection
                                } else {
                                    Strings.storage_cleanup_detail_select_all
                                }
                            )
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            SelectionSummaryBar(
                selectedCount = selectedCount,
                selectedBytes = selectedBytes,
                isDeleting = detail.isDeleting,
                canClean = canCleanSelected,
                onCleanClick = { showCleanConfirm = true }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            when {
                detail.isLoading -> LoadingView()
                detail.items.isEmpty() -> EmptyView()
                else -> ItemList(detail = detail, viewModel = viewModel)
            }
        }
    }

    if (showCleanConfirm) {
        TinaConfirmDialog(
            title = stringResource(Strings.storage_cleanup_detail_confirm_title),
            message = stringResource(
                Strings.storage_cleanup_detail_confirm_message,
                selectedCount,
                formatStorageSize(selectedBytes),
            ),
            onConfirm = {
                showCleanConfirm = false
                viewModel.cleanSelectedInDetail()
            },
            onDismiss = { showCleanConfirm = false },
            isDanger = true
        )
    }
}

@Composable
private fun SelectionSummaryBar(
    selectedCount: Int,
    selectedBytes: Long,
    isDeleting: Boolean,
    canClean: Boolean,
    onCleanClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selectedCount == 0) {
                stringResource(Strings.storage_cleanup_detail_no_selection)
            } else {
                stringResource(
                    Strings.storage_cleanup_detail_selected_summary,
                    selectedCount,
                    formatStorageSize(selectedBytes),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            TextButton(
                onClick = onCleanClick,
                enabled = canClean
            ) {
                Text(stringResource(Strings.storage_cleanup_detail_clean_selected))
            }
        }
    }
}

@Composable
private fun ItemList(
    detail: com.wuxianggujun.tinaide.ui.compose.screens.settings.StorageCleanupDetailState,
    viewModel: StorageCleanupViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(detail.items, key = { it.absolutePath }) { item ->
            val expanded = item.absolutePath in detail.expandedPaths
            val children = detail.childrenByParent[item.absolutePath]
            val isLoadingChildren = item.absolutePath in detail.loadingChildrenPaths
            val checkState = StorageCleanupSelectionResolver.topLevelCheckState(
                item = item,
                loadedChildren = children,
                selectedPaths = detail.selectedPaths,
            )
            TopLevelRow(
                item = item,
                expanded = expanded,
                checkState = checkState,
                enabled = !detail.isDeleting,
                onToggleSelect = { viewModel.toggleTopLevelSelection(item) },
                onToggleExpand = { viewModel.toggleExpand(item) },
            )
            if (expanded) {
                if (isLoadingChildren && children == null) {
                    LoadingChildrenIndicator()
                } else if (children == null || children.isEmpty()) {
                    EmptyChildrenLabel()
                } else {
                    children.forEach { child ->
                        val childState = StorageCleanupSelectionResolver.childCheckState(
                            child = child,
                            parent = item,
                            selectedPaths = detail.selectedPaths,
                        )
                        ChildRow(
                            child = child,
                            checkState = childState,
                            enabled = !detail.isDeleting,
                            onToggleSelect = { viewModel.toggleChildSelection(child, item) }
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun TopLevelRow(
    item: CleanupNode,
    expanded: Boolean,
    checkState: CheckState,
    enabled: Boolean,
    onToggleSelect: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && item.isDirectory, onClick = onToggleExpand)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(
            state = checkState.toToggleable(),
            onClick = onToggleSelect,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatStorageSize(item.bytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (item.isDirectory) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }
    }
}

@Composable
private fun ChildRow(
    child: CleanupNode,
    checkState: CheckState,
    enabled: Boolean,
    onToggleSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onToggleSelect)
            .padding(start = 40.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checkState == CheckState.CHECKED,
            onCheckedChange = { onToggleSelect() },
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = child.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatStorageSize(child.bytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Strings.storage_cleanup_detail_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingChildrenIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = stringResource(Strings.storage_cleanup_detail_loading_children),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyChildrenLabel() {
    Text(
        text = stringResource(Strings.storage_cleanup_detail_no_children),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, top = 6.dp, bottom = 10.dp)
    )
    Spacer(modifier = Modifier.height(0.dp))
}

private fun CheckState.toToggleable(): ToggleableState = when (this) {
    CheckState.CHECKED -> ToggleableState.On
    CheckState.UNCHECKED -> ToggleableState.Off
    CheckState.PARTIAL -> ToggleableState.Indeterminate
}

private fun categoryTitleRes(category: CleanupCategory): Int = when (category) {
    CleanupCategory.BUILD_INTERMEDIATES -> Strings.storage_cleanup_cat_build
    CleanupCategory.PROOT_CACHE -> Strings.storage_cleanup_cat_proot_cache
    CleanupCategory.DOWNLOAD_CACHE -> Strings.storage_cleanup_cat_download_cache
    CleanupCategory.EXPORT_CACHE -> Strings.storage_cleanup_cat_export_cache
    CleanupCategory.APP_LOGS -> Strings.storage_cleanup_cat_app_logs
    CleanupCategory.INSTALL_LOGS -> Strings.storage_cleanup_cat_install_logs
}

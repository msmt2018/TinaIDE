package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.storage.CleanupCategory
import com.wuxianggujun.tinaide.ui.compose.components.TinaConfirmDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaTopBar
import com.wuxianggujun.tinaide.ui.compose.screens.settings.StorageCleanupItem
import com.wuxianggujun.tinaide.ui.compose.screens.settings.StorageCleanupUiState
import com.wuxianggujun.tinaide.ui.compose.screens.settings.StorageCleanupViewModel
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCard
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCategoryTitle
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsClickableItem
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsDisplayItem
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanupScreen(
    onBack: () -> Unit,
    viewModel: StorageCleanupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCleanAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedback) {
        val feedback = state.feedback ?: return@LaunchedEffect
        val message = when (feedback) {
            is StorageCleanupUiState.Feedback.CleanupDone ->
                context.getString(Strings.storage_cleanup_done, formatStorageSize(feedback.freedBytes))

            is StorageCleanupUiState.Feedback.CleanupFailed ->
                context.getString(
                    Strings.storage_cleanup_failed,
                    feedback.failedCount,
                    formatStorageSize(feedback.freedBytes)
                )
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeFeedback()
    }

    BackHandler(enabled = detail.category != null) {
        viewModel.closeDetail()
    }

    val activeDetailCategory = detail.category
    if (activeDetailCategory != null) {
        StorageCleanupDetailScreen(
            category = activeDetailCategory,
            viewModel = viewModel,
            onBack = { viewModel.closeDetail() }
        )
        return
    }

    Scaffold(
        topBar = {
            TinaTopBar(
                title = stringResource(Strings.settings_title_storage_cleanup),
                onNavigateBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.isScanning && !state.isCleaningAll
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Strings.storage_cleanup_refresh)
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
                .verticalScroll(rememberScrollState())
        ) {
            TotalCleanupCard(
                totalBytes = state.totalBytes,
                isScanning = state.isScanning,
                isCleaningAll = state.isCleaningAll,
                onCleanAll = { showCleanAllConfirm = true }
            )

            SettingsCategoryTitle(stringResource(Strings.settings_cat_storage_cleanup))

            SettingsCard {
                state.items.forEachIndexed { index, item ->
                    CleanupItemRow(
                        item = item,
                        isScanning = state.isScanning,
                        isBusy = state.isCleaningAll || item.isCleaning,
                        showDivider = index != state.items.lastIndex,
                        onClick = { viewModel.openDetail(item.category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCleanAllConfirm) {
        TinaConfirmDialog(
            title = stringResource(Strings.storage_cleanup_confirm_all_title),
            message = stringResource(Strings.storage_cleanup_confirm_all_message),
            onConfirm = {
                showCleanAllConfirm = false
                viewModel.cleanAll()
            },
            onDismiss = { showCleanAllConfirm = false },
            isDanger = true
        )
    }
}

@Composable
private fun TotalCleanupCard(
    totalBytes: Long,
    isScanning: Boolean,
    isCleaningAll: Boolean,
    onCleanAll: () -> Unit
) {
    SettingsCategoryTitle(stringResource(Strings.storage_cleanup_total))
    SettingsCard {
        SettingsDisplayItem(
            title = stringResource(Strings.storage_cleanup_total),
            value = if (isScanning) {
                stringResource(Strings.storage_cleanup_scanning)
            } else {
                formatStorageSize(totalBytes)
            },
            showDivider = true
        )
        SettingsClickableItem(
            title = stringResource(Strings.storage_cleanup_clean_all),
            value = if (isCleaningAll) stringResource(Strings.storage_cleanup_cleaning) else null,
            onClick = {
                if (!isScanning && !isCleaningAll && totalBytes > 0L) {
                    onCleanAll()
                }
            },
            showDivider = false
        )
    }
}

@Composable
private fun CleanupItemRow(
    item: StorageCleanupItem,
    isScanning: Boolean,
    isBusy: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val meta = categoryMeta(item.category)
    val valueText = when {
        item.isCleaning -> stringResource(Strings.storage_cleanup_cleaning)
        isScanning && item.bytes == 0L -> stringResource(Strings.storage_cleanup_scanning)
        else -> formatStorageSize(item.bytes)
    }
    Box {
        SettingsClickableItem(
            title = stringResource(meta.titleRes),
            subtitle = stringResource(meta.subtitleRes),
            value = valueText,
            onClick = {
                if (!isBusy && !isScanning) {
                    onClick()
                }
            },
            showDivider = showDivider
        )
        if (item.isCleaning) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .size(18.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

private data class CleanupCategoryMeta(
    val titleRes: Int,
    val subtitleRes: Int
)

private fun categoryMeta(category: CleanupCategory): CleanupCategoryMeta = when (category) {
    CleanupCategory.BUILD_INTERMEDIATES -> CleanupCategoryMeta(
        Strings.storage_cleanup_cat_build,
        Strings.storage_cleanup_cat_build_desc
    )

    CleanupCategory.PROOT_CACHE -> CleanupCategoryMeta(
        Strings.storage_cleanup_cat_proot_cache,
        Strings.storage_cleanup_cat_proot_cache_desc
    )

    CleanupCategory.DOWNLOAD_CACHE -> CleanupCategoryMeta(
        Strings.storage_cleanup_cat_download_cache,
        Strings.storage_cleanup_cat_download_cache_desc
    )

    CleanupCategory.EXPORT_CACHE -> CleanupCategoryMeta(
        Strings.storage_cleanup_cat_export_cache,
        Strings.storage_cleanup_cat_export_cache_desc
    )

    CleanupCategory.APP_LOGS -> CleanupCategoryMeta(
        Strings.storage_cleanup_cat_app_logs,
        Strings.storage_cleanup_cat_app_logs_desc
    )

    CleanupCategory.INSTALL_LOGS -> CleanupCategoryMeta(
        Strings.storage_cleanup_cat_install_logs,
        Strings.storage_cleanup_cat_install_logs_desc
    )
}

internal fun formatStorageSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

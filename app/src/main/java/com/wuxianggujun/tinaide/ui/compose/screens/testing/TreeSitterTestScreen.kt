package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDivider
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionTitle
import com.wuxianggujun.tinaide.ui.compose.components.TinaSingleChoiceDialog

internal data class TreeSitterTestLspControlsState(
    val builtinCmakeLspControlEnabled: Boolean
)

internal object TreeSitterTestScreenSupport {
    fun requireSampleOptions(
        samples: List<TreeSitterSampleOption>
    ): List<TreeSitterSampleOption> {
        require(samples.isNotEmpty()) {
            "Tree-sitter developer test samples must not be empty"
        }
        return samples
    }

    fun resolveSelectedSample(
        samples: List<TreeSitterSampleOption>,
        selectedSampleId: String
    ): TreeSitterSampleOption {
        val availableSamples = requireSampleOptions(samples)
        return availableSamples.firstOrNull { it.id == selectedSampleId }
            ?: availableSamples.first()
    }

    fun buildInfoCardText(
        vararg sections: String
    ): String = sections
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(separator = "\n\n")

    fun buildDialogOptions(
        samples: List<TreeSitterSampleOption>
    ): List<Pair<String, String>> = requireSampleOptions(samples).map { sample ->
        sample.id to sample.label
    }

    fun resolveLspControlsState(): TreeSitterTestLspControlsState = TreeSitterTestLspControlsState(
        builtinCmakeLspControlEnabled = true
    )
}

@Composable
fun TreeSitterTestScreen(onNavigateBack: (() -> Unit)? = null) {
    val editorLspEnabled by Prefs.devEditorLspEnabledFlow.collectAsState()
    val builtinCmakeLspEnabled by Prefs.devBuiltinCmakeLspEnabledFlow.collectAsState()
    val scenario = remember { DevEditorTestCatalog.treeSitter }
    val title = stringResource(scenario.titleRes)
    val samples = remember {
        TreeSitterTestScreenSupport.requireSampleOptions(
            DevEditorTestCatalog.treeSitterSampleOptions
        )
    }
    val lspControlsState = remember {
        TreeSitterTestScreenSupport.resolveLspControlsState()
    }
    var selectedSampleId by remember { mutableStateOf(samples.first().id) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    val selectedSample = remember(samples, selectedSampleId) {
        TreeSitterTestScreenSupport.resolveSelectedSample(
            samples = samples,
            selectedSampleId = selectedSampleId
        )
    }
    val previewHint = stringResource(Strings.tree_sitter_test_preview_hint)
    val optionsInfoText = TreeSitterTestScreenSupport.buildInfoCardText(
        stringResource(Strings.tree_sitter_test_lsp_hint),
        stringResource(Strings.tree_sitter_test_cmake_hint),
        stringResource(Strings.tree_sitter_test_recommended_files)
    )

    DevEditorTestHost(
        workspaceKey = scenario.workspaceKey,
        title = title,
        fixtures = listOf(selectedSample.fixture),
        onNavigateBack = onNavigateBack,
        activeFixtureIndex = scenario.activeFixtureIndex,
        reloadToken = reloadToken,
        topBarActions = {
            TreeSitterOptionsMenu(
                infoText = optionsInfoText,
                selectedSampleLabel = selectedSample.label,
                editorLspEnabled = editorLspEnabled,
                builtinCmakeLspEnabled = builtinCmakeLspEnabled,
                builtinCmakeLspControlEnabled = lspControlsState.builtinCmakeLspControlEnabled,
                onEditorLspEnabledChange = { Prefs.devEditorLspEnabled = it },
                onBuiltinCmakeLspEnabledChange = { Prefs.devBuiltinCmakeLspEnabled = it },
                onSelectLanguage = { showLanguageDialog = true },
                onReanalyze = { reloadToken += 1 }
            )
        },
        headerContent = { _ ->
            DevEditorInfoCard(text = previewHint)
        }
    )

    if (showLanguageDialog) {
        TinaSingleChoiceDialog(
            title = stringResource(Strings.tree_sitter_test_select_language),
            options = TreeSitterTestScreenSupport.buildDialogOptions(samples),
            selectedValue = selectedSampleId,
            onSelected = { sampleId ->
                selectedSampleId = sampleId
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun TreeSitterOptionsMenu(
    infoText: String,
    selectedSampleLabel: String,
    editorLspEnabled: Boolean,
    builtinCmakeLspEnabled: Boolean,
    builtinCmakeLspControlEnabled: Boolean,
    onEditorLspEnabledChange: (Boolean) -> Unit,
    onBuiltinCmakeLspEnabledChange: (Boolean) -> Unit,
    onSelectLanguage: () -> Unit,
    onReanalyze: () -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Strings.dev_editor_test_more_options)
            )
        }

        TinaDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(
                    text = stringResource(Strings.dev_editor_test_info),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TinaDropdownMenuDivider()
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(stringResource(Strings.tree_sitter_test_actions))
            }
            TinaDropdownMenuItem(
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(Strings.tree_sitter_test_select_language))
                        Text(
                            text = selectedSampleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {
                    showMenu = false
                    onSelectLanguage()
                }
            )
            TinaDropdownMenuItem(
                text = { Text(stringResource(Strings.tree_sitter_test_reanalyze)) },
                onClick = {
                    showMenu = false
                    onReanalyze()
                }
            )

            TinaDropdownMenuDivider()
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(stringResource(Strings.tree_sitter_test_feature_config))
            }
            TreeSitterToggleMenuItem(
                text = stringResource(Strings.dev_options_editor_lsp_enabled),
                checked = editorLspEnabled,
                onCheckedChange = onEditorLspEnabledChange
            )
            TreeSitterToggleMenuItem(
                text = stringResource(Strings.dev_options_builtin_cmake_lsp_enabled),
                checked = builtinCmakeLspEnabled,
                enabled = builtinCmakeLspControlEnabled,
                onCheckedChange = onBuiltinCmakeLspEnabledChange
            )
        }
    }
}

@Composable
private fun TreeSitterToggleMenuItem(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    TinaDropdownMenuItem(
        text = { Text(text) },
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        trailingIcon = if (checked) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
            }
        } else {
            null
        }
    )
}

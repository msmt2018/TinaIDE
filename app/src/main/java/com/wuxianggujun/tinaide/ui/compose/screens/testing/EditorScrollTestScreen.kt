package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.config.DeveloperDiagnosticsSettings
import com.wuxianggujun.tinaide.core.config.EditorSettings
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDivider
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionTitle

internal data class EditorScrollContentProfile(
    val id: String,
    @param:StringRes @get:StringRes val labelRes: Int,
    val lineCount: Int
)

internal object EditorScrollTestScreenSupport {
    fun contentProfiles(): List<EditorScrollContentProfile> = listOf(
        EditorScrollContentProfile("small", Strings.editor_scroll_test_content_size_small, 100),
        EditorScrollContentProfile("medium", Strings.editor_scroll_test_content_size_medium, 500),
        EditorScrollContentProfile("large", Strings.editor_scroll_test_content_size_large, 1000),
        EditorScrollContentProfile("xlarge", Strings.editor_scroll_test_content_size_xlarge, 3000),
        EditorScrollContentProfile("xxlarge", Strings.editor_scroll_test_content_size_xxlarge, 5000)
    )

    fun resolveContentProfile(
        profiles: List<EditorScrollContentProfile>,
        selectedProfileId: String
    ): EditorScrollContentProfile {
        require(profiles.isNotEmpty()) {
            "Editor scroll developer test profiles must not be empty"
        }
        return profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
    }

    fun buildFixture(profile: EditorScrollContentProfile): DevEditorFixture = DevEditorFixture(
        relativePath = "EditorScroll_${profile.id}.kt",
        content = buildScrollProbeContent(profile.lineCount)
    )

    fun buildScrollProbeContent(lineCount: Int): String {
        val safeLineCount = lineCount.coerceAtLeast(1)
        return buildString {
            appendLine("package scrolltest")
            appendLine()
            appendLine("class EditorScrollProbe {")
            repeat(safeLineCount) { index ->
                val number = index + 1
                val suffix = number.toString().padStart(4, '0')
                appendLine("    fun marker$suffix(input: Int): Int = input + $number")
            }
            appendLine("}")
        }.trimEnd()
    }
}

@Composable
fun EditorScrollTestScreen(onNavigateBack: (() -> Unit)? = null) {
    val scenario = remember { DevEditorTestCatalog.editorScroll }
    val title = stringResource(scenario.titleRes)
    val description = stringResource(scenario.descriptionRes)
    val profiles = remember { EditorScrollTestScreenSupport.contentProfiles() }
    var selectedProfileId by rememberSaveable { mutableStateOf(profiles.first().id) }
    val selectedProfile = remember(profiles, selectedProfileId) {
        EditorScrollTestScreenSupport.resolveContentProfile(
            profiles = profiles,
            selectedProfileId = selectedProfileId
        )
    }
    val fixture = remember(selectedProfile) {
        EditorScrollTestScreenSupport.buildFixture(selectedProfile)
    }
    val editorSettings by Prefs.editorSettingsFlow.collectAsState()
    val diagnosticsSettings by Prefs.devDiagnosticsSettingsFlow.collectAsState()

    DevEditorTestHost(
        workspaceKey = scenario.workspaceKey,
        title = title,
        fixtures = listOf(fixture),
        onNavigateBack = onNavigateBack,
        activeFixtureIndex = scenario.activeFixtureIndex,
        topBarActions = {
            EditorScrollOptionsMenu(
                description = description,
                profiles = profiles,
                selectedProfile = selectedProfile,
                editorSettings = editorSettings,
                diagnosticsSettings = diagnosticsSettings,
                onProfileSelected = { selectedProfileId = it }
            )
        }
    )
}

@Composable
private fun EditorScrollOptionsMenu(
    description: String,
    profiles: List<EditorScrollContentProfile>,
    selectedProfile: EditorScrollContentProfile,
    editorSettings: EditorSettings,
    diagnosticsSettings: DeveloperDiagnosticsSettings,
    onProfileSelected: (String) -> Unit
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
                    text = stringResource(Strings.editor_scroll_test_config),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TinaDropdownMenuDivider()
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(stringResource(Strings.editor_scroll_test_content_size))
            }
            profiles.forEach { profile ->
                SelectableMenuItem(
                    text = stringResource(profile.labelRes),
                    selected = profile.id == selectedProfile.id,
                    onClick = { onProfileSelected(profile.id) }
                )
            }

            TinaDropdownMenuDivider()
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(stringResource(Strings.editor_scroll_test_scroll_config))
            }
            ToggleMenuItem(
                text = stringResource(Strings.editor_scroll_test_enable_fling),
                checked = editorSettings.scrollFlingEnabled,
                onCheckedChange = Prefs::setEditorScrollFlingEnabled
            )
            ToggleMenuItem(
                text = stringResource(Strings.editor_scroll_test_single_direction_dragging),
                checked = editorSettings.singleDirectionDragging,
                onCheckedChange = Prefs::setEditorSingleDirectionDragging
            )
            ToggleMenuItem(
                text = stringResource(Strings.editor_scroll_test_single_direction_fling),
                checked = editorSettings.singleDirectionFling,
                onCheckedChange = Prefs::setEditorSingleDirectionFling
            )

            TinaDropdownMenuDivider()
            TinaDropdownMenuSectionHeader {
                TinaDropdownMenuSectionTitle(stringResource(Strings.editor_scroll_test_debug_tools))
            }
            ToggleMenuItem(
                text = stringResource(Strings.dev_options_diagnostics_enabled),
                checked = diagnosticsSettings.diagnosticsEnabled,
                onCheckedChange = { Prefs.devDiagnosticsEnabled = it }
            )
            ToggleMenuItem(
                text = stringResource(Strings.editor_scroll_test_touch_diagnostics),
                checked = diagnosticsSettings.editorTouchDiagnosticsEnabled,
                enabled = diagnosticsSettings.diagnosticsEnabled,
                onCheckedChange = { Prefs.editorTouchDiagnosticsEnabled = it }
            )
            ToggleMenuItem(
                text = stringResource(Strings.dev_options_editor_scroll_touch_log),
                checked = diagnosticsSettings.editorScrollLogEnabled,
                enabled = diagnosticsSettings.diagnosticsEnabled,
                onCheckedChange = { Prefs.devEditorTouchScrollLogEnabled = it }
            )
            ToggleMenuItem(
                text = stringResource(Strings.dev_options_editor_fling_touch_log),
                checked = diagnosticsSettings.editorFlingLogEnabled,
                enabled = diagnosticsSettings.diagnosticsEnabled,
                onCheckedChange = { Prefs.devEditorTouchFlingLogEnabled = it }
            )
        }
    }
}

@Composable
private fun SelectableMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TinaDropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        trailingIcon = if (selected) {
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

@Composable
private fun ToggleMenuItem(
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

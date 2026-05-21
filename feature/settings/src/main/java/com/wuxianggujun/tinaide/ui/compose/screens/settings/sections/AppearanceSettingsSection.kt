package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.config.DebugToolbarPosition
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaSingleChoiceDialog
import com.wuxianggujun.tinaide.ui.compose.screens.settings.SettingsViewModel
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCard
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCategoryTitle
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsClickableItem

@Composable
internal fun AppearanceSettingsSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDebugToolbarDialog by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsState()
    val currentTheme = state.appTheme
    val debugToolbarPosition = state.debugToolbarPosition

    val themeDisplayName = stringResource(
        AppearanceSettingsSectionSupport.resolveThemeLabel(currentTheme)
    )

    Spacer(modifier = Modifier.height(8.dp))

    // 主题设置
    SettingsCategoryTitle(stringResource(Strings.settings_cat_theme))

    SettingsCard {
        SettingsClickableItem(
            title = stringResource(Strings.settings_app_theme),
            subtitle = stringResource(Strings.settings_app_theme_desc),
            value = themeDisplayName,
            onClick = { showThemeDialog = true },
            showDivider = false
        )
    }

    // 调试设置
    SettingsCategoryTitle(stringResource(Strings.settings_cat_debug))

    SettingsCard {
        SettingsClickableItem(
            title = stringResource(Strings.settings_debug_toolbar_position),
            subtitle = stringResource(Strings.settings_debug_toolbar_desc),
            value = debugToolbarPosition.getDisplayName(context),
            onClick = { showDebugToolbarDialog = true },
            showDivider = false
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (showThemeDialog) {
        val themes = AppearanceSettingsSectionSupport.buildThemeOptions().map { option ->
            option.value to stringResource(option.labelRes)
        }

        TinaSingleChoiceDialog(
            title = stringResource(Strings.dialog_title_select_theme),
            options = themes,
            selectedValue = currentTheme,
            onSelected = { themeValue ->
                if (AppearanceSettingsSectionSupport.shouldApplyThemeChange(currentTheme, themeValue)) {
                    val previousTheme = currentTheme
                    viewModel.setAppTheme(themeValue)
                    Prefs.applyNightMode(themeValue)

                    if (AppearanceSettingsSectionSupport.shouldRecreateForThemeChange(previousTheme, themeValue)) {
                        (context as? Activity)?.recreate()
                    }
                }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showDebugToolbarDialog) {
        DebugToolbarPositionDialog(
            currentPosition = debugToolbarPosition,
            onPositionSelected = { position ->
                viewModel.setDebugToolbarPosition(position)
                showDebugToolbarDialog = false
            },
            onDismiss = { showDebugToolbarDialog = false }
        )
    }
}

@Composable
private fun DebugToolbarPositionDialog(
    currentPosition: DebugToolbarPosition,
    onPositionSelected: (DebugToolbarPosition) -> Unit,
    onDismiss: () -> Unit
) {
    val options = AppearanceSettingsSectionSupport.buildDebugToolbarPositionOptions().map { option ->
        option.value to stringResource(option.labelRes)
    }

    TinaSingleChoiceDialog(
        title = stringResource(Strings.dialog_title_debug_toolbar),
        options = options,
        selectedValue = currentPosition.value,
        onSelected = { value -> onPositionSelected(DebugToolbarPosition.fromString(value)) },
        onDismiss = onDismiss
    )
}

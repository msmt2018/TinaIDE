package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import com.wuxianggujun.tinaide.core.config.AppTheme
import com.wuxianggujun.tinaide.core.config.Prefs
import com.wuxianggujun.tinaide.core.config.ThemeManager
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDivider
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuItem
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionTitle

@Composable
fun ThemePreviewTestScreen(onNavigateBack: (() -> Unit)? = null) {
    val scenario = remember { DevEditorTestCatalog.themePreview }
    val title = stringResource(scenario.titleRes)
    val fixtures = remember { scenario.fixturesProvider() }
    var showThemeMenu by rememberSaveable { mutableStateOf(false) }
    val currentTheme by ThemeManager.themeFlow.collectAsState()

    DevEditorTestHost(
        workspaceKey = scenario.workspaceKey,
        title = title,
        fixtures = fixtures,
        onNavigateBack = onNavigateBack,
        activeFixtureIndex = scenario.activeFixtureIndex,
        topBarActions = {
            Box {
                IconButton(onClick = { showThemeMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Strings.theme_preview_test_menu)
                    )
                }

                TinaDropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { showThemeMenu = false }
                ) {
                    TinaDropdownMenuSectionHeader {
                        TinaDropdownMenuSectionTitle(
                            text = stringResource(Strings.theme_preview_test_menu),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    TinaDropdownMenuDivider()

                    ThemePreviewMenuItems(
                        currentTheme = currentTheme,
                        onThemeSelected = { theme ->
                            Prefs.setTheme(theme.name)
                            showThemeMenu = false
                        }
                    )
                }
            }
        }
    ) { _, _ ->
        // 保持空 footer，避免把额外操作块放到页面底部。
    }
}

@Composable
private fun ThemePreviewMenuItems(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    val themes = listOf(
        AppTheme.DARK,
        AppTheme.LIGHT,
        AppTheme.GRAY,
        AppTheme.AUTO
    )

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        themes.forEach { theme ->
            TinaDropdownMenuItem(
                text = { Text(stringResource(theme.labelRes())) },
                onClick = { onThemeSelected(theme) },
                trailingIcon = if (currentTheme == theme) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

private fun AppTheme.labelRes(): Int = when (this) {
    AppTheme.DARK -> Strings.theme_dark
    AppTheme.LIGHT -> Strings.theme_light
    AppTheme.GRAY -> Strings.theme_gray
    AppTheme.AUTO -> Strings.theme_auto
}

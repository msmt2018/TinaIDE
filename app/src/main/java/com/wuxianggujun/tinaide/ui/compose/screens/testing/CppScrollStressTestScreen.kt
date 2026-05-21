package com.wuxianggujun.tinaide.ui.compose.screens.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenu
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuDivider
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaDropdownMenuSectionTitle

@Composable
fun CppScrollStressTestScreen(onNavigateBack: (() -> Unit)? = null) {
    val scenario = remember { DevEditorTestCatalog.cppScrollStress }
    val title = stringResource(scenario.titleRes)
    val description = stringResource(scenario.descriptionRes)
    val fixtures = remember { scenario.fixturesProvider() }

    DevEditorTestHost(
        workspaceKey = scenario.workspaceKey,
        title = title,
        fixtures = fixtures,
        onNavigateBack = onNavigateBack,
        activeFixtureIndex = scenario.activeFixtureIndex,
        topBarActions = {
            CppScrollStressInfoMenu(description = description)
        }
    )
}

@Composable
private fun CppScrollStressInfoMenu(description: String) {
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
            TinaDropdownMenuDivider()
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

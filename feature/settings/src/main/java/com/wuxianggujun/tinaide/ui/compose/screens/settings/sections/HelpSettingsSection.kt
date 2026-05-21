package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCard
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCategoryTitle
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsMenuItemWithIcon

/**
 * 帮助与文档设置区块
 * 显示帮助文档入口
 */
@Composable
internal fun HelpSettingsSection(
    onOpenHelpCenter: () -> Unit
) {
    val helpCenterEntry = HelpSettingsSectionSupport.resolveHelpCenterEntrySpec()

    Spacer(modifier = Modifier.height(8.dp))

    // 帮助中心
    SettingsCategoryTitle(stringResource(Strings.settings_title_help))

    SettingsCard {
        SettingsMenuItemWithIcon(
            iconRes = helpCenterEntry.iconRes,
            iconBackgroundColor = helpCenterEntry.iconBackgroundColor,
            title = stringResource(helpCenterEntry.titleRes),
            subtitle = stringResource(helpCenterEntry.subtitleRes),
            onClick = onOpenHelpCenter,
            showDivider = helpCenterEntry.showDivider,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
}

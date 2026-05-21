package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.plugin.script.PermissionLevel
import com.wuxianggujun.tinaide.plugin.script.PluginPermission
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDangerButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton

@Composable
fun PluginPermissionDialog(
    pluginName: String,
    permissions: Set<PluginPermission>,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    val groupedPermissions = permissions.groupBy { it.level }
    val hasHighRisk = groupedPermissions.containsKey(PermissionLevel.L3_HIGH_RISK)
    val hasMediumRisk = groupedPermissions.containsKey(PermissionLevel.L2_MEDIUM_RISK)

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = { TinaDialogTitleText(stringResource(Strings.plugin_permission_dialog_title)) },
        text = {
            TinaDialogContentColumn(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                TinaDialogMessageCard(
                    message = stringResource(Strings.plugin_permission_dialog_subtitle, pluginName),
                    color = if (hasHighRisk) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f)
                    },
                    textColor = if (hasHighRisk) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (hasHighRisk) {
                    PermissionSection(
                        title = stringResource(Strings.plugin_permission_high_risk),
                        titleColor = MaterialTheme.colorScheme.error,
                        permissions = groupedPermissions[PermissionLevel.L3_HIGH_RISK] ?: emptyList()
                    )
                }

                if (hasMediumRisk) {
                    PermissionSection(
                        title = stringResource(Strings.plugin_permission_medium_risk),
                        titleColor = MaterialTheme.colorScheme.tertiary,
                        permissions = groupedPermissions[PermissionLevel.L2_MEDIUM_RISK] ?: emptyList()
                    )
                }

                val lowRiskPermissions = groupedPermissions[PermissionLevel.L1_LOW_RISK] ?: emptyList()
                if (lowRiskPermissions.isNotEmpty()) {
                    PermissionSection(
                        title = stringResource(Strings.plugin_permission_low_risk),
                        titleColor = MaterialTheme.colorScheme.primary,
                        permissions = lowRiskPermissions
                    )
                }

                val noRiskPermissions = groupedPermissions[PermissionLevel.L0_NO_RISK] ?: emptyList()
                if (noRiskPermissions.isNotEmpty()) {
                    PermissionSection(
                        title = stringResource(Strings.plugin_permission_no_risk),
                        titleColor = MaterialTheme.colorScheme.outline,
                        permissions = noRiskPermissions
                    )
                }

                if (hasHighRisk) {
                    TinaDialogMessageCard(
                        message = stringResource(Strings.plugin_permission_high_risk_warning),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        textColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        confirmButton = {
            if (hasHighRisk) {
                TinaDangerButton(
                    text = stringResource(Strings.plugin_permission_confirm_high_risk),
                    onClick = onConfirm
                )
            } else {
                TinaPrimaryButton(
                    text = stringResource(Strings.plugin_permission_confirm_normal),
                    onClick = onConfirm
                )
            }
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.plugin_permission_deny),
                onClick = onDeny
            )
        }
    )
}

@Composable
private fun PermissionSection(
    title: String,
    titleColor: Color,
    permissions: List<PluginPermission>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        permissions.forEach { permission ->
            PermissionItem(permission = permission)
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PermissionItem(permission: PluginPermission) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when (permission.level) {
                        PermissionLevel.L3_HIGH_RISK -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        PermissionLevel.L2_MEDIUM_RISK -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        PermissionLevel.L1_LOW_RISK -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        PermissionLevel.L0_NO_RISK -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = when (permission.level) {
                    PermissionLevel.L3_HIGH_RISK -> MaterialTheme.colorScheme.error
                    PermissionLevel.L2_MEDIUM_RISK -> MaterialTheme.colorScheme.tertiary
                    PermissionLevel.L1_LOW_RISK -> MaterialTheme.colorScheme.primary
                    PermissionLevel.L0_NO_RISK -> MaterialTheme.colorScheme.outline
                }
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = permission.id,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = permission.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.wuxianggujun.tinaide.ui.projectlist

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class AnnouncementTone(
    val containerColor: Color,
    val iconTint: Color,
    val icon: ImageVector
)

@Composable
internal fun announcementTone(type: AnnouncementType): AnnouncementTone {
    val colorScheme = MaterialTheme.colorScheme
    return when (type) {
        AnnouncementType.NEW_RELEASE -> AnnouncementTone(
            containerColor = colorScheme.tertiaryContainer,
            iconTint = colorScheme.tertiary,
            icon = Icons.Outlined.NewReleases
        )
        AnnouncementType.INFO -> AnnouncementTone(
            containerColor = colorScheme.secondaryContainer,
            iconTint = colorScheme.secondary,
            icon = Icons.Outlined.Info
        )
        AnnouncementType.IMPORTANT -> AnnouncementTone(
            containerColor = colorScheme.primaryContainer,
            iconTint = colorScheme.primary,
            icon = Icons.Outlined.Campaign
        )
        AnnouncementType.WARNING -> AnnouncementTone(
            containerColor = colorScheme.errorContainer,
            iconTint = colorScheme.error,
            icon = Icons.Outlined.Warning
        )
    }
}

@Composable
internal fun AnnouncementTypeBadge(
    type: AnnouncementType,
    modifier: Modifier = Modifier,
    iconPadding: Dp = 10.dp,
    iconSize: Dp = 22.dp
) {
    val tone = announcementTone(type)
    Surface(
        modifier = modifier,
        color = tone.containerColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = tone.icon,
            contentDescription = null,
            tint = tone.iconTint,
            modifier = Modifier
                .padding(iconPadding)
                .size(iconSize)
        )
    }
}

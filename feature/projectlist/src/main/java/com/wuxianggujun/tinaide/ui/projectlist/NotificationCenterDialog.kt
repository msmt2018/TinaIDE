package com.wuxianggujun.tinaide.ui.projectlist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogHeader
import com.wuxianggujun.tinaide.ui.compose.components.TinaCustomDialogScaffold
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogActionRow
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaOverlayPanelSurface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationCenterDialog(
    notifications: List<Announcement>,
    onDismiss: () -> Unit,
    onAnnouncementClick: (Announcement) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val filteredNotifications = remember(notifications, unreadOnly) {
        if (unreadOnly) notifications.filterNot(Announcement::isRead) else notifications
    }
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    TinaCustomDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        contentPadding = PaddingValues(0.dp)
    ) {
        TinaCustomDialogScaffold(
            modifier = Modifier.fillMaxSize(),
            header = {
                TinaCustomDialogHeader(
                    title = stringResource(Strings.notification_center_title),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    subtitle = if (notifications.isEmpty()) {
                        stringResource(Strings.notification_center_empty_message)
                    } else {
                        stringResource(Strings.notification_center_summary, notifications.size)
                    },
                    trailingContent = {
                        ProjectDialogActionButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Strings.btn_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ProjectDialogSectionSurface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NotificationFilterChip(
                            selected = !unreadOnly,
                            text = stringResource(Strings.notification_filter_all),
                            onClick = { unreadOnly = false }
                        )
                        NotificationFilterChip(
                            selected = unreadOnly,
                            text = stringResource(Strings.notification_filter_unread, unreadCount),
                            onClick = { unreadOnly = true }
                        )
                    }
                }
            },
            footer = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TinaDialogActionRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    ProjectDialogActionButton(
                        onClick = onMarkAllRead,
                        enabled = unreadCount > 0,
                        color = if (unreadCount > 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(Strings.notification_mark_all_read),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (unreadCount > 0) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            }
                        )
                    }
                }
            }
        ) {
            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ProjectDialogSectionSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = if (notifications.isEmpty()) {
                                stringResource(Strings.notification_center_empty_title)
                            } else {
                                stringResource(Strings.notification_center_empty_filtered_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (notifications.isEmpty()) {
                                stringResource(Strings.notification_center_empty_message)
                            } else {
                                stringResource(Strings.notification_center_empty_filtered_message)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotifications, key = { it.id }) { announcement ->
                        NotificationCenterItem(
                            announcement = announcement,
                            onClick = { onAnnouncementClick(announcement) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    NotificationChip(
        text = text,
        onClick = onClick,
        textStyle = MaterialTheme.typography.labelLarge,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun NotificationCenterItem(
    announcement: Announcement,
    onClick: () -> Unit,
) {
    val previewText = remember(announcement.content) {
        announcement.content
            ?.stripMarkdownPreview()
            ?.takeIf { it.isNotBlank() }
    }
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }
    val retentionText = announcement.expiresAtMillis?.let { expiresAt ->
        stringResource(
            Strings.notification_retention_until,
            formatter.format(Instant.ofEpochMilli(expiresAt))
        )
    } ?: stringResource(Strings.notification_retention_forever)
    val itemShape = RoundedCornerShape(20.dp)

    TinaOverlayPanelSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .clickable(onClick = onClick),
        shape = itemShape,
        containerColor = if (announcement.isRead) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        TinaDialogContentColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnnouncementTypeBadge(type = announcement.type)

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatter.format(Instant.ofEpochMilli(announcement.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!announcement.isRead) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    )
                }
            }

            previewText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationMetaChip(
                    text = if (announcement.isRead) {
                        stringResource(Strings.notification_status_read)
                    } else {
                        stringResource(Strings.notification_status_unread)
                    }
                )
                NotificationMetaChip(text = retentionText)
                if (announcement.isPopup) {
                    NotificationMetaChip(text = stringResource(Strings.notification_popup_badge))
                }
            }

            Text(
                text = stringResource(Strings.notification_open_detail_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onClick)
            )
        }
    }
}

@Composable
private fun NotificationMetaChip(text: String) {
    NotificationChip(
        text = text,
        textStyle = MaterialTheme.typography.labelMedium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun NotificationChip(
    text: String,
    textStyle: TextStyle,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val chipShape = RoundedCornerShape(999.dp)
    Surface(
        color = containerColor,
        shape = chipShape,
        modifier = if (onClick != null) {
            modifier
                .clip(chipShape)
                .clickable(onClick = onClick)
        } else {
            modifier
        }
    ) {
        Text(
            text = text,
            style = textStyle,
            color = contentColor,
            modifier = Modifier.padding(contentPadding)
        )
    }
}

private fun String.stripMarkdownPreview(): String {
    return this
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), " ")
        .replace(Regex("\\[(.*?)]\\((.*?)\\)"), "$1")
        .replace(Regex("[#>*_~]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * Git 提交表情数据
 */
data class GitCommitEmoji(
    val emoji: String,
    val code: String,
    val descriptionRes: Int
)

/**
 * 常用 Git 提交表情列表（基于 gitmoji 规范）
 */
val gitCommitEmojis = listOf(
    GitCommitEmoji("✨", ":sparkles:", Strings.gitmoji_sparkles),
    GitCommitEmoji("🐛", ":bug:", Strings.gitmoji_bug),
    GitCommitEmoji("🔥", ":fire:", Strings.gitmoji_fire),
    GitCommitEmoji("📝", ":memo:", Strings.gitmoji_memo),
    GitCommitEmoji("🎨", ":art:", Strings.gitmoji_art),
    GitCommitEmoji("⚡", ":zap:", Strings.gitmoji_zap),
    GitCommitEmoji("🚀", ":rocket:", Strings.gitmoji_rocket),
    GitCommitEmoji("✅", ":white_check_mark:", Strings.gitmoji_white_check_mark),
    GitCommitEmoji("🔧", ":wrench:", Strings.gitmoji_wrench),
    GitCommitEmoji("➕", ":heavy_plus_sign:", Strings.gitmoji_heavy_plus_sign),
    GitCommitEmoji("➖", ":heavy_minus_sign:", Strings.gitmoji_heavy_minus_sign),
    GitCommitEmoji("🔒", ":lock:", Strings.gitmoji_lock),
    GitCommitEmoji("🏷️", ":label:", Strings.gitmoji_label),
    GitCommitEmoji("💄", ":lipstick:", Strings.gitmoji_lipstick),
    GitCommitEmoji("🚧", ":construction:", Strings.gitmoji_construction),
    GitCommitEmoji("♻️", ":recycle:", Strings.gitmoji_recycle),
    GitCommitEmoji("🗑️", ":wastebasket:", Strings.gitmoji_wastebasket),
    GitCommitEmoji("📦", ":package:", Strings.gitmoji_package),
    GitCommitEmoji("🔀", ":twisted_rightwards_arrows:", Strings.gitmoji_twisted_rightwards_arrows),
    GitCommitEmoji("⬆️", ":arrow_up:", Strings.gitmoji_arrow_up),
    GitCommitEmoji("⬇️", ":arrow_down:", Strings.gitmoji_arrow_down),
    GitCommitEmoji("🔖", ":bookmark:", Strings.gitmoji_bookmark),
    GitCommitEmoji("🚑", ":ambulance:", Strings.gitmoji_ambulance),
    GitCommitEmoji("💚", ":green_heart:", Strings.gitmoji_green_heart)
)

/**
 * Git 提交表情选择器对话框
 */
@Composable
fun GitCommitEmojiPickerDialog(
    onEmojiSelected: (GitCommitEmoji) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TinaDialogTitleText(stringResource(Strings.gitmoji_picker_title))
        },
        text = {
            TinaDialogContentColumn {
                TinaDialogCard(
                    contentModifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gitCommitEmojis) { emoji ->
                            EmojiItem(
                                emoji = emoji,
                                onClick = {
                                    onEmojiSelected(emoji)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_close),
                onClick = onDismiss
            )
        },
        modifier = modifier
    )
}

@Composable
private fun EmojiItem(
    emoji: GitCommitEmoji,
    onClick: () -> Unit
) {
    TinaDialogSelectableCard(
        selected = false,
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        contentPadding = PaddingValues(6.dp),
        unselectedColor = MaterialTheme.colorScheme.surface,
        unselectedBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji.emoji,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(emoji.descriptionRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

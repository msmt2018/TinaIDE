package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuxianggujun.tinaide.core.i18n.Strings

@Composable
internal fun TerminalInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    focusRequester: FocusRequester
) {
    Surface(
        color = Color(0xFF2D2D2D),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                color = TerminalColors.Prompt,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )

            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                                onHistoryUp()
                                true
                            }
                            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                                onHistoryDown()
                                true
                            }
                            else -> false
                        }
                    },
                textStyle = TextStyle(
                    color = TerminalColors.Text,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(TerminalColors.Cursor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            TinaPanelSegmentButton(
                onClick = onSend,
                modifier = Modifier.size(32.dp),
                minHeight = 32.dp,
                shape = MaterialTheme.shapes.small,
                color = Color.White.copy(alpha = 0.08f),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Strings.content_desc_send),
                    tint = TerminalColors.Prompt
                )
            }
        }
    }
}

@Composable
internal fun TerminalStartHint(onStart: () -> Unit) {
    Surface(
        color = Color(0xFF2D2D2D),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Strings.terminal_not_started),
                color = TerminalColors.Text.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.width(16.dp))

            TinaSecondaryButton(
                text = stringResource(Strings.btn_start_shell),
                onClick = onStart
            )
        }
    }
}

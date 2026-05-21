package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.editor.SearchState
import kotlin.math.roundToInt

/**
 * 悬浮搜索框容器
 * 在内容区域上层显示可拖动的搜索框
 */
@Composable
fun FloatingSearchBarContainer(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleCaseSensitive: () -> Unit,
    onToggleRegex: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = searchState.isActive,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            FloatingSearchBar(
                searchState = searchState,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onToggleCaseSensitive = onToggleCaseSensitive,
                onToggleRegex = onToggleRegex,
                onPrevious = onPrevious,
                onNext = onNext,
                onClose = onClose
            )
        }
    }
}

/**
 * 可拖动的悬浮搜索框
 */
@Composable
fun FloatingSearchBar(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleCaseSensitive: () -> Unit,
    onToggleRegex: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var offset by remember {
        mutableStateOf(Offset(0f, with(density) { 8.dp.toPx() }))
    }
    val panelShape = RoundedCornerShape(TinaShapes.CardCorner)

    TinaOverlayPanelSurface(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .padding(end = 8.dp),
        shape = panelShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        SearchBarContent(
            searchState = searchState,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onToggleCaseSensitive = onToggleCaseSensitive,
            onToggleRegex = onToggleRegex,
            onPrevious = onPrevious,
            onNext = onNext,
            onClose = onClose,
            modifier = Modifier.padding(4.dp),
            leading = {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(Strings.content_desc_drag),
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offset = Offset(
                                    x = offset.x + dragAmount.x,
                                    y = offset.y + dragAmount.y
                                )
                            }
                        },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

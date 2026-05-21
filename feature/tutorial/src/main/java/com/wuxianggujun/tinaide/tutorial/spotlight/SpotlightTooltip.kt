package com.wuxianggujun.tinaide.tutorial.spotlight

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.tutorial.data.TooltipPosition
import com.wuxianggujun.tinaide.tutorial.data.TutorialStep
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogActionRow
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaOutlinedButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton

/**
 * Spotlight 提示框组件
 */
@Composable
fun SpotlightTooltip(
    step: TutorialStep,
    targetBounds: Rect,
    currentIndex: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    highlightPadding: Dp = 8.dp,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val tooltipWidth = minOf(screenWidth - 32.dp, 320.dp)
    val tooltipPadding = 16.dp
    val estimatedTooltipHeightPx = with(density) { 200.dp.toPx() }

    var measuredTooltipHeightPx by remember { mutableStateOf<Float?>(null) }

    // 计算提示框位置
    val position = remember(step.position, targetBounds, screenHeight) {
        calculateTooltipPosition(
            requestedPosition = step.position,
            targetBounds = targetBounds,
            screenHeight = with(density) { screenHeight.toPx() },
            screenWidth = with(density) { screenWidth.toPx() },
            highlightPadding = with(density) { highlightPadding.toPx() },
            estimatedTooltipHeight = estimatedTooltipHeightPx
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val offsetX = with(density) {
            val targetCenterX = targetBounds.center.x
            val tooltipWidthPx = tooltipWidth.toPx()
            val screenWidthPx = screenWidth.toPx()

            // 水平居中于目标，但不超出屏幕边界
            val idealX = targetCenterX - tooltipWidthPx / 2
            val clampedX = idealX.coerceIn(
                tooltipPadding.toPx(),
                screenWidthPx - tooltipWidthPx - tooltipPadding.toPx()
            )
            clampedX.toInt()
        }

        val offsetY = with(density) {
            val screenHeightPx = screenHeight.toPx()
            val tooltipHeightPx = measuredTooltipHeightPx ?: estimatedTooltipHeightPx
            val tooltipPaddingPx = tooltipPadding.toPx()
            val highlightPaddingPx = highlightPadding.toPx()
            val gapPx = 16.dp.toPx()

            val rawY = when (position) {
                TooltipPosition.TOP -> targetBounds.top - highlightPaddingPx - gapPx - tooltipHeightPx
                TooltipPosition.BOTTOM -> targetBounds.bottom + highlightPaddingPx + gapPx
                else -> targetBounds.bottom + highlightPaddingPx + gapPx
            }

            rawY.coerceIn(
                minimumValue = tooltipPaddingPx,
                maximumValue = (screenHeightPx - tooltipHeightPx - tooltipPaddingPx).coerceAtLeast(tooltipPaddingPx)
            ).toInt()
        }

        val isLastStep = currentIndex == totalSteps - 1

        SpotlightTooltipPanel(
            title = stringResource(step.titleRes),
            modifier = modifier
                .offset { IntOffset(offsetX, offsetY) }
                .widthIn(max = tooltipWidth)
                .animateContentSize()
                .onGloballyPositioned { coordinates ->
                    measuredTooltipHeightPx = coordinates.size.height.toFloat()
                },
            onClose = onSkip,
            onCloseContentDescription = stringResource(Strings.tutorial_skip),
            footer = {
                TinaDialogActionRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentIndex > 0) {
                        TinaOutlinedButton(
                            text = stringResource(Strings.tutorial_previous),
                            onClick = onPrevious,
                            modifier = Modifier.weight(1f),
                            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack
                        )
                    } else {
                        TinaTextButton(
                            text = stringResource(Strings.tutorial_skip),
                            onClick = onSkip,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TinaPrimaryButton(
                        text = stringResource(
                            if (isLastStep) Strings.tutorial_complete else Strings.tutorial_next
                        ),
                        onClick = onNext,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        ) {
            TinaDialogCard {
                Text(
                    text = stringResource(step.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SpotlightProgressCard(currentIndex = currentIndex, totalSteps = totalSteps)
        }
    }
}

/**
 * 计算提示框的最佳位置
 */
private fun calculateTooltipPosition(
    requestedPosition: TooltipPosition,
    targetBounds: Rect,
    screenHeight: Float,
    screenWidth: Float,
    highlightPadding: Float,
    estimatedTooltipHeight: Float
): TooltipPosition {
    if (requestedPosition != TooltipPosition.AUTO) {
        return requestedPosition
    }

    // 计算目标上方和下方的可用空间
    val spaceAbove = targetBounds.top - highlightPadding
    val spaceBelow = screenHeight - targetBounds.bottom - highlightPadding

    return when {
        spaceBelow >= estimatedTooltipHeight -> TooltipPosition.BOTTOM
        spaceAbove >= estimatedTooltipHeight -> TooltipPosition.TOP
        spaceBelow > spaceAbove -> TooltipPosition.BOTTOM
        else -> TooltipPosition.TOP
    }
}

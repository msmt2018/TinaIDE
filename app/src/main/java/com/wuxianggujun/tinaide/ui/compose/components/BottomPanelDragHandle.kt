package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.logging.GestureTrace
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Compose 版本的拖拽手柄
 *
 * 支持实时跟手拖拽，松手后平滑过渡到目标状态
 */
@Composable
fun DragHandle(
    state: BottomPanelDragState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }

    TinaOverlayPanelSurface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(state) {
                val component = "BottomPanelHandle"
                var lastLogUptimeMillis = 0L
                detectVerticalDragGestures(
                    onDragStart = {
                        state.isDragging = true
                        velocityTracker.resetTracking()
                        if (GestureTrace.isEnabled()) {
                            GestureTrace.w(component, "dragStart height=${state.currentHeight}")
                        }
                    },
                    onDragEnd = {
                        state.isDragging = false
                        val velocity = velocityTracker.calculateVelocity().y
                        if (GestureTrace.isEnabled()) {
                            GestureTrace.w(component, "dragEnd vY=${"%.1f".format(velocity)} height=${state.currentHeight}")
                        }
                        scope.launch {
                            state.settle(velocity)
                        }
                    },
                    onDragCancel = {
                        state.isDragging = false
                        if (GestureTrace.isEnabled()) {
                            GestureTrace.w(component, "dragCancel height=${state.currentHeight}")
                        }
                        scope.launch {
                            state.settle(0f)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (GestureTrace.isEnabled()) {
                            val now = change.uptimeMillis
                            if (now - lastLogUptimeMillis >= 120) {
                                lastLogUptimeMillis = now
                                GestureTrace.d(
                                    component,
                                    "drag dY=${"%.1f".format(dragAmount)} pos=(${"%.1f".format(change.position.x)},${"%.1f".format(change.position.y)}) height=${state.currentHeight}"
                                )
                            }
                        }
                        velocityTracker.addPosition(
                            change.uptimeMillis,
                            change.position
                        )
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            state.dragBy(dragAmount)
                        }
                    }
                )
            },
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

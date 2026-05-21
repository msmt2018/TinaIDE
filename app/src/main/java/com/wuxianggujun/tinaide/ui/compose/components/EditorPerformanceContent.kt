package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.editorview.EditorRenderPerformanceSnapshot
import com.wuxianggujun.tinaide.core.i18n.Strings
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun EditorPerformanceContent(
    snapshotProvider: (() -> EditorRenderPerformanceSnapshot?)?,
    modifier: Modifier = Modifier
) {
    var snapshot by remember(snapshotProvider) {
        mutableStateOf(snapshotProvider?.invoke())
    }

    LaunchedEffect(snapshotProvider) {
        if (snapshotProvider == null) {
            snapshot = null
            return@LaunchedEffect
        }
        while (currentCoroutineContext().isActive) {
            snapshot = snapshotProvider.invoke()
            delay(450L)
        }
    }

    val performanceSnapshot = snapshot
    if (performanceSnapshot == null) {
        EmptyStateContent(
            message = stringResource(Strings.editor_perf_unavailable),
            modifier = modifier
        )
        return
    }

    val frameMetrics = remember(performanceSnapshot) {
        listOf(
            MetricItem(Strings.editor_perf_total_frames, performanceSnapshot.totalRenderedFrames.toString()),
            MetricItem(Strings.editor_perf_slow_frames, performanceSnapshot.slowRenderedFrames.toString()),
            MetricItem(Strings.editor_perf_last_render_ms, performanceSnapshot.lastRenderDurationMs.toString()),
            MetricItem(Strings.editor_perf_last_visible_lines, performanceSnapshot.lastVisibleLineCount.toString()),
            MetricItem(Strings.editor_perf_last_frame_hits, performanceSnapshot.lastFrameCacheHits.toString()),
            MetricItem(Strings.editor_perf_last_frame_misses, performanceSnapshot.lastFrameCacheMisses.toString())
        )
    }
    val cacheMetrics = remember(performanceSnapshot) {
        listOf(
            MetricItem(Strings.editor_perf_total_cache_hits, performanceSnapshot.totalCacheHits.toString()),
            MetricItem(Strings.editor_perf_total_cache_misses, performanceSnapshot.totalCacheMisses.toString()),
            MetricItem(
                Strings.editor_perf_total_cache_hit_rate_percent,
                String.format(Locale.ROOT, "%.1f", performanceSnapshot.totalCacheHitRatePercent)
            ),
            MetricItem(Strings.editor_perf_text_line_cache, performanceSnapshot.textLineCacheSize.toString()),
            MetricItem(Strings.editor_perf_text_scan_cache, performanceSnapshot.textScanCacheSize.toString()),
            MetricItem(Strings.editor_perf_line_layout_entries, performanceSnapshot.lineLayoutCacheEntryCount.toString()),
            MetricItem(Strings.editor_perf_line_layout_floats, performanceSnapshot.lineLayoutCacheFloatCount.toString())
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PerformanceSectionCard(
            title = stringResource(Strings.editor_perf_section_frame),
            metrics = frameMetrics
        )
        PerformanceSectionCard(
            title = stringResource(Strings.editor_perf_section_cache),
            metrics = cacheMetrics
        )
    }
}

@Composable
private fun PerformanceSectionCard(
    title: String,
    metrics: List<MetricItem>,
    modifier: Modifier = Modifier
) {
    TinaOverlayPanelSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            metrics.forEach { metric ->
                PerformanceMetricRow(metric = metric)
            }
        }
    }
}

@Composable
private fun PerformanceMetricRow(
    metric: MetricItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(metric.label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = metric.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

private data class MetricItem(
    val label: Int,
    val value: String
)

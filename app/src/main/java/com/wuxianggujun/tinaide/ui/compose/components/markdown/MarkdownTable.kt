package com.wuxianggujun.tinaide.ui.compose.components.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * GFM 表格渲染
 *
 * 使用 SubcomposeLayout 两阶段测量实现自适应列宽，外层 horizontalScroll 支持横向滚动。
 */
@Composable
internal fun MarkdownTable(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
) {
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }

    val headerCells = headerNode?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { it.getTextInNode(content).toString().trim() }
        ?: emptyList()

    val rows = rowNodes.map { rowNode ->
        rowNode.children
            .filter { it.type == GFMTokenTypes.CELL }
            .map { it.getTextInNode(content).toString().trim() }
    }

    val columnCount = headerCells.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return

    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, borderColor), MaterialTheme.shapes.small)
            .horizontalScroll(rememberScrollState())
    ) {
        SubcomposeLayout { constraints ->
            val cellPaddingPx = 8.dp.roundToPx()
            val minColWidthPx = 60.dp.roundToPx()
            val maxColWidthPx = 200.dp.roundToPx()
            val unbounded = Constraints(0, Constraints.Infinity, 0, Constraints.Infinity)

            // 第一阶段：测量自然尺寸，计算列宽
            val colWidths = IntArray(columnCount) { minColWidthPx }

            // 测量表头
            val headerPlaceables = Array(columnCount) { c ->
                val text = headerCells.getOrElse(c) { "" }
                val measurables = subcompose("h_$c") {
                    TableCell(text, cellPaddingPx.toDp(), fontWeight = FontWeight.SemiBold)
                }
                val placeable = measurables.first().measure(unbounded)
                colWidths[c] = placeable.width.coerceIn(minColWidthPx, maxColWidthPx)
                    .coerceAtLeast(colWidths[c])
                placeable
            }

            // 测量数据行
            val bodyPlaceables = Array(rows.size) { r ->
                Array(columnCount) { c ->
                    val text = rows[r].getOrElse(c) { "" }
                    val measurables = subcompose("b_${r}_$c") {
                        TableCell(text, cellPaddingPx.toDp())
                    }
                    val placeable = measurables.first().measure(unbounded)
                    colWidths[c] = placeable.width.coerceIn(minColWidthPx, maxColWidthPx)
                        .coerceAtLeast(colWidths[c])
                    placeable
                }
            }

            // 第二阶段：用确定的列宽重新测量
            val finalHeaderPlaceables = Array(columnCount) { c ->
                val text = headerCells.getOrElse(c) { "" }
                subcompose("hf_$c") {
                    TableCell(text, cellPaddingPx.toDp(), fontWeight = FontWeight.SemiBold)
                }.first().measure(Constraints.fixedWidth(colWidths[c]))
            }

            val finalBodyPlaceables = Array(rows.size) { r ->
                Array(columnCount) { c ->
                    val text = rows[r].getOrElse(c) { "" }
                    subcompose("bf_${r}_$c") {
                        TableCell(text, cellPaddingPx.toDp())
                    }.first().measure(Constraints.fixedWidth(colWidths[c]))
                }
            }

            // 计算行高
            val headerHeight = finalHeaderPlaceables.maxOfOrNull { it.height } ?: 0
            val rowHeights = IntArray(rows.size) { r ->
                finalBodyPlaceables[r].maxOfOrNull { it.height } ?: 0
            }

            val totalWidth = colWidths.sum()
            val totalHeight = headerHeight + rowHeights.sum()
            val borderPx = 1.dp.roundToPx()

            // 绘制背景和边框的 placeables
            val headerBgPlaceable = subcompose("hbg") {
                Box(Modifier.background(headerBg))
            }.first().measure(Constraints.fixed(totalWidth, headerHeight))

            layout(totalWidth, totalHeight) {
                // 表头背景
                headerBgPlaceable.place(0, 0)

                // 表头单元格
                var x = 0
                finalHeaderPlaceables.forEachIndexed { c, p ->
                    p.place(x, 0)
                    x += colWidths[c]
                }

                // 数据行
                var y = headerHeight
                rows.indices.forEach { r ->
                    x = 0
                    finalBodyPlaceables[r].forEachIndexed { c, p ->
                        p.place(x, y)
                        x += colWidths[c]
                    }
                    y += rowHeights[r]
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    padding: Dp,
    fontWeight: FontWeight? = null,
) {
    Box(
        modifier = Modifier
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .padding(padding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = fontWeight,
        )
    }
}

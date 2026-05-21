package com.wuxianggujun.tinaide.ui.compose.state.editor

internal fun resolveMarkerLine(
    requestedLine: Int,
    lineCount: Int,
    lineTextAt: (Int) -> String
): Int? {
    if (requestedLine !in 0 until lineCount) return null

    if (!isNonMarkerLine(lineTextAt(requestedLine))) {
        return requestedLine
    }

    for (line in requestedLine - 1 downTo 0) {
        if (!isNonMarkerLine(lineTextAt(line))) return line
    }
    for (line in requestedLine + 1 until lineCount) {
        if (!isNonMarkerLine(lineTextAt(line))) return line
    }
    return null
}

private fun isNonMarkerLine(lineText: String): Boolean {
    val trimmed = lineText.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.matches(Regex("""^[{}]+\s*;?$"""))) return true
    return false
}

package com.dotline.launcher.home

import kotlin.math.ceil

/** Pure maths for putting Android app widgets on the Dotline grid (unit-tested). */
object WidgetHostMath {
    /** Cell size assumed when the grid has not been measured yet (a 5 x 6 grid on a 1080 x 2400 screen). */
    private const val FALLBACK_CELL_W_DP = 77f
    private const val FALLBACK_CELL_H_DP = 100f

    /** A widget may ask for a size a few dp above a whole number of cells without needing the next cell. */
    private const val TOLERANCE_DP = 8f

    /**
     * Grid cells (columns, rows) an app widget needs for its minimum size [minWidthDp] x [minHeightDp],
     * at least 1 x 1 and at most the whole grid.
     */
    fun spanFor(
        minWidthDp: Int,
        minHeightDp: Int,
        cellWidthDp: Float,
        cellHeightDp: Float,
        cols: Int,
        rows: Int,
    ): Pair<Int, Int> {
        val cw = if (cellWidthDp.isFinite() && cellWidthDp > 0f) cellWidthDp else FALLBACK_CELL_W_DP
        val ch = if (cellHeightDp.isFinite() && cellHeightDp > 0f) cellHeightDp else FALLBACK_CELL_H_DP
        val spanX = cells(minWidthDp, cw).coerceIn(1, cols.coerceAtLeast(1))
        val spanY = cells(minHeightDp, ch).coerceIn(1, rows.coerceAtLeast(1))
        return spanX to spanY
    }

    private fun cells(minDp: Int, cellDp: Float): Int {
        if (minDp <= 0) return 1
        return ceil((minDp - TOLERANCE_DP) / cellDp).toInt().coerceAtLeast(1)
    }
}

package io.github.baba983.nativedo.ui.topic

internal class StableUserListSheetSizePolicy(
    private val rowHeightPx: Int,
    private val minimumVisibleRows: Int = DEFAULT_MINIMUM_VISIBLE_ROWS,
    private val maximumSizingRows: Int = DEFAULT_MAXIMUM_SIZING_ROWS,
) {
    init {
        require(rowHeightPx > 0)
        require(minimumVisibleRows > 0)
        require(maximumSizingRows >= minimumVisibleRows)
    }

    fun rowsHeight(userCount: Int? = null): Int {
        val rows = normalizedRows(userCount)
        return (rows.toLong() * rowHeightPx)
            .coerceAtMost(MAX_SAFE_CONTENT_HEIGHT_PX.toLong())
            .toInt()
    }

    fun skeletonRows(predictedCount: Int?): Int {
        return normalizedRows(predictedCount)
    }

    private fun normalizedRows(userCount: Int?): Int {
        return maxOf(minimumVisibleRows, userCount?.coerceAtLeast(0) ?: 0)
            .coerceAtMost(maximumSizingRows)
    }

    private companion object {
        private const val DEFAULT_MINIMUM_VISIBLE_ROWS = 5
        private const val DEFAULT_MAXIMUM_SIZING_ROWS = 100
        private const val MAX_SAFE_CONTENT_HEIGHT_PX = Int.MAX_VALUE / 4
    }
}

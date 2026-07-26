package io.github.baba983.nativedo.ui.topic

internal class StableUserListSheetSizePolicy(
    private val rowHeightPx: Int,
    private val fallbackVisibleRows: Int = DEFAULT_FALLBACK_VISIBLE_ROWS,
    private val maximumVisibleRows: Int = DEFAULT_MAXIMUM_VISIBLE_ROWS,
) {
    init {
        require(rowHeightPx > 0)
        require(fallbackVisibleRows > 0)
        require(maximumVisibleRows > 0)
    }

    fun rowsHeight(userCount: Int? = null): Int {
        val rows = normalizedRows(userCount)
        return (rows.toLong() * rowHeightPx)
            .coerceAtMost(MAX_SAFE_CONTENT_HEIGHT_PX.toLong())
            .toInt()
    }

    fun minimumHeight(): Int {
        return rowHeightPx
    }

    fun skeletonRows(predictedCount: Int?): Int {
        return normalizedRows(predictedCount)
    }

    private fun normalizedRows(userCount: Int?): Int {
        return (userCount ?: fallbackVisibleRows)
            .coerceAtLeast(1)
            .coerceAtMost(maximumVisibleRows)
    }

    private companion object {
        private const val DEFAULT_FALLBACK_VISIBLE_ROWS = 6
        private const val DEFAULT_MAXIMUM_VISIBLE_ROWS = 6
        private const val MAX_SAFE_CONTENT_HEIGHT_PX = Int.MAX_VALUE / 4
    }
}

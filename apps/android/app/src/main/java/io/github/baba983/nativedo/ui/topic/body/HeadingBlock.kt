/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer header block cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

internal class HeadingBlock(
    html: String,
    private val level: Int,
    context: PostBodyBuildContext,
) : TextLayoutBlock(
    html = html,
    context = context,
    paint = context.headingPaint,
) {
    override fun topPaddingPx(): Int = when (level) {
        1, 2 -> dp(18f)
        else -> dp(12f)
    }

    override fun bottomPaddingPx(): Int = dp(6f)
}

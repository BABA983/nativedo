/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockParagraphCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body

internal class ParagraphBlock(
    html: String,
    context: PostBodyBuildContext,
) : TextLayoutBlock(
    html = html,
    context = context,
    paint = context.paragraphPaint,
)

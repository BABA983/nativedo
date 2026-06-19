/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockParagraphCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

final class ParagraphBlock extends TextLayoutBlock {
    ParagraphBlock(String html, PostBodyBuildContext context) {
        super(html, context, context.getParagraphPaint());
    }

    ParagraphBlock(String html, PostBodyBuildContext context, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
        super(html, context, context.getParagraphPaint(), inlineState, inlineSpoilerStateKey);
    }
}

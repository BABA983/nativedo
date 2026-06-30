/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer header block cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.text.TextPaint;
import org.telegram.messenger.AndroidUtilities;

final class HeadingBlock extends TextLayoutBlock {
    private static final String TELEGRAM_HEADING_TYPEFACE = "fonts/mw_bold.ttf";

    HeadingBlock(String html, int level, PostBodyBuildContext context) {
        super(html, context, headingPaint(context, level));
    }

    HeadingBlock(String html, int level, PostBodyBuildContext context, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
        super(html, context, headingPaint(context, level), inlineState, inlineSpoilerStateKey);
    }

    private static TextPaint headingPaint(PostBodyBuildContext context, int level) {
        TextPaint paint = new TextPaint(context.getHeadingPaint());
        paint.setTextSize(AndroidUtilities.dp(headingTextSizeDp(level)));
        paint.setTypeface(AndroidUtilities.getTypeface(headingTypefacePath()));
        return paint;
    }

    static float headingTextSizeDp(int level) {
        if (level <= 1) {
            return 23f;
        }
        if (level == 2) {
            return 20f;
        }
        return 17f;
    }

    static String headingTypefacePath() {
        return TELEGRAM_HEADING_TYPEFACE;
    }
}

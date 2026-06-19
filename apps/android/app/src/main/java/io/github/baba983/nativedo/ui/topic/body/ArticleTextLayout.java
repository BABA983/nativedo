/*
 * SPDX-FileCopyrightText: 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package io.github.baba983.nativedo.ui.topic.body;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.DynamicDrawableSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;

final class ArticleTextLayout {
    private ArticleTextLayout() {
    }

    static StaticLayout.Builder builder(CharSequence source, TextPaint paint, int width) {
        if (source.length() > 0 && source.charAt(source.length() - 1) == '\n') {
            source = source.subSequence(0, source.length() - 1);
        }
        source = Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
        return StaticLayout.Builder.obtain(source, 0, source.length(), paint, Math.max(width, 1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(AndroidUtilities.dp(4f), 1.0f)
                .setIncludePad(false);
    }
}

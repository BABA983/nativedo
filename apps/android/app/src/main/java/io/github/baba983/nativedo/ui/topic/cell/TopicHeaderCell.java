/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ArticleViewer.java block header cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.cell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.DynamicDrawableSpan;
import android.view.View;
import java.util.List;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;

public final class TopicHeaderCell extends View implements Theme.Colorable {
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private CharSequence titleText = "";
    private CharSequence metaText = "";
    private StaticLayout titleLayout;
    private StaticLayout metaLayout;
    private final int textX = AndroidUtilities.dp(18f);
    private final int titleY = AndroidUtilities.dp(14f);
    private final int metaSpacing = AndroidUtilities.dp(8f);
    private final int bottomPadding = AndroidUtilities.dp(12f);

    public TopicHeaderCell(Context context) {
        super(context);
        titlePaint.setTextSize(AndroidUtilities.dp(22f));
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        metaPaint.setTextSize(AndroidUtilities.dp(13f));
        updateColors();
    }

    public void bind(String title, int totalPosts, int viewCount, List<String> tags) {
        titleText = title != null ? title : "";
        StringBuilder meta = new StringBuilder();
        meta.append(totalPosts).append(" posts");
        if (viewCount > 0) {
            meta.append(" · ").append(viewCount).append(" views");
        }
        if (tags != null && !tags.isEmpty()) {
            meta.append(" · ");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    meta.append(" / ");
                }
                meta.append(tags.get(i));
            }
        }
        metaText = meta;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int textWidth = Math.max(width - AndroidUtilities.dp(36f), AndroidUtilities.dp(120f));
        CharSequence titleSource = replaceEmoji(titleText, titlePaint);
        CharSequence metaSource = replaceEmoji(metaText, metaPaint);
        titleLayout = StaticLayout.Builder.obtain(titleSource, 0, titleSource.length(), titlePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(AndroidUtilities.dp(4f), 1f)
                .setIncludePad(false)
                .build();
        metaLayout = StaticLayout.Builder.obtain(metaSource, 0, metaSource.length(), metaPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(AndroidUtilities.dp(4f), 1f)
                .setIncludePad(false)
                .build();
        int height = titleY + titleLayout.getHeight() + metaSpacing + metaLayout.getHeight() + bottomPadding;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(textX, titleY);
            titleLayout.draw(canvas);
            canvas.restore();
        }
        if (metaLayout != null) {
            canvas.save();
            canvas.translate(textX, titleY + (titleLayout != null ? titleLayout.getHeight() : 0) + metaSpacing);
            metaLayout.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public void updateColors() {
        titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        metaPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        invalidate();
    }

    private static CharSequence replaceEmoji(CharSequence text, TextPaint paint) {
        CharSequence source = text == null || text.length() == 0 ? " " : text;
        return Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
    }
}

/*
 * This file is part of NativeDo.
 *
 * Provides a NativeDo-scoped subset of Telegram for Android's ReplyMessageLine
 * drawing API for quote/reply chrome ports.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ReplyMessageLine {

    public static final int TYPE_REPLY = 0;
    public static final int TYPE_QUOTE = 1;
    public static final int TYPE_CODE = 2;
    public static final int TYPE_LINK = 3;
    public static final int TYPE_CONTACT = 4;

    private final View parentView;
    private final RectF rectF = new RectF();
    private final Path clipPath = new Path();
    private final Path backgroundPath = new Path();
    private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final float[] radii = new float[8];

    public boolean hasColor2, hasColor3;
    public int backgroundColor, nameColor, color1, color2, color3, emojiColor;

    public ReplyMessageLine(View view) {
        parentView = view;
        check(null, null, null, null, TYPE_REPLY);
    }

    public int getColor() {
        return color1;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int check(
            MessageObject messageObject,
            TLRPC.User currentUser,
            TLRPC.Chat currentChat,
            Theme.ResourcesProvider resourcesProvider,
            final int type
    ) {
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        hasColor2 = false;
        hasColor3 = false;
        if (type == TYPE_LINK) {
            color1 = color2 = color3 = Theme.getColor(Theme.key_chat_inPreviewLine, resourcesProvider);
        } else {
            color1 = color2 = color3 = Theme.getColor(Theme.key_chat_inReplyLine, resourcesProvider);
        }
        backgroundColor = Theme.multAlpha(color1, type == TYPE_LINK ? 0.10f : (dark ? 0.12f : 0.10f));
        nameColor = type == TYPE_LINK ? color1 : Theme.getColor(Theme.key_chat_inReplyNameText, resourcesProvider);
        emojiColor = getColor();
        return nameColor;
    }

    public boolean hasSticker() {
        return false;
    }

    public void drawLine(Canvas canvas, RectF rect) {
        drawLine(canvas, rect, 1f);
    }

    public void drawLine(Canvas canvas, RectF rect, float alpha) {
        canvas.save();

        clipPath.rewind();
        final int rad = (int) Math.floor(SharedConfig.bubbleRadius / 3f);
        rectF.set(rect.left, rect.top, rect.left + Math.max(dp(3), dp(2 * rad)), rect.bottom);
        clipPath.addRoundRect(rectF, dp(rad), dp(rad), Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.clipRect(rect.left, rect.top, rect.left + dp(3), rect.bottom);

        color1Paint.setColor(Theme.multAlpha(color1, alpha));
        canvas.drawPaint(color1Paint);

        canvas.restore();
    }

    public void drawBackground(Canvas canvas, RectF rect, float leftRad, float rightRad, float bottomRad, float alpha) {
        drawBackground(canvas, rect, leftRad, rightRad, bottomRad, alpha, false, false);
    }

    public void drawBackground(
            Canvas canvas,
            RectF rect,
            float leftRad,
            float rightRad,
            float bottomRad,
            float alpha,
            boolean hasQuote,
            boolean emojiOnly
    ) {
        int linkPreviewRad = dp((int) Math.floor(SharedConfig.bubbleRadius / 3f));
        radii[0] = radii[1] = Math.max(linkPreviewRad, dp(leftRad));
        radii[2] = radii[3] = dp(rightRad);
        radii[4] = radii[5] = dp(bottomRad);
        radii[6] = radii[7] = Math.max(linkPreviewRad, dp(bottomRad));
        drawBackground(canvas, rect, alpha, hasQuote, emojiOnly);
    }

    public void drawBackground(Canvas canvas, RectF rect, float alpha) {
        drawBackground(canvas, rect, alpha, false, false);
    }

    public void drawBackground(Canvas canvas, RectF rect, float alpha, boolean hasQuote, boolean emojiOnly) {
        if (emojiOnly) {
            return;
        }
        backgroundPath.rewind();
        backgroundPath.addRoundRect(rect, radii, Path.Direction.CW);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * alpha));
        canvas.drawPath(backgroundPath, backgroundPaint);
    }
}

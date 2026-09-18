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
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.RadiiUtils;
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
    private final Path backgroundPath = new Path();
    private final Paint color1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final float[] radii = new float[8];
    private final Paint patternPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap patternBitmap;
    private int cachedBg, cachedBar2, cachedBar3, cachedBarHeight;
    private boolean cachedHasColor3;

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

    /** Applies a flat color without changing the defaults used by {@link #check}. */
    public void setSimpleColor(int color, boolean dark) {
        hasColor2 = hasColor3 = false;
        color1 = color2 = color3 = color;
        backgroundColor = Theme.multAlpha(color, dark ? 0.12f : 0.10f);
        emojiColor = color;
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
        final int rad = (int) Math.floor(SharedConfig.bubbleRadius / 3f);
        final float lineRight = rect.left + Math.max(dp(3), dp(2 * rad));

        color1Paint.setColor(Theme.multAlpha(color1, alpha));

        if (!hasColor2) {
            // single color
            rectF.set(rect.left, rect.top, lineRight, rect.bottom);
            canvas.save();
            canvas.clipRect(rectF.left, rectF.top, rectF.left + dp(3), rectF.bottom);
            canvas.drawRoundRect(rectF, dp(rad), dp(rad), color1Paint);
            canvas.restore();
        } else {
            // multi color: one tile holds every color -> one draw call
            canvas.save();
            canvas.translate(rect.left, rect.top);

            checkPatternBitmap(color1, color2, color3, 1f, hasColor3 ? 1f : 0f, alpha);
            patternPaint.setAlpha(0xFF);
            rectF.set(0, 0, lineRight - rect.left, rect.bottom - rect.top);
            canvas.clipRect(rectF.left, rectF.top, rectF.left + dp(3), rectF.bottom);
            canvas.drawRoundRect(rectF, dp(rad), dp(rad), patternPaint);

            canvas.restore();
        }
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
        backgroundPaint.setColor(Theme.multAlpha(backgroundColor, alpha));
        if (RadiiUtils.radiiAreSame(radii)) {
            canvas.drawRoundRect(rect, radii[0], radii[0], backgroundPaint);
        } else {
            backgroundPath.rewind();
            backgroundPath.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(backgroundPath, backgroundPaint);
        }
    }

    // Rebuilds the pattern tile when any baked-in value changes. Colors are
    // pre-composited on this side, so the writer below only lays down flat pixels.
    private void checkPatternBitmap(int c1, int c2, int c3, float c2a, float c3a, float alpha) {
        final int bg = Theme.multAlpha(c1, alpha);
        final int bar2 = ColorUtils.compositeColors(Theme.multAlpha(c2, alpha * c2a), bg);
        final int bar3 = hasColor3
                ? ColorUtils.compositeColors(Theme.multAlpha(c3, alpha * c3a), bg) : 0;
        final int barHeight = Math.round(dpf2(6.33f));

        final int width = Math.max(1, dp(3));
        final int period = hasColor3
                ? dp(6.33f + 6.33f + 3 + 3.33f)
                : dp(6.33f + 3 + 3.33f);

        if (patternBitmap != null
                && cachedBg == bg && cachedBar2 == bar2 && cachedBar3 == bar3
                && cachedHasColor3 == hasColor3 && cachedBarHeight == barHeight
                && patternBitmap.getWidth() == width
                && patternBitmap.getHeight() == period) {
            return;
        }
        cachedBg = bg;
        cachedBar2 = bar2;
        cachedBar3 = bar3;
        cachedHasColor3 = hasColor3;
        cachedBarHeight = barHeight;

        if (patternBitmap == null || patternBitmap.getWidth() != width || patternBitmap.getHeight() != period) {
            if (patternBitmap != null) {
                patternBitmap.recycle();
            }
            patternBitmap = Bitmap.createBitmap(width, period, Bitmap.Config.ARGB_8888);
            patternPaint.setShader(new BitmapShader(patternBitmap, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT));
        }
        Utilities.drawReplyLinePattern(patternBitmap, bg, bar2, bar3, barHeight, hasColor3);
    }
}

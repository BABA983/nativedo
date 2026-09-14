/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatActionCell.java.
 * This date-only cell keeps its custom-date measurement and service-bubble drawing geometry.
 */
package io.github.baba983.nativedo.ui.topic.cell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

public class TopicDateCell extends View implements Theme.Colorable {
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path backgroundPath = new Path();
    private final RectF backgroundRect = new RectF();
    private final Paint shaderOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean floating;

    private StaticLayout textLayout;
    private CharSequence customText = "";
    private long customDate;
    private int previousWidth;
    private int textWidth;
    private int textHeight;
    private int textXLeft;
    private final int textY = AndroidUtilities.dp(7f);
    private float floatingTopClip;

    public TopicDateCell(@NonNull Context context, boolean floating) {
        super(context);
        this.floating = floating;
        textPaint.setTypeface(AndroidUtilities.bold());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        updateColors();
    }

    @Override
    public void updateColors() {
        textPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        textPaint.linkColor = textPaint.getColor();
        // Force layout rebuild so StaticLayout uses the new TextPaint color.
        if (previousWidth > 0 && !TextUtils.isEmpty(customText)) {
            createLayout(previousWidth);
        }
        invalidate();
    }

    public void setCustomDate(long date) {
        CharSequence text = LocaleController.formatDateChat(date);
        if ((customDate == date || customDate / 3600L == date / 3600L) && TextUtils.equals(customText, text)) {
            return;
        }
        customDate = date;
        if (TextUtils.equals(customText, text)) {
            return;
        }
        customText = text;
        setContentDescription(text);
        if (getMeasuredWidth() > 0) {
            createLayout(getMeasuredWidth());
        }
        requestLayout();
        invalidate();
    }

    public long getCustomDate() {
        return customDate;
    }

    public void setFloatingTopClip(float floatingTopClip) {
        float nextClip = Math.max(0f, floatingTopClip);
        if (this.floatingTopClip != nextClip) {
            this.floatingTopClip = nextClip;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.max(AndroidUtilities.dp(30f), MeasureSpec.getSize(widthMeasureSpec));
        if (textLayout == null || previousWidth != width) {
            createLayout(width);
        }
        setMeasuredDimension(width, textHeight + AndroidUtilities.dp(14f));
    }

    private void createLayout(int width) {
        previousWidth = width;
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2f));
        textPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        textPaint.linkColor = textPaint.getColor();
        int maxWidth = Math.max(1, width - AndroidUtilities.dp(30f));
        textLayout = new StaticLayout(customText, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0, false);
        textWidth = 0;
        textHeight = 0;
        for (int line = 0; line < textLayout.getLineCount(); line++) {
            textWidth = Math.max(textWidth, (int) Math.ceil(textLayout.getLineWidth(line)));
            textHeight = Math.max(textHeight, (int) Math.ceil(textLayout.getLineBottom(line)));
        }
        textXLeft = (width - textLayout.getWidth()) / 2;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (textLayout == null) {
            return;
        }
        int restoreCount = canvas.save();
        if (floating && floatingTopClip > 0f) {
            canvas.clipRect(0f, floatingTopClip, getWidth(), getHeight());
        }
        float left = (getWidth() - textWidth) / 2f - AndroidUtilities.dp(8f);
        float right = (getWidth() + textWidth) / 2f + AndroidUtilities.dp(8f);
        float top = AndroidUtilities.dp(4f);
        float bottom = top + textHeight + AndroidUtilities.dp(6f);
        backgroundRect.set(left, top, right, bottom);
        backgroundPath.rewind();
        backgroundPath.addRoundRect(
                backgroundRect,
                AndroidUtilities.dp(11f),
                AndroidUtilities.dp(11f),
                Path.Direction.CW);
        backgroundPath.close();

        Paint backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground);
        Paint darkenBackgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken);
        int backgroundHeight = getParent() instanceof View
                ? ((View) getParent()).getMeasuredHeight()
                : getMeasuredHeight();
        Theme.applyServiceShaderMatrix(
                getMeasuredWidth(),
                backgroundHeight,
                getX(),
                getY() + AndroidUtilities.dp(4f));
        int oldAlpha = backgroundPaint.getAlpha();
        int oldDarkenAlpha = darkenBackgroundPaint.getAlpha();
        if (floating) {
            backgroundPaint.setAlpha((int) (oldAlpha * .75f));
            darkenBackgroundPaint.setAlpha((int) (oldDarkenAlpha * .75f));
        }
        canvas.drawPath(backgroundPath, backgroundPaint);
        // Telegram's wallpaper shader intentionally follows the wallpaper. On a
        // light pattern that can make the service chip visually disappear, so keep
        // the theme's translucent service color as a contrast layer above it.
        if (backgroundPaint.getShader() != null) {
            int themeColor = Theme.getColor(Theme.key_chat_serviceBackground);
            int overlayAlpha = android.graphics.Color.alpha(themeColor);
            if (overlayAlpha == 0) {
                overlayAlpha = 0x66;
            }
            // Paint#setAlpha is multiplied with the alpha in the color. Store an
            // opaque RGB color here so the theme alpha is applied exactly once.
            int overlayColor = android.graphics.Color.rgb(
                    android.graphics.Color.red(themeColor),
                    android.graphics.Color.green(themeColor),
                    android.graphics.Color.blue(themeColor));
            overlayAlpha = Math.min(0x88, overlayAlpha);
            overlayAlpha = (int) (overlayAlpha * (floating ? .75f : 1f));
            shaderOverlayPaint.setColor(overlayColor);
            shaderOverlayPaint.setAlpha(overlayAlpha);
            canvas.drawPath(backgroundPath, shaderOverlayPaint);
        }
        if (Theme.hasGradientService() && darkenBackgroundPaint.getAlpha() > 0) {
            canvas.drawPath(backgroundPath, darkenBackgroundPaint);
        }
        backgroundPaint.setAlpha(oldAlpha);
        darkenBackgroundPaint.setAlpha(oldDarkenAlpha);

        textPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        canvas.save();
        canvas.translate(textXLeft, textY);
        textLayout.draw(canvas);
        canvas.restore();
        canvas.restoreToCount(restoreCount);
    }
}

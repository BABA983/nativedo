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
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF backgroundRect = new RectF();
    private final boolean floating;

    private StaticLayout textLayout;
    private CharSequence customText = "";
    private long customDate;
    private int previousWidth;
    private int textWidth;
    private int textHeight;
    private int textXLeft;
    private final int textY = AndroidUtilities.dp(7f);

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
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
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
        float left = (getWidth() - textWidth) / 2f - AndroidUtilities.dp(8f);
        float right = (getWidth() + textWidth) / 2f + AndroidUtilities.dp(8f);
        float top = AndroidUtilities.dp(4f);
        float bottom = top + textHeight + AndroidUtilities.dp(6f);
        backgroundRect.set(left, top, right, bottom);
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
        backgroundPaint.setAlpha(floating ? Math.round(255f * .75f) : 255);
        canvas.drawRoundRect(
                backgroundRect,
                AndroidUtilities.dp(11f),
                AndroidUtilities.dp(11f),
                backgroundPaint
        );

        textPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        canvas.save();
        canvas.translate(textXLeft, textY);
        textLayout.draw(canvas);
        canvas.restore();
    }
}

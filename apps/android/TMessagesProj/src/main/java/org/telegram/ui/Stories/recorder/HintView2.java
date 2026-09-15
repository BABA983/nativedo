/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.TypefaceSpan;

public class HintView2 extends View {

    public static final int DIRECTION_BOTTOM = 0;
    public static final int DIRECTION_TOP = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;

    public HintView2(Context context, int direction) {
        super(context);
    }

    public HintView2 setText(CharSequence text) { return this; }
    public HintView2 setMaxWidthPx(int px) { return this; }
    public HintView2 setMultilineText(boolean multiline) { return this; }
    public HintView2 setRounding(float rounding) { return this; }
    @Override
    public void setPadding(int left, int top, int right, int bottom) { super.setPadding(left, top, right, bottom); }
    public HintView2 setJointPx(float jointTranslation, float joint) { return this; }
    public HintView2 setDirection(int dir) { return this; }
    public HintView2 setBgColor(int color) { return this; }
    public HintView2 setTextColor(int color) { return this; }
    public HintView2 setCloseButton(boolean enabled) { return this; }
    public HintView2 setIcon(int resId) { return this; }
    public HintView2 setDuration(long ms) { return this; }
    public HintView2 setHideByTouch(boolean h) { return this; }
    public HintView2 setOnHiddenListener(Runnable r) { return this; }
    public boolean show() { return true; }
    public void hide() {}

    public static float measureCorrectly(CharSequence text, Paint paint) {
        if (text == null) {
            return 0;
        }
        if (!(text instanceof Spanned)) {
            return paint.measureText(text.toString());
        }
        Spanned spanned = (Spanned) text;
        TypefaceSpan[] spans = spanned.getSpans(0, text.length(), TypefaceSpan.class);
        ReplacementSpan[] replacementSpans = spanned.getSpans(0, text.length(), ReplacementSpan.class);
        int add = 0;
        for (int i = 0; i < replacementSpans.length; ++i) {
            ReplacementSpan span = replacementSpans[i];
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            add += Math.max(0, span.getSize(paint, text, start, end, paint.getFontMetricsInt()) - paint.measureText(spanned, start, end));
        }
        if (spans == null || spans.length == 0) {
            return paint.measureText(text.toString()) + add;
        }
        float len = 0;
        int s = 0, e;
        for (int i = 0; i < spans.length; ++i) {
            final int spanstart = spanned.getSpanStart(spans[i]);
            final int spanend = spanned.getSpanEnd(spans[i]);

            e = Math.max(s, spanstart);
            if (e - s > 0) {
                len += paint.measureText(spanned, s, e);
            }
            s = e;
            e = Math.max(s, spanend);
            if (e - s > 0) {
                Typeface oldTypeface = paint.getTypeface();
                paint.setTypeface(spans[i].getTypeface());
                len += paint.measureText(spanned, s, e);
                paint.setTypeface(oldTypeface);
            }
            s = e;
        }
        e = Math.max(s, text.length());
        if (e - s > 0) {
            len += paint.measureText(spanned, s, e);
        }
        return len + add;
    }

    public static int cutInFancyHalf(CharSequence text, TextPaint paint) {
        if (TextUtils.indexOf(text, '\n') >= 0) {
            return Integer.MAX_VALUE;
        }
        int mid = text.length() / 2;
        float leftWidth = 0, rightWidth = 0;
        float prevLeftWidth = 0;
        float prevRightWidth = Float.MAX_VALUE;

        int dir = -1;
        for (int i = 0; i < 10; ++i) {
            while (mid > 0 && mid < text.length() && text.charAt(mid) != ' ') {
                mid += dir;
            }

            leftWidth = measureCorrectly(text.subSequence(0, mid), paint);
            rightWidth = measureCorrectly(AndroidUtilities.getTrimmedString(text.subSequence(mid, text.length())), paint);

            if (leftWidth == prevLeftWidth && rightWidth == prevRightWidth) {
                break;
            }

            prevLeftWidth = leftWidth;
            prevRightWidth = rightWidth;

            if (leftWidth < rightWidth) {
                dir = +1;
                mid += dir;
            } else {
                dir = -1;
                mid += dir;
            }

            if (mid <= 0 || mid >= text.length()) {
                break;
            }
        }

        return (int) Math.ceil(Math.max(leftWidth, rightWidth));
    }
}

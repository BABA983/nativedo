/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

public class TextCell extends FrameLayout {

    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView valueView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int leftPadding = 20;
    private int rightPadding = 23;
    private boolean needDivider;
    private boolean subtitleVisible;
    private boolean isRTL;
    private final Theme.ResourcesProvider resourcesProvider;

    public TextCell(Context context) {
        this(context, null);
    }

    public TextCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        setForeground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        setWillNotDraw(false);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        addView(iconView);

        titleView = new TextView(context);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(titleView);

        subtitleView = new TextView(context);
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(subtitleView);

        valueView = new TextView(context);
        valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        addView(valueView);

        dividerPaint.setColor(getThemedColor(Theme.key_divider));
        isRTL = LocaleController.isRTL;
    }

    public TextView getTextView() {
        return titleView;
    }

    public ImageView getImageView() {
        return iconView;
    }

    public void setColors(int icon, int text) {
        titleView.setTextColor(getThemedColor(text));
        if (icon >= 0) {
            iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(icon), PorterDuff.Mode.SRC_IN));
        }
    }

    public void setText(CharSequence text, boolean divider) {
        titleView.setText(text);
        subtitleVisible = false;
        subtitleView.setVisibility(GONE);
        valueView.setText(null);
        valueView.setVisibility(GONE);
        iconView.setVisibility(GONE);
        leftPadding = 20;
        rightPadding = 23;
        needDivider = divider;
        requestLayout();
        invalidate();
    }

    public void setTextAndIcon(CharSequence text, int resId, boolean divider) {
        titleView.setText(text);
        subtitleVisible = false;
        subtitleView.setVisibility(GONE);
        valueView.setText(null);
        valueView.setVisibility(GONE);
        iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        iconView.setImageResource(resId);
        iconView.setVisibility(VISIBLE);
        leftPadding = 71;
        rightPadding = 23;
        needDivider = divider;
        requestLayout();
        invalidate();
    }

    public void setTextAndIcon(CharSequence text, Drawable drawable, boolean divider) {
        titleView.setText(text);
        subtitleVisible = false;
        subtitleView.setVisibility(GONE);
        valueView.setText(null);
        valueView.setVisibility(GONE);
        iconView.setColorFilter(null);
        iconView.setImageDrawable(drawable);
        iconView.setVisibility(VISIBLE);
        leftPadding = 71;
        rightPadding = 23;
        needDivider = divider;
        requestLayout();
        invalidate();
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        titleView.setText(text);
        subtitleVisible = false;
        subtitleView.setVisibility(GONE);
        valueView.setText(value);
        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        iconView.setVisibility(GONE);
        leftPadding = 20;
        rightPadding = TextUtils.isEmpty(value) ? 23 : 150;
        needDivider = divider;
        requestLayout();
        invalidate();
    }

    public void setTextAndValueAndIcon(CharSequence text, CharSequence value, int resId, boolean divider) {
        titleView.setText(text);
        subtitleVisible = false;
        subtitleView.setVisibility(GONE);
        valueView.setText(value);
        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        iconView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        iconView.setImageResource(resId);
        iconView.setVisibility(VISIBLE);
        leftPadding = 71;
        rightPadding = TextUtils.isEmpty(value) ? 23 : 150;
        needDivider = divider;
        requestLayout();
        invalidate();
    }

    public void setTextAndValueAndIcon(CharSequence text, CharSequence value, Drawable drawable, boolean divider) {
        titleView.setText(text);
        subtitleVisible = false;
        subtitleView.setVisibility(GONE);
        valueView.setText(value);
        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        iconView.setColorFilter(null);
        iconView.setImageDrawable(drawable);
        iconView.setVisibility(VISIBLE);
        leftPadding = 71;
        rightPadding = TextUtils.isEmpty(value) ? 23 : 150;
        needDivider = divider;
        requestLayout();
        invalidate();
    }

    public void setValue(CharSequence value, boolean animated) {
        valueView.setText(value);
        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        rightPadding = TextUtils.isEmpty(value) ? 23 : 150;
        requestLayout();
        invalidate();
    }

    public void setSubtitle(CharSequence subtitle) {
        subtitleVisible = !TextUtils.isEmpty(subtitle);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitleVisible ? VISIBLE : GONE);
        requestLayout();
        invalidate();
    }

    public static CharSequence applyNewSpan(CharSequence str) {
        return str;
    }

    public static CharSequence applyNewSpan(CharSequence str, boolean usePaintAlpha) {
        return str;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(subtitleVisible ? 64 : 50);
        int textWidth = Math.max(0, width - AndroidUtilities.dp(leftPadding + rightPadding));
        titleView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY));
        subtitleView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        valueView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(120), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        iconView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int textWidth = titleView.getMeasuredWidth();
        int textLeft = isRTL ? getMeasuredWidth() - AndroidUtilities.dp(leftPadding) - textWidth : AndroidUtilities.dp(leftPadding);
        if (subtitleVisible) {
            titleView.layout(textLeft, AndroidUtilities.dp(11), textLeft + textWidth, AndroidUtilities.dp(33));
            subtitleView.layout(textLeft, AndroidUtilities.dp(34), textLeft + subtitleView.getMeasuredWidth(), AndroidUtilities.dp(54));
        } else {
            int titleTop = (height - titleView.getMeasuredHeight()) / 2;
            titleView.layout(textLeft, titleTop, textLeft + textWidth, titleTop + titleView.getMeasuredHeight());
            subtitleView.layout(textLeft, titleTop, textLeft, titleTop);
        }
        int valueLeft = isRTL
                ? AndroidUtilities.dp(23)
                : getMeasuredWidth() - AndroidUtilities.dp(23) - valueView.getMeasuredWidth();
        valueView.layout(valueLeft, 0, valueLeft + valueView.getMeasuredWidth(), height);
        int iconLeft = isRTL ? getMeasuredWidth() - iconView.getMeasuredWidth() : 0;
        iconView.layout(iconLeft, (height - iconView.getMeasuredHeight()) / 2, iconLeft + iconView.getMeasuredWidth(), (height + iconView.getMeasuredHeight()) / 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            int start = AndroidUtilities.dp(iconView.getVisibility() == VISIBLE ? 68 : 20);
            if (isRTL) {
                canvas.drawRect(0, getMeasuredHeight() - 1, getMeasuredWidth() - start, getMeasuredHeight(), dividerPaint);
            } else {
                canvas.drawRect(start, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), dividerPaint);
            }
        }
    }

    public void updateRTL() {
        if (isRTL == LocaleController.isRTL) {
            return;
        }
        isRTL = LocaleController.isRTL;
        titleView.setGravity((isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        subtitleView.setGravity((isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        valueView.setGravity((isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        requestLayout();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

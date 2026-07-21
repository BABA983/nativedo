/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;

public class TextCheckCell extends FrameLayout {

    public int itemId;

    private final TextView titleView;
    private final Switch switchView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean needDivider;
    private int height = 50;
    private boolean drawCheckRipple;

    private int animatedColorBackground;
    private float animationProgress;
    private Paint animationPaint;
    private float lastTouchX;
    private ObjectAnimator animator;

    private final Theme.ResourcesProvider resourcesProvider;
    private boolean attached;
    private boolean isRTL;

    public TextCheckCell(Context context) {
        this(context, null);
    }

    public TextCheckCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        setForeground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        setWillNotDraw(false);

        titleView = new TextView(context);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(titleView);

        switchView = new Switch(context, resourcesProvider);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(switchView);

        dividerPaint.setColor(getThemedColor(Theme.key_divider));
        isRTL = LocaleController.isRTL;
    }

    public void setTextAndCheck(CharSequence text, boolean checked, boolean divider) {
        titleView.setText(text);
        switchView.setChecked(checked, attached);
        needDivider = divider;
        invalidate();
    }

    public void setChecked(boolean checked) {
        switchView.setChecked(checked, true);
    }

    public boolean isChecked() {
        return switchView.isChecked();
    }

    public void setDivider(boolean divider) {
        needDivider = divider;
        invalidate();
    }

    public void setDrawCheckRipple(boolean value) {
        drawCheckRipple = value;
        switchView.setDrawRipple(value);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        switchView.setEnabled(enabled);
    }

    public void setHeight(int value) {
        height = value;
    }

    public void setTypeface(Typeface typeface) {
        titleView.setTypeface(typeface);
    }

    public void setColors(int key, int switchKey, int switchKeyChecked, int switchThumb, int switchThumbChecked) {
        titleView.setTextColor(getThemedColor(key));
        switchView.setColors(switchKey, switchKeyChecked, switchThumb, switchThumbChecked);
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        super.setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(titleView, View.ALPHA, value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(switchView, View.ALPHA, value ? 1.0f : 0.5f));
        } else {
            titleView.setAlpha(value ? 1.0f : 0.5f);
            switchView.setAlpha(value ? 1.0f : 0.5f);
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        lastTouchX = event.getX();
        return super.onTouchEvent(event);
    }

    @Override
    public void setBackgroundColor(int color) {
        if (animatedColorBackground != color) {
            clearAnimation();
            animatedColorBackground = 0;
            super.setBackgroundColor(color);
        }
    }

    public void setBackgroundColorAnimated(boolean checked, int color) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (animatedColorBackground != 0) {
            setBackgroundColor(animatedColorBackground);
        }
        if (animationPaint == null) {
            animationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        switchView.setOverrideColor(checked ? 1 : 2);
        animatedColorBackground = color;
        animationPaint.setColor(animatedColorBackground);
        animationProgress = 0.0f;
        animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 0.0f, 1.0f);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatedColorBackground = 0;
                setBackgroundColor(color);
                invalidate();
            }
        });
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animator.setDuration(240).start();
    }

    private static final android.util.Property<TextCheckCell, Float> ANIMATION_PROGRESS = new android.util.Property<TextCheckCell, Float>(Float.class, "animationProgress") {
        @Override
        public void set(TextCheckCell object, Float value) {
            object.setAnimationProgress(value);
        }

        @Override
        public Float get(TextCheckCell object) {
            return object.animationProgress;
        }
    };

    private void setAnimationProgress(float value) {
        animationProgress = value;
        float tx = lastTouchX;
        float rad = Math.max(tx, getMeasuredWidth() - tx) + AndroidUtilities.dp(40);
        switchView.setOverrideColorProgress(tx, getMeasuredHeight() / 2, rad * animationProgress);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int h = AndroidUtilities.dp(height);
        titleView.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, width - AndroidUtilities.dp(92)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        );
        switchView.measure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(38), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY)
        );
        setMeasuredDimension(width, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int h = bottom - top;
        int titleLeft = isRTL
                ? getMeasuredWidth() - AndroidUtilities.dp(21) - titleView.getMeasuredWidth()
                : AndroidUtilities.dp(21);
        titleView.layout(titleLeft, 0, titleLeft + titleView.getMeasuredWidth(), h);
        int switchLeft = isRTL
                ? AndroidUtilities.dp(21)
                : getMeasuredWidth() - AndroidUtilities.dp(21) - switchView.getMeasuredWidth();
        int switchTop = (h - switchView.getMeasuredHeight()) / 2;
        switchView.layout(switchLeft, switchTop, switchLeft + switchView.getMeasuredWidth(), switchTop + switchView.getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (animatedColorBackground != 0) {
            float tx = lastTouchX;
            float rad = Math.max(tx, getMeasuredWidth() - tx) + AndroidUtilities.dp(40);
            int cy = getMeasuredHeight() / 2;
            float animatedRad = rad * animationProgress;
            canvas.drawCircle(tx, cy, animatedRad, animationPaint);
        }
        super.onDraw(canvas);
        if (needDivider) {
            int start = isRTL ? 0 : AndroidUtilities.dp(20);
            int end = isRTL ? getMeasuredWidth() - AndroidUtilities.dp(20) : getMeasuredWidth();
            canvas.drawRect(start, getMeasuredHeight() - 1, end, getMeasuredHeight(), dividerPaint);
        }
    }

    public void updateRTL() {
        if (isRTL == LocaleController.isRTL) {
            return;
        }
        isRTL = LocaleController.isRTL;
        titleView.setGravity((isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(switchView.isChecked());
        info.setContentDescription(titleView.getText());
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

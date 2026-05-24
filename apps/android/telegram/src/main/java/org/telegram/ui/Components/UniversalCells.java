/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android browser/settings cells.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public final class UniversalCells {

    private UniversalCells() {
    }

    public static final class SectionCell extends FrameLayout {
        private final TextView textView;
        private final boolean header;

        public SectionCell(Context context, boolean header) {
            super(context);
            this.header = header;
            setBackgroundColor(Theme.getColor(header ? Theme.key_windowBackgroundWhite : Theme.key_windowBackgroundGray));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(header ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, header ? 15 : 14);
            textView.setTypeface(Typeface.DEFAULT_BOLD);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(textView, frame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, header ? 20 : 16, header ? 6 : 0, header ? 20 : 16, 0));
        }

        public void setText(CharSequence text) {
            textView.setText(text);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(header ? 40 : 32), MeasureSpec.EXACTLY)
            );
        }
    }

    public static final class ShadowInfoCell extends FrameLayout {
        private final TextView textView;
        private boolean empty;

        public ShadowInfoCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.LEFT);
            textView.setLineSpacing(AndroidUtilities.dp(1), 1.0f);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        }

        public void setText(@Nullable CharSequence text) {
            empty = TextUtils.isEmpty(text);
            textView.setText(empty ? "" : text);
            textView.setPadding(0, AndroidUtilities.dp(empty ? 2 : 10), 0, AndroidUtilities.dp(empty ? 0 : 17));
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            textView.setLayoutParams(layoutParams);
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (empty) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY)
                );
                return;
            }
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
        }
    }

    public static final class BookmarkCell extends FrameLayout {
        private final ImageView iconView;
        private final LinearLayout textLayout;
        private final FrameLayout.LayoutParams textLayoutParams;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView timeView;
        private final TextView insertView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        public BookmarkCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            setWillNotDraw(false);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(iconView, frame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 8, 8, 8));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), .55f));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setMaxLines(1);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subtitleLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            subtitleLp.topMargin = AndroidUtilities.dp(3);
            textLayout.addView(subtitleView, subtitleLp);

            textLayoutParams = frame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 64, 0, 70, 0);
            addView(textLayout, textLayoutParams);

            timeView = new TextView(context);
            timeView.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), .55f));
            timeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            timeView.setMaxLines(1);
            timeView.setEllipsize(TextUtils.TruncateAt.END);
            timeView.setGravity(Gravity.RIGHT);
            timeView.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);
            addView(timeView, frame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 64, -10, 12, 0));

            insertView = new TextView(context);
            insertView.setText(">");
            insertView.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), .6f));
            insertView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            insertView.setGravity(Gravity.CENTER);
            addView(insertView, frame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 8, 8, 8, 8));

            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        }

        public void bind(@Nullable CharSequence title, @Nullable CharSequence subtitle, @Nullable Bitmap favicon, boolean history, long time, boolean divider) {
            String titleText = title == null ? "" : title.toString();
            titleView.setText(titleText);
            subtitleView.setText(subtitle);
            if (favicon != null) {
                iconView.setImageDrawable(new BitmapDrawable(getResources(), favicon));
                iconView.clearColorFilter();
            } else {
                iconView.setImageDrawable(new LetterDrawable(titleText, 32));
            }
            timeView.setVisibility(history ? VISIBLE : GONE);
            insertView.setVisibility(history ? GONE : VISIBLE);
            if (history) {
                timeView.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(time));
            }
            textLayoutParams.rightMargin = AndroidUtilities.dp(history ? 70 : 52);
            textLayout.setLayoutParams(textLayoutParams);
            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(59), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY)
            );
        }
    }

    public static final class TextCell extends FrameLayout {
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView valueView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int leftPadding = 20;
        private int rightPadding = 23;
        private boolean needDivider;
        private boolean subtitleVisible;

        public TextCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            setWillNotDraw(false);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.MULTIPLY));
            addView(iconView);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(titleView);

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(subtitleView);

            valueView = new TextView(context);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            valueView.setSingleLine(true);
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            addView(valueView);

            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        }

        public void bind(@Nullable CharSequence title, @Nullable CharSequence subtitle, @Nullable CharSequence value, int iconResId, boolean red, boolean accent, boolean divider) {
            titleView.setText(title);
            titleView.setTextColor(red ? 0xffd14b45 : Theme.getColor(accent ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteBlackText));
            subtitleVisible = !TextUtils.isEmpty(subtitle);
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(subtitleVisible ? VISIBLE : GONE);
            valueView.setText(value);
            valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            iconView.setVisibility(iconResId == 0 ? GONE : VISIBLE);
            if (iconResId != 0) {
                iconView.setImageResource(iconResId);
            }
            leftPadding = iconResId == 0 ? 20 : 71;
            rightPadding = TextUtils.isEmpty(value) ? 23 : 150;
            needDivider = divider;
            requestLayout();
            invalidate();
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
            int textLeft = AndroidUtilities.dp(leftPadding);
            if (subtitleVisible) {
                titleView.layout(textLeft, AndroidUtilities.dp(11), textLeft + titleView.getMeasuredWidth(), AndroidUtilities.dp(33));
                subtitleView.layout(textLeft, AndroidUtilities.dp(34), textLeft + subtitleView.getMeasuredWidth(), AndroidUtilities.dp(54));
            } else {
                int titleTop = (height - titleView.getMeasuredHeight()) / 2;
                titleView.layout(textLeft, titleTop, textLeft + titleView.getMeasuredWidth(), titleTop + titleView.getMeasuredHeight());
                subtitleView.layout(textLeft, titleTop, textLeft, titleTop);
            }
            int valueLeft = getMeasuredWidth() - AndroidUtilities.dp(23) - valueView.getMeasuredWidth();
            valueView.layout(valueLeft, 0, valueLeft + valueView.getMeasuredWidth(), height);
            iconView.layout(0, (height - iconView.getMeasuredHeight()) / 2, iconView.getMeasuredWidth(), (height + iconView.getMeasuredHeight()) / 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                int start = AndroidUtilities.dp(iconView.getVisibility() == VISIBLE ? 68 : 20);
                canvas.drawRect(start, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), dividerPaint);
            }
        }
    }

    public static final class RadioCell extends FrameLayout {
        private final RadioMarkView radioView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;
        private boolean subtitleVisible;

        public RadioCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            setWillNotDraw(false);

            radioView = new RadioMarkView(context);
            addView(radioView);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(titleView);

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(subtitleView);

            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        }

        public void bind(@Nullable CharSequence title, @Nullable CharSequence subtitle, boolean checked, boolean divider) {
            titleView.setText(title);
            subtitleVisible = !TextUtils.isEmpty(subtitle);
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(subtitleVisible ? VISIBLE : GONE);
            radioView.setChecked(checked);
            needDivider = divider;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = AndroidUtilities.dp(subtitleVisible ? 72 : 50);
            int textWidth = Math.max(0, width - AndroidUtilities.dp(72));
            radioView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY));
            titleView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY));
            subtitleView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int height = bottom - top;
            radioView.layout(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(40), AndroidUtilities.dp(36));
            int textLeft = AndroidUtilities.dp(51);
            if (subtitleVisible) {
                titleView.layout(textLeft, AndroidUtilities.dp(13), textLeft + titleView.getMeasuredWidth(), AndroidUtilities.dp(35));
                subtitleView.layout(textLeft, AndroidUtilities.dp(39), textLeft + subtitleView.getMeasuredWidth(), AndroidUtilities.dp(59));
            } else {
                int titleTop = (height - titleView.getMeasuredHeight()) / 2;
                titleView.layout(textLeft, titleTop, textLeft + titleView.getMeasuredWidth(), titleTop + titleView.getMeasuredHeight());
                subtitleView.layout(textLeft, titleTop, textLeft, titleTop);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), dividerPaint);
            }
        }
    }

    public static final class CheckCell extends FrameLayout {
        private final TextView titleView;
        private final SwitchView switchView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        public CheckCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            setWillNotDraw(false);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(titleView);

            switchView = new SwitchView(context);
            addView(switchView);

            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        }

        public void bind(@Nullable CharSequence title, boolean checked, boolean divider) {
            titleView.setText(title);
            switchView.setChecked(checked);
            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = AndroidUtilities.dp(50);
            titleView.measure(
                    MeasureSpec.makeMeasureSpec(Math.max(0, width - AndroidUtilities.dp(92)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            );
            switchView.measure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(38), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY)
            );
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int height = bottom - top;
            titleView.layout(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21) + titleView.getMeasuredWidth(), height);
            int switchLeft = getMeasuredWidth() - AndroidUtilities.dp(21) - switchView.getMeasuredWidth();
            int switchTop = (height - switchView.getMeasuredHeight()) / 2;
            switchView.layout(switchLeft, switchTop, switchLeft + switchView.getMeasuredWidth(), switchTop + switchView.getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), dividerPaint);
            }
        }
    }

    public static final class WebsiteCell extends FrameLayout {
        private final ImageView imageView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final ImageView optionsView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        public WebsiteCell(Context context) {
            super(context);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            setWillNotDraw(false);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(imageView, frame(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, frame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 68, 7, 54, 0));

            subtitleView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setPivotY(getMeasuredHeight() / 2f);
                }
            };
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setMaxLines(1);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setPivotX(0);
            addView(subtitleView, frame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 68, 30, 54, 0));

            optionsView = new ImageView(context);
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setImageDrawable(new MoreDrawable());
            optionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
            addView(optionsView, frame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 18, 0));

            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        }

        public void bind(@Nullable CharSequence title, ArrayList<String> domains, @Nullable Bitmap favicon, boolean divider) {
            titleView.setText(title);
            StringBuilder subtitle = new StringBuilder();
            for (String domain : domains) {
                if (subtitle.length() > 0) {
                    subtitle.append(", ");
                }
                subtitle.append(domain);
            }
            subtitleView.setText(subtitle);
            if (TextUtils.isEmpty(title)) {
                subtitleView.setTranslationY(-AndroidUtilities.dp(14));
                subtitleView.setScaleX(1.3f);
                subtitleView.setScaleY(1.3f);
            } else {
                subtitleView.setTranslationY(0);
                subtitleView.setScaleX(1f);
                subtitleView.setScaleY(1f);
            }
            String iconText = TextUtils.isEmpty(title)
                    ? domains.isEmpty() || TextUtils.isEmpty(domains.get(0)) ? "" : domains.get(0)
                    : title.toString();
            if (favicon != null) {
                imageView.setImageBitmap(favicon);
            } else {
                imageView.setImageDrawable(new LetterDrawable(iconText, 28));
            }
            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(64), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY)
            );
        }
    }

    private static final class RadioMarkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;

        RadioMarkView(Context context) {
            super(context);
        }

        void setChecked(boolean checked) {
            if (this.checked != checked) {
                this.checked = checked;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int color = Theme.getColor(checked ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setColor(color);
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(9), paint);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, AndroidUtilities.dp(5), paint);
            }
        }
    }

    private static final class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;

        SwitchView(Context context) {
            super(context);
        }

        void setChecked(boolean checked) {
            if (this.checked != checked) {
                this.checked = checked;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = getHeight() / 2f;
            paint.setColor(checked
                    ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                    : Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), .45f));
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            float thumbRadius = AndroidUtilities.dp(9);
            float thumbCx = checked ? getWidth() - radius : radius;
            canvas.drawCircle(thumbCx, radius, thumbRadius, paint);
        }
    }

    private static final class LetterDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int size;
        private final String letter;

        LetterDrawable(@Nullable String title, int sizeDp) {
            size = AndroidUtilities.dp(sizeDp);
            letter = firstLetter(title);
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textPaint.setTextSize(AndroidUtilities.dp(14));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            rect.set(bounds);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), .1f));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float y = bounds.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(letter, bounds.centerX(), y, textPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            textPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            textPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        private static String firstLetter(@Nullable String text) {
            if (TextUtils.isEmpty(text)) {
                return "";
            }
            int end = text.offsetByCodePoints(0, 1);
            return text.substring(0, end).toUpperCase();
        }
    }

    private static final class MoreDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        MoreDrawable() {
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float r = AndroidUtilities.dp(1.7f);
            path.rewind();
            path.addCircle(cx, cy - AndroidUtilities.dp(6), r, Path.Direction.CW);
            path.addCircle(cx, cy, r, Path.Direction.CW);
            path.addCircle(cx, cy + AndroidUtilities.dp(6), r, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(24);
        }
    }

    private static FrameLayout.LayoutParams frame(float width, float height, int gravity, float left, float top, float right, float bottom) {
        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(width, height, gravity);
        lp.setMargins(AndroidUtilities.dp(left), AndroidUtilities.dp(top), AndroidUtilities.dp(right), AndroidUtilities.dp(bottom));
        return lp;
    }
}

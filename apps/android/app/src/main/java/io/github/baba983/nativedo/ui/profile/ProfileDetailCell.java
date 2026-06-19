/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Cells/TextDetailCell.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Cells/TextDetailCell.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/**
 * Native port of Telegram's TextDetailCell.
 * Shows a title row and a subtitle/value row, with an optional divider.
 * Simplified: uses plain TextView instead of SpoilersTextView/LinksTextView,
 * since NativeDo profile doesn't need spoiler or inline-link support.
 */
public class ProfileDetailCell extends FrameLayout implements Theme.Colorable {

    private static final int VIEW_TYPE = 10_002;

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView textView;
    private final TextView valueTextView;
    private boolean needDivider;

    public ProfileDetailCell(Context context) {
        super(context);

        // Mirrors upstream TextDetailCell: selector ripple for click feedback
        setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        textView.setPadding(dp(6), dp(2), dp(6), dp(5));
        addView(textView, LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
            LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
            18, 8 - 2, 18, 0
        ));

        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, dp(1), 0, dp(6));
        addView(valueTextView, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
            LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
            18, 33 - 1, 18, 10 - 6
        ));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(60) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
        );
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            Paint paint = dividerPaint;
            canvas.drawLine(
                LocaleController.isRTL ? 0 : dp(20),
                getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? dp(20) : 0),
                getMeasuredHeight() - 1,
                paint
            );
        }
    }

    @Override
    public void updateColors() {
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
    }

    public static final class Factory implements UItem.ViewFactory {
        private static final Factory INSTANCE = new Factory();

        @Override
        public int getViewType() {
            return VIEW_TYPE;
        }

        @Override
        public View createView(Context context) {
            return new ProfileDetailCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter) {
            view.setId(item.id);
            ((ProfileDetailCell) view).setTextAndValue(item.text, item.subtext, divider);
            ((ProfileDetailCell) view).updateColors();
        }

        @Override
        public boolean isEnabled(UItem item) {
            return false;
        }

        public static UItem of(int id, CharSequence title, CharSequence value) {
            final UItem item = UItem.ofFactory(INSTANCE);
            item.id = id;
            item.text = title;
            item.subtext = value;
            return item;
        }
    }
}

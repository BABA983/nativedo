// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/web/AddressBarList.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: address-panel=L2, clip-reveal=L0, card-layout=L2
// HOST-WIRING:
//   - Standalone FrameLayout attached to BrowserSheet.containerView / BrowserFragment root.
//   - Uses NativeDo Theme and AndroidUtilities instead of Telegram's resource providers.
//   - Copied msg_language.png and msg_copy.png from Telegram drawable resources.
// ADAPTED:
//   - Keeps upstream clipRect dispatchDraw reveal animation (L0) and current URL card shape
//     (favicon + title + URL + copy button).
//   - Omits suggestions, bookmarks, recent searches, and UniversalRecyclerView (out of scope).
//   - Card background uses GradientDrawable instead of Theme.createRadSelectorDrawable.
// OMITTED:
//   - UniversalRecyclerView suggestions list, BookmarksList, recent searches, search engines,
//     Address2View/BookmarkView, and ItemOptions popup integration.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/web/AddressBarList.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package io.github.baba983.nativedo.ui.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class BrowserAddressPanel extends FrameLayout {

    private final FrameLayout currentView;
    private final ImageView currentIconView;
    private final ImageView currentCopyView;
    private final LinearLayout currentTextContainer;
    private final TextView currentTitleView;
    private final TextView currentLinkView;

    private int listBackgroundColor;
    private int grayBackgroundColor;
    private float openProgress;

    private Runnable onCurrentClick;
    private View.OnClickListener onCopyClick;

    public BrowserAddressPanel(Context context) {
        super(context);
        setWillNotDraw(false);

        currentView = new FrameLayout(context);
        FrameLayout.LayoutParams currentLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL);
        currentLp.leftMargin = AndroidUtilities.dp(12);
        currentLp.rightMargin = AndroidUtilities.dp(12);
        currentLp.bottomMargin = AndroidUtilities.dp(15);
        addView(currentView, currentLp);

        currentIconView = new ImageView(context);
        currentIconView.setScaleType(ImageView.ScaleType.CENTER);
        currentIconView.setImageResource(R.drawable.msg_language);
        FrameLayout.LayoutParams iconLp = LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        iconLp.leftMargin = AndroidUtilities.dp(16);
        iconLp.rightMargin = AndroidUtilities.dp(16);
        iconLp.topMargin = AndroidUtilities.dp(16);
        iconLp.bottomMargin = AndroidUtilities.dp(16);
        currentView.addView(currentIconView, iconLp);

        currentCopyView = new ImageView(context);
        currentCopyView.setScaleType(ImageView.ScaleType.CENTER);
        currentCopyView.setImageResource(R.drawable.msg_copy);
        currentCopyView.setOnClickListener(v -> {
            if (onCopyClick != null) {
                onCopyClick.onClick(v);
            }
        });
        FrameLayout.LayoutParams copyLp = LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.RIGHT);
        copyLp.leftMargin = AndroidUtilities.dp(14);
        copyLp.topMargin = AndroidUtilities.dp(14);
        copyLp.rightMargin = AndroidUtilities.dp(14);
        copyLp.bottomMargin = AndroidUtilities.dp(14);
        currentView.addView(currentCopyView, copyLp);

        currentTextContainer = new LinearLayout(context);
        currentTextContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textContainerLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        textContainerLp.leftMargin = AndroidUtilities.dp(54);
        textContainerLp.rightMargin = AndroidUtilities.dp(54);
        textContainerLp.topMargin = AndroidUtilities.dp(9);
        textContainerLp.bottomMargin = AndroidUtilities.dp(9);
        currentView.addView(currentTextContainer, textContainerLp);

        currentTitleView = new TextView(context);
        currentTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            currentTitleView.setTypeface(Typeface.create(null, 500, false));
        } else {
            currentTitleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        currentTitleView.setMaxLines(4);
        currentTitleView.setEllipsize(TextUtils.TruncateAt.END);
        currentTextContainer.addView(currentTitleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        currentLinkView = new TextView(context);
        currentLinkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        currentLinkView.setMaxLines(3);
        currentLinkView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        linkLp.topMargin = AndroidUtilities.dp(2);
        currentTextContainer.addView(currentLinkView, linkLp);

        setOpenProgress(0f);
    }

    public void setCurrent(String title, String url, Bitmap favicon, Runnable onCurrentClick, View.OnClickListener onCopyClick) {
        currentTitleView.setText(title == null || title.isEmpty() ? "about:blank" : title);
        currentLinkView.setText(url == null || url.isEmpty() ? "about:blank" : url);

        if (favicon != null) {
            currentIconView.setImageDrawable(new BitmapDrawable(getContext().getResources(), favicon));
            currentIconView.setColorFilter(null);
        } else {
            currentIconView.setImageResource(R.drawable.msg_language);
            // color filter will be set in setColors
        }

        this.onCurrentClick = onCurrentClick;
        this.onCopyClick = onCopyClick;

        currentView.setOnClickListener(v -> {
            if (this.onCurrentClick != null) {
                this.onCurrentClick.run();
            }
        });
    }

    public void setOpenProgress(float progress) {
        if (Math.abs(this.openProgress - progress) > 0.0001f) {
            this.openProgress = progress;
            invalidate();
        }
    }

    public void setColors(int backgroundColor, int textColor) {
        this.listBackgroundColor = backgroundColor;

        float dark = AndroidUtilities.computePerceivedBrightness(backgroundColor) >= .721f ? 0f : 1f;
        grayBackgroundColor = ColorUtils.blendARGB(backgroundColor, textColor, AndroidUtilities.lerp(.05f, .12f, dark));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(AndroidUtilities.dp(15));
        cardBg.setColor(grayBackgroundColor);
        currentView.setBackground(cardBg);

        currentTitleView.setTextColor(textColor);
        currentLinkView.setTextColor(ColorUtils.setAlphaComponent(textColor, (int) (0xff * .6f)));
        currentIconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        currentCopyView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));

        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight() * openProgress);
        canvas.drawColor(listBackgroundColor);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (openProgress < .3f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }
}

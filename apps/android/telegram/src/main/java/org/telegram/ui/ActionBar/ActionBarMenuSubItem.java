// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/ActionBarMenuSubItem.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: popup-menu-row=L1
// HOST-WIRING:
//   - AndroidUtilities/LayoutHelper/Theme imports rewritten to NativeDo.
//   - AnimatedEmojiSpan/RLottieImageView replaced with TextView/ImageView for the current slice.
// OMITTED:
//   - Check rows, animated icons, BackupImageView, RTL specialization, swipe-back hooks,
//     accessibility checkbox metadata, and multiline/premium helpers not used by BrowserSheet history.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBarMenuSubItem.java.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class ActionBarMenuSubItem extends FrameLayout {

    public final TextView textView;
    public final TextView subtextView;
    public final ImageView imageView;
    private int textColor;
    private int iconColor;
    private int selectorColor;

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        textColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider);
        iconColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider);
        selectorColor = Theme.multAlpha(textColor, .12f);
        updateBackground();
        setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        textView = new TextView(context);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        subtextView = new TextView(context);
        subtextView.setLines(1);
        subtextView.setSingleLine(true);
        subtextView.setGravity(Gravity.LEFT);
        subtextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtextView.setPadding(AndroidUtilities.dp(43), 0, 0, 0);
        FrameLayout.LayoutParams subtextParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subtextParams.topMargin = AndroidUtilities.dp(10);
        addView(subtextView, subtextParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
    }

    public void setTextAndIcon(CharSequence text, int icon, Drawable iconDrawable) {
        textView.setText(text);
        if (iconDrawable != null) {
            imageView.setColorFilter(null);
            imageView.setImageDrawable(iconDrawable);
            imageView.setVisibility(VISIBLE);
            textView.setPadding(AndroidUtilities.dp(43), 0, 0, 0);
        } else if (icon != 0) {
            imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            imageView.setImageResource(icon);
            imageView.setVisibility(VISIBLE);
            textView.setPadding(AndroidUtilities.dp(43), 0, 0, 0);
        } else {
            imageView.setVisibility(INVISIBLE);
            textView.setPadding(0, 0, 0, 0);
        }
    }

    public ActionBarMenuSubItem setColors(int textColor, int iconColor) {
        setTextColor(textColor);
        setIconColor(iconColor);
        return this;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        textView.setTextColor(textColor);
    }

    public void setIconColor(int iconColor) {
        this.iconColor = iconColor;
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setSubtextColor(int color) {
        subtextView.setTextColor(color);
    }

    public void setSubtext(CharSequence text) {
        subtextView.setText(text);
        boolean visible = !TextUtils.isEmpty(text);
        subtextView.setVisibility(visible ? VISIBLE : GONE);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
        layoutParams.bottomMargin = visible ? AndroidUtilities.dp(10) : 0;
        textView.setLayoutParams(layoutParams);
    }

    public TextView getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setIconDrawable(Drawable drawable) {
        setTextAndIcon(textView.getText(), 0, drawable);
    }

    public void setSelectorColor(int selectorColor) {
        this.selectorColor = selectorColor;
        updateBackground();
    }

    public void updateBackground() {
        setBackground(Theme.createSelectorDrawable(selectorColor, Theme.RIPPLE_MASK_ALL));
    }
}

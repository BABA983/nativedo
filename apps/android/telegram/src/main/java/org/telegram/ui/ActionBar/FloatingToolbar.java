/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's FloatingToolbar.java, adapted as a
 * compact toolbar for NativeDo text selection.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.List;

public final class FloatingToolbar {
    public static final int STYLE_THEME = 1;
    private static final int MAX_MAIN_ITEMS = 2;
    private static final int LINE_HEIGHT_DP = 48;

    private final Context context;
    private final View windowView;
    private final PopupWindow popupWindow;
    private final LinearLayout contentView;
    private final LinearLayout mainPanel;
    private final LinearLayout overflowPanel;
    private final FrameLayout overflowHeader;
    private final ImageView mainOverflowIcon;
    private final ImageView headerOverflowIcon;
    private final TextView overflowBackText;
    private final View overflowDivider;
    private final Interpolator fastOutSlowInInterpolator;
    private final Interpolator linearOutSlowInInterpolator;
    private final Rect contentRect = new Rect();
    private final Rect newLayoutRect = new Rect();
    private final Rect oldLayoutRect = new Rect();
    private final View.OnLayoutChangeListener orientationChangeHandler = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            newLayoutRect.set(left, top, right, bottom);
            oldLayoutRect.set(oldLeft, oldTop, oldRight, oldBottom);
            if (popupWindow.isShowing() && !newLayoutRect.equals(oldLayoutRect)) {
                updateLayout();
            }
        }
    };
    private Menu menu;
    private MenuItem.OnMenuItemClickListener menuItemClickListener;
    private int overflowWidth = AndroidUtilities.dp(148f);
    private boolean hidden;
    private boolean overflowOpen;
    private boolean orientationHandlerRegistered;

    public FloatingToolbar(Context context, View windowView, int style, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.windowView = windowView;
        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(4f), AndroidUtilities.dp(6f), AndroidUtilities.dp(4f));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        background.setCornerRadius(AndroidUtilities.dp(12f));
        contentView.setBackground(background);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            contentView.setElevation(AndroidUtilities.dp(8f));
        }
        contentView.setClipToOutline(true);
        mainPanel = new LinearLayout(context);
        mainPanel.setOrientation(LinearLayout.HORIZONTAL);
        overflowPanel = new LinearLayout(context);
        overflowPanel.setOrientation(LinearLayout.VERTICAL);
        overflowPanel.setVisibility(View.GONE);
        overflowHeader = new FrameLayout(context);
        mainOverflowIcon = createOverflowIcon(true);
        headerOverflowIcon = createOverflowIcon(false);
        overflowBackText = new TextView(context);
        overflowBackText.setText(context.getString(R.string.Back));
        overflowBackText.setTextSize(16);
        overflowBackText.setSingleLine(true);
        overflowBackText.setGravity(Gravity.CENTER_VERTICAL);
        overflowBackText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        overflowBackText.setAlpha(0f);
        overflowDivider = new View(context);
        overflowDivider.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator));
        overflowHeader.addView(headerOverflowIcon, new FrameLayout.LayoutParams(AndroidUtilities.dp(56f), AndroidUtilities.dp(LINE_HEIGHT_DP), Gravity.LEFT | Gravity.CENTER_VERTICAL));
        FrameLayout.LayoutParams backTextParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(LINE_HEIGHT_DP), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        backTextParams.leftMargin = AndroidUtilities.dp(56f);
        overflowHeader.addView(overflowBackText, backTextParams);
        FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (1f / AndroidUtilities.density)), Gravity.TOP | Gravity.FILL_HORIZONTAL);
        overflowHeader.addView(overflowDivider, dividerParams);
        overflowHeader.setVisibility(View.GONE);
        overflowHeader.setOnClickListener(v -> closeOverflow(true));
        contentView.addView(mainPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(LINE_HEIGHT_DP)));
        contentView.addView(overflowHeader, new LinearLayout.LayoutParams(overflowWidth, AndroidUtilities.dp(LINE_HEIGHT_DP)));
        contentView.addView(overflowPanel, new LinearLayout.LayoutParams(overflowWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        fastOutSlowInInterpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in);
        linearOutSlowInInterpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in);
        popupWindow = new PopupWindow(contentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setAnimationStyle(0);
    }

    public FloatingToolbar setMenu(Menu menu) {
        this.menu = menu;
        rebuildMenuItems();
        return this;
    }

    public FloatingToolbar setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener listener) {
        menuItemClickListener = listener;
        rebuildMenuItems();
        return this;
    }

    public FloatingToolbar setContentRect(Rect rect) {
        contentRect.set(rect);
        return this;
    }

    public FloatingToolbar show() {
        hidden = false;
        registerOrientationHandler();
        if (mainPanel.getChildCount() == 0 && menu != null) {
            rebuildMenuItems();
        }
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST)
        );
        int x = contentRect.centerX() - contentView.getMeasuredWidth() / 2;
        int y = contentRect.top - contentView.getMeasuredHeight() - AndroidUtilities.dp(8f);
        if (y < AndroidUtilities.statusBarHeight + AndroidUtilities.dp(4f)) {
            y = contentRect.bottom + AndroidUtilities.dp(8f);
        }
        if (y + contentView.getMeasuredHeight() > AndroidUtilities.displaySize.y - AndroidUtilities.dp(8f)) {
            y = AndroidUtilities.displaySize.y - contentView.getMeasuredHeight() - AndroidUtilities.dp(8f);
        }
        y = Math.max(AndroidUtilities.statusBarHeight + AndroidUtilities.dp(4f), y);
        x = Math.max(AndroidUtilities.dp(8f), Math.min(x, AndroidUtilities.displaySize.x - contentView.getMeasuredWidth() - AndroidUtilities.dp(8f)));
        if (popupWindow.isShowing()) {
            popupWindow.update(x, y, contentView.getMeasuredWidth(), contentView.getMeasuredHeight());
        } else {
            contentView.setAlpha(0f);
            contentView.setScaleX(0.94f);
            contentView.setScaleY(0.94f);
            popupWindow.showAtLocation(windowView, Gravity.TOP | Gravity.LEFT, x, y);
            contentView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        }
        return this;
    }

    public FloatingToolbar updateLayout() {
        if (popupWindow.isShowing() && !hidden) {
            show();
        }
        return this;
    }

    public void dismiss() {
        unregisterOrientationHandler();
        contentView.animate().cancel();
        popupWindow.dismiss();
    }

    public void hide() {
        hidden = true;
        contentView.animate().cancel();
        popupWindow.dismiss();
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setOutsideTouchable(boolean outsideTouchable, PopupWindow.OnDismissListener onDismiss) {
        popupWindow.setOutsideTouchable(outsideTouchable);
        popupWindow.setOnDismissListener(onDismiss);
    }

    private void registerOrientationHandler() {
        if (!orientationHandlerRegistered) {
            windowView.addOnLayoutChangeListener(orientationChangeHandler);
            orientationHandlerRegistered = true;
        }
    }

    private void unregisterOrientationHandler() {
        if (orientationHandlerRegistered) {
            windowView.removeOnLayoutChangeListener(orientationChangeHandler);
            orientationHandlerRegistered = false;
        }
    }

    private void rebuildMenuItems() {
        mainPanel.removeAllViews();
        overflowPanel.removeAllViews();
        overflowHeader.setVisibility(View.GONE);
        if (menu == null) {
            return;
        }
        List<MenuItem> visibleItems = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.isVisible() && item.isEnabled()) {
                visibleItems.add(item);
            }
        }
        if (visibleItems.isEmpty()) {
            return;
        }
        int mainItemsCount = Math.min(visibleItems.size(), MAX_MAIN_ITEMS);
        if (visibleItems.size() == MAX_MAIN_ITEMS + 1) {
            mainItemsCount = MAX_MAIN_ITEMS + 1;
        }
        overflowWidth = calculateOverflowWidth(visibleItems, mainItemsCount);
        setOverflowWidth(overflowWidth);
        for (int i = 0; i < mainItemsCount; i++) {
            mainPanel.addView(createButton(visibleItems.get(i), false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(LINE_HEIGHT_DP)));
        }
        if (mainItemsCount < visibleItems.size()) {
            mainPanel.addView(mainOverflowIcon, new LinearLayout.LayoutParams(AndroidUtilities.dp(56f), AndroidUtilities.dp(LINE_HEIGHT_DP)));
            for (int i = mainItemsCount; i < visibleItems.size(); i++) {
                overflowPanel.addView(createButton(visibleItems.get(i), true), new LinearLayout.LayoutParams(overflowWidth, AndroidUtilities.dp(LINE_HEIGHT_DP)));
            }
            mainPanel.setVisibility(overflowOpen ? View.GONE : View.VISIBLE);
            overflowPanel.setVisibility(overflowOpen ? View.VISIBLE : View.GONE);
            overflowHeader.setVisibility(overflowOpen ? View.VISIBLE : View.GONE);
        } else {
            overflowOpen = false;
            mainPanel.setVisibility(View.VISIBLE);
            overflowPanel.setVisibility(View.GONE);
            overflowHeader.setVisibility(View.GONE);
        }
        updateOverflowIcon(false);
    }

    private int calculateOverflowWidth(List<MenuItem> visibleItems, int firstOverflowIndex) {
        int width = AndroidUtilities.dp(148f);
        width = Math.max(width, (int) Math.ceil(overflowBackText.getPaint().measureText(overflowBackText.getText().toString())) + AndroidUtilities.dp(70f));
        for (int i = firstOverflowIndex; i < visibleItems.size(); i++) {
            CharSequence title = visibleItems.get(i).getTitle();
            if (title != null) {
                width = Math.max(width, (int) Math.ceil(overflowBackText.getPaint().measureText(title.toString())) + AndroidUtilities.dp(28f));
            }
        }
        return Math.min(width, AndroidUtilities.displaySize.x - AndroidUtilities.dp(16f));
    }

    private void setOverflowWidth(int width) {
        ViewGroup.LayoutParams headerParams = overflowHeader.getLayoutParams();
        headerParams.width = width;
        overflowHeader.setLayoutParams(headerParams);
        ViewGroup.LayoutParams panelParams = overflowPanel.getLayoutParams();
        panelParams.width = width;
        overflowPanel.setLayoutParams(panelParams);
    }

    private TextView createButton(MenuItem item, boolean overflow) {
        TextView textView = new TextView(context);
        textView.setGravity(overflow ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        textView.setText(item.getTitle());
        textView.setTextSize(15);
        textView.setSingleLine(true);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0);
        textView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP, AndroidUtilities.dp(6f)));
        textView.setOnClickListener(v -> {
            if (menuItemClickListener != null) {
                menuItemClickListener.onMenuItemClick(item);
            }
        });
        return textView;
    }

    private ImageView createOverflowIcon(boolean opensOverflow) {
        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(12f), AndroidUtilities.dp(16f), AndroidUtilities.dp(12f));
        imageView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        imageView.setOnClickListener(v -> {
            if (overflowOpen || !opensOverflow) {
                closeOverflow(true);
            } else {
                openOverflow(true);
            }
        });
        return imageView;
    }

    private void openOverflow(boolean animate) {
        if (overflowOpen) {
            return;
        }
        overflowOpen = true;
        mainPanel.setVisibility(View.GONE);
        overflowHeader.setVisibility(View.VISIBLE);
        overflowPanel.setVisibility(View.VISIBLE);
        updateOverflowIcon(animate);
        if (animate) {
            animateOverflow(true);
        } else {
            overflowBackText.setAlpha(1f);
            overflowDivider.setAlpha(1f);
            overflowPanel.setAlpha(1f);
        }
        updateLayout();
    }

    private void closeOverflow(boolean animate) {
        if (!overflowOpen) {
            return;
        }
        overflowOpen = false;
        updateOverflowIcon(animate);
        if (animate) {
            animateOverflow(false);
        } else {
            overflowHeader.setVisibility(View.GONE);
            overflowPanel.setVisibility(View.GONE);
            overflowBackText.setAlpha(0f);
            overflowDivider.setAlpha(0f);
            mainPanel.setVisibility(View.VISIBLE);
        }
        updateLayout();
    }

    private void animateOverflow(boolean opening) {
        overflowHeader.clearAnimation();
        overflowPanel.clearAnimation();
        overflowBackText.animate().cancel();
        overflowDivider.animate().cancel();
        if (opening) {
            overflowBackText.setAlpha(0f);
            overflowDivider.setAlpha(0f);
            overflowPanel.setAlpha(0f);
            overflowPanel.setTranslationY(-AndroidUtilities.dp(6f));
            AnimationSet animationSet = new AnimationSet(true);
            Animation heightAnimation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, android.view.animation.Transformation t) {
                    overflowPanel.setTranslationY(-AndroidUtilities.dp(6f) * (1f - interpolatedTime));
                    overflowPanel.setAlpha(interpolatedTime);
                }
            };
            heightAnimation.setDuration(250);
            heightAnimation.setInterpolator(fastOutSlowInInterpolator);
            animationSet.addAnimation(heightAnimation);
            overflowPanel.startAnimation(animationSet);
            overflowBackText.animate().alpha(1f).setDuration(250).setInterpolator(linearOutSlowInInterpolator).start();
            overflowDivider.animate().alpha(1f).setDuration(250).setInterpolator(linearOutSlowInInterpolator).start();
        } else {
            overflowBackText.animate().alpha(0f).setDuration(180).setInterpolator(linearOutSlowInInterpolator).start();
            overflowDivider.animate().alpha(0f).setDuration(180).setInterpolator(linearOutSlowInInterpolator).start();
            overflowPanel.animate().alpha(0f).translationY(-AndroidUtilities.dp(6f)).setDuration(180).setInterpolator(fastOutSlowInInterpolator).withEndAction(() -> {
                if (!overflowOpen) {
                    overflowHeader.setVisibility(View.GONE);
                    overflowPanel.setVisibility(View.GONE);
                    mainPanel.setVisibility(View.VISIBLE);
                    updateLayout();
                }
            }).start();
        }
    }

    private void updateOverflowIcon(boolean animate) {
        int color = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem);
        if (animate && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AnimatedVectorDrawable mainDrawable = newAnimatedDrawable(overflowOpen);
            AnimatedVectorDrawable headerDrawable = newAnimatedDrawable(overflowOpen);
            mainDrawable.setTint(color);
            headerDrawable.setTint(color);
            mainOverflowIcon.setImageDrawable(mainDrawable);
            headerOverflowIcon.setImageDrawable(headerDrawable);
            mainDrawable.start();
            headerDrawable.start();
            return;
        }
        Drawable drawable = context.getDrawable(overflowOpen ? R.drawable.ft_avd_tooverflow : R.drawable.ft_avd_toarrow);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(color);
        }
        mainOverflowIcon.setImageDrawable(drawable);
        headerOverflowIcon.setImageDrawable(drawable == null ? null : drawable.getConstantState().newDrawable().mutate());
        mainOverflowIcon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        headerOverflowIcon.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }

    private AnimatedVectorDrawable newAnimatedDrawable(boolean toArrow) {
        return (AnimatedVectorDrawable) context.getDrawable(toArrow ? R.drawable.ft_avd_toarrow_animation : R.drawable.ft_avd_tooverflow_animation).mutate();
    }
}

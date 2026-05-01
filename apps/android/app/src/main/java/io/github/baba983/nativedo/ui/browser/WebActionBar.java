// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/web/WebActionBar.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: browser-actionbar=L2, address-animation=L0, progress-animation=L1, scroll-progress=L1, title-swap=L1, popup-menu=L1, adaptive-color=L1, scrubber-gesture=L0, back-history=L1
// HOST-WIRING:
//   - Extends NativeDo's current ActionBar slice so ActionBarLayout can host it unchanged.
//   - Uses NativeDo strings, Theme keys, and existing ActionBarMenu popup support.
// ADAPTED:
//   - Keeps Telegram WebActionBar's title/subtitle, address mode, progress, and menu shape.
//   - Address animation uses upstream 360ms EASE_OUT_QUINT.
//   - Keeps Telegram's sheet-browser toolbar semantics: the right toolbar arrow collapses to tabs;
//     NativeDo omits forward from both the action bar and overflow for http browser parity.
//   - Replaces the placeholder line with NativeDo's LineProgressView (smooth 200ms EASE_OUT_QUINT chase).
//   - Ports the upstream action-bar reading progress background fill driven by
//     ArticleViewer.updatePages() -> WebActionBar.setProgress(index, pageProgress).
//   - Keeps Telegram's Title[2] slot swap + titleProgress transition geometry for page
//     navigation, using the NativeDo AnimatedTextDrawable slice from upstream.
//   - Adds upstream WebActionBar.setColors(int, boolean) adaptive color path: animated
//     blendARGB(fromBackgroundColor -> backgroundColor), derives
//     textColor / iconColor / addressBackgroundColor / addressTextColor from the page
//     background's perceived brightness (<= .721 => dark) and applies them to the bar
//     background, BackDrawable, ActionBarMenu icons, address rounded fill, search/address
//     EditTexts. Mirrors WebActionBar.java L518-602.
//   - Ports WebActionBar.dispatchTouchEvent / longPressRunnable / onScrolledProgress
//     (L1001-1034). NativeDo computes the same leftmenu.getRight()/rightmenu.getLeft()
//     bounds from the current back button and menu geometry because its ActionBar slice
//     does not expose those upstream LinearLayouts. Constants kept verbatim:
//     0.8 * ViewConfiguration.getLongPressTimeout() press window and
//     (x - pressX) / (0.8 * getWidth()) normalized delta.
//   - Exposes upstream-style setBackButton / backButton long-click surface so BrowserSheet can
//     port ArticleViewer's WebView/page-stack history menu without replacing NativeDo ActionBar.
// OMITTED:
//   - Search field full theming pass, Instant View, bookmarks/history/settings state,
//     bottom action bar mode, and Telegram item-options menu icons. Forward button still
//     omitted from both the action bar and overflow for http browser parity.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/web/WebActionBar.java.
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.ActionBar;
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenu;
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenuItem;
import io.github.baba983.nativedo.ui.actionbar.BackDrawable;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.AnimatedTextDrawable;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.LayoutHelper;
import io.github.baba983.nativedo.ui.components.LineProgressView;

public class WebActionBar extends ActionBar {

    public interface Delegate {
        void onBackPressed();

        void onAddressSubmitted(String value);

        void onMenuItem(int id);

        void onSearchUpdated(String text);

        default void onAddressingProgress(float progress) {}

        default void onAddressColorsChanged(int backgroundColor, int textColor) {}

        default void onAddressInputChanged(String text) {}

        default void onTitleClicked() {}

        /**
         * Mirrors upstream WebActionBar.onScrolledProgress(float) -> ArticleViewer
         * pages[0].addProgress(delta). The host should call
         * {@code webViewContainer.setScrollProgress(getScrollProgress() + delta)}.
         */
        default void onScrolledProgress(float delta) {}
    }

    public static final int MENU_OPEN_EXTERNAL = 1;
    public static final int MENU_REFRESH = 2;
    public static final int MENU_COPY_LINK = 3;
    public static final int MENU_MINIMIZE = 5;
    public static final int MENU_SEARCH = 6;

    private final Delegate delegate;
    private final BackDrawable backButtonDrawable;
    private final FrameLayout titleContainer;
    private final Title[] titles = new Title[2];
    private final Rect currentTitleClipBounds = new Rect();
    private final Rect nextTitleClipBounds = new Rect();
    private final EditText addressEditText;
    private final LineProgressView progressView;
    private final float[] progress = new float[2];
    private final Paint[] progressBackgroundPaint = new Paint[2];

    private String currentTitle = "";
    private String currentUrl = "";
    private boolean addressing;
    private float addressingProgress;
    private ValueAnimator addressAnimator;

    private float titleProgress;

    private boolean searching;
    private float searchingProgress;
    private final EditText searchEditText;
    private final ImageView searchClearButton;
    private ValueAnimator searchAnimator;
    private boolean backButtonShown;

    private final Paint addressBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint addressRoundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF addressRect = new RectF();
    private int addressBackgroundColor;
    private int addressTextColor;

    // Upstream-style adaptive color state (WebActionBar.java L74-82, L466-470).
    private boolean adaptiveColorSet;
    private int currentBackgroundColor;
    private int currentTextColor;
    private int currentIconColor;
    private int fromBackgroundColor;
    private ValueAnimator colorAnimator;

    // Long-press scrubber state (WebActionBar.java L999-1011).
    private float pressX;
    private float pressY;
    private long pressTime;
    private boolean longClicked;
    private final Runnable longPressRunnable = () -> {
        longClicked = true;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignored) {}
    };

    public WebActionBar(Context context, Delegate delegate) {
        super(context);
        this.delegate = delegate;
        super.setTitle("");
        for (int i = 0; i < progressBackgroundPaint.length; ++i) {
            progressBackgroundPaint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        backButtonDrawable = new BackDrawable(false);
        backButtonDrawable.setAnimationTime(200.0f);
        backButtonDrawable.setRotation(1.0f, false);
        setBackButtonDrawable(backButtonDrawable);

        titleContainer = new FrameLayout(context);
        addView(titleContainer);

        titles[0] = new Title(context);
        titles[1] = new Title(context);
        titles[1].setVisibility(GONE);
        FrameLayout.LayoutParams slotLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        titleContainer.addView(titles[0], slotLp);
        titleContainer.addView(titles[1], new FrameLayout.LayoutParams(slotLp));
        // Upstream WebActionBar checks `longClicked` from its dispatchTouchEvent gesture; we
        // mirror that here so the scrubber long-press doesn't also fire the address overlay.
        View.OnClickListener showAddressClick = v -> {
            if (longClicked) {
                longClicked = false;
                return;
            }
            if (delegate != null) {
                delegate.onTitleClicked();
            }
        };
        titles[0].setOnClickListener(showAddressClick);
        titles[1].setOnClickListener(showAddressClick);

        addressEditText = new EditText(context);
        addressEditText.setSingleLine(true);
        addressEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.66f);
        addressEditText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        addressEditText.setHintTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        addressEditText.setHint(R.string.app_browser_address_placeholder);
        addressEditText.setSelectAllOnFocus(true);
        addressEditText.setVisibility(GONE);
        addressEditText.setAlpha(0f);
        addressEditText.setBackgroundResource(0);
        addressEditText.setGravity(Gravity.FILL_VERTICAL);
        addressEditText.setTextIsSelectable(false);
        addressEditText.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        addressEditText.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        addressEditText.setInputType(addressEditText.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        addressEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                if (delegate != null) {
                    delegate.onAddressSubmitted(addressEditText.getText().toString());
                }
                showAddress(false, true);
                return true;
            }
            return false;
        });
        addView(addressEditText);

        addressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!addressing) return;
                if (delegate != null) {
                    delegate.onAddressInputChanged(s == null ? null : s.toString());
                }
            }
        });

        searchEditText = new EditText(context);
        searchEditText.setSingleLine(true);
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchEditText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        searchEditText.setHintTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        searchEditText.setHint(R.string.app_browser_search_placeholder);
        searchEditText.setBackgroundResource(0);
        searchEditText.setVisibility(GONE);
        searchEditText.setAlpha(0f);
        searchEditText.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8), 0);
        searchEditText.setTextIsSelectable(false);
        searchEditText.setInputType(searchEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchEditText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && actionId == EditorInfo.IME_ACTION_SEARCH) {
                AndroidUtilities.hideKeyboard(searchEditText);
                return true;
            }
            return false;
        });
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String text = s.toString();
                searchClearButton.setVisibility(searching && text.length() > 0 ? VISIBLE : GONE);
                searchClearButton.setAlpha(searching && text.length() > 0 ? 1f : 0f);
                if (delegate != null) {
                    delegate.onSearchUpdated(text);
                }
            }
        });
        addView(searchEditText);

        progressView = new LineProgressView(context);
        progressView.setProgressColor(Theme.getColor(Theme.key_telegram_color));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.BOTTOM | Gravity.START));

        searchClearButton = new ImageView(context);
        searchClearButton.setScaleType(ImageView.ScaleType.CENTER);
        searchClearButton.setImageDrawable(new SearchClearDrawable());
        searchClearButton.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_actionBarDefaultIcon), 18), Theme.RIPPLE_MASK_CIRCLE_20DP));
        searchClearButton.setVisibility(GONE);
        searchClearButton.setAlpha(0f);
        searchClearButton.setContentDescription(context.getString(R.string.app_browser_search_clear));
        searchClearButton.setOnClickListener(v -> searchEditText.setText(""));
        addView(searchClearButton, LayoutHelper.createFrame(54, 56, Gravity.BOTTOM | Gravity.RIGHT));

        ActionBarMenu menu = createMenu();
        ActionBarMenuItem minimizeItem = menu.addItem(MENU_MINIMIZE, new CollapseDrawable());
        minimizeItem.setContentDescription(context.getString(R.string.app_browser_minimize));
        ActionBarMenuItem moreItem = menu.addItem(0, new MoreDrawable());
        moreItem.setContentDescription(context.getString(R.string.app_browser_more_actions));
        moreItem.addSubItem(MENU_OPEN_EXTERNAL, context.getString(R.string.app_browser_open_in_browser));
        moreItem.addSubItem(MENU_REFRESH, context.getString(R.string.app_browser_refresh));
        moreItem.addSubItem(MENU_COPY_LINK, context.getString(R.string.app_browser_copy_link));
        moreItem.addSubItem(MENU_SEARCH, context.getString(R.string.app_browser_search));

        setActionBarMenuOnItemClick(new ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && addressing) {
                    showAddress(false, true);
                    return;
                }
                if (id == -1) {
                    if (searching) {
                        showSearch(false, true);
                        return;
                    }
                    if (delegate != null) {
                        delegate.onBackPressed();
                    }
                    return;
                }
                if (delegate != null) {
                    delegate.onMenuItem(id);
                }
            }
        });

        setColors(Theme.getColor(Theme.key_iv_background), false);
    }

    @Override
    public void setTitle(CharSequence value) {
        currentTitle = value == null ? "" : value.toString();
        titles[0].setTitleText(currentTitle);
    }

    @Override
    public CharSequence getTitle() {
        return currentTitle;
    }

    public void setSubtitle(CharSequence value) {
        titles[0].setSubtitleText(value == null ? "" : value);
    }

    public void setTitle(int i, CharSequence title, CharSequence subtitle) {
        titles[i].setTitleText(title == null ? "" : title, false);
        titles[i].setSubtitleText(subtitle == null ? "" : subtitle, false);
        if (i == 0) {
            currentTitle = titles[i].getTitleText().toString();
            currentUrl = titles[i].getSubtitleText().toString();
        }
    }

    public void setCurrentUrl(String url) {
        currentUrl = url == null ? "" : url;
        setSubtitle(currentUrl);
    }

    /**
     * Updates the off-screen slot used by the next page transition.
     */
    public void setNextTitle(CharSequence value, CharSequence subtitle) {
        titles[1].setTitleText(value == null ? "" : value, false);
        titles[1].setSubtitleText(subtitle == null ? "" : subtitle, false);
    }

    /**
     * Mirrors upstream WebActionBar.swap(): page motion drives titleProgress separately.
     */
    public void swap() {
        Title title = titles[0];
        titles[0] = titles[1];
        titles[1] = title;
        titles[0].setVisibility(VISIBLE);
        titles[1].setVisibility(titleProgress > 0f ? VISIBLE : GONE);

        float progressValue = progress[0];
        progress[0] = progress[1];
        progress[1] = progressValue;

        currentTitle = titles[0].getTitleText().toString();
        currentUrl = titles[0].getSubtitleText().toString();
        invalidate();
    }

    public void setTransitionProgress(float progress) {
        titleProgress = clamp01(progress);
        titles[0].setVisibility(VISIBLE);
        titles[1].setVisibility(titleProgress > 0f ? VISIBLE : GONE);
        applyTitleProgress();
        invalidate();
    }

    public void finishTransition() {
        swap();
        setTransitionProgress(0f);
    }

    private void applyTitleProgress() {
        float p = clamp01(titleProgress);
        int width = getWidth();
        float leftPadding = width * p - AndroidUtilities.dp(54 - 24) * clamp01(2f * p);
        float pivotY = titleContainer.getHeight() / 2f;
        int currentClipRight = Math.max(0, Math.round(titleContainer.getWidth() - leftPadding));
        currentTitleClipBounds.set(0, 0, currentClipRight, titleContainer.getHeight());
        titles[0].setClipBounds(currentTitleClipBounds);

        titles[0].setPivotX(0f);
        titles[0].setPivotY(pivotY);
        titles[0].setTranslationX(leftPadding);
        float currentScale = AndroidUtilities.lerp(1f, .5f, p);
        titles[0].setScaleX(currentScale);
        titles[0].setScaleY(currentScale);
        titles[0].setAlpha((1f - p) * (1f - searchingProgress));

        titles[1].setPivotX(0f);
        titles[1].setPivotY(pivotY);
        float nextTranslation = -AndroidUtilities.dp(12) * (1f - p);
        titles[1].setTranslationX(nextTranslation);
        int nextClipRight = Math.max(0, Math.round(width * p - titleContainer.getLeft() - nextTranslation));
        nextTitleClipBounds.set(0, 0, nextClipRight, titleContainer.getHeight());
        titles[1].setClipBounds(nextTitleClipBounds);
        float nextScale = AndroidUtilities.lerp(.5f, 1f, p);
        titles[1].setScaleX(nextScale);
        titles[1].setScaleY(nextScale);
        titles[1].setAlpha(p * (1f - searchingProgress) * (1f - addressingProgress));
        if (p > 0f) {
            titles[1].bringToFront();
        }
    }

    public void setLoadingProgress(int progress, boolean visible) {
        int clamped = Math.max(0, Math.min(100, progress));
        progressView.setProgress(clamped / 100f, true);
    }

    public void setProgress(float progress) {
        setProgress(0, progress);
    }

    public void setProgress(int i, float progress) {
        this.progress[i] = progress;
        invalidate();
    }

    /**
     * Adaptive color entry point — mirrors WebActionBar.java L518-519. Always animates from
     * the previous bar color (or the theme color on first call) to {@code backgroundColor}.
     */
    public void setColors(int backgroundColor, boolean animated) {
        setColors(backgroundColor, -1f, animated);
    }

    /**
     * Adaptive color update — mirrors WebActionBar.java L522-602. {@code dark} negative means
     * "compute from perceived brightness"; the animator path passes the lerp'd value so the
     * derived text/icon colors don't twitch between frames.
     */
    public void setColors(int backgroundColor, float dark, boolean animated) {
        if (adaptiveColorSet && currentBackgroundColor == backgroundColor) {
            return;
        }
        if (!animated) {
            adaptiveColorSet = true;
            if (dark < 0) {
                dark = AndroidUtilities.computePerceivedBrightness(backgroundColor) <= .721f ? 1f : 0f;
            }
            currentBackgroundColor = backgroundColor;
            currentTextColor = ColorUtils.blendARGB(Color.BLACK, Color.WHITE, dark);
            currentIconColor = multAlpha(currentTextColor, .55f);

            super.setBackgroundColor(backgroundColor);
            int progressBackgroundColor = ColorUtils.compositeColors(multAlpha(currentTextColor, AndroidUtilities.lerp(.07f, .2f, dark)), backgroundColor);
            for (Paint paint : progressBackgroundPaint) {
                paint.setColor(progressBackgroundColor);
            }
            updateAddressColors();

            // Apply text/icon colors to title slots, address EditText, search EditText, back
            // button, and ActionBarMenu icons so the whole bar tracks the page brightness.
            for (Title title : titles) {
                title.setTitleTextColor(currentTextColor);
                int subtitleColor = ColorUtils.compositeColors(multAlpha(currentTextColor, .6f), backgroundColor);
                title.setSubtitleTextColor(subtitleColor);
            }
            addressEditText.setTextColor(addressTextColor);
            addressEditText.setHintTextColor(multAlpha(addressTextColor, .6f));
            searchEditText.setTextColor(currentTextColor);
            searchEditText.setHintTextColor(multAlpha(currentTextColor, .6f));

            backButtonDrawable.setColor(ColorUtils.blendARGB(currentTextColor, addressTextColor, addressingProgress));
            backButtonDrawable.setRotatedColor(ColorUtils.blendARGB(currentTextColor, addressTextColor, addressingProgress));

            itemsColor = currentTextColor;
            if (menu != null) {
                // ActionBarMenu.updateItemsColor() is package-private, but we already know the
                // children are ActionBarMenuItem and they expose setIconColor publicly.
                for (int i = 0; i < menu.getChildCount(); i++) {
                    View child = menu.getChildAt(i);
                    if (child instanceof ActionBarMenuItem) {
                        ((ActionBarMenuItem) child).setIconColor(currentTextColor);
                    }
                }
            }

            invalidate();
            return;
        }

        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        fromBackgroundColor = currentBackgroundColor;
        final float fromDark = AndroidUtilities.computePerceivedBrightness(fromBackgroundColor) <= .721f ? 1f : 0f;
        final float toDark = AndroidUtilities.computePerceivedBrightness(backgroundColor) <= .721f ? 1f : 0f;
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            setColors(
                    ColorUtils.blendARGB(fromBackgroundColor, backgroundColor, t),
                    AndroidUtilities.lerp(fromDark, toDark, t),
                    false
            );
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setColors(backgroundColor, toDark, false);
            }
        });
        colorAnimator.start();
    }

    public int getBackgroundColorAdaptive() {
        return adaptiveColorSet ? currentBackgroundColor : Theme.getColor(Theme.key_actionBarDefault);
    }

    public int getTextColorAdaptive() {
        return adaptiveColorSet ? currentTextColor : Theme.getColor(Theme.key_actionBarDefaultTitle);
    }

    public boolean isAddressing() {
        return addressing;
    }

    public void closeAddress(boolean animated) {
        showAddress(false, animated);
    }

    public void showSearch(boolean show, boolean animated) {
        if (searching == show) {
            return;
        }
        if (searchAnimator != null) {
            searchAnimator.cancel();
        }
        searching = show;
        backButtonDrawable.setRotation(backButtonShown || show ? 0f : 1f, animated);
        if (show) {
            searchEditText.setVisibility(VISIBLE);
            if (addressing) {
                showAddress(false, false);
            }
        }
        if (animated) {
            searchAnimator = ValueAnimator.ofFloat(searchingProgress, show ? 1f : 0f);
            if (show) {
                menu.setVisibility(VISIBLE);
                menu.setAlpha(1f);
            }
            searchAnimator.addUpdateListener(animation -> {
                searchingProgress = (float) animation.getAnimatedValue();
                applySearchProgress();
            });
            searchAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    searchingProgress = show ? 1f : 0f;
                    applySearchProgress();
                    if (!searching) {
                        searchEditText.setVisibility(GONE);
                        searchEditText.setText("");
                        menu.setVisibility(VISIBLE);
                        menu.setAlpha(1f);
                    } else {
                        menu.setVisibility(GONE);
                    }
                    updateSearchKeyboard();
                }
            });
            searchAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            searchAnimator.setDuration(320);
            searchAnimator.start();
        } else {
            searchingProgress = show ? 1f : 0f;
            searchEditText.setVisibility(show ? VISIBLE : GONE);
            if (!show) {
                searchEditText.setText("");
                menu.setVisibility(VISIBLE);
                menu.setAlpha(1f);
            } else {
                menu.setVisibility(GONE);
            }
            applySearchProgress();
            updateSearchKeyboard();
        }
    }

    private void applySearchProgress() {
        float p = Math.max(0f, Math.min(1f, searchingProgress));
        titleContainer.setAlpha(1f - p * (1f - addressingProgress));
        searchEditText.setAlpha(p);
        if (menu != null) {
            menu.setAlpha(1f - p);
        }
    }

    private void updateSearchKeyboard() {
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (searching) {
            searchEditText.requestFocus();
            if (imm != null) {
                imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            searchEditText.clearFocus();
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
            }
        }
    }

    public boolean isSearching() {
        return searching;
    }

    public void setBackButton(boolean show) {
        backButtonShown = show;
        if (!isSearching() && !isAddressing()) {
            backButtonDrawable.setRotation(backButtonShown ? 0f : 1f, true);
        }
    }

    public void setBackButtonCached(boolean show) {
        backButtonShown = show;
    }

    public boolean isBackButton() {
        return backButtonShown;
    }

    public void setBackButtonRotation(float rotation, boolean animated) {
        backButtonDrawable.setRotation(rotation, animated);
    }

    public float getBackButtonRotation() {
        return backButtonDrawable.getRotation();
    }

    public void setOnBackButtonLongClickListener(View.OnLongClickListener listener) {
        getBackButtonImageView().setOnLongClickListener(listener);
    }

    public void showAddress(String url, boolean animated) {
        addressEditText.setText(url == null ? "" : url);
        addressEditText.selectAll();
        showAddress(true, animated);
    }

    public void showAddress(boolean show, boolean animated) {
        if (addressing == show) {
            return;
        }
        if (addressAnimator != null) {
            addressAnimator.cancel();
        }
        addressing = show;
        backButtonDrawable.setRotation(backButtonShown || show ? 0f : 1f, animated);
        updateAddressColors();
        if (animated) {
            if (show) {
                addressEditText.setVisibility(VISIBLE);
            }
            addressAnimator = ValueAnimator.ofFloat(addressingProgress, show ? 1f : 0f);
            addressAnimator.addUpdateListener(animation -> {
                addressingProgress = (float) animation.getAnimatedValue();
                onAddressingProgress(addressingProgress);
                applyAddressProgress();
                invalidate();
            });
            addressAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    addressingProgress = show ? 1f : 0f;
                    onAddressingProgress(addressingProgress);
                    applyAddressProgress();
                    if (!addressing) {
                        addressEditText.setVisibility(GONE);
                        if (menu != null && menu.getChildCount() >= 2) {
                            menu.getChildAt(0).setTranslationX(0);
                            menu.getChildAt(1).setTranslationX(0);
                        }
                    }
                    invalidate();
                }
            });
            addressAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            addressAnimator.setDuration(360);
            addressAnimator.start();
        } else {
            addressingProgress = show ? 1f : 0f;
            addressEditText.setVisibility(show ? VISIBLE : GONE);
            onAddressingProgress(addressingProgress);
            applyAddressProgress();
            invalidate();
        }
        scheduleAddressKeyboard();
    }

    private Runnable pendingAddressKeyboard;

    private void scheduleAddressKeyboard() {
        if (pendingAddressKeyboard != null) {
            removeCallbacks(pendingAddressKeyboard);
        }
        pendingAddressKeyboard = () -> {
            pendingAddressKeyboard = null;
            updateAddressKeyboard();
        };
        postDelayed(pendingAddressKeyboard, addressing ? 100 : 0);
    }

    private void applyAddressProgress() {
        titleContainer.setAlpha(1f - addressingProgress);
        addressEditText.setAlpha(addressingProgress);
        addressEditText.setScaleX(0.96f + 0.04f * addressingProgress);
        if (menu != null && menu.getChildCount() >= 2) {
            menu.getChildAt(0).setTranslationX(AndroidUtilities.dp(56 + 56) * addressingProgress);
            menu.getChildAt(1).setTranslationX(AndroidUtilities.dp(56) * addressingProgress);
        }
    }

    private void updateAddressKeyboard() {
        InputMethodManager inputMethodManager = getContext().getSystemService(InputMethodManager.class);
        if (addressing) {
            addressEditText.requestFocus();
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(addressEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            addressEditText.clearFocus();
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(addressEditText.getWindowToken(), 0);
            }
        }
    }

    public void hideKeyboard() {
        if (searching) {
            searchEditText.clearFocus();
            AndroidUtilities.hideKeyboard(searchEditText);
        }
        if (addressing) {
            addressEditText.clearFocus();
            AndroidUtilities.hideKeyboard(addressEditText);
        }
    }

    public boolean hideKeyboardIfInputFocused() {
        if (searchEditText.isFocused()) {
            searchEditText.clearFocus();
            AndroidUtilities.hideKeyboard(searchEditText);
            return true;
        }
        if (addressEditText.isFocused()) {
            addressEditText.clearFocus();
            AndroidUtilities.hideKeyboard(addressEditText);
            return true;
        }
        return false;
    }

    private void onAddressingProgress(float progress) {
        // Blend from the live page-derived textColor (see setColors) when adaptive, otherwise
        // from the stock items color, just as upstream does in WebActionBar.java L560-561.
        int restColor = adaptiveColorSet ? currentTextColor : itemsColor;
        backButtonDrawable.setColor(ColorUtils.blendARGB(restColor, addressTextColor, addressingProgress));
        backButtonDrawable.setRotatedColor(ColorUtils.blendARGB(restColor, addressTextColor, addressingProgress));
        if (delegate != null) {
            delegate.onAddressingProgress(progress);
        }
    }

    private void updateAddressColors() {
        // When adaptive color is active, source the brightness from the live page color so the
        // address overlay matches the bar; otherwise keep upstream's theme-based fallback.
        int bg = adaptiveColorSet ? currentBackgroundColor : Theme.getColor(Theme.key_actionBarDefault);
        float dark = AndroidUtilities.computePerceivedBrightness(bg) <= .721f ? 1f : 0f;
        addressBackgroundColor = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, dark);
        addressTextColor = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 1f - dark);
        addressBackgroundPaint.setColor(addressBackgroundColor);
        if (adaptiveColorSet) {
            // Mirrors upstream addressRoundPaint = blendOver(addressBackgroundColor,
            // multAlpha(textColor, lerp(.07f, .2f, dark))) — see WebActionBar.java L552.
            int tint = multAlpha(currentTextColor, AndroidUtilities.lerp(.07f, .2f, dark));
            addressRoundPaint.setColor(ColorUtils.compositeColors(tint, addressBackgroundColor));
        } else {
            addressRoundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        if (delegate != null) {
            delegate.onAddressColorsChanged(addressBackgroundColor, addressTextColor);
        }
    }

    private static int multAlpha(int color, float multiply) {
        int a = Math.max(0, Math.min(0xFF, Math.round(Color.alpha(color) * multiply)));
        return ColorUtils.setAlphaComponent(color, a);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float l = getWidth() * titleProgress;
        if (titleProgress > 0f && progress[1] > 0f) {
            int wasAlpha = progressBackgroundPaint[1].getAlpha();
            progressBackgroundPaint[1].setAlpha((int) (wasAlpha * (1f - searchingProgress) * (1f - addressingProgress)));
            canvas.drawRect(0, 0, progress[1] * getWidth(), getHeight(), progressBackgroundPaint[1]);
            progressBackgroundPaint[1].setAlpha(wasAlpha);
        }
        if (progress[0] > 0f) {
            int wasAlpha = progressBackgroundPaint[0].getAlpha();
            progressBackgroundPaint[0].setAlpha((int) ((1f - clamp01(titleProgress * 4)) * wasAlpha * (1f - searchingProgress) * (1f - addressingProgress)));
            canvas.drawRect(l, 0, l + progress[0] * getWidth(), getHeight(), progressBackgroundPaint[0]);
            progressBackgroundPaint[0].setAlpha(wasAlpha);
        }
        if (addressingProgress > 0) {
            int wasAlpha = addressBackgroundPaint.getAlpha();
            addressBackgroundPaint.setAlpha((int) (wasAlpha * addressingProgress));
            canvas.drawRect(0, 0, getWidth(), getHeight(), addressBackgroundPaint);
            addressBackgroundPaint.setAlpha(wasAlpha);

            int topInset = AndroidUtilities.statusBarHeight;
            int barHeight = AndroidUtilities.dp(56);
            int menuWidth = menu == null ? 0 : menu.getMeasuredWidth();
            float rightEdge = getWidth() - menuWidth - AndroidUtilities.dp(12);

            float h = AndroidUtilities.dp(42);
            float cx = (topInset + topInset + barHeight) / 2f;
            addressRect.set(
                    AndroidUtilities.dp(6),
                    cx - h / 2f,
                    AndroidUtilities.lerp(rightEdge, getWidth() - AndroidUtilities.dp(6), addressingProgress),
                    cx + h / 2f
            );
            wasAlpha = addressRoundPaint.getAlpha();
            addressRoundPaint.setAlpha((int) (wasAlpha * addressingProgress));
            canvas.drawRoundRect(addressRect, AndroidUtilities.dp(50), AndroidUtilities.dp(50), addressRoundPaint);
            addressRoundPaint.setAlpha(wasAlpha);
        }

        super.dispatchDraw(canvas);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int topInset = AndroidUtilities.statusBarHeight;
        int barHeight = AndroidUtilities.dp(56);
        int menuWidth = menu == null ? 0 : menu.getMeasuredWidth();
        int left = AndroidUtilities.dp(54);
        int right = AndroidUtilities.dp(12) + menuWidth;
        int titleWidth = Math.max(0, width - left - right);

        titleContainer.measure(
                MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        addressEditText.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, width - AndroidUtilities.dp(60)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY)
        );
        progressView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(2), MeasureSpec.EXACTLY)
        );
        searchEditText.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, width - AndroidUtilities.dp(64) - AndroidUtilities.dp(54)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        searchClearButton.measure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(54), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        setMeasuredDimension(width, topInset + barHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int topInset = AndroidUtilities.statusBarHeight;
        int menuWidth = menu == null ? 0 : menu.getMeasuredWidth();
        int titleLeft = AndroidUtilities.dp(54);
        int titleRight = getMeasuredWidth() - menuWidth - AndroidUtilities.dp(12);
        titleContainer.layout(titleLeft, topInset, titleRight, topInset + AndroidUtilities.dp(56));

        int addressLeft = AndroidUtilities.dp(48);
        int addressTop = topInset + AndroidUtilities.dp(7);
        addressEditText.layout(addressLeft, addressTop, getMeasuredWidth() - AndroidUtilities.dp(12), addressTop + AndroidUtilities.dp(42));
        progressView.layout(0, getMeasuredHeight() - AndroidUtilities.dp(2), getMeasuredWidth(), getMeasuredHeight());
        searchEditText.layout(AndroidUtilities.dp(64), topInset, getMeasuredWidth() - AndroidUtilities.dp(64) - AndroidUtilities.dp(54), topInset + AndroidUtilities.dp(56));
        searchClearButton.layout(getMeasuredWidth() - AndroidUtilities.dp(54), topInset, getMeasuredWidth(), topInset + AndroidUtilities.dp(56));
    }

    /**
     * L0 port of WebActionBar.dispatchTouchEvent (L1013-1034). The press window
     * {@code 0.8 * ViewConfiguration.getLongPressTimeout()} and the normalized
     * {@code (x - pressX) / (0.8f * getWidth())} delta are kept verbatim so the scrubber
     * feels identical to upstream.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            longClicked = false;
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            if (ev.getX() > getLeftMenuRight() && ev.getX() < getRightMenuLeft()
                    && !isSearching() && !isAddressing()) {
                pressX = ev.getX();
                pressY = ev.getY();
                pressTime = System.currentTimeMillis();
                AndroidUtilities.runOnUIThread(longPressRunnable, (long) (ViewConfiguration.getLongPressTimeout() * .8f));
            }
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE
                && System.currentTimeMillis() - pressTime > ViewConfiguration.getLongPressTimeout() * .8f) {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            longClicked = true;
            onScrolledProgress((ev.getX() - pressX) / (.8f * getWidth()));
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            pressTime = 0;
        }
        pressX = ev.getX();
        return super.dispatchTouchEvent(ev);
    }

    private int getLeftMenuRight() {
        return AndroidUtilities.dp(54);
    }

    private int getRightMenuLeft() {
        if (menu == null) {
            return getWidth();
        }
        int menuWidth = menu.getWidth() > 0 ? menu.getWidth() : menu.getMeasuredWidth();
        return getWidth() - menuWidth;
    }

    /**
     * Forwards the L0 normalized delta to the host so it can advance the WebView scroll —
     * mirrors WebActionBar.onScrolledProgress(float) being overridden in ArticleViewer to
     * call pages[0].addProgress(delta).
     */
    protected void onScrolledProgress(float delta) {
        if (delegate != null) {
            delegate.onScrolledProgress(delta);
        }
    }

    private static class Title extends View {

        private final AnimatedTextDrawable titleDrawable = new AnimatedTextDrawable(true, true, true);
        private final AnimatedTextDrawable subtitleDrawable = new AnimatedTextDrawable(true, true, true);
        private int titleColor = Theme.getColor(Theme.key_actionBarDefaultTitle);
        private int subtitleColor = Theme.getColor(Theme.key_actionBarDefaultSubtitle);

        Title(Context context) {
            super(context);

            titleDrawable.ignoreRTL = true;
            titleDrawable.setTextSize(AndroidUtilities.dp(18.33f));
            titleDrawable.setScaleProperty(.6f);
            titleDrawable.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            titleDrawable.setEllipsizeByGradient(false);
            titleDrawable.setCallback(this);
            titleDrawable.setOverrideFullWidth(9999999);
            titleDrawable.setTextColor(titleColor);

            subtitleDrawable.ignoreRTL = true;
            subtitleDrawable.setTextSize(AndroidUtilities.dp(14));
            subtitleDrawable.setEllipsizeByGradient(false);
            subtitleDrawable.setCallback(this);
            subtitleDrawable.setOverrideFullWidth(9999999);
            subtitleDrawable.setTextColor(subtitleColor);
        }

        void setTitleText(CharSequence value) {
            setTitleText(value, true);
        }

        void setTitleText(CharSequence value, boolean animated) {
            titleDrawable.setText(value, animated);
        }

        void setSubtitleText(CharSequence value) {
            setSubtitleText(value, false);
        }

        void setSubtitleText(CharSequence value, boolean animated) {
            subtitleDrawable.setText(value, animated);
        }

        void setTitleTextColor(int color) {
            titleColor = color;
            titleDrawable.setTextColor(color);
        }

        void setSubtitleTextColor(int color) {
            subtitleColor = color;
            subtitleDrawable.setTextColor(color);
        }

        CharSequence getTitleText() {
            return titleDrawable.getText() == null ? "" : titleDrawable.getText();
        }

        CharSequence getSubtitleText() {
            return subtitleDrawable.getText() == null ? "" : subtitleDrawable.getText();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float both = titleDrawable.isNotEmpty() * subtitleDrawable.isNotEmpty();

            canvas.save();
            canvas.translate(0, -AndroidUtilities.dp(1));
            canvas.translate(0, -AndroidUtilities.dp(4) * both);
            float titleScale = AndroidUtilities.lerp(1f, .86f, both);
            canvas.scale(titleScale, titleScale, 0, 0);
            titleDrawable.setBounds(0, 0, w, h);
            titleDrawable.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.translate(0, -AndroidUtilities.dp(1) + AndroidUtilities.dp(14) * both - AndroidUtilities.dp(4) * (1f - both));
            float subtitleScale = AndroidUtilities.lerp(1.15f, .9f, both);
            canvas.scale(subtitleScale, subtitleScale, 0, 0);
            subtitleDrawable.setBounds(0, 0, w, h);
            subtitleDrawable.draw(canvas);
            canvas.restore();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == titleDrawable || who == subtitleDrawable || super.verifyDrawable(who);
        }
    }

    private static class MoreDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MoreDrawable() {
            paint.setColor(0xff000000);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float cx = getBounds().centerX();
            float cy = getBounds().centerY();
            float r = AndroidUtilities.dpf2(1.65f);
            canvas.drawCircle(cx, cy - AndroidUtilities.dp(6), r, paint);
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawCircle(cx, cy + AndroidUtilities.dp(6), r, paint);
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

    private static class CollapseDrawable extends Drawable {
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CollapseDrawable() {
            paint.setColor(0xff000000);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float cx = getBounds().centerX();
            float cy = getBounds().centerY();
            float w = getBounds().width();
            float arrowWidth = w * 0.57f;

            path.rewind();
            path.moveTo(cx + arrowWidth / 2f, cy);
            path.lineTo(cx + arrowWidth / 2f, cy);
            path.moveTo(cx + arrowWidth / 2f - w * .27f, cy - w * .54f / 2f);
            path.lineTo(cx + arrowWidth / 2f, cy);
            path.lineTo(cx + arrowWidth / 2f - w * .27f, cy + w * .54f / 2f);

            canvas.save();
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            canvas.translate(0, -w * 0.10f);
            canvas.rotate(90, cx, cy);
            canvas.drawPath(path, paint);
            canvas.restore();
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

    static class SearchClearDrawable extends Drawable {
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SearchClearDrawable() {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float cx = getBounds().centerX();
            float cy = getBounds().centerY();
            float s = getBounds().width() * 0.25f;

            path.rewind();
            path.moveTo(cx - s, cy - s);
            path.lineTo(cx + s, cy + s);
            path.moveTo(cx + s, cy - s);
            path.lineTo(cx - s, cy + s);

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
}

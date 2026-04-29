// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/web/WebActionBar.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: browser-actionbar=L2, address-animation=L0, progress-animation=L1, title-swap=L1, popup-menu=L1
// HOST-WIRING:
//   - Extends NativeDo's current ActionBar slice so ActionBarLayout can host it unchanged.
//   - Uses NativeDo strings, Theme keys, and existing ActionBarMenu popup support.
// ADAPTED:
//   - Keeps Telegram WebActionBar's title/subtitle, address mode, progress, and menu shape.
//   - Address animation uses upstream 360ms EASE_OUT_QUINT.
//   - Keeps Telegram's sheet-browser toolbar semantics: the right toolbar arrow collapses to tabs;
//     NativeDo omits forward from both the action bar and overflow for http browser parity.
//   - Replaces the placeholder line with NativeDo's LineProgressView (smooth 200ms EASE_OUT_QUINT chase).
//   - Adds Title[2] slots + titleProgress cross-slide (360ms EASE_OUT_QUINT) for navigation
//     transitions, in lieu of the upstream AnimatedTextDrawable per-character animation
//     (kept as a follow-up).
// OMITTED:
//   - Search field, dynamic color extraction, Instant View, bookmarks/history/settings state,
//     bottom action bar mode, long-press page scrubber, and Telegram item-options menu icons.
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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

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
    private final EditText addressEditText;
    private final LineProgressView progressView;

    private String currentTitle = "";
    private String currentUrl = "";
    private boolean addressing;
    private float addressingProgress;
    private ValueAnimator addressAnimator;

    private float titleProgress;
    private ValueAnimator titleAnimator;

    private boolean searching;
    private float searchingProgress;
    private final EditText searchEditText;
    private final ImageView searchClearButton;
    private ValueAnimator searchAnimator;

    private final Paint addressBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint addressRoundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF addressRect = new RectF();
    private int addressBackgroundColor;
    private int addressTextColor;

    public WebActionBar(Context context, Delegate delegate) {
        super(context);
        this.delegate = delegate;
        super.setTitle("");
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
        View.OnClickListener showAddressClick = v -> {
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

    public void setCurrentUrl(String url) {
        currentUrl = url == null ? "" : url;
        setSubtitle(currentUrl);
    }

    /**
     * Updates the off-screen slot used by the next {@link #swap(boolean)} animation.
     */
    public void setNextTitle(CharSequence value, CharSequence subtitle) {
        titles[1].setTitleText(value == null ? "" : value);
        titles[1].setSubtitleText(subtitle == null ? "" : subtitle);
    }

    /**
     * Cross-slides the off-screen slot in over 360ms (EASE_OUT_QUINT) and swaps the slot indices
     * once the animation settles, mirroring upstream WebActionBar.swap().
     */
    public void swap(boolean animated) {
        if (titleAnimator != null) {
            titleAnimator.cancel();
            titleAnimator = null;
        }
        currentTitle = titles[1].getTitleText().toString();
        currentUrl = titles[1].getSubtitleText().toString();
        titles[1].setVisibility(VISIBLE);
        if (!animated) {
            titleProgress = 1f;
            applyTitleProgress();
            commitSwap();
            return;
        }
        titleAnimator = ValueAnimator.ofFloat(titleProgress, 1f);
        titleAnimator.addUpdateListener(animation -> {
            titleProgress = (float) animation.getAnimatedValue();
            applyTitleProgress();
        });
        titleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                titleProgress = 1f;
                applyTitleProgress();
                commitSwap();
            }
        });
        titleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        titleAnimator.setDuration(360);
        titleAnimator.start();
    }

    private void applyTitleProgress() {
        float p = Math.max(0f, Math.min(1f, titleProgress));
        int width = titleContainer.getWidth();
        // Slot 0 slides slightly left and fades out; slot 1 slides in from the right.
        titles[0].setTranslationX(-AndroidUtilities.dp(12) * p);
        titles[0].setAlpha(1f - p);
        titles[1].setTranslationX(width <= 0 ? AndroidUtilities.dp(12) * (1f - p) : (width * 0.18f) * (1f - p));
        titles[1].setAlpha(p);
    }

    private void commitSwap() {
        // After the slide finishes, slot[1] becomes the resting slot.
        Title tmp = titles[0];
        titles[0] = titles[1];
        titles[1] = tmp;
        titleProgress = 0f;
        titles[0].setTranslationX(0f);
        titles[0].setAlpha(1f);
        titles[1].setVisibility(GONE);
        titles[1].setTranslationX(0f);
        titles[1].setAlpha(0f);
        titles[1].setTitleText("");
        titles[1].setSubtitleText("");
    }

    public void setProgress(int progress, boolean visible) {
        int clamped = Math.max(0, Math.min(100, progress));
        progressView.setProgress(clamped / 100f, true);
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
        backButtonDrawable.setRotation(show ? 0f : 1f, animated);
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
        backButtonDrawable.setRotation(show ? 0f : 1f, animated);
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
            if (addressing) {
                updateAddressKeyboard();
            }
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

    private void onAddressingProgress(float progress) {
        backButtonDrawable.setColor(ColorUtils.blendARGB(itemsColor, addressTextColor, addressingProgress));
        backButtonDrawable.setRotatedColor(ColorUtils.blendARGB(itemsColor, addressTextColor, addressingProgress));
        if (delegate != null) {
            delegate.onAddressingProgress(progress);
        }
    }

    private void updateAddressColors() {
        int bg = Theme.getColor(Theme.key_actionBarDefault);
        float dark = AndroidUtilities.computePerceivedBrightness(bg) <= .721f ? 1f : 0f;
        addressBackgroundColor = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, dark);
        addressTextColor = ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 1f - dark);
        addressBackgroundPaint.setColor(addressBackgroundColor);
        addressRoundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (delegate != null) {
            delegate.onAddressColorsChanged(addressBackgroundColor, addressTextColor);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int topInset = AndroidUtilities.statusBarHeight;
        int barHeight = AndroidUtilities.dp(56);
        int menuWidth = menu == null ? 0 : menu.getMeasuredWidth();
        int left = AndroidUtilities.dp(72);
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
        int titleLeft = AndroidUtilities.dp(72);
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
     * Single title/subtitle slot. Two of these layered inside titleContainer give us the cross-slide
     * swap upstream achieves with its painted titles[] + AnimatedTextDrawable; that fancier per-glyph
     * animation can replace this slot later without changing the public swap() API.
     */
    private static class Title extends FrameLayout {

        private final TextView titleView;
        private final TextView subtitleView;

        Title(Context context) {
            super(context);

            titleView = new TextView(context);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
            FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(25),
                    Gravity.TOP | Gravity.START);
            titleLp.topMargin = AndroidUtilities.dp(5);
            addView(titleView, titleLp);

            subtitleView = new TextView(context);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            subtitleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
            FrameLayout.LayoutParams subtitleLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(19),
                    Gravity.TOP | Gravity.START);
            subtitleLp.topMargin = AndroidUtilities.dp(30);
            addView(subtitleView, subtitleLp);
        }

        void setTitleText(CharSequence value) {
            titleView.setText(value);
        }

        void setSubtitleText(CharSequence value) {
            subtitleView.setText(value);
        }

        CharSequence getTitleText() {
            return titleView.getText() == null ? "" : titleView.getText();
        }

        CharSequence getSubtitleText() {
            return subtitleView.getText() == null ? "" : subtitleView.getText();
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

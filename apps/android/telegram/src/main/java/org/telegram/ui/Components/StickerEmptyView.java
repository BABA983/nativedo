/*
 * Host-shaped empty state used by BookmarksFragment / HistoryFragment.
 * Layout/spacing match upstream StickerEmptyView for STICKER_TYPE_SEARCH;
 * Telegram sticker assets / MediaDataController are not ported — the sticker
 * slot is omitted so empty bookmarks/history show a clean centered title.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Pin-shaped empty list chrome without animated sticker dependency.
 */
public class StickerEmptyView extends FrameLayout {

    public static final int STICKER_TYPE_NO_CONTACTS = 0;
    public static final int STICKER_TYPE_SEARCH = 1;
    public static final int STICKER_TYPE_ALBUM = 11;
    public static final int STICKER_TYPE_PRIVACY = 12;
    public static final int STICKER_TYPE_DONE = 16;

    public final LinearLayout linearLayout;
    public final TextView title;
    public final TextView subtitle;

    private final Theme.ResourcesProvider resourcesProvider;
    private final View progressView;
    private RadialProgressView progressBar;
    private boolean progressShowing;
    private boolean animateLayoutChange;
    private int lastH;

    public StickerEmptyView(@NonNull Context context, View progressView, int type) {
        this(context, progressView, type, null);
    }

    public StickerEmptyView(@NonNull Context context, View progressView, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.progressView = progressView;

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        title = new TextView(context);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(16), 0, dp(16), 0);

        subtitle = new LinkSpanDrawable.LinksTextView(context);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(dp(24), 0, dp(24), 0);

        // Pin stacks sticker (117) then title (+12). Without sticker assets keep a
        // modest top spacer so the title sits near the visual center, not the top.
        linearLayout.addView(title, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        linearLayout.addView(subtitle, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
        addView(linearLayout, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 46, 0, 46, 30));

        if (progressView == null) {
            progressBar = new RadialProgressView(context, resourcesProvider);
            progressBar.setAlpha(0f);
            progressBar.setScaleY(0.5f);
            progressBar.setScaleX(0.5f);
            addView(progressBar, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
    }

    public void showProgress(boolean show, boolean animated) {
        progressShowing = show;
        if (show) {
            linearLayout.animate().cancel();
            if (animated) {
                linearLayout.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).start();
            } else {
                linearLayout.setAlpha(0f);
                linearLayout.setScaleX(0.8f);
                linearLayout.setScaleY(0.8f);
            }
            if (progressView != null) {
                progressView.setVisibility(VISIBLE);
                if (animated) {
                    progressView.animate().cancel();
                    progressView.animate().alpha(1f).setDuration(150).start();
                } else {
                    progressView.setAlpha(1f);
                }
            } else if (progressBar != null) {
                if (animated) {
                    progressBar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
                } else {
                    progressBar.setAlpha(1f);
                    progressBar.setScaleX(1f);
                    progressBar.setScaleY(1f);
                }
            }
        } else {
            if (animated) {
                linearLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
            } else {
                linearLayout.setAlpha(1f);
                linearLayout.setScaleX(1f);
                linearLayout.setScaleY(1f);
            }
            if (progressView != null) {
                if (animated) {
                    progressView.animate().setListener(null).cancel();
                    progressView.animate().alpha(0f).setDuration(150).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            progressView.setVisibility(GONE);
                        }
                    }).start();
                } else {
                    progressView.setAlpha(0f);
                    progressView.setVisibility(GONE);
                }
            } else if (progressBar != null) {
                if (animated) {
                    progressBar.animate().alpha(0f).scaleX(0.5f).scaleY(0.5f).setDuration(150).start();
                } else {
                    progressBar.setAlpha(0f);
                    progressBar.setScaleX(0.5f);
                    progressBar.setScaleY(0.5f);
                }
            }
        }
    }

    public void setAnimateLayoutChange(boolean value) {
        animateLayoutChange = value;
    }

    /** Pin-compatible helpers used by some call sites. */
    public void title(CharSequence text) {
        title.setText(text);
    }

    public void subtitle(CharSequence text) {
        subtitle.setText(text);
    }

    public void setColors(int titleKey, int subtitleKey, int key1, int key2) {
        title.setTextColor(getThemedColor(titleKey));
        subtitle.setTextColor(getThemedColor(subtitleKey));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if ((animateLayoutChange) && lastH > 0 && lastH != getMeasuredHeight()) {
            float y = (lastH - getMeasuredHeight()) / 2f;
            linearLayout.setTranslationY(linearLayout.getTranslationY() + y);
            linearLayout.animate().translationY(0).setDuration(250).start();
            if (progressBar != null) {
                progressBar.setTranslationY(progressBar.getTranslationY() + y);
                progressBar.animate().translationY(0).setDuration(250).start();
            }
        }
        lastH = getMeasuredHeight();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

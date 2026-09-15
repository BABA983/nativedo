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
import android.animation.ValueAnimator;
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
    private boolean preventMoving;
    private int keyboardSize;
    private int lastH;
    private ValueAnimator visibilityAnimator;
    private float visibilityFactor;
    private boolean visibilityValue;

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
        title.setTag(Theme.key_windowBackgroundWhiteBlackText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(16), 0, dp(16), 0);

        subtitle = new LinkSpanDrawable.LinksTextView(context);
        subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setLinkTextColor(getThemedColor(Theme.key_windowBackgroundWhiteLinkText));
        subtitle.setTag(Theme.key_windowBackgroundWhiteGrayText);
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

    public void showProgress(boolean show) {
        showProgress(show, true);
    }

    public void showProgress(boolean show, boolean animated) {
        if (progressShowing == show) {
            return;
        }
        progressShowing = show;
        if (getVisibility() != View.VISIBLE) {
            return;
        }
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

    public void setKeyboardHeight(int keyboardSize, boolean animated) {
        if (this.keyboardSize == keyboardSize) {
            return;
        }
        if (getVisibility() != View.VISIBLE) {
            animated = false;
        }
        this.keyboardSize = keyboardSize;
        float y = -(keyboardSize >> 1) + (keyboardSize > 0 ? dp(20) : 0);
        if (animated) {
            linearLayout.animate().translationY(y).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
            if (progressBar != null) {
                progressBar.animate().translationY(y).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
            }
        } else {
            linearLayout.setTranslationY(y);
            if (progressBar != null) {
                progressBar.setTranslationY(y);
            }
        }
    }

    public void setPreventMoving(boolean preventMoving) {
        this.preventMoving = preventMoving;
        if (!preventMoving) {
            linearLayout.setTranslationY(0);
            if (progressBar != null) {
                progressBar.setTranslationY(0);
            }
        }
    }

    /** Pin-compatible helpers used by some call sites. */
    public void title(CharSequence text) {
        title.setText(text);
    }

    public void subtitle(CharSequence text) {
        setSubtitle(text);
    }

    public void setSubtitle(CharSequence text) {
        int whitespaceCount = getWhitespaceCount(text);
        if (whitespaceCount > 4 && text.length() > 20) {
            int closestToCenterWhitespace = -1;
            int closestValue = 0;
            int centerIndex = text.length() >> 1;
            for (int i = 0; i < text.length(); i++) {
                if (Character.isWhitespace(text.charAt(i))) {
                    int value = Math.abs(centerIndex - i);
                    if (closestToCenterWhitespace == -1 || value < closestValue) {
                        closestValue = value;
                        closestToCenterWhitespace = i;
                    }
                }
            }
            if (closestToCenterWhitespace > 0) {
                text = text.subSequence(0, closestToCenterWhitespace) + "\n" + text.subSequence(closestToCenterWhitespace + 1, text.length());
            }
        }
        subtitle.setText(text);
    }

    private int getWhitespaceCount(CharSequence text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    public void setColors(int titleKey, int subtitleKey, int key1, int key2) {
        title.setTag(titleKey);
        title.setTextColor(getThemedColor(titleKey));
        subtitle.setTag(subtitleKey);
        subtitle.setTextColor(getThemedColor(subtitleKey));
    }

    @Override
    public void setVisibility(int visibility) {
        setVisibility(visibility, true);
    }

    public void setVisibility(int visibility, boolean animated) {
        setVisibility(visibility == VISIBLE, animated, false);

        if (getVisibility() != visibility && visibility == VISIBLE) {
            if (progressShowing) {
                linearLayout.animate().alpha(0f).scaleY(0.8f).scaleX(0.8f).setDuration(150).start();
                showProgressView(false);
            } else {
                linearLayout.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(150).start();
                hideProgressView(true);
            }
        }
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            lastH = 0;
            linearLayout.animate().cancel();
            linearLayout.setAlpha(0f);
            linearLayout.setScaleX(0.8f);
            linearLayout.setScaleY(0.8f);
            hideProgressView(true);
        }
    }

    private void showProgressView(boolean animated) {
        if (progressView != null) {
            progressView.animate().setListener(null).cancel();
            progressView.setVisibility(VISIBLE);
            if (animated) {
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
    }

    private void hideProgressView(boolean animated) {
        if (progressView != null) {
            progressView.animate().setListener(null).cancel();
            if (animated) {
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if ((animateLayoutChange || preventMoving) && lastH > 0 && lastH != getMeasuredHeight()) {
            float y = (lastH - getMeasuredHeight()) / 2f;
            linearLayout.setTranslationY(linearLayout.getTranslationY() + y);
            if (!preventMoving) {
                linearLayout.animate().translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
            }
            if (progressBar != null) {
                progressBar.setTranslationY(progressBar.getTranslationY() + y);
                if (!preventMoving) {
                    progressBar.animate().translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250);
                }
            }
        }
        lastH = getMeasuredHeight();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public float getVisibilityFactor() {
        return visibilityFactor;
    }

    private void setVisibility(boolean visibility, boolean animated, boolean force) {
        if (visibilityValue == visibility && !force) {
            return;
        }

        visibilityValue = visibility;
        setEnabled(visibility);

        if (visibilityAnimator != null) {
            visibilityAnimator.cancel();
            visibilityAnimator = null;
        }

        if (!animated) {
            visibilityFactor = visibility ? 1 : 0;
            onVisibilityChange(visibilityFactor);
            return;
        }

        visibilityAnimator = ValueAnimator.ofFloat(visibilityFactor, visibility ? 1 : 0);
        visibilityAnimator.setDuration(480L);
        visibilityAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        visibilityAnimator.addUpdateListener(a -> {
            visibilityFactor = (float) a.getAnimatedValue();
            onVisibilityChange(visibilityFactor);
        });
        visibilityAnimator.start();
    }

    protected void onVisibilityChange(float factor) {
        invalidate();
    }
}

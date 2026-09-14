/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/BottomSheetTabs.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BottomSheetTabs.java
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
package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.HashSet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.ActionBar.BrowserSheet;
import org.telegram.ui.bots.WebViewRequestProps;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class BottomSheetTabs extends FrameLayout {
    public static String urlWithoutFragment(String url) {
        if (url == null) {
            return null;
        }
        int index = url.indexOf('#');
        return index >= 0 ? url.substring(0, index) : url;
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public boolean drawTabs = true;
    public boolean doNotDismiss = false;

    private final ActionBarLayout actionBarLayout;
    private final ArrayList<WebTabData> tabs = new ArrayList<>();
    private final ArrayList<TabDrawable> tabDrawables = new ArrayList<>();
    private final RectF rect = new RectF();
    private boolean closeRippleHit;
    private boolean hit;

    private int backgroundColor;
    private int tabColor;
    private boolean tabIsDark;

    private ValueAnimator bottomTabsAnimator;
    public float bottomTabsProgress;
    public int bottomTabsHeight;
    private static final CubicBezierInterpolator KEYBOARD_INTERPOLATOR = new CubicBezierInterpolator(
            0.19919472913616398,
            0.010644531250000006,
            0.27920937042459737,
            0.91025390625
    );

    private final HashSet<Runnable> invalidateListeners = new HashSet<>();
    private final HashSet<Runnable> relayoutListeners = new HashSet<>();

    public BottomSheetTabs(Context context, ActionBarLayout actionBarLayout) {
        super(context);
        this.actionBarLayout = actionBarLayout;

        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setNavigationBarColor(Theme.getColor(Theme.key_iv_background), false);
        updateMultipleTitle();
        updateVisibility(false);
    }

    public void openTab(WebTabData tab) {
        if (tab == null) {
            return;
        }
        BaseFragment fragment = actionBarLayout.getLastFragment();
        if (fragment == null) {
            return;
        }
        if (tab.browserSheet != null) {
            BrowserSheet sheet = tab.browserSheet;
            fragment.addSheet(sheet);
            sheet.reset();
            sheet.attachInternal(fragment);
            sheet.animateOpen(true, true, null);
            removeTab(tab, false);
            return;
        }
        if (tab.articleViewer instanceof ArticleViewer) {
            ArticleViewer articleViewer = (ArticleViewer) tab.articleViewer;
            if (articleViewer.sheet == null) {
                return;
            }
            BottomSheetTabDialog.checkSheet(articleViewer.sheet);
            fragment.addSheet(articleViewer.sheet);
            articleViewer.sheet.reset();
            articleViewer.setParentActivity(fragment.getParentActivity(), fragment);
            articleViewer.sheet.attachInternal(fragment);
            articleViewer.sheet.animateOpen(true, true, null);
            removeTab(tab, false);
        }
    }

    public WebTabData tryReopenTab(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        for (int i = 0; i < tabs.size(); ++i) {
            WebTabData tab = tabs.get(i);
            if (!(tab.articleViewer instanceof ArticleViewer)) {
                continue;
            }
            ArticleViewer articleViewer = (ArticleViewer) tab.articleViewer;
            if (articleViewer.pagesStack.isEmpty()) {
                continue;
            }
            Object lastPage = articleViewer.pagesStack.get(articleViewer.pagesStack.size() - 1);
            if (!(lastPage instanceof ArticleViewer.CachedWeb)) {
                continue;
            }
            ArticleViewer.CachedWeb web = (ArticleViewer.CachedWeb) lastPage;
            org.telegram.ui.web.BotWebViewContainer.MyWebView webView = web.webView;
            if (webView == null && articleViewer.pages != null && articleViewer.pages[0] != null) {
                webView = articleViewer.pages[0].getWebView();
            }
            if (webView == null) {
                continue;
            }
            String currentUrl = webView.canGoBack() ? webView.getUrl() : webView.getOpenURL();
            if (TextUtils.equals(urlWithoutFragment(currentUrl), urlWithoutFragment(url))) {
                openTab(tab);
                return tab;
            }
        }
        return null;
    }

    public boolean isExpanded() {
        return !tabs.isEmpty();
    }

    public ArrayList<WebTabData> getTabs() {
        return tabs;
    }

    public ArrayList<TabDrawable> getTabDrawables() {
        return tabDrawables;
    }

    public int getExpandedHeight() {
        final int count = tabs.size();
        if (count == 0) {
            return 0;
        } else if (count == 1) {
            return AndroidUtilities.dp(60);
        } else {
            return AndroidUtilities.dp(68);
        }
    }

    public TabDrawable findTabDrawable(WebTabData tab) {
        for (int i = 0; i < tabDrawables.size(); ++i) {
            if (tabDrawables.get(i).tab == tab) {
                return tabDrawables.get(i);
            }
        }
        return null;
    }

    public TabDrawable pushTab(WebTabData tab) {
        TabDrawable tabDrawable = new TabDrawable(this, tab);
        tabDrawable.setPositionImmediate(-1);
        tabDrawable.setAlphaImmediate(0f);
        tabDrawables.add(tabDrawable);

        tabs.add(0, tab);
        updateTabPositions();
        updateMultipleTitle();
        updateVisibility(true);

        invalidate();
        return tabDrawable;
    }

    public boolean removeTab(WebTabData tab, boolean destroy) {
        tabs.remove(tab);
        if (destroy && tab != null) {
            tab.destroy();
        }
        updateTabPositions();
        updateMultipleTitle();
        postDelayed(() -> {
            for (int i = 0; i < tabDrawables.size(); ++i) {
                if (tabDrawables.get(i).tab == tab) {
                    tabDrawables.remove(i);
                    i--;
                }
            }
            invalidate();
        }, 320);
        updateVisibility(true);
        invalidate();
        return tabs.isEmpty();
    }

    public boolean removeAll() {
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).destroy();
        }
        tabs.clear();
        for (int i = 0; i < tabDrawables.size(); i++) {
            TabDrawable drawable = tabDrawables.get(i);
            drawable.index = -1;
        }
        postDelayed(() -> {
            tabDrawables.clear();
            invalidate();
        }, 320);
        updateMultipleTitle();
        updateVisibility(true);
        invalidate();
        return true;
    }

    private void updateTabPositions() {
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);
            final int index = tabs.indexOf(drawable.tab);
            drawable.index = index;
            if (index >= 0) {
                drawable.position = index;
            }
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || true;
    }

    private static TextPaint textPaint;

    private static TextPaint getTextPaint() {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(AndroidUtilities.dp(17));
        }
        return textPaint;
    }

    private void updateMultipleTitle() {
        CharSequence title = null;
        for (int i = 0; i < tabDrawables.size(); ++i) {
            TabDrawable drawable = tabDrawables.get(i);

            if (tabs.size() > 1 && drawable.position == 0) {
                title = tabs.size() - 1 + " more tabs - " + drawable.tab.getTitle();
                drawable.setOverrideTitle(title);
            } else {
                title = drawable.tab.getTitle();
                drawable.setOverrideTitle(null);
            }
        }

        if (tabs.isEmpty()) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setContentDescription("");
        } else {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(title == null ? "" : title);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return touchEvent(event.getAction(), event.getX(), event.getY()) || super.onTouchEvent(event);
    }

    public boolean touchEvent(int action, float x, float y) {
        BottomSheetTabsOverlay overlay = actionBarLayout.getBottomSheetTabsOverlay();
        if (overlay != null && overlay.isAnimatingSheet()) {
            clearTouchState();
            return bottomTabsProgress > 0;
        }
        if (drawTabs && bottomTabsProgress > 0) {
            WebTabData lastTab = tabs.isEmpty() ? null : tabs.get(0);
            TabDrawable drawable = findTabDrawable(lastTab);

            if (drawable != null) {
                getTabBounds(rect, drawable.getPosition());
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    closeRippleHit = drawable.closeRipple.getBounds().contains((int) (x - rect.left), (int) (y - rect.centerY()));
                    hit = !closeRippleHit && rect.contains(x, y);
                    drawable.closeRipple.setState(closeRippleHit ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[]{});
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (hit && action == MotionEvent.ACTION_UP) {
                        click();
                    } else if (closeRippleHit && action == MotionEvent.ACTION_UP) {
                        removeTab(lastTab, true);
                    }
                    closeRippleHit = false;
                    hit = false;
                    drawable.closeRipple.setState(new int[]{});
                }
                for (int i = 0; i < tabDrawables.size(); ++i) {
                    if (tabDrawables.get(i) != drawable) {
                        tabDrawables.get(i).closeRipple.setState(new int[]{});
                    }
                }
            } else {
                hit = false;
                closeRippleHit = false;
            }
        } else {
            hit = false;
            closeRippleHit = false;
        }
        return hit || closeRippleHit;
    }

    private void clearTouchState() {
        closeRippleHit = false;
        hit = false;
        for (int i = 0; i < tabDrawables.size(); ++i) {
            tabDrawables.get(i).closeRipple.setState(new int[]{});
        }
    }

    public void click() {
        final int count = tabs.size();
        if (count == 0) {
            return;
        }
        BottomSheetTabsOverlay overlay = actionBarLayout.getBottomSheetTabsOverlay();
        if (overlay != null) {
            overlay.stopAnimations();
        }
        if (count > 1 && overlay != null) {
            overlay.openTabsView();
            return;
        }
        openTab(tabs.get(tabs.size() - 1));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (bottomTabsProgress <= 0) {
            return;
        }

        backgroundPaint.setColor(backgroundColor);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        super.dispatchDraw(canvas);

        if (drawTabs) {
            for (int i = 0; i < tabDrawables.size(); ++i) {
                final TabDrawable drawable = tabDrawables.get(i);
                float position = drawable.getPosition();
                float alpha = drawable.getAlpha();

                if (alpha <= 0) {
                    continue;
                }
                if (position > 1.99f) {
                    continue;
                }

                getTabBounds(rect, position);
                drawable.setExpandProgress(0f);
                drawable.setBackgroundColor(tabColor, tabIsDark);
                drawable.draw(canvas, rect, AndroidUtilities.dp(10), alpha, 1f);
            }
        }
    }

    public void setupTab(TabDrawable drawable) {
        drawable.setExpandProgress(0f);
        drawable.setBackgroundColor(tabColor, tabIsDark);
    }

    public void getTabBounds(RectF rect, float position) {
        rect.set(
                AndroidUtilities.dp(4),
                getHeight() - AndroidUtilities.dp(4) - AndroidUtilities.dp(50),
                getWidth() - AndroidUtilities.dp(4),
                getHeight() - AndroidUtilities.dp(4)
        );
        rect.offset(0, position * -AndroidUtilities.dp(8));
        final float scale = AndroidUtilities.lerp(1f, .95f, Math.abs(position));
        final float cx = rect.centerX();
        final float cy = rect.centerY();
        final float w = rect.width();
        final float h = rect.height();
        rect.left = cx - w / 2f * scale;
        rect.right = cx + w / 2f * scale;
        rect.top = cy - h / 2f * scale;
        rect.bottom = cy + h / 2f * scale;
    }

    public void updateVisibility(boolean animated) {
        if (bottomTabsHeight == getExpandedHeight()) {
            return;
        }
        if (bottomTabsAnimator != null) {
            ValueAnimator previous = bottomTabsAnimator;
            bottomTabsAnimator = null;
            previous.cancel();
        }
        bottomTabsHeight = getExpandedHeight();
        for (Runnable listener : relayoutListeners) {
            listener.run();
        }
        if (animated) {
            bottomTabsAnimator = ValueAnimator.ofFloat(bottomTabsProgress, bottomTabsHeight);
            bottomTabsAnimator.addUpdateListener(animation -> {
                bottomTabsProgress = (float) animation.getAnimatedValue();
                for (Runnable listener : invalidateListeners) {
                    listener.run();
                }
                invalidate();
            });
            bottomTabsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (bottomTabsAnimator == animation) {
                        bottomTabsProgress = bottomTabsHeight;
                        for (Runnable listener : invalidateListeners) {
                            listener.run();
                        }
                    }
                }
            });
            bottomTabsAnimator.setDuration(250);
            bottomTabsAnimator.setInterpolator(KEYBOARD_INTERPOLATOR);
            bottomTabsAnimator.start();
        } else {
            bottomTabsProgress = bottomTabsHeight;
            invalidate();
        }
        ViewParent parent = getParent();
        if (parent instanceof View) {
            ViewCompat.requestApplyInsets((View) parent);
        }
    }

    public static class ClipTools {
        private final BottomSheetTabs tabs;
        private final RectF clipRect = new RectF();
        private final float[] clipRadius = new float[8];
        private final Path clipPath = new Path();
        private final Paint clipShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ClipTools(BottomSheetTabs tabs) {
            this.tabs = tabs;
        }

        public void clip(Canvas canvas, boolean withShadow, boolean isKeyboardVisible, int width, int height, float visible) {
            final int bottomSheetHeight = (int) ((isKeyboardVisible ? 0 : tabs.getHeight(true)) * visible);
            final int bottomRadius = Math.min(1, bottomSheetHeight / AndroidUtilities.dp(60)) * AndroidUtilities.dp(10);
            if (bottomSheetHeight <= 0) {
                return;
            }

            clipRadius[0] = clipRadius[1] = clipRadius[2] = clipRadius[3] = 0;
            clipRadius[4] = clipRadius[5] = clipRadius[6] = clipRadius[7] = bottomRadius;

            clipPath.rewind();
            clipRect.set(0, 0, width, tabs.getY() + tabs.getHeight() - bottomSheetHeight);
            clipPath.addRoundRect(clipRect, clipRadius, Path.Direction.CW);

            clipShadowPaint.setAlpha(0);
            if (withShadow) {
                clipShadowPaint.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x10000000);
                canvas.drawPath(clipPath, clipShadowPaint);
            }
            canvas.clipPath(clipPath);
        }
    }

    public int getHeight(boolean animated) {
        if (animated) {
            return (int) bottomTabsProgress;
        } else {
            return bottomTabsHeight;
        }
    }

    /**
     * True while tabs height is mid-animation (or snapped height differs from progress).
     * Used by Chat/TopicDetail to suppress AdjustPan height animations that would race
     * the BottomSheetTabs margin snap + progress ease and cause a post-settle shake.
     */
    public boolean isAnimatingHeight() {
        return bottomTabsAnimator != null || Math.round(bottomTabsProgress) != bottomTabsHeight;
    }

    public void setNavigationBarColor(int color) {
        setNavigationBarColor(color, true);
    }

    public void setNavigationBarColor(int color, boolean animated) {
        backgroundColor = color;
        final boolean isDark = computePerceivedBrightness(color) < .721f;
        tabColor = blendOver(color, multAlpha(0xFFFFFFFF, isDark ? .08f : .75f));
        tabIsDark = computePerceivedBrightness(tabColor) < .721f;
        invalidate();
    }

    public void listen(Runnable invalidate, Runnable relayout) {
        invalidateListeners.add(invalidate);
        relayoutListeners.add(relayout);
    }

    public void stopListening(Runnable invalidate, Runnable relayout) {
        invalidateListeners.remove(invalidate);
        relayoutListeners.remove(relayout);
    }

    public boolean tryRemoveTabWith(Object owner) {
        for (int i = 0; i < tabs.size(); i++) {
            WebTabData tab = tabs.get(i);
            if (tab != null && tab.articleViewer == owner) {
                removeTab(tab, true);
                return true;
            }
        }
        return false;
    }

    public static class WebTabData {
        public BrowserSheet browserSheet;
        public WebViewRequestProps props;
        public Object articleViewer;
        public String title;
        public String startUrl;
        public String currentUrl;
        public String lastUrl;
        public org.telegram.ui.web.BotWebViewContainer.MyWebView webView;
        public Object proxy;
        public Bitmap favicon;
        public Bitmap previewBitmap;
        public View view2;
        public float articleProgress;
        public int viewWidth;
        public int viewHeight;
        public int viewScroll;
        public int actionBarColor;
        public int backgroundColor;
        public boolean themeIsDark;
        public boolean overrideActionBarColor;

        public void destroy() {
            try {
                if (webView != null) {
                    webView.destroy();
                    webView = null;
                }
                if (browserSheet != null) {
                    browserSheet.destroyFromTabs();
                    browserSheet = null;
                }
                if (articleViewer instanceof ArticleViewer) {
                    ((ArticleViewer) articleViewer).destroy();
                }
                articleViewer = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public String getTitle() {
            if (TextUtils.isEmpty(title)) {
                return TextUtils.isEmpty(currentUrl) ? "Web" : currentUrl;
            }
            return title;
        }
    }

    public static class TabDrawable {

        public final WebTabData tab;
        public final View parentView;
        private int position;
        public int index;
        public final AnimatedFloat animatedPosition;
        public final AnimatedFloat animatedAlpha;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint faviconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public final Drawable closeRipple = Theme.createSelectorDrawable(0x30ffffff, Theme.RIPPLE_MASK_CIRCLE_20DP);

        private int backgroundColor;
        private int tabColor;
        private boolean backgroundIsDark;
        private boolean tabIsDark;
        private float progress;

        private Bitmap favicon;
        private CharSequence title;
        private CharSequence overrideTitle;
        private float expandProgress;

        private final float[] radii = new float[8];
        private final Path rectPath = new Path();
        private final Path closePath = new Path();
        private final Path expandPath = new Path();

        public TabDrawable(View view, WebTabData tab) {
            parentView = view;
            this.tab = tab;
            closeRipple.setCallback(view);

            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStrokeCap(Paint.Cap.ROUND);

            animatedPosition = new AnimatedFloat(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            animatedAlpha = new AnimatedFloat(view, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

            this.favicon = tab.favicon;
            this.title = tab.getTitle();
            this.tabColor = tab.actionBarColor;
            this.tabIsDark = computePerceivedBrightness(tabColor) < .721f;
            this.progress = tab.articleProgress;

            closePath.rewind();
            closePath.moveTo(0, 0);
            closePath.lineTo(AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            closePath.moveTo(AndroidUtilities.dp(12), 0);
            closePath.lineTo(0, AndroidUtilities.dp(12));

            expandPath.rewind();
            expandPath.moveTo(0, AndroidUtilities.dp(6.33f) / 2f);
            expandPath.lineTo(AndroidUtilities.dp(12.66f) / 2f, -AndroidUtilities.dp(6.33f) / 2f);
            expandPath.lineTo(AndroidUtilities.dp(12.66f), AndroidUtilities.dp(6.33f) / 2f);
        }

        public void setOverrideTitle(CharSequence title) {
            overrideTitle = title;
        }

        public void setPositionImmediate(float value) {
            position = (int) value;
            animatedPosition.force(value);
        }

        public void setAlphaImmediate(float value) {
            animatedAlpha.force(value);
        }

        public float getPosition() {
            return index < 0 ? position : animatedPosition.set(position);
        }

        public float getAlpha() {
            final float position = getPosition();
            float positionAlpha;
            if (position < 0) {
                positionAlpha = 1f + position;
            } else if (position >= 0 && position < 1) {
                positionAlpha = AndroidUtilities.lerp(1f, .87f, position);
            } else {
                positionAlpha = .87f * (1f - Math.min(1, position - 1));
            }
            return positionAlpha * animatedAlpha.set(index >= 0);
        }

        public void setBackgroundColor(int color, boolean isDark) {
            backgroundColor = color;
            backgroundIsDark = isDark;
        }

        public void setExpandProgress(float expandProgress) {
            this.expandProgress = expandProgress;
        }

        public void draw(Canvas canvas, RectF bounds, float radius, float alpha, float contentAlpha) {
            final int backgroundColor = ColorUtils.blendARGB(this.backgroundColor, this.tabColor, expandProgress);
            backgroundPaint.setColor(backgroundColor);
            backgroundPaint.setAlpha((int) (0xFF * alpha));
            backgroundPaint.setShadowLayer(AndroidUtilities.dpf2(2.33f), 0, AndroidUtilities.dp(1), multAlpha(0x10000000, alpha));

            radii[0] = radii[1] = radii[2] = radii[3] = radius;
            radii[4] = radii[5] = radii[6] = radii[7] = AndroidUtilities.lerp(radius, 0, expandProgress);
            rectPath.rewind();
            rectPath.addRoundRect(bounds, radii, Path.Direction.CW);
            canvas.drawPath(rectPath, backgroundPaint);

            if (progress > 0 && expandProgress > 0 && alpha > 0) {
                canvas.save();
                canvas.clipPath(rectPath);
                progressPaint.setColor(multAlpha(computePerceivedBrightness(backgroundColor) > .721f ? Color.BLACK : Color.WHITE, .07f * alpha));
                canvas.drawRect(bounds.left, bounds.top, bounds.left + bounds.width() * progress, bounds.bottom, progressPaint);
                canvas.restore();
            }

            final float isDark = AndroidUtilities.lerp(backgroundIsDark ? 1f : 0f, tabIsDark ? 1f : 0f, expandProgress);
            final int iconColor = ColorUtils.blendARGB(0xFF000000, 0xFFFFFFFF, isDark);

            iconPaint.setColor(iconColor);
            iconPaint.setStrokeWidth(AndroidUtilities.dp(2));

            canvas.save();
            canvas.translate(bounds.left, bounds.centerY());
            closeRipple.setBounds(
                    -AndroidUtilities.dp(25),
                    -AndroidUtilities.dp(25),
                    AndroidUtilities.dp(25),
                    AndroidUtilities.dp(25)
            );
            closeRipple.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.translate(bounds.left + AndroidUtilities.dp(18), bounds.centerY() - AndroidUtilities.dp(6));
            iconPaint.setAlpha((int) (0xFF * alpha * contentAlpha));
            canvas.drawPath(closePath, iconPaint);
            canvas.restore();

            canvas.save();
            canvas.translate(bounds.right - AndroidUtilities.dp(30.66f), bounds.centerY());
            iconPaint.setAlpha((int) (0xFF * alpha * contentAlpha * (1f - expandProgress)));
            canvas.drawPath(expandPath, iconPaint);
            canvas.restore();

            int leftPadding = 0;
            if (favicon != null) {
                final int size = AndroidUtilities.dp(24);
                canvas.save();
                RectF faviconRect = new RectF(
                        bounds.left + AndroidUtilities.dp(56),
                        bounds.centerY() - size / 2f,
                        bounds.left + AndroidUtilities.dp(56) + size,
                        bounds.centerY() + size / 2f
                );
                faviconPaint.setAlpha((int) (0xFF * alpha * contentAlpha));
                canvas.drawBitmap(favicon, null, faviconRect, faviconPaint);
                canvas.restore();
                leftPadding = size + AndroidUtilities.dp(4);
            }

            TextPaint paint = getTextPaint();
            paint.setColor(iconColor);
            paint.setAlpha((int) (0xFF * alpha * contentAlpha));
            CharSequence drawnTitle = overrideTitle == null ? title : overrideTitle;
            CharSequence ellipsized = TextUtils.ellipsize(
                    drawnTitle,
                    paint,
                    Math.max(0, bounds.width() - AndroidUtilities.dp(100) - leftPadding),
                    TextUtils.TruncateAt.END
            );
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            float baseline = bounds.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(
                    ellipsized,
                    0,
                    ellipsized.length(),
                    bounds.left + AndroidUtilities.dp(60) + leftPadding,
                    baseline,
                    paint
            );
        }
    }

    private static float computePerceivedBrightness(int color) {
        return (Color.red(color) * 0.2126f + Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f) / 255f;
    }

    private static int blendOver(int background, int foreground) {
        float foregroundAlpha = Color.alpha(foreground) / 255f;
        float backgroundAlpha = Color.alpha(background) / 255f;
        float alpha = foregroundAlpha + backgroundAlpha * (1 - foregroundAlpha);
        if (alpha == 0f) {
            return 0;
        }
        return Color.argb(
                (int) (alpha * 255),
                (int) ((Color.red(foreground) * foregroundAlpha + Color.red(background) * backgroundAlpha * (1 - foregroundAlpha)) / alpha),
                (int) ((Color.green(foreground) * foregroundAlpha + Color.green(background) * backgroundAlpha * (1 - foregroundAlpha)) / alpha),
                (int) ((Color.blue(foreground) * foregroundAlpha + Color.blue(background) * backgroundAlpha * (1 - foregroundAlpha)) / alpha)
        );
    }

    private static int multAlpha(int color, float multiply) {
        if (multiply == 1f) {
            return color;
        }
        return ColorUtils.setAlphaComponent(color, Math.max(0, Math.min(0xFF, (int) (Color.alpha(color) * multiply))));
    }
}

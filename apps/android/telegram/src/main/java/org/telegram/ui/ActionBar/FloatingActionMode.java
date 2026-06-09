/*
 * This file is part of NativeDo.
 *
 * Derived in part from Android/Telegram FloatingActionMode, adapted as a compact
 * bridge from ActionMode.Callback2 to NativeDo's FloatingToolbar.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Rect;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.PopupMenu;
import android.widget.PopupWindow;

import org.telegram.messenger.AndroidUtilities;

public final class FloatingActionMode extends ActionMode {
    private static final int MAX_HIDE_DURATION = 3000;
    private static final int MOVING_HIDE_DELAY = 50;

    private final Context context;
    private final ActionMode.Callback2 callback;
    private final View originatingView;
    private final Menu menu;
    private final Rect contentRect = new Rect();
    private final Rect previousContentRect = new Rect();
    private final Rect viewRectOnScreen = new Rect();
    private final Rect screenRect = new Rect();
    private FloatingToolbar floatingToolbar;
    private FloatingToolbarVisibilityHelper visibilityHelper;
    private boolean finished;
    private final Runnable showRunnable = this::showToolbar;
    private final Runnable movingOffRunnable = () -> {
        if (visibilityHelper != null) {
            visibilityHelper.setMoving(false);
            visibilityHelper.updateToolbarVisibility();
        }
    };

    public FloatingActionMode(Context context, ActionMode.Callback2 callback, View originatingView, FloatingToolbar floatingToolbar) {
        this.context = context;
        this.callback = callback;
        this.originatingView = originatingView;
        PopupMenu popupMenu = new PopupMenu(context, null);
        this.menu = popupMenu.getMenu();
        popupMenu.setOnMenuItemClickListener(item -> this.callback.onActionItemClicked(this, item));
        setType(ActionMode.TYPE_FLOATING);
        setFloatingToolbar(floatingToolbar);
    }

    private void setFloatingToolbar(FloatingToolbar toolbar) {
        floatingToolbar = toolbar.setMenu(menu).setOnMenuItemClickListener(item -> callback.onActionItemClicked(this, item));
        visibilityHelper = new FloatingToolbarVisibilityHelper(floatingToolbar);
        visibilityHelper.activate();
    }

    @Override
    public void setTitle(CharSequence title) {
    }

    @Override
    public void setTitle(int resId) {
    }

    @Override
    public void setSubtitle(CharSequence subtitle) {
    }

    @Override
    public void setSubtitle(int resId) {
    }

    @Override
    public void setCustomView(View view) {
    }

    @Override
    public void invalidate() {
        if (finished) {
            return;
        }
        callback.onPrepareActionMode(this, menu);
        floatingToolbar.setMenu(menu);
        invalidateContentRect();
    }

    @Override
    public void invalidateContentRect() {
        if (finished) {
            return;
        }
        callback.onGetContentRect(this, originatingView, contentRect);
        if (contentRect.left == 0 && contentRect.right == 0) {
            contentRect.left = 1;
            contentRect.right = 1;
        }
        repositionToolbar();
    }

    public void updateViewLocationInWindow() {
        invalidateContentRect();
    }

    private void repositionToolbar() {
        if (isContentRectWithinBounds()) {
            visibilityHelper.setOutOfBounds(false);
            if (!contentRect.equals(previousContentRect)) {
                originatingView.removeCallbacks(movingOffRunnable);
                visibilityHelper.setMoving(true);
                originatingView.postDelayed(movingOffRunnable, MOVING_HIDE_DELAY);
                floatingToolbar.setContentRect(contentRect).updateLayout();
            }
        } else {
            visibilityHelper.setOutOfBounds(true);
        }
        visibilityHelper.updateToolbarVisibility();
        previousContentRect.set(contentRect);
    }

    private boolean isContentRectWithinBounds() {
        originatingView.getGlobalVisibleRect(viewRectOnScreen);
        if (viewRectOnScreen.isEmpty()) {
            return false;
        }
        screenRect.set(0, 0, AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        return intersectsClosed(contentRect, screenRect) && intersectsClosed(contentRect, viewRectOnScreen);
    }

    private static boolean intersectsClosed(Rect a, Rect b) {
        return a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom;
    }

    @Override
    public void hide(long duration) {
        duration = duration == ActionMode.DEFAULT_HIDE_DURATION ? ViewConfiguration.getDefaultActionModeHideDuration() : duration;
        duration = Math.min(duration, MAX_HIDE_DURATION);
        originatingView.removeCallbacks(showRunnable);
        visibilityHelper.setHideRequested(true);
        visibilityHelper.updateToolbarVisibility();
        if (duration > 0) {
            originatingView.postDelayed(showRunnable, duration);
        } else {
            showToolbar();
        }
    }

    public void setOutsideTouchable(boolean outsideTouchable, PopupWindow.OnDismissListener onDismiss) {
        floatingToolbar.setOutsideTouchable(outsideTouchable, onDismiss);
    }

    @Override
    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        originatingView.removeCallbacks(showRunnable);
        originatingView.removeCallbacks(movingOffRunnable);
        floatingToolbar.dismiss();
        visibilityHelper.deactivate();
        callback.onDestroyActionMode(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (visibilityHelper != null) {
            visibilityHelper.setWindowFocused(hasWindowFocus);
            visibilityHelper.updateToolbarVisibility();
        }
    }

    @Override
    public Menu getMenu() {
        return menu;
    }

    @Override
    public CharSequence getTitle() {
        return null;
    }

    @Override
    public CharSequence getSubtitle() {
        return null;
    }

    @Override
    public View getCustomView() {
        return null;
    }

    @Override
    public MenuInflater getMenuInflater() {
        return new MenuInflater(context);
    }

    private void showToolbar() {
        if (!finished) {
            visibilityHelper.setHideRequested(false);
            visibilityHelper.updateToolbarVisibility();
        }
    }

    private static final class FloatingToolbarVisibilityHelper {
        private static final long MIN_SHOW_DURATION_FOR_MOVE_HIDE = 500;

        private final FloatingToolbar toolbar;
        private boolean hideRequested;
        private boolean moving;
        private boolean outOfBounds;
        private boolean windowFocused = true;
        private boolean active;
        private long lastShowTime;

        FloatingToolbarVisibilityHelper(FloatingToolbar toolbar) {
            this.toolbar = toolbar;
        }

        void activate() {
            hideRequested = false;
            moving = false;
            outOfBounds = false;
            windowFocused = true;
            active = true;
        }

        void deactivate() {
            active = false;
            toolbar.dismiss();
        }

        void setHideRequested(boolean hideRequested) {
            this.hideRequested = hideRequested;
        }

        void setMoving(boolean moving) {
            boolean showingLongEnough = System.currentTimeMillis() - lastShowTime > MIN_SHOW_DURATION_FOR_MOVE_HIDE;
            if (!moving || showingLongEnough) {
                this.moving = moving;
            }
        }

        void setOutOfBounds(boolean outOfBounds) {
            this.outOfBounds = outOfBounds;
        }

        void setWindowFocused(boolean windowFocused) {
            this.windowFocused = windowFocused;
        }

        void updateToolbarVisibility() {
            if (!active) {
                return;
            }
            if (hideRequested || moving || outOfBounds || !windowFocused) {
                toolbar.hide();
            } else {
                toolbar.show();
                lastShowTime = System.currentTimeMillis();
            }
        }
    }
}

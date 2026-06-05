/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class ActionBar extends FrameLayout {

    public static class ActionBarMenuOnItemClick {
        public void onItemClick(int id) {
        }
    }

    public final ImageView backButtonImageView;
    private final SimpleTextView titleTextView;
    public ActionBarMenu menu;
    private ActionBarMenu actionMode;
    private boolean actionModeVisible;
    public int itemsColor;
    public int itemsBackgroundColor;
    private int itemsActionModeColor;
    private int itemsActionModeBackgroundColor;
    private int actionModeColor;
    private int titleColor;
    private ActionBarMenuOnItemClick actionBarMenuOnItemClick;
    private boolean isSearchFieldVisible;
    private AnimatorSet searchVisibleAnimator;
    private boolean centerScale;
    private boolean occupyStatusBar = true;
    private boolean castShadows = SharedConfig.drawActionBarShadow;
    private boolean addToContainer = true;
    private RecyclerView adaptiveBackgroundListView;
    private final RecyclerView.OnScrollListener adaptiveBackgroundScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            updateAdaptiveBackground();
        }
    };

    public ActionBar(Context context) {
        super(context);
        setWillNotDraw(false);

        backButtonImageView = new ImageView(context);
        backButtonImageView.setScaleType(ImageView.ScaleType.CENTER);
        backButtonImageView.setContentDescription(context.getString(R.string.action_back));
        backButtonImageView.setVisibility(GONE);
        backButtonImageView.setOnClickListener(v -> {
            if (!actionModeVisible && isSearchFieldVisible) {
                closeSearchField();
                return;
            }
            if (actionBarMenuOnItemClick != null) {
                actionBarMenuOnItemClick.onItemClick(-1);
            }
        });
        addView(backButtonImageView, LayoutHelper.createFrame(54, 56, Gravity.START | Gravity.TOP));

        titleTextView = new SimpleTextView(context);
        titleTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        titleTextView.setTextSize(20);
        titleTextView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        applyTheme();
    }

    public void setTitle(CharSequence value) {
        titleTextView.setText(value);
    }

    public CharSequence getTitle() {
        return titleTextView.getText();
    }

    public void setBackButtonDrawable(Drawable drawable) {
        backButtonImageView.setImageDrawable(drawable);
        backButtonImageView.setVisibility(drawable == null ? GONE : VISIBLE);
        applyBackButtonColor(drawable);
    }

    public void setBackButtonImage(int resId) {
        setBackButtonDrawable(resId == 0 ? null : getContext().getResources().getDrawable(resId));
    }

    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) {
        actionBarMenuOnItemClick = listener;
    }

    public ActionBarMenuOnItemClick getActionBarMenuOnItemClick() {
        return actionBarMenuOnItemClick;
    }

    int getItemsColor(ActionBarMenu menu) {
        return menu == actionMode && itemsActionModeColor != 0 ? itemsActionModeColor : itemsColor;
    }

    int getItemsBackgroundColor(ActionBarMenu menu) {
        return menu == actionMode && itemsActionModeBackgroundColor != 0 ? itemsActionModeBackgroundColor : itemsBackgroundColor;
    }

    protected ImageView getBackButtonImageView() {
        return backButtonImageView;
    }

    public SimpleTextView getTitleTextView() {
        return titleTextView;
    }

    public void setTitleAnimated(CharSequence value, boolean crossfade, int duration, org.telegram.ui.Components.CubicBezierInterpolator interpolator) {
        setTitle(value);
    }

    public ActionBarMenu createMenu() {
        if (menu != null) {
            return menu;
        }
        menu = new ActionBarMenu(getContext(), this);
        addView(menu, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));
        return menu;
    }

    public void onSearchFieldVisibilityChanged(boolean visible) {
        isSearchFieldVisible = visible;
        if (searchVisibleAnimator != null) {
            searchVisibleAnimator.cancel();
        }
        searchVisibleAnimator = new AnimatorSet();
        ArrayList<View> viewsToHide = new ArrayList<>();
        viewsToHide.add(titleTextView);

        for (int i = 0; i < viewsToHide.size(); i++) {
            View view = viewsToHide.get(i);
            if (!visible) {
                view.setVisibility(VISIBLE);
                view.setAlpha(0f);
                view.setScaleX(0.95f);
                view.setScaleY(0.95f);
            }
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, visible ? 0f : 1f));
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_Y, visible ? 0.95f : 1f));
            searchVisibleAnimator.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, visible ? 0.95f : 1f));
        }
        requestLayout();
        centerScale = true;
        searchVisibleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (int i = 0; i < viewsToHide.size(); i++) {
                    View view = viewsToHide.get(i);
                    if (visible) {
                        view.setVisibility(GONE);
                        view.setAlpha(0f);
                    } else {
                        view.setAlpha(1f);
                    }
                }
            }
        });
        searchVisibleAnimator.setDuration(150).start();
    }

    public void closeSearchField() {
        closeSearchField(true);
    }

    public void closeSearchField(boolean closeKeyboard) {
        if (!isSearchFieldVisible || menu == null) {
            return;
        }
        menu.closeSearchField(closeKeyboard);
    }

    public void applyTheme() {
        itemsColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
        itemsBackgroundColor = Theme.getColor(Theme.key_actionBarDefaultSelector);
        if (itemsActionModeColor == 0) {
            itemsActionModeColor = itemsColor;
        }
        if (itemsActionModeBackgroundColor == 0) {
            itemsActionModeBackgroundColor = itemsBackgroundColor;
        }
        titleColor = Theme.getColor(Theme.key_actionBarDefaultTitle);
        titleTextView.setTextColor(titleColor);
        backButtonImageView.setBackground(Theme.createSelectorDrawable(itemsBackgroundColor));
        applyBackButtonColor(backButtonImageView.getDrawable());
        if (menu != null) {
            menu.updateItemsBackgroundColor();
            menu.updateItemsColor();
            menu.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), true);
            menu.setPopupItemsSelectorColor(Theme.getColor(Theme.key_listSelector));
            menu.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        }
        updateAdaptiveBackground();
    }

    public void setItemsColor(int color, boolean isActionMode) {
        if (isActionMode) {
            itemsActionModeColor = color;
            if (actionMode != null) {
                actionMode.updateItemsColor();
            }
            invalidate();
            return;
        }
        itemsColor = color;
        applyBackButtonColor(backButtonImageView.getDrawable());
        if (menu != null) {
            menu.updateItemsColor();
        }
        invalidate();
    }

    public void setItemsBackgroundColor(int color, boolean isActionMode) {
        if (isActionMode) {
            itemsActionModeBackgroundColor = color;
            if (actionMode != null) {
                actionMode.updateItemsBackgroundColor();
            }
            invalidate();
            return;
        }
        itemsBackgroundColor = color;
        backButtonImageView.setBackground(Theme.createSelectorDrawable(itemsBackgroundColor));
        if (menu != null) {
            menu.updateItemsBackgroundColor();
        }
        invalidate();
    }

    public void setActionModeColor(int color) {
        actionModeColor = color;
        if (actionMode != null) {
            actionMode.setBackgroundColor(color);
        }
    }

    public ActionBarMenu createActionMode() {
        if (actionMode != null) {
            return actionMode;
        }
        actionMode = new ActionBarMenu(getContext(), this);
        actionMode.setBackgroundColor(actionModeColor != 0 ? actionModeColor : Theme.getColor(Theme.key_actionBarDefault));
        actionMode.setVisibility(INVISIBLE);
        addView(actionMode, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT));
        return actionMode;
    }

    public boolean isActionModeShowed() {
        return actionModeVisible;
    }

    public void showActionMode() {
        if (actionModeVisible) {
            return;
        }
        actionModeVisible = true;
        if (actionMode != null) {
            actionMode.bringToFront();
            backButtonImageView.bringToFront();
            backButtonImageView.setBackground(Theme.createSelectorDrawable(getItemsBackgroundColor(actionMode)));
            actionMode.animate().cancel();
            actionMode.setAlpha(0f);
            actionMode.setVisibility(VISIBLE);
            actionMode.animate().alpha(1f).setDuration(150).start();
            requestLayout();
        }
        titleTextView.setVisibility(INVISIBLE);
        if (menu != null) {
            menu.setVisibility(INVISIBLE);
        }
    }

    public void hideActionMode() {
        if (!actionModeVisible) {
            return;
        }
        actionModeVisible = false;
        titleTextView.setVisibility(VISIBLE);
        backButtonImageView.setBackground(Theme.createSelectorDrawable(itemsBackgroundColor));
        if (menu != null) {
            menu.setVisibility(VISIBLE);
        }
        if (actionMode != null) {
            actionMode.animate().cancel();
            actionMode.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (!actionModeVisible && actionMode != null) {
                    actionMode.setVisibility(INVISIBLE);
                    actionMode.setAlpha(1f);
                }
            }).start();
        }
    }

    public void setTitleColor(int color) {
        titleColor = color;
        titleTextView.setTextColor(color);
        invalidate();
    }

    public void setAdaptiveBackground(@Nullable RecyclerView listView) {
        if (adaptiveBackgroundListView == listView) {
            return;
        }
        if (adaptiveBackgroundListView != null) {
            adaptiveBackgroundListView.removeOnScrollListener(adaptiveBackgroundScrollListener);
        }
        adaptiveBackgroundListView = listView;
        if (adaptiveBackgroundListView != null) {
            adaptiveBackgroundListView.addOnScrollListener(adaptiveBackgroundScrollListener);
        }
        updateAdaptiveBackground();
    }

    private void updateAdaptiveBackground() {
        if (adaptiveBackgroundListView == null) {
            setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
            setCastShadows(SharedConfig.drawActionBarShadow);
            return;
        }
        int scrollOffset = adaptiveBackgroundListView.computeVerticalScrollOffset();
        float progress = Math.min(1f, scrollOffset / (float) AndroidUtilities.dp(24));
        setBackgroundColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_actionBarDefault), (int) (255 * progress)));
        setCastShadows(progress > 0.98f && SharedConfig.drawActionBarShadow);
    }

    public boolean shouldAddToContainer() {
        return addToContainer;
    }

    public void setAddToContainer(boolean value) {
        addToContainer = value;
    }

    public void setCastShadows(boolean value) {
        if (castShadows != value) {
            castShadows = value;
            invalidate();
        }
    }

    public void setOccupyStatusBar(boolean value) {
        if (occupyStatusBar != value) {
            occupyStatusBar = value;
            requestLayout();
        }
    }

    public boolean getOccupyStatusBar() {
        return occupyStatusBar;
    }

    private boolean clipContent;

    public void setClipContent(boolean value) {
        clipContent = value;
    }

    public static int getCurrentActionBarHeight() {
        if (AndroidUtilities.isTablet()) {
            return AndroidUtilities.dp(64);
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return AndroidUtilities.dp(48);
        } else {
            return AndroidUtilities.dp(56);
        }
    }

    public void setHeight(int height) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int topInset = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        int barHeight = getCurrentActionBarHeight();
        int totalHeight = topInset + barHeight;
        setMeasuredDimension(width, totalHeight);

        int menuWidth = 0;
        if (menu != null && menu.getVisibility() != GONE) {
            int menuWidthSpec;
            boolean searchFieldVisible = menu.searchFieldVisible();
            if (searchFieldVisible && !isSearchFieldVisible) {
                menu.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
                );
                int itemsWidth = menu.getItemsMeasuredWidth(true);
                menuWidthSpec = MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 74 : 66) + itemsWidth, MeasureSpec.EXACTLY);
                menu.translateXItems(-itemsWidth);
            } else if (isSearchFieldVisible) {
                menuWidthSpec = MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(AndroidUtilities.isTablet() ? 74 : 66), MeasureSpec.EXACTLY);
                menu.translateXItems(0);
            } else {
                menuWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
                menu.translateXItems(0);
            }
            menu.measure(menuWidthSpec, MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY));
            menuWidth = menu.getMeasuredWidth();
        }

        int buttonSize = AndroidUtilities.dp(54);
        backButtonImageView.measure(
            MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY)
        );
        int titleLeft = backButtonImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(72) : AndroidUtilities.dp(20);
        int availableTitleWidth = width - menuWidth - AndroidUtilities.dp(16) - titleLeft;
        titleTextView.measure(
            MeasureSpec.makeMeasureSpec(availableTitleWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24) + titleTextView.getPaddingTop() + titleTextView.getPaddingBottom(), MeasureSpec.AT_MOST)
        );
        if (centerScale) {
            CharSequence text = titleTextView.getText();
            titleTextView.setPivotX(text == null ? 0 : titleTextView.getTextPaint().measureText(text, 0, text.length()) / 2f);
            titleTextView.setPivotY(AndroidUtilities.dp(24) >> 1);
        } else {
            titleTextView.setPivotX(0);
            titleTextView.setPivotY(0);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == backButtonImageView || child == titleTextView || child == menu || child.getVisibility() == GONE) {
                continue;
            }
            measureChildWithMargins(
                child,
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                0,
                MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY),
                0
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int topInset = occupyStatusBar ? AndroidUtilities.statusBarHeight : 0;
        int barHeight = getCurrentActionBarHeight();
        int buttonSize = AndroidUtilities.dp(54);
        backButtonImageView.layout(0, topInset, buttonSize, topInset + barHeight);

        if (menu != null && menu.getVisibility() != GONE) {
            int menuLeft = menu.searchFieldVisible() ? AndroidUtilities.dp(AndroidUtilities.isTablet() ? 74 : 66) : getMeasuredWidth() - menu.getMeasuredWidth();
            menu.layout(menuLeft, topInset, getMeasuredWidth(), topInset + barHeight);
        }

        int titleLeft = backButtonImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(72) : AndroidUtilities.dp(20);
        int titleTop = (barHeight - titleTextView.getTextHeight()) / 2;
        titleTextView.layout(
            titleLeft,
            topInset + titleTop - titleTextView.getPaddingTop(),
            titleLeft + titleTextView.getMeasuredWidth(),
            topInset + titleTop + titleTextView.getTextHeight() - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom()
        );
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == backButtonImageView || child == titleTextView || child == menu || child.getVisibility() == GONE) {
                continue;
            }
            child.layout(0, topInset, getMeasuredWidth(), topInset + barHeight);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (castShadows) {
            canvas.save();
            canvas.clipRect(0, getHeight() - 1, getWidth(), getHeight());
            canvas.drawColor(Color.parseColor("#1F000000"));
            canvas.restore();
        }
    }

    private void applyBackButtonColor(Drawable drawable) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof BackDrawable) {
            ((BackDrawable) drawable).setColor(itemsColor);
            ((BackDrawable) drawable).setRotatedColor(itemsColor);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(itemsColor, PorterDuff.Mode.MULTIPLY));
        }
    }
}

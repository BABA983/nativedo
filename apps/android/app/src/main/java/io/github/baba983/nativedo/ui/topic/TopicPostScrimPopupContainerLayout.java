/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * NativeDo port derived from Telegram Android:
 * TMessagesProj/src/main/java/org/telegram/ui/Components/ChatScrimPopupContainerLayout.java
 */

package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class TopicPostScrimPopupContainerLayout extends LinearLayout {

    public interface ProgressListener {
        void onProgress(float progress);
    }

    public interface ReactionRail {
        void setTransitionProgressListener(ProgressListener listener);
        void startEnterAnimation(boolean animatePopup);
        int itemsCount();
    }

    private static final float CLIP_PROGRESS = 0.25f;
    private static final int BIG_BUBBLE_RADIUS_DP = 8;
    private static final int SMALL_BUBBLE_RADIUS_DP = 4;
    private static final int BIG_BUBBLE_OFFSET_DP = 36;
    private static final int BIG_BUBBLE_SHADOW_PAD_DP = 3;
    private static final int SMALL_BUBBLE_SHADOW_PAD_DP = -1;
    private static final int RAIL_TOP_PADDING_DP = 4;
    private static final int RAIL_BOTTOM_PADDING_DP = 22;
    private static final int SHADOW_PAD_DP = 7;

    private final Runnable closeMenu;
    private ReactionRailFrame reactionRailContainer;
    private ReactionRail reactionRail;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;
    private int reactionRailPreferredWidth;
    private int maxHeight;
    private float popupLayoutLeftOffset;
    private float progressToSwipeBack;
    private boolean reactionRailEnterVisible = true;

    public TopicPostScrimPopupContainerLayout(Context context, Runnable closeMenu) {
        super(context);
        this.closeMenu = closeMenu;
        setOrientation(LinearLayout.VERTICAL);
        setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusableInTouchMode(true);
    }

    public void addReactionRail(View railView, int widthDp) {
        if (!(railView instanceof ReactionRail)) {
            throw new IllegalArgumentException("reaction rail must implement ReactionRail");
        }
        ReactionRailFrame railContainer = new ReactionRailFrame(getContext());
        reactionRailPreferredWidth = AndroidUtilities.dp(widthDp);
        reactionRail = (ReactionRail) railView;
        reactionRail.setTransitionProgressListener(progress -> {
            setReactionsTransitionProgress(progress);
            railContainer.setTransitionProgress(progress);
        });
        railContainer.addView(railView, LayoutHelper.createFrame(widthDp, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        reactionRailContainer = railContainer;
        addView(
                railContainer,
                LayoutHelper.createLinear(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT,
                        0,
                        0,
                        0,
                        -4
                )
        );
    }

    public void addPopupMenu(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
        setPopupWindowLayout(popupLayout);
        boolean isReactionsAvailable = reactionRailContainer != null;
        if (isReactionsAvailable) {
            LinearLayout.LayoutParams railParams = (LinearLayout.LayoutParams) reactionRailContainer.getLayoutParams();
            railParams.bottomMargin = -AndroidUtilities.dp(20);
            reactionRailContainer.setLayoutParams(railParams);
        }
        addView(
                popupLayout,
                LayoutHelper.createLinearRelatively(
                        LayoutHelper.WRAP_CONTENT,
                        LayoutHelper.WRAP_CONTENT,
                        Gravity.LEFT,
                        isReactionsAvailable ? 16 : 0,
                        0,
                        isReactionsAvailable ? 36 : 0,
                        0
                )
        );
    }

    public void setPopupWindowLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout) {
        this.popupWindowLayout = popupWindowLayout;
        popupWindowLayout.setOnSizeChangedListener(this::requestLayout);
        if (popupWindowLayout.getSwipeBack() != null) {
            popupWindowLayout.getSwipeBack().addOnSwipeBackProgressListener((layout, toProgress, progress) -> {
                updateReactionRailSwipeBackProgress(toProgress, progress);
                progressToSwipeBack = progress;
                updatePopupTranslation();
            });
        }
    }

    public void setReactionsTransitionProgress(float progress) {
        if (popupWindowLayout != null) {
            popupWindowLayout.setReactionsTransitionProgress(progress);
        }
    }

    public void setPopupAlpha(float alpha) {
        if (popupWindowLayout != null) {
            popupWindowLayout.setAlpha(alpha);
        }
        if (reactionRailContainer != null) {
            reactionRailContainer.setAlpha(alpha);
        }
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = Math.max(0, maxHeight);
    }

    public void updateReactionRailWidth(int widthDp) {
        reactionRailPreferredWidth = AndroidUtilities.dp(widthDp);
        if (reactionRailContainer != null) {
            reactionRailContainer.setContentWidth(reactionRailPreferredWidth);
        }
        requestLayout();
    }

    private void updatePopupTranslation() {
        if (popupWindowLayout == null) {
            return;
        }
        float direction = LocaleController.isRTL ? -1f : 1f;
        popupWindowLayout.setTranslationX(direction * (1f - progressToSwipeBack) * popupLayoutLeftOffset);
    }

    private void updateReactionRailSwipeBackProgress(float toProgress, float progress) {
        if (reactionRail == null || reactionRailContainer == null) {
            return;
        }
        if (toProgress <= 0f && !reactionRailEnterVisible) {
            reactionRailContainer.setAlpha(1f);
            reactionRail.startEnterAnimation(false);
            reactionRailEnterVisible = true;
        } else if (toProgress >= 1f && reactionRailEnterVisible) {
            reactionRailContainer.setAlpha(1f - progress);
            if (progress >= 1f) {
                reactionRailEnterVisible = false;
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK &&
                event.getRepeatCount() == 0 &&
                event.getAction() == KeyEvent.ACTION_UP) {
            closeMenu.run();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = super.dispatchTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && !handled) {
            closeMenu.run();
        }
        return handled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int constrainedHeightSpec = maxHeight > 0
                ? MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                : heightMeasureSpec;
        ReactionRailFrame rail = reactionRailContainer;
        ActionBarPopupWindow.ActionBarPopupWindowLayout menu = popupWindowLayout;
        if (rail == null || menu == null) {
            super.onMeasure(widthMeasureSpec, constrainedHeightSpec);
            return;
        }

        rail.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
        if (reactionRailPreferredWidth > 0) {
            rail.setContentWidth(reactionRailPreferredWidth);
        }
        popupLayoutLeftOffset = 0f;
        updatePopupTranslation();
        super.onMeasure(widthMeasureSpec, constrainedHeightSpec);

        View menuContainer = null;
        if (menu.getSwipeBack() != null && menu.getSwipeBack().getChildCount() > 0) {
            menuContainer = menu.getSwipeBack().getChildAt(0);
        } else if (menu.getChildCount() > 0) {
            menuContainer = menu.getChildAt(0);
        }
        int menuContainerWidth = menuContainer != null && menuContainer.getMeasuredWidth() > 0
                ? menuContainer.getMeasuredWidth()
                : menu.getMeasuredWidth();
        int swipeBackWidth = menu.getSwipeBack() != null ? menu.getSwipeBack().getMeasuredWidth() : 0;
        int maxWidth = Math.max(rail.getMeasuredWidth(), Math.max(menu.getMeasuredWidth(), swipeBackWidth));
        int itemsCount = reactionRail != null ? reactionRail.itemsCount() : 0;
        int targetRailContentWidth = reactionRailContentWidthForMenu(
                reactionRailPreferredWidth,
                itemsCount,
                menuContainerWidth,
                maxWidth
        );
        if (targetRailContentWidth > 0 && targetRailContentWidth != reactionRailPreferredWidth) {
            rail.setContentWidth(targetRailContentWidth);
            super.onMeasure(widthMeasureSpec, constrainedHeightSpec);
        }
    }

    private int reactionRailContentWidthForMenu(int preferredWidth, int itemsCount, int menuContainerWidth, int maxWidth) {
        if (preferredWidth <= 0 || itemsCount <= 0 || menuContainerWidth <= 0 || maxWidth <= 0) {
            return preferredWidth;
        }
        int maxReactionsLayoutWidth = menuContainerWidth + AndroidUtilities.dp(16) + AndroidUtilities.dp(16) + AndroidUtilities.dp(36);
        if (maxReactionsLayoutWidth > maxWidth) {
            maxReactionsLayoutWidth = maxWidth;
        }
        if (preferredWidth <= maxReactionsLayoutWidth) {
            return preferredWidth;
        }
        int maxFullCount = Math.max(1, ((maxReactionsLayoutWidth - AndroidUtilities.dp(16)) / AndroidUtilities.dp(36)) + 1);
        int newWidth = maxFullCount * AndroidUtilities.dp(36) + AndroidUtilities.dp(8);
        if (newWidth > preferredWidth || maxFullCount == itemsCount) {
            newWidth = preferredWidth;
        }
        return Math.max(AndroidUtilities.dp(200), Math.min(preferredWidth, newWidth));
    }

    private static final class ReactionRailFrame extends FrameLayout {
        private final RectF backgroundRect = new RectF();
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable bubbleShadow;
        private float transitionProgress = 1f;

        ReactionRailFrame(Context context) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            bubbleShadow = context.getResources().getDrawable(org.telegram.messenger.R.drawable.reactions_bubble_shadow).mutate();
            applyThemeColors();
            setPadding(0, AndroidUtilities.dp(RAIL_TOP_PADDING_DP), 0, AndroidUtilities.dp(RAIL_BOTTOM_PADDING_DP));
        }

        private void applyThemeColors() {
            int backgroundColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
            backgroundPaint.setColor(backgroundColor);
            bubblePaint.setColor(backgroundColor);
            bubbleShadow.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));
        }

        void setTransitionProgress(float progress) {
            float nextProgress = Math.max(0f, progress);
            if (transitionProgress == nextProgress) {
                return;
            }
            transitionProgress = nextProgress;
            invalidate();
        }

        void setContentWidth(int widthPx) {
            View child = getChildCount() > 0 ? getChildAt(0) : null;
            if (child == null || child.getLayoutParams() == null || child.getLayoutParams().width == widthPx) {
                return;
            }
            child.getLayoutParams().width = widthPx;
            child.setLayoutParams(child.getLayoutParams());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawTransitionBackground(canvas);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            drawTransitionBubbles(canvas);
        }

        private void drawTransitionBackground(Canvas canvas) {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            // Re-sample on draw so an open rail survives day/night switches.
            applyThemeColors();
            float progress = Math.max(0f, transitionProgress);
            float pivotX = LocaleController.isRTL ? getWidth() * 0.125f : getWidth() * 0.875f;
            float clipGate = Math.max(CLIP_PROGRESS, Math.min(1f, progress));
            float rectLeft = getPaddingLeft();
            float rectTop = getPaddingTop();
            float rectRight = getWidth() - getPaddingRight();
            float rectBottom = getHeight() - getPaddingBottom();
            float rectWidth = Math.max(0f, rectRight - rectLeft);
            float left = LocaleController.isRTL ? rectLeft : clamp(rectLeft + rectWidth * (1f - clipGate), rectLeft, rectRight);
            float right = LocaleController.isRTL ? clamp(rectLeft + rectWidth * clipGate, rectLeft, rectRight) : rectRight;
            int restore = canvas.save();
            if (progress != 1f) {
                canvas.scale(progress, progress, pivotX, getHeight() / 2f);
            }
            int shadowPad = AndroidUtilities.dp(SHADOW_PAD_DP);
            bubbleShadow.setAlpha(255);
            bubbleShadow.setBounds((int) (left - shadowPad), (int) rectTop - shadowPad, (int) (right + shadowPad), (int) rectBottom + shadowPad);
            bubbleShadow.draw(canvas);
            backgroundRect.set(left, rectTop, right, rectBottom);
            float radius = backgroundRect.height() / 2f;
            canvas.drawRoundRect(backgroundRect, radius, radius, backgroundPaint);
            canvas.restoreToCount(restore);
        }

        private void drawTransitionBubbles(Canvas canvas) {
            float bubbleProgress = reactionPickerRailBubbleProgress(transitionProgress);
            if (bubbleProgress <= 0f) {
                return;
            }
            float bigRadius = AndroidUtilities.dp(BIG_BUBBLE_RADIUS_DP) * bubbleProgress;
            float smallRadius = AndroidUtilities.dp(SMALL_BUBBLE_RADIUS_DP) * bubbleProgress;
            float bigOffset = AndroidUtilities.dp(BIG_BUBBLE_OFFSET_DP);
            float bigCx = LocaleController.isRTL ? bigOffset : getWidth() - bigOffset;
            float bigCy = getHeight() - getPaddingBottom();
            drawBubbleShadow(canvas, bigCx, bigCy, bigRadius, BIG_BUBBLE_SHADOW_PAD_DP, bubbleProgress);
            canvas.drawCircle(bigCx, bigCy, bigRadius, bubblePaint);

            float smallCx = LocaleController.isRTL
                    ? bigOffset - AndroidUtilities.dp(BIG_BUBBLE_RADIUS_DP)
                    : getWidth() - bigOffset + AndroidUtilities.dp(BIG_BUBBLE_RADIUS_DP);
            float smallCy = getHeight() - AndroidUtilities.dp(SMALL_BUBBLE_RADIUS_DP) - AndroidUtilities.dp(3);
            drawBubbleShadow(canvas, smallCx, smallCy, bigRadius, SMALL_BUBBLE_SHADOW_PAD_DP, bubbleProgress);
            canvas.drawCircle(smallCx, smallCy, smallRadius, bubblePaint);
        }

        private void drawBubbleShadow(Canvas canvas, float cx, float cy, float radius, int shadowPadDp, float progress) {
            float shadowPad = AndroidUtilities.dp(shadowPadDp) * progress;
            bubbleShadow.setAlpha(255);
            bubbleShadow.setBounds(
                    (int) (cx - radius - shadowPad),
                    (int) (cy - radius - shadowPad),
                    (int) (cx + radius + shadowPad),
                    (int) (cy + radius + shadowPad)
            );
            bubbleShadow.draw(canvas);
        }
    }

    private static float reactionPickerRailBubbleProgress(float progress) {
        return clamp((clamp(progress, 0f, 1f) - CLIP_PROGRESS) / (1f - CLIP_PROGRESS), 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's org/telegram/ui/Components/ProfileActionsView.java.
 * It preserves the upstream measurement, drawing, touch, animation, and
 * accessibility shape while binding to NativeDo profile destinations.
 */
package io.github.baba983.nativedo.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("ViewConstructor")
public class DiscourseProfileActionsView extends View {

    public static final int KEY_EDIT_PROFILE = 1;
    public static final int KEY_TOPICS = 2;
    public static final int KEY_REPLIES = 3;
    public static final int KEY_SHARE = 4;

    public static final class ActionSpec {
        public final int key;
        public final CharSequence title;
        public final int filledIcon;
        public final int outlineIcon;

        public ActionSpec(int key, @NonNull CharSequence title, @DrawableRes int filledIcon, @DrawableRes int outlineIcon) {
            this.key = key;
            this.title = title;
            this.filledIcon = filledIcon;
            this.outlineIcon = outlineIcon;
        }
    }

    public interface OnActionClickListener {
        void onClick(int key, float x, float y);
    }

    private final List<Action> actions = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float xpadding = dpf2(14);
    private final float ypadding = dpf2(12);
    private final float top = dpf2(8);
    private final int targetHeight;

    private int activeCount;
    private float currentHeight;
    private float parentExpanded;
    private boolean openingLayout = true;
    private OnActionClickListener onActionClickListener;
    private ColorFilter iconColorFilter;
    private int iconColorFilterColor;
    private Action hit;
    private float downX;
    private float downY;
    private long downTime;
    private AccessibilityNodeProvider accessibilityNodeProvider;

    public DiscourseProfileActionsView(@NonNull Context context, int targetHeight) {
        super(context);
        this.targetHeight = (int) (targetHeight - ypadding - top);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setOnActionClickListener(@Nullable OnActionClickListener listener) {
        onActionClickListener = listener;
    }

    public void setParentExpanded(float expanded) {
        if (parentExpanded != expanded) {
            parentExpanded = expanded;
            invalidate();
        }
    }

    public void setActions(@NonNull List<ActionSpec> specs, boolean animated) {
        Map<Integer, Action> existing = new HashMap<>();
        for (Action action : actions) {
            if (!action.isDeleting && !action.isDeleted) {
                existing.put(action.key, action);
            }
        }

        List<Action> next = new ArrayList<>();
        for (ActionSpec spec : specs) {
            Action action = existing.remove(spec.key);
            if (action == null) {
                action = new Action(spec);
                action.isOpening = animated && !openingLayout;
            } else {
                action.update(spec);
            }
            next.add(action);
        }
        for (Action removed : existing.values()) {
            removed.delete();
            next.add(removed);
        }
        actions.clear();
        actions.addAll(next);
        activeCount = specs.size();
        accessibilityNodeProvider = null;
        invalidate();
    }

    public void finishOpeningLayout() {
        openingLayout = false;
    }

    public void updatePosition(float y, float newHeight) {
        currentHeight = newHeight;
        setTranslationY(y);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.makeMeasureSpec((int) (targetHeight + top + ypadding), MeasureSpec.EXACTLY)
        );
    }

    private float getItemWidth() {
        if (activeCount <= 0) {
            return 0f;
        }
        float betweenPadding = xpadding / 2f;
        return (getMeasuredWidth() - betweenPadding * (activeCount - 1) - xpadding * 2f) / activeCount;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float height = Math.max(0f, currentHeight - ypadding - top);
        if (height <= 0f || activeCount <= 0) {
            return;
        }

        float betweenPadding = xpadding / 2f;
        float width = getItemWidth();
        float left = xpadding;
        for (Action action : actions) {
            if (action.isDeleted) {
                continue;
            }
            if (!action.isDeleting) {
                action.rect.set(left, top, left + width, top + height);
                left += width + betweenPadding;
            }
            action.updatePosition();
        }

        float fraction = Utilities.clamp01(height / targetHeight);
        float backgroundAlpha = Utilities.clamp01((fraction - 0.2f) / 0.8f);
        if (backgroundAlpha <= 0f) {
            return;
        }

        paint.setColor(Theme.getColor(Theme.key_profile_actionBackground));
        paint.setStyle(Paint.Style.FILL);
        for (Action action : actions) {
            if (action.isDeleted) {
                continue;
            }
            AndroidUtilities.rectTmp.set(action.rect);
            AndroidUtilities.rectTmp.inset(
                    action.rect.width() / 2f * (1f - action.getScale()),
                    action.rect.height() / 2f * (1f - action.getScale())
            );
            paint.setAlpha((int) (255 * action.getAlpha() * backgroundAlpha));
            if (parentExpanded < .5f) {
                paint.setShadowLayer(dpf2(1.5f), 0, 0, 0x20000000);
            } else {
                paint.clearShadowLayer();
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, getRoundRadius(), getRoundRadius(), paint);
        }

        float contentAlpha = Utilities.clamp01((fraction - 0.4f) / 0.6f);
        if (contentAlpha > 0f) {
            for (Action action : actions) {
                drawAction(canvas, action, fraction, contentAlpha);
            }
        }

        for (int i = actions.size() - 1; i >= 0; i--) {
            if (actions.get(i).isDeleted) {
                actions.remove(i);
            }
        }
    }

    private void drawAction(@NonNull Canvas canvas, @NonNull Action action, float fraction, float alpha) {
        if (action.isDeleted) {
            return;
        }
        float useWhiteIcon = MathUtils.clamp((parentExpanded - .75f) / .25f, 0f, 1f);
        int contentColor = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_profile_actionIcon),
                Color.WHITE,
                useWhiteIcon
        );
        if (iconColorFilter == null || iconColorFilterColor != contentColor) {
            iconColorFilterColor = contentColor;
            iconColorFilter = new PorterDuffColorFilter(contentColor, PorterDuff.Mode.SRC_IN);
        }

        canvas.save();
        alpha *= action.getAlpha();
        float cx = action.rect.centerX();
        float cy = action.rect.centerY();
        float scale = fraction * action.getScale();
        canvas.scale(scale, scale, cx, cy);
        canvas.clipRect(action.rect);

        action.updateBounds();
        float outlineAlpha = (1f - useWhiteIcon) * alpha;
        float filledAlpha = useWhiteIcon * alpha;
        drawActionDrawable(canvas, action.drawableOutline, outlineAlpha);
        drawActionDrawable(canvas, action.drawableFilled, filledAlpha);

        if (action.textLayout != null) {
            float textTop = action.bounds.bottom + dpf2(2.5f);
            canvas.save();
            canvas.translate(cx - action.textLayout.getWidth() / 2f, textTop);
            action.textPaint.setColor(contentColor);
            action.textPaint.setAlpha((int) (255 * alpha));
            action.textLayout.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawActionDrawable(@NonNull Canvas canvas, @Nullable Drawable drawable, float alpha) {
        if (drawable == null || alpha <= 0f) {
            return;
        }
        drawable.setColorFilter(iconColorFilter);
        drawable.setAlpha((int) (255 * alpha));
        drawable.draw(canvas);
    }

    public float getRoundRadius() {
        return dp(16);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (currentHeight < dp(8)) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        int eventAction = event.getAction();
        if (eventAction == MotionEvent.ACTION_DOWN) {
            hit = null;
            for (Action action : actions) {
                if (!action.isDeleting && action.rect.contains(x, y)) {
                    hit = action;
                    downX = x;
                    downY = y;
                    downTime = System.currentTimeMillis();
                    action.bounce.setPressed(true);
                    break;
                }
            }
        } else if (eventAction == MotionEvent.ACTION_MOVE) {
            if (hit != null && (Math.abs(x - downX) > 20 || Math.abs(y - downY) > 20)) {
                hit.bounce.setPressed(false);
                hit = null;
            }
        } else if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) {
            if (hit != null) {
                Action clicked = hit;
                clicked.bounce.setPressed(false);
                if (eventAction == MotionEvent.ACTION_UP && clicked.rect.contains(x, y)) {
                    if (System.currentTimeMillis() - downTime > 250) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {
                        }
                    }
                    if (onActionClickListener != null) {
                        onActionClickListener.onClick(clicked.key, clicked.rect.left, clicked.rect.top);
                    }
                }
                hit = null;
                return true;
            }
        }
        return hit != null;
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (accessibilityNodeProvider == null) {
            accessibilityNodeProvider = new AccessibilityNodeProvider() {
                @Override
                public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                    int[] position = {0, 0};
                    getLocationOnScreen(position);
                    if (virtualViewId == HOST_VIEW_ID) {
                        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(DiscourseProfileActionsView.this);
                        onInitializeAccessibilityNodeInfo(info);
                        info.setEnabled(true);
                        for (Action action : actions) {
                            if (!action.isDeleting && !action.isDeleted) {
                                info.addChild(DiscourseProfileActionsView.this, action.key);
                            }
                        }
                        return info;
                    }
                    Action action = find(virtualViewId);
                    if (action == null || action.rect.isEmpty()) {
                        return null;
                    }
                    AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
                    info.setSource(DiscourseProfileActionsView.this, virtualViewId);
                    info.setParent(DiscourseProfileActionsView.this);
                    info.setPackageName(getContext().getPackageName());
                    info.setClassName(android.widget.Button.class.getName());
                    info.setText(action.title);
                    info.setClickable(true);
                    info.setFocusable(true);
                    info.setEnabled(true);
                    info.setVisibleToUser(true);
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                    info.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                    Rect bounds = new Rect(
                            (int) action.rect.left,
                            (int) action.rect.top,
                            (int) action.rect.right,
                            (int) action.rect.bottom
                    );
                    info.setBoundsInParent(bounds);
                    bounds.offset(position[0], position[1]);
                    info.setBoundsInScreen(bounds);
                    return info;
                }

                @Override
                public boolean performAction(int virtualViewId, int actionId, @Nullable Bundle arguments) {
                    if (virtualViewId == HOST_VIEW_ID) {
                        return performAccessibilityAction(actionId, arguments);
                    }
                    Action action = find(virtualViewId);
                    if (action == null) {
                        return false;
                    }
                    if (actionId == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                        sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                        return true;
                    }
                    if (actionId == AccessibilityNodeInfo.ACTION_CLICK) {
                        if (onActionClickListener != null) {
                            onActionClickListener.onClick(virtualViewId, 0f, 0f);
                        }
                        return true;
                    }
                    return false;
                }
            };
        }
        return accessibilityNodeProvider;
    }

    private void sendAccessibilityEventForVirtualView(int viewId, int eventType) {
        AccessibilityManager manager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null || !manager.isTouchExplorationEnabled()) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setPackageName(getContext().getPackageName());
        event.setSource(this, viewId);
        if (getParent() != null) {
            getParent().requestSendAccessibilityEvent(this, event);
        }
    }

    @Nullable
    private Action find(int key) {
        for (Action action : actions) {
            if (!action.isDeleting && !action.isDeleted && action.key == key) {
                return action;
            }
        }
        return null;
    }

    private final class Action {
        final int key;
        final ButtonBounce bounce = new ButtonBounce(DiscourseProfileActionsView.this);
        final RectF prevRect = new RectF();
        final RectF rect = new RectF();
        final RectF from = new RectF();
        final RectF to = new RectF();
        final Rect bounds = new Rect();
        final AnimatedFloat positionFraction = new AnimatedFloat(
                DiscourseProfileActionsView.this,
                0,
                250,
                CubicBezierInterpolator.DEFAULT
        );
        final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        CharSequence title;
        Drawable drawableFilled;
        Drawable drawableOutline;
        StaticLayout textLayout;
        int textLayoutWidth;
        boolean isOpening;
        boolean isDeleting;
        boolean isDeleted;

        Action(@NonNull ActionSpec spec) {
            key = spec.key;
            textPaint.setTextSize(dp(11));
            textPaint.setTypeface(AndroidUtilities.bold());
            update(spec);
        }

        void update(@NonNull ActionSpec spec) {
            title = spec.title;
            drawableFilled = ContextCompat.getDrawable(getContext(), spec.filledIcon);
            drawableOutline = ContextCompat.getDrawable(getContext(), spec.outlineIcon);
            if (drawableFilled != null) drawableFilled = drawableFilled.mutate();
            if (drawableOutline != null) drawableOutline = drawableOutline.mutate();
            textLayout = null;
            textLayoutWidth = 0;
        }

        void updateBounds() {
            int iconSize = dp(24);
            int textWidth = Math.max(1, (int) actionTextWidth());
            ensureTextLayout(textWidth);
            int textHeight = textLayout == null ? 0 : textLayout.getHeight();
            float drawableTop = Math.max(0, (targetHeight - textHeight) / 3f + dpf2(1.33f));
            int cx = (int) rect.centerX();
            bounds.set(cx - iconSize / 2, (int) drawableTop, cx + iconSize / 2, (int) drawableTop + iconSize);
            if (drawableFilled != null) drawableFilled.setBounds(bounds);
            if (drawableOutline != null) drawableOutline.setBounds(bounds);
        }

        float actionTextWidth() {
            return Math.max(1f, rect.width() - dp(2));
        }

        void ensureTextLayout(int width) {
            if (textLayout != null && textLayoutWidth == width) {
                return;
            }
            textLayoutWidth = width;
            textLayout = StaticLayout.Builder.obtain(title, 0, title.length(), textPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setEllipsizedWidth(width)
                    .setIncludePad(false)
                    .setMaxLines(3)
                    .build();
        }

        float getScale() {
            return bounce.getScale(.04f);
        }

        float getAlpha() {
            if (isDeleting) {
                return 1f - positionFraction.set(1f);
            }
            if (isOpening) {
                return positionFraction.set(1f);
            }
            return 1f;
        }

        void delete() {
            isDeleting = true;
            from.set(prevRect);
            to.set(prevRect);
            to.left = to.right = to.centerX();
            positionFraction.set(0f, true);
        }

        void updatePosition() {
            if (isDeleting) {
                animatePosition();
                return;
            }
            if (openingLayout) {
                isOpening = false;
                prevRect.set(rect);
                from.set(rect);
                to.set(rect);
                positionFraction.set(1f, true);
                return;
            }
            if (to.isEmpty()) {
                isOpening = true;
                to.set(rect);
                from.set(rect);
                from.left = from.right = to.centerX();
                positionFraction.set(0f, true);
            }
            if (!rect.equals(to)) {
                from.set(prevRect);
                to.set(rect);
                positionFraction.set(0f, true);
            }
            animatePosition();
            prevRect.set(rect);
        }

        void animatePosition() {
            float progress = positionFraction.set(1f);
            if (progress != 1f) {
                rect.left = AndroidUtilities.lerp(from.left, to.left, progress);
                rect.right = AndroidUtilities.lerp(from.right, to.right, progress);
                invalidate();
            } else {
                isOpening = false;
                if (isDeleting) {
                    isDeleted = true;
                }
            }
        }
    }
}

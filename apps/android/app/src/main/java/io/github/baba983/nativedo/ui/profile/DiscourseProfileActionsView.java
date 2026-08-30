/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of org/telegram/ui/Components/ProfileActionsView.java, bound to
 * Discourse profile actions.
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
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Components.ProfileGalleryBlurView;
import org.telegram.ui.Components.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("ViewConstructor")
public class DiscourseProfileActionsView extends View implements ProfileGalleryBlurView.ActionBlurTarget {

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
    private final Paint paint = new Paint();
    final float xpadding;
    final float ypadding;
    final float top;
    final float textPadding;
    private final int targetHeight;

    private int activeCount;
    private float currentHeight;
    private float parentExpanded;
    private int actionColor = Color.BLACK;
    private boolean openingLayout = true;
    private OnActionClickListener onActionClickListener;
    private ColorFilter iconColorFilter;
    private int iconColorFilterColor;
    private Action hit;
    private float downX;
    private float downY;
    private long downTime;
    private AccessibilityNodeProvider accessibilityNodeProvider;
    private final Path blurClipPath = new Path();
    private final Path avatarClipPath = new Path();
    private RenderNode blurRenderNode;
    private ProfileActivity.AvatarImageView blurAvatarView;
    private float blurRenderNodeScale;
    private float blurRenderNodeTranslationY;
    private boolean suppressSolidBackground;

    public DiscourseProfileActionsView(@NonNull Context context, int targetHeight) {
        super(context);
        paint.setColor(Color.BLACK);
        paint.setAlpha(40);

        xpadding = dpf2(14);
        ypadding = dpf2(12);
        top = dpf2(8);
        textPadding = dpf2(4);

        this.targetHeight = (int) (targetHeight - ypadding - top);
        setBackgroundColor(Color.TRANSPARENT);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setOnActionClickListener(@Nullable OnActionClickListener listener) {
        onActionClickListener = listener;
    }

    @Override
    public void drawingBlur(boolean drawing) {
        if (suppressSolidBackground != drawing || blurRenderNode != null) {
            suppressSolidBackground = drawing;
            blurRenderNode = null;
            blurAvatarView = null;
            invalidate();
        }
    }

    @Override
    public void drawingBlur(
            @NonNull RenderNode renderNode,
            @Nullable ProfileActivity.AvatarImageView avatarView,
            float scale,
            float translationY
    ) {
        suppressSolidBackground = false;
        blurRenderNode = renderNode;
        blurAvatarView = avatarView;
        blurRenderNodeScale = scale;
        blurRenderNodeTranslationY = translationY;
        invalidate();
    }

    public void setParentExpanded(float expanded) {
        if (parentExpanded != expanded) {
            parentExpanded = expanded;
            invalidate();
        }
    }

    public void setActionsColor(int color) {
        if (actionColor != color) {
            actionColor = color;
            paint.setColor(actionColor);
            invalidate();
        }
    }

    private boolean isButtonColorLight() {
        return AndroidUtilities.computePerceivedBrightness(actionColor) > .72f;
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
        int w = getMeasuredWidth();
        float betweenPadding = xpadding / 2f;
        return (w - betweenPadding * (activeCount - 1) - xpadding * 2f) / activeCount;
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
        if (blurRenderNode != null) {
            blurClipPath.rewind();
        }
        for (Action action : actions) {
            if (action.isDeleted) {
                continue;
            }
            if (!action.isDeleting) {
                action.rect.set(left, top, left + width, top + height);
                left += width + betweenPadding;
            }
            action.updatePosition();
            if (blurRenderNode != null) {
                AndroidUtilities.rectTmp.set(action.rect);
                AndroidUtilities.rectTmp.inset(
                        action.rect.width() / 2f * (1f - action.getScale()),
                        action.rect.height() / 2f * (1f - action.getScale())
                );
                AndroidUtilities.rectTmp.inset(-1, -1);
                blurClipPath.addRoundRect(
                        AndroidUtilities.rectTmp,
                        getRoundRadius(),
                        getRoundRadius(),
                        Path.Direction.CCW
                );
            }
        }

        float fraction = Utilities.clamp01(height / targetHeight);
        float backgroundAlpha = Utilities.clamp01((fraction - 0.2f) / 0.8f);
        if (backgroundAlpha <= 0f) {
            return;
        }

        if (!suppressSolidBackground) {
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
                int wasAlpha = paint.getAlpha();
                int newAlpha = (int) (action.getAlpha() * backgroundAlpha * wasAlpha);
                paint.setAlpha(newAlpha);
                if (SharedConfig.shadowsInSections && isButtonColorLight() && parentExpanded < .5f) {
                    paint.setShadowLayer(
                            dpf2(1.5f),
                            0,
                            0,
                            Theme.multAlpha(
                                    Color.BLACK & 0x20FFFFFF,
                                    newAlpha / 255f
                            )
                    );
                } else {
                    paint.clearShadowLayer();
                }
                canvas.drawRoundRect(AndroidUtilities.rectTmp, getRoundRadius(), getRoundRadius(), paint);
                paint.setAlpha(wasAlpha);
            }
        }

        drawBlurRenderNode(canvas);

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
        boolean buttonColorLight = isButtonColorLight();
        float useWhiteIcon = buttonColorLight
                ? (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? Utilities.clamp01((parentExpanded - .75f) / .25f)
                    : 0f)
                : 1f;
        int contentColor = ColorUtils.blendARGB(Color.BLACK, Color.WHITE, useWhiteIcon);
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

        updateBounds(action);
        float textHeight = action.text.getHeight();
        float textY = action.bounds.bottom + action.bounds.top
                - textHeight * action.textScale / 2f - dp(4.66f);
        canvas.save();
        canvas.scale(
                action.textScale,
                action.textScale,
                cx,
                textY + textHeight * action.textScale / 2f
        );
        action.text.draw(canvas, cx - action.text.getWidth() / 2f, textY, contentColor, alpha);
        canvas.restore();

        float outlineAlpha = (1f - useWhiteIcon) * alpha;
        float filledAlpha = useWhiteIcon * alpha;
        drawActionDrawable(canvas, action.drawableOutline, outlineAlpha);
        drawActionDrawable(canvas, action.drawableFilled, filledAlpha);
        canvas.restore();
    }

    private void drawBlurRenderNode(@NonNull Canvas canvas) {
        if (
                blurRenderNode == null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                !blurRenderNode.hasDisplayList() ||
                !canvas.isHardwareAccelerated()
        ) {
            return;
        }
        canvas.save();
        if (blurAvatarView != null && blurAvatarView.getParent() instanceof View) {
            View host = (View) blurAvatarView.getParent();
            float left = host.getX();
            float top = host.getY() - getTranslationY();
            float width = host.getWidth() * host.getScaleX();
            float height = host.getHeight() * host.getScaleY();
            avatarClipPath.rewind();
            avatarClipPath.addRoundRect(
                    left,
                    top,
                    left + width,
                    top + height,
                    blurAvatarView.getRoundRadiusForExpand() * host.getScaleX(),
                    blurAvatarView.getRoundRadiusForExpand() * host.getScaleY(),
                    Path.Direction.CCW
            );
            canvas.clipPath(avatarClipPath);
        }
        canvas.clipPath(blurClipPath);
        canvas.translate(0f, blurRenderNodeTranslationY);
        canvas.scale(blurRenderNodeScale, blurRenderNodeScale);
        canvas.drawRenderNode(blurRenderNode);
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

    private void updateBounds(Action action) {
        final float cx = action.rect.centerX();
        final float cy = action.rect.centerY();
        final int drawableSize = dp(24);
        final float drawableR = drawableSize * .5f;

        int textWidth = Math.max(1, (int) (action.rect.width() - dp(2)));
        action.ensureTextLayout(textWidth);
        float textHeight = action.text.getHeight();
        int lineCount = action.text.getLineCount();
        action.textScale = lineCount >= 3 ? .75f : lineCount >= 2 ? .85f : 1f;
        final float drawableTop = Math.max(
                0,
                (targetHeight - textHeight * action.textScale) / 3f + dpf2(1.33f)
        );
        action.setBounds(
                (int) (cx - drawableR),
                (int) drawableTop,
                (int) (cx + drawableR),
                (int) (drawableTop + drawableSize)
        );
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
        Text text;
        CharSequence title;
        Drawable drawableFilled;
        Drawable drawableOutline;
        int textLayoutWidth;
        float textScale = 1f;
        boolean isOpening;
        boolean isDeleting;
        boolean isDeleted;

        Action(@NonNull ActionSpec spec) {
            key = spec.key;
            update(spec);
        }

        void update(@NonNull ActionSpec spec) {
            title = spec.title;
            text = new Text(title, 11, AndroidUtilities.bold())
                    .multiline(3)
                    .align(Layout.Alignment.ALIGN_CENTER);
            drawableFilled = ContextCompat.getDrawable(getContext(), spec.filledIcon);
            drawableOutline = ContextCompat.getDrawable(getContext(), spec.outlineIcon);
            if (drawableFilled != null) drawableFilled = drawableFilled.mutate();
            if (drawableOutline != null) drawableOutline = drawableOutline.mutate();
            textLayoutWidth = 0;
        }

        public void setBounds(int left, int top, int right, int bottom) {
            bounds.set(left, top, right, bottom);
            checkBounds();
        }

        private void checkBounds() {
            if (drawableFilled != null) drawableFilled.setBounds(bounds);
            if (drawableOutline != null) drawableOutline.setBounds(bounds);
        }

        void ensureTextLayout(int width) {
            if (textLayoutWidth == width) {
                return;
            }
            textLayoutWidth = width;
            text.setMaxWidth(width);
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

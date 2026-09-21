/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * NativeDo stub of org.telegram.ui.Components.ChatActivityEnterView.
 * Upstream is the chat input bar (~14k lines). NativeDo only needs the type
 * signature so PhotoViewer's mentionContainer/chatActivityEnterView fields compile.
 */
public class ChatActivityEnterView extends FrameLayout {

    public ChatActivityEnterView(Context context) {
        super(context);
    }

    public void closeKeyboard() {
    }

    public void hidePopup(boolean byBackButton) {
    }

    public void hidePopup(boolean byBackButton, boolean animated) {
    }

    public void onResume() {
    }

    public void onPause() {
    }

    /**
     * NativeDo port of the pin's send button - the part of the pin's 635-line class the rich
     * editor's toolbar mounts and drives (`org/telegram/ui/iv/RichEditorToolbar.java`, byte-exact).
     *
     * <p>Kept upstream-shaped, from `ChatActivityEnterView.java` at the pinned commit:</p>
     * <ul>
     *   <li>the plane is drawn at its own intrinsic size (24dp for `send_plane_24`), centered in
     *       the circle the button paints - the pin never stretches the drawable to the view, which
     *       is what made the toolbar's 44dp mount look bloated;</li>
     *   <li>{@code checkBackgroundRect} / {@code RADIUS = 19} / the {@code dpf2(3)} margin and
     *       {@code dpf2(38)} circle, drawn only for {@code isNewDesignSendButton} (the flag the
     *       toolbar passes);</li>
     *   <li>the open state ({@code isOpen()}, overridden by the toolbar to follow
     *       {@code setSendLoading}) with its 420ms {@link AnimatedFloat} and the plane drawn
     *       through {@code drawableInverse};</li>
     *   <li>{@code setLoading(shown, progress)} and its loading arc - the pin's {@code INFINITE_LOADING}
     *       segment math, {@code R = dp(8.66f)}, the 2dp round-cap stroke and the `.6f` shrink;</li>
     *   <li>{@code setLocked} and its badge (the premium gate that sets it is a chat wire-out, so
     *       this tree only carries the state and the drawing).</li>
     * </ul>
     *
     * <p>Left out: the price/stars/count/emoji states, {@code blurredBackgroundDrawable}, the
     * ephemeral outline and the {@code appear()} pop-in - all of them chat-composer surfaces with no
     * caller here (`docs/render-model-alignment.md`, "the toolbar"). The resource id, the drawable
     * surface, {@code isOpen()}, {@code setResourceId(int)} and the usual View API are what the
     * toolbar and our host use.</p>
     */
    public static class SendButton extends View implements ItemOptions.ScrimView {

        public static final float INFINITE_LOADING = -3f;

        public final Theme.ResourcesProvider resourcesProvider;

        public int resId;

        private Drawable drawable;
        private Drawable inactiveDrawable;
        private Drawable drawableInverse;
        private int drawableColor;

        private final boolean isNewDesignSendButton;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public SendButton(Context context, int resId, Theme.ResourcesProvider resourcesProvider) {
            this(context, resId, resourcesProvider, false);
        }

        public SendButton(Context context, int resId, Theme.ResourcesProvider resourcesProvider, boolean isNewDesignSendButton) {
            super(context);
            this.resId = resId;
            this.resourcesProvider = resourcesProvider;
            this.isNewDesignSendButton = isNewDesignSendButton;

            drawable = context.getResources().getDrawable(resId).mutate();
            inactiveDrawable = context.getResources().getDrawable(resId).mutate();
            drawableInverse = context.getResources().getDrawable(resId).mutate();
            drawable.setCallback(this);
            inactiveDrawable.setCallback(this);
            drawableInverse.setCallback(this);

            loadingPaint.setStyle(Paint.Style.STROKE);
            loadingPaint.setStrokeWidth(dp(2));
            loadingPaint.setStrokeJoin(Paint.Join.ROUND);
            loadingPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        /** The pin's open state - `starsPrice > 0` there (a chat wire-out); the toolbar overrides it. */
        public boolean isOpen() {
            return false;
        }

        private boolean locked;
        private Drawable lockIcon;
        private int lockIconColor;
        public void setLocked(boolean locked) {
            if (this.locked == locked) return;
            this.locked = locked;
            invalidate();
        }

        public void setResourceId(int resId) {
            if (this.resId != resId) {
                this.resId = resId;
                drawable = getContext().getResources().getDrawable(resId).mutate();
                inactiveDrawable = getContext().getResources().getDrawable(resId).mutate();
                drawableInverse = getContext().getResources().getDrawable(resId).mutate();
                drawable.setCallback(this);
                inactiveDrawable.setCallback(this);
                drawableInverse.setCallback(this);
                invalidate();
            }
        }

        public boolean isInactive() {
            return false;
        }

        public boolean shouldDrawBackground() {
            return false;
        }

        public int getFillColor() {
            return Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
        }

        public final AnimatedFloat open = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean infiniteLoading;
        private final FastOutSlowInInterpolator loadingInterpolator = new FastOutSlowInInterpolator();

        private boolean loadingShown;
        private float loadingProgress;
        private final AnimatedFloat loadingAnimatedShown = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat loadingAnimatedProgress = new AnimatedFloat(this, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);

        private final Path path = new Path();
        private final Paint loadingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public void setLoading(boolean shown, float progress) {
            if (loadingShown == shown && (!shown || Math.abs(loadingProgress - progress) < 0.01f) && infiniteLoading == Math.abs(progress - INFINITE_LOADING) < 0.01f)
                return;
            infiniteLoading = Math.abs(progress - INFINITE_LOADING) < 0.01f;
            if (!loadingShown && shown) {
                loadingAnimatedProgress.set(0, true);
            }
            loadingAnimatedShown.setDelay(shown && loadingAnimatedShown.get() >= 1f ? 650 : 0);
            loadingShown = shown;
            loadingProgress = !shown ? 1f : progress;
            invalidate();
        }

        public void updateColors() {
            int color = isNewDesignSendButton ? Color.WHITE : Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
            if (color != drawableColor) {
                drawableColor = color;
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                int c = Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
                inactiveDrawable.setColorFilter(new PorterDuffColorFilter(Color.argb(0xb4, Color.red(c), Color.green(c), Color.blue(c)), PorterDuff.Mode.SRC_IN));
                drawableInverse.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }
            if (isNewDesignSendButton) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider));
            } else if (shouldDrawBackground()) {
                backgroundPaint.setColor(getFillColor());
            } else {
                backgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 75));
            }
        }

        private final RectF backgroundRect = new RectF();
        private static final int RADIUS = 19;

        private void checkBackgroundRect() {
            final float margin = dpf2(3);
            final float height = dpf2(38);
            final float width = Math.max(height, dpf2(10 + 10));
            backgroundRect.set(
                    getMeasuredWidth() - width - margin,
                    getMeasuredHeight() - height - margin,
                    getMeasuredWidth() - margin,
                    getMeasuredHeight() - margin
            );
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int saved = canvas.save();

            if (!isNewDesignSendButton) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            }
            updateColors();
            checkBackgroundRect();
            if (isNewDesignSendButton) {
                canvas.drawRoundRect(backgroundRect, dp(RADIUS), dp(RADIUS), backgroundPaint);
            }

            final boolean inactive = isInactive();
            final Drawable drawable = inactive ? this.inactiveDrawable : this.drawable;

            int x, y;
            if (isNewDesignSendButton) {
                x = Math.round(backgroundRect.right - backgroundRect.height() / 2f - drawable.getIntrinsicWidth() / 2f);
                y = Math.round(backgroundRect.top + backgroundRect.height() / 2f - drawable.getIntrinsicHeight() / 2f);
            } else {
                // the pin's `center` branch: the composer's other layouts trim the icon by hand
                x = getMeasuredWidth() - getMeasuredHeight() / 2 - drawable.getIntrinsicWidth() / 2;
                y = (getMeasuredHeight() - drawable.getIntrinsicHeight()) / 2;
            }

            final float loadingShown = this.loadingAnimatedShown.set(this.loadingShown);

            final float openProgress = open.set(isOpen());
            if (openProgress < 1) {
                drawable.setAlpha(0xFF);
                drawable.setBounds(x, y, x + drawable.getIntrinsicWidth(), y + drawable.getIntrinsicHeight());
                drawable.draw(canvas);
            }
            // the pin's `newCounterPos == false` geometry with the price terms at zero (no price
            // state here): it opens from the circle's own size to the dp(4) margin, right-aligned
            final float right = AndroidUtilities.lerp(getMeasuredWidth() - getMeasuredHeight() / 2.0f, getMeasuredWidth() - dp(4), openProgress);
            final float cy = getMeasuredHeight() - dp(4) - getCircleHeight() / 2f;
            final float h = getCircleHeight() * openProgress;
            final float w = getCircleWidth() * openProgress;

            setPivotX(right - w / 2.0f);
            setPivotY(cy);

            if (openProgress > 0) {
                canvas.save();
                path.rewind();
                final float r = Math.min(w, h) / 2.0f;
                AndroidUtilities.rectTmp.set(right - w, cy - h / 2.0f, right, cy + h / 2.0f);
                path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);

                if (!isNewDesignSendButton) {
                    canvas.drawPath(path, backgroundPaint);
                }
                canvas.clipPath(path);
                if (loadingShown > 0) {
                    loadingPaint.setColor(0xFFFFFFFF);
                    loadingPaint.setAlpha((int) (0xFF * loadingShown));
                    final float R = dp(8.66f);
                    AndroidUtilities.rectTmp.set(right - w / 2.0f - R, cy - R, right - w / 2.0f + R, cy + R);
                    if (infiniteLoading) {
                        final long t = System.currentTimeMillis() % 5400L;
                        float segment0 = Math.max(0, 1520 * t / 5400f - 20);
                        float segment1 = 1520 * t / 5400f;
                        for (int i = 0; i < 4; ++i) {
                            segment1 += loadingInterpolator.getInterpolation((t - i * 1350) / 667f) * 250;
                            segment0 += loadingInterpolator.getInterpolation((t - (667 + i * 1350)) / 667f) * 250;
                        }
                        canvas.drawArc(AndroidUtilities.rectTmp, segment0, segment1 - segment0, false, loadingPaint);
                    } else {
                        float startAngle = -90f;
                        startAngle += (System.currentTimeMillis() % 3000L / 1000f * 120f) % 360f;
                        canvas.drawArc(AndroidUtilities.rectTmp, startAngle, 360 * loadingAnimatedProgress.set(this.loadingProgress), false, loadingPaint);
                    }
                    canvas.save();
                    final float s = AndroidUtilities.lerp(1f, .6f, loadingShown);
                    canvas.scale(s, s, right - w / 2.0f, cy);
                    invalidate();
                }
                drawableInverse.setAlpha((int) (0xFF * (1f - loadingShown)));
                drawableInverse.setBounds(x, y, x + drawable.getIntrinsicWidth(), y + drawable.getIntrinsicHeight());
                drawableInverse.draw(canvas);
                if (loadingShown > 0) {
                    canvas.restore();
                }
                canvas.restore();
            }

            if (!isNewDesignSendButton) {
                canvas.restore();
            }

            canvas.restoreToCount(saved);
            super.onDraw(canvas);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (!locked) {
                super.draw(canvas);
                return;
            }
            canvas.saveLayerAlpha(-dp(6), -dp(6), getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            super.draw(canvas);

            final float sz = dp(18);
            final float _cx = backgroundRect.left + sz / 2f - dp(6);
            final float _cy = backgroundRect.top + sz / 2f - dp(6);
            canvas.drawCircle(_cx, _cy, sz / 2f + dp(2), Theme.PAINT_CLEAR);
            canvas.drawCircle(_cx, _cy, sz / 2f, backgroundPaint);
            if (lockIcon == null) {
                lockIcon = getContext().getResources().getDrawable(R.drawable.mini_switch_lock).mutate();
                lockIcon.setColorFilter(new PorterDuffColorFilter(lockIconColor = drawableColor, PorterDuff.Mode.SRC_IN));
            }
            if (lockIconColor != drawableColor) {
                lockIcon.setColorFilter(new PorterDuffColorFilter(lockIconColor = drawableColor, PorterDuff.Mode.SRC_IN));
            }
            lockIcon.setBounds((int) (_cx - dp(8)), (int) (_cy - dp(8)), (int) (_cx + dp(8)), (int) (_cy + dp(8)));
            lockIcon.draw(canvas);

            canvas.restore();
        }

        public int getCircleWidth() {
            return getMeasuredHeight() - dp(8);
        }

        public int getCircleHeight() {
            return getMeasuredHeight() - dp(8);
        }
    }
}

/*
 * Host stub for the pin's org/telegram/ui/iv/RichEditor.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ScaleStateListAnimator;

/**
 * NativeDo: the pin's editor view is 2,609 lines and its body is the attach-alert editor host (the
 * toolbar, the command menu, the media pickers) - the chat composer surface this tree does not have
 * yet, see {@code docs/render-model-alignment.md}, "The ui/iv project". This file carries exactly the
 * members the already-vendored cells use - the drag-state drawable, the round button (with its
 * premium badge wrapper) and the shadow wrapper - copied from the pin verbatim. When the editor host
 * lands, the pin's whole file replaces this one and the overlay's {@code native} entry goes.
 */
public class RichEditor {

    public static class DraggingDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat animatedDragging = new AnimatedFloat(this::invalidateSelf, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean dragging;

        public DraggingDrawable(int color) {
            setColor(color);
        }

        public void setColor(int color) {
            paint.setColor(color);
        }

        public void setDragging(boolean dragging) {
            if (this.dragging == dragging) return;
            this.dragging = dragging;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float dragging = animatedDragging.set(this.dragging);
            if (dragging <= 0) return;
            paint.setAlpha((int) (alpha * dragging));
            paint.setShadowLayer(dragging * dp(12), 0, dp(3), Theme.multAlpha(0x30000000, dragging));
            final Rect bounds = getBounds();
            final float horizPadding = dp(8) * dragging;
            final float vertPadding = dp(0) * dragging;
            final float radius = dp(12) * dragging;
            canvas.drawRoundRect(
                bounds.left + horizPadding,
                bounds.top + vertPadding,
                bounds.right - horizPadding,
                bounds.bottom - vertPadding + dp(6) * dragging,
                radius, radius,
                paint
            );
        }

        private int alpha = 0xFF;
        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public static class Button extends ImageView implements Theme.Colorable {

        private int startIcon, currentIcon;
        private boolean premium;
        private boolean premiumLocked;
        private int roundRadius = 20;
        private int backgroundColorKey = Theme.key_windowBackgroundWhite;
        private Theme.ResourcesProvider resourcesProvider;
        private boolean enabled = true;
        public Button(Context context, int icon, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            startIcon = currentIcon = icon;
            this.resourcesProvider = resourcesProvider;

            if (icon != 0) {
                setImageResource(icon);
            }
            setScaleType(ScaleType.CENTER);
            ScaleStateListAnimator.apply(this);

            updateColors();
        }

        public Button setPremium() {
            premium = true;
            setImageDrawable(wrapPremium(currentIcon));
            return this;
        }

        public void setPremiumLocked(boolean locked) {
            premiumLocked = locked;
            if (getDrawable() instanceof RequiresPremiumDrawable) {
                ((RequiresPremiumDrawable) getDrawable()).setPremium(locked);
            }
        }

        private RequiresPremiumDrawable wrapPremium(int icon) {
            return new RequiresPremiumDrawable(getContext(), icon)
                .setCutoutColorKey(backgroundColorKey)
                .setPremium(premiumLocked);
        }

        private boolean selected;
        public void setSelected(boolean selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            updateColors();
        }

        private boolean accent = true;
        public Button setAccent(boolean accent) {
            if (this.accent == accent) return this;
            this.accent = accent;
            updateColors();
            return this;
        }

        public void setEnabled(boolean enabled) {
            if (this.enabled == enabled) return;
            setClickable(enabled);
            animate()
                .alpha((this.enabled = enabled) ? 1.0f : 0.5f)
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
        }

        public void updateIcon(int newIcon) {
            if (currentIcon == newIcon) return;
            currentIcon = newIcon;
            if (premium) {
                AndroidUtilities.updateImageViewImageAnimated(this, wrapPremium(newIcon));
            } else {
                AndroidUtilities.updateImageViewImageAnimated(this, newIcon);
            }
        }

        public void resetIcon() {
            updateIcon(startIcon);
        }

        public Button setRoundRadius(int roundRadius) {
            this.roundRadius = roundRadius;
            updateColors();
            return this;
        }

        public Button setBackgroundColorKey(int key) {
            if (this.backgroundColorKey == key) return this;
            this.backgroundColorKey = key;
            updateColors();
            return this;
        }

        @Override
        public void updateColors() {
            if (selected) {
                final int color = Theme.getColor(accent ? Theme.key_featuredStickers_addButton : Theme.key_windowBackgroundWhiteBlackText);
                setBackground(Theme.createRadSelectorDrawable(Theme.blendOver(Theme.getColor(backgroundColorKey, resourcesProvider), Theme.multAlpha(color, .10f)), Theme.multAlpha(color, .10f), dp(roundRadius), dp(roundRadius)));
                setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            } else {
                setBackground(Theme.createRadSelectorDrawable(Theme.getColor(backgroundColorKey, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider), dp(roundRadius), dp(roundRadius)));
                setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }
        }
    }

    public static Drawable withShadow(Drawable inner) {
        return new ShadowWrapperDrawable(inner);
    }
    public static class RequiresPremiumDrawable extends Drawable {

        private final Context context;
        public final Drawable icon;
        private Drawable premiumIconCutout;
        private int premiumIconCutoutColorKey = Theme.key_windowBackgroundWhite;
        private int premiumIconCutoutColor;
        private Drawable premiumIcon;
        public boolean showPremiumIcon = true;

        public RequiresPremiumDrawable(Context context, int iconResId) {
            this(context, context.getResources().getDrawable(iconResId).mutate());
        }

        public RequiresPremiumDrawable(Context context, Drawable icon) {
            this.context = context;
            this.icon = icon;
        }

        public RequiresPremiumDrawable setPremium(boolean premium) {
            if (showPremiumIcon == premium) return this;
            showPremiumIcon = premium;
            invalidateSelf();
            return this;
        }

        public RequiresPremiumDrawable setCutoutColorKey(int colorKey) {
            premiumIconCutoutColorKey = colorKey;
            return this;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            final int cx = bounds.centerX(), cy = bounds.centerY();
            icon.setBounds(
                cx - icon.getIntrinsicWidth() / 2,
                cy - icon.getIntrinsicHeight() / 2,
                cx + icon.getIntrinsicWidth() / 2,
                cy + icon.getIntrinsicHeight() / 2
            );
            icon.draw(canvas);

            if (showPremiumIcon) {
                final int pcx = cx + dp(9), pcy = cy + dp(9);
                final int cutoutColor = Theme.getColor(premiumIconCutoutColorKey);
                if (premiumIconCutout == null) {
                    premiumIconCutout = context.getResources().getDrawable(R.drawable.star_premium_cutout).mutate();
                    premiumIconCutout.setColorFilter(new PorterDuffColorFilter(premiumIconCutoutColor = cutoutColor, PorterDuff.Mode.SRC_IN));
                }
                if (cutoutColor != premiumIconCutoutColor) {
                    premiumIconCutout.setColorFilter(new PorterDuffColorFilter(premiumIconCutoutColor = cutoutColor, PorterDuff.Mode.SRC_IN));
                }
                if (premiumIcon == null) {
                    premiumIcon = context.getResources().getDrawable(R.drawable.star_premium).mutate();
                }
                premiumIconCutout.setBounds(pcx - dp(9), pcy - dp(9), pcx + dp(9), pcy + dp(9));
                premiumIconCutout.draw(canvas);
                premiumIcon.setBounds(pcx - dp(9), pcy - dp(9), pcx + dp(9), pcy + dp(9));
                premiumIcon.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            icon.setAlpha(alpha);
        }
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            icon.setColorFilter(colorFilter);
        }
        @Override
        public int getOpacity() {
            return icon.getOpacity();
        }
        @Override
        public int getIntrinsicHeight() {
            return Math.max(dp(38), icon.getIntrinsicHeight());
        }
        @Override
        public int getIntrinsicWidth() {
            return Math.max(dp(38), icon.getIntrinsicWidth());
        }
    }
    private static class ShadowWrapperDrawable extends Drawable implements Drawable.Callback {
        private final Drawable inner;
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shadowPath = new Path();
        private final RectF rectF = new RectF();
        private final Outline outline = new Outline();
        private boolean pathDirty = true;

        ShadowWrapperDrawable(Drawable inner) {
            this.inner = inner;
            inner.setCallback(this);
            shadowPaint.setColor(0x00000000);
            if (Theme.isCurrentThemeDark()) {
                shadowPaint.setShadowLayer(dp(12), 0, dp(4), Theme.multAlpha(0xFF000000, .30f));
            } else {
                shadowPaint.setShadowLayer(dp(12), 0, dp(4), Theme.multAlpha(0xFF000000, .10f));
            }
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            inner.setBounds(bounds);
            pathDirty = true;
        }

        @Override
        public boolean isStateful() {
            return inner.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return inner.setState(state);
        }

        @Override
        public void jumpToCurrentState() {
            inner.jumpToCurrentState();
        }

        @Override
        public void setHotspot(float x, float y) {
            inner.setHotspot(x, y);
        }

        @Override
        public void setHotspotBounds(int left, int top, int right, int bottom) {
            inner.setHotspotBounds(left, top, right, bottom);
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }

        private void rebuildPath() {
            shadowPath.reset();
            rectF.set(getBounds());
            float radius = -1f;
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    inner.getOutline(outline);
                    radius = outline.getRadius();
                } catch (Throwable ignore) {}
            }
            if (radius > 0f) {
                shadowPath.addRoundRect(rectF, radius, radius, Path.Direction.CW);
            } else {
                shadowPath.addRect(rectF, Path.Direction.CW);
            }
            pathDirty = false;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (pathDirty) rebuildPath();
            canvas.drawPath(shadowPath, shadowPaint);
            inner.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            inner.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            inner.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    static String blockButtonContentDescription(int flag) {
        switch (flag) {
            case 1: return getString(R.string.AccDescrIVTextStyle);
            case 2: return getString(R.string.AccDescrIVListStyle);
            case 4: return getString(R.string.AccDescrIVTable);
            case 7: return getString(R.string.AccDescrIVFormula);
            case 9: return getString(R.string.AccDescrIVDetails);
            default: return null;
        }
    }

    static String formattingButtonContentDescription(int styleFlag) {
        if (styleFlag == RichTextStyle.BOLD) return getString(R.string.Bold);
        if (styleFlag == RichTextStyle.ITALIC) return getString(R.string.Italic);
        if (styleFlag == RichTextStyle.UNDERLINE) return getString(R.string.Underline);
        if (styleFlag == RichTextStyle.STRIKE) return getString(R.string.Strike);
        if (styleFlag == RichTextStyle.SPOILER) return getString(R.string.Spoiler);
        if (styleFlag == RichTextStyle.MONO) return getString(R.string.Mono);
        if (styleFlag == RichTextStyle.MARKED) return getString(R.string.Highlight);
        if (styleFlag == RichTextStyle.SUBSCRIPT) return getString(R.string.Subscript);
        if (styleFlag == RichTextStyle.SUPERSCRIPT) return getString(R.string.Superscript);
        return null;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.WeakHashMap;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class Bulletin {

    public static final int DURATION_SHORT = 1500;
    public static final int DURATION_LONG = 2750;
    public static final int DURATION_PROLONG = 5000;

    static final int ICON_INFO = 0;
    static final int ICON_COPY = 1;
    static final int ICON_ERROR = 2;

    private static final int SIDE_MARGIN_DP = 8;
    private static final int VERTICAL_MARGIN_DP = 8;
    private static final int ENTER_TRANSLATION_DP = 24;

    private static final WeakHashMap<Object, Delegate> delegates = new WeakHashMap<>();
    private static Bulletin visibleBulletin;

    public boolean hideAfterBottomSheet = true;

    private final ViewGroup containerLayout;
    private final BulletinView layout;
    private final Runnable hideRunnable = () -> hide();
    private int duration;
    private boolean showing;
    private boolean top;
    private Delegate currentDelegate;

    public Bulletin() {
        this(null, null, ICON_INFO, DURATION_SHORT, null);
    }

    Bulletin(ViewGroup containerLayout, CharSequence text, int iconType, int duration, Theme.ResourcesProvider resourcesProvider) {
        this.containerLayout = containerLayout;
        this.duration = duration;
        Context context = containerLayout != null ? containerLayout.getContext() : null;
        layout = context != null ? new BulletinView(context, resourcesProvider, text, iconType) : null;
    }

    public static Bulletin getVisibleBulletin() {
        return visibleBulletin;
    }

    public static void addDelegate(Object owner, Delegate delegate) {
        if (owner != null && delegate != null) {
            delegates.put(owner, delegate);
        }
    }

    public static void removeDelegate(Object owner) {
        if (owner != null) {
            delegates.remove(owner);
        }
    }

    public Bulletin show() {
        return show(false);
    }

    public Bulletin show(boolean top) {
        if (showing || containerLayout == null || layout == null) {
            return this;
        }
        this.top = top;
        if (visibleBulletin != null && visibleBulletin != this) {
            visibleBulletin.hide(0);
        }
        visibleBulletin = this;
        showing = true;
        currentDelegate = findDelegate();
        if (layout.getParent() instanceof ViewGroup) {
            ((ViewGroup) layout.getParent()).removeView(layout);
        }
        updatePosition();
        layout.setAlpha(0f);
        layout.setTranslationY(dp(top ? -ENTER_TRANSLATION_DP : ENTER_TRANSLATION_DP));
        containerLayout.addView(layout);
        containerLayout.bringChildToFront(layout);
        layout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start();
        AndroidUtilities.runOnUIThread(hideRunnable, duration);
        return this;
    }

    public Bulletin hide() {
        return hide(180);
    }

    public Bulletin hide(long duration) {
        return hide(duration > 0, duration);
    }

    public Bulletin hide(boolean animated, long duration) {
        if (!showing || layout == null) {
            return this;
        }
        showing = false;
        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        if (visibleBulletin == this) {
            visibleBulletin = null;
        }
        Runnable remove = () -> {
            if (layout.getParent() instanceof ViewGroup) {
                ((ViewGroup) layout.getParent()).removeView(layout);
            }
            layout.setAlpha(1f);
            layout.setTranslationY(0f);
        };
        if (animated) {
            long hideDuration = duration > 0 ? duration : 180;
            layout.animate()
                    .alpha(0f)
                    .translationY(dp(top ? -ENTER_TRANSLATION_DP : ENTER_TRANSLATION_DP))
                    .setDuration(hideDuration)
                    .withEndAction(remove)
                    .start();
        } else {
            layout.animate().cancel();
            remove.run();
        }
        return this;
    }

    public boolean isShowing() {
        return showing;
    }

    public Bulletin setDuration(int duration) {
        this.duration = duration;
        if (showing) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            AndroidUtilities.runOnUIThread(hideRunnable, duration);
        }
        return this;
    }

    public void updatePosition() {
        if (layout == null) {
            return;
        }
        int bottomOffset = 0;
        int topOffset = 0;
        if (currentDelegate != null) {
            topOffset = Math.max(0, currentDelegate.getTopOffset(0));
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                top ? Gravity.TOP : Gravity.BOTTOM
        );
        params.setMargins(
                dp(SIDE_MARGIN_DP),
                dp(VERTICAL_MARGIN_DP) + topOffset,
                dp(SIDE_MARGIN_DP),
                dp(VERTICAL_MARGIN_DP) + bottomOffset
        );
        layout.setLayoutParams(params);
    }

    private Delegate findDelegate() {
        Delegate delegate = delegates.get(containerLayout);
        if (delegate != null) {
            return delegate;
        }
        return null;
    }

    public interface Delegate {
        default int getTopOffset(int tag) {
            return 0;
        }
    }

    private static final class BulletinView extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;

        BulletinView(Context context, Theme.ResourcesProvider resourcesProvider, CharSequence text, int iconType) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setMinimumHeight(dp(48));
            setClipToOutline(true);
            updateBackground();

            IconView iconView = new IconView(context, resourcesProvider, iconType);
            addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 12, 16, 12));

            TextView textView = new TextView(context);
            textView.setSingleLine(false);
            textView.setMaxLines(2);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setText(text);
            textView.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setGravity(Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 56, 8, 16, 8));
        }

        private void updateBackground() {
            setBackground(Theme.createRoundRectDrawable(dp(6), Theme.getColor(Theme.key_undo_background, resourcesProvider)));
        }
    }

    private static final class IconView extends View {
        private final Theme.ResourcesProvider resourcesProvider;
        private final int iconType;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        IconView(Context context, Theme.ResourcesProvider resourcesProvider, int iconType) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.iconType = iconType;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int color = Theme.getColor(Theme.key_undo_infoColor, resourcesProvider);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(2));
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            if (iconType == ICON_COPY) {
                rect.set(dp(7), dp(5), dp(18), dp(16));
                canvas.drawRoundRect(rect, dp(2), dp(2), paint);
                rect.set(dp(4), dp(8), dp(15), dp(19));
                canvas.drawRoundRect(rect, dp(2), dp(2), paint);
            } else if (iconType == ICON_ERROR) {
                canvas.drawCircle(cx, cy, dp(9), paint);
                canvas.drawLine(cx, dp(7), cx, dp(13), paint);
                canvas.drawPoint(cx, dp(17), paint);
            } else {
                canvas.drawCircle(cx, cy, dp(9), paint);
                canvas.drawLine(cx, dp(11), cx, dp(17), paint);
                canvas.drawPoint(cx, dp(7), paint);
            }
        }
    }
}

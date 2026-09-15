/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.Map;

/**
 * Telegram-shaped alert surface following upstream AlertDialog's window,
 * sizing, theme keys, button placement, custom-view handling, and top-animation
 * contract.
 */
public class AlertDialog extends Dialog {

    public static final int ALERT_TYPE_MESSAGE = 0;
    public static final int ALERT_TYPE_LOADING = 2;
    public static final int ALERT_TYPE_SPINNER = 3;

    private final Context context;
    private final int progressViewStyle;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean dismissDialogByButtons = true;
    private boolean delayedShowScheduled;
    private boolean canCancel = true;
    private boolean canceledOnTouchOutside = true;
    private long shownAt;
    private int width;
    private int backgroundHorizontalPadding;

    // Keep these names stable: callers update title/message before and after show.
    private CharSequence currentTitle;
    private CharSequence currentMessage;
    private View customView;
    private CharSequence[] items;
    private int[] itemIcons;
    private DialogInterface.OnClickListener itemsListener;

    private CharSequence positiveLabel;
    private DialogInterface.OnClickListener positiveListener;
    private CharSequence negativeLabel;
    private DialogInterface.OnClickListener negativeListener;
    private CharSequence neutralLabel;
    private DialogInterface.OnClickListener neutralListener;
    private int redButton = Integer.MIN_VALUE;

    private int topAnimationId;
    private int topAnimationSize;
    private boolean topAnimationAutoRepeat = true;
    private int topBackgroundColor;
    private Map<String, Integer> topAnimationLayerColors;

    private TextView titleTextView;
    private TextView messageTextView;
    private ProgressBar progressBar;
    private ViewGroup buttonsLayout;

    private final Runnable showDelayedRunnable = this::showInternal;

    public AlertDialog(Context context, int progressViewStyle) {
        this(context, progressViewStyle, null);
    }

    public AlertDialog(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
        super(context != null ? context : ApplicationContextHolder.get(), R.style.TransparentDialog);
        this.context = context != null ? context : ApplicationContextHolder.get();
        this.progressViewStyle = progressViewStyle;
        this.resourcesProvider = resourcesProvider;
    }

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private View createMessageContent() {
        DialogLayout container = new DialogLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        Drawable background = context.getResources().getDrawable(R.drawable.popup_fixed_alert4).mutate();
        background.setColorFilter(new PorterDuffColorFilter(
                getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        Rect backgroundPaddings = new Rect();
        background.getPadding(backgroundPaddings);
        backgroundHorizontalPadding = backgroundPaddings.left + backgroundPaddings.right;
        container.setBackground(background);

        if (topAnimationId != 0) {
            RLottieImageView topImageView = new RLottieImageView(context);
            topImageView.setAutoRepeat(topAnimationAutoRepeat);
            if (topAnimationLayerColors != null) {
                for (Map.Entry<String, Integer> entry : topAnimationLayerColors.entrySet()) {
                    topImageView.setLayerColor(entry.getKey(), entry.getValue());
                }
            }
            topImageView.setAnimation(topAnimationId, topAnimationSize, topAnimationSize);
            topImageView.playAnimation();
            topImageView.setScaleType(ImageView.ScaleType.CENTER);
            topImageView.setBackground(Theme.createRoundRectDrawable(
                    dp(10), 0, topBackgroundColor));
            container.addView(topImageView, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, 132, Gravity.LEFT | Gravity.TOP));
        }

        if (!TextUtils.isEmpty(currentTitle)) {
            titleTextView = new TextView(context);
            titleTextView.setText(currentTitle);
            titleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.setMargins(dp(24), dp(19), dp(24),
                    items != null ? dp(14) : dp(10));
            container.addView(titleTextView, titleParams);
        }

        ScrollView contentScrollView = new ScrollView(context);
        contentScrollView.setVerticalScrollBarEnabled(false);
        LinearLayout scrollContainer = new LinearLayout(context);
        scrollContainer.setOrientation(LinearLayout.VERTICAL);
        contentScrollView.addView(scrollContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        messageTextView = new TextView(context);
        messageTextView.setText(currentMessage);
        messageTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        messageTextView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        messageTextView.setVisibility(TextUtils.isEmpty(currentMessage) ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(dp(24), 0, dp(24), customView != null || items != null ? dp(12) : 0);
        scrollContainer.addView(messageTextView, messageParams);

        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i] == null) {
                    continue;
                }
                AlertDialogCell cell = new AlertDialogCell(context);
                cell.setTextAndIcon(items[i], itemIcons != null && i < itemIcons.length ? itemIcons[i] : 0);
                cell.setTag(i);
                cell.setOnClickListener(view -> {
                    if (itemsListener != null) {
                        itemsListener.onClick(AlertDialog.this, (Integer) view.getTag());
                    }
                    dismiss();
                });
                scrollContainer.addView(cell, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
            }
        }

        if (customView != null) {
            if (customView.getParent() instanceof ViewGroup) {
                ((ViewGroup) customView.getParent()).removeView(customView);
            }
            scrollContainer.addView(customView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        container.addView(contentScrollView, scrollParams);

        if (positiveLabel != null || negativeLabel != null || neutralLabel != null) {
            buttonsLayout = createButtonsLayout();
            container.addView(buttonsLayout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    buttonsLayout instanceof LinearLayout ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(52)));
        } else if (customView == null && items == null) {
            container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
                    container.getPaddingRight(), container.getPaddingBottom() + dp(8));
        }
        return container;
    }

    private View createProgressContent() {
        FrameLayout container = new FrameLayout(context);
        container.setBackground(Theme.createRoundRectDrawable(
                dp(18), getThemedColor(Theme.key_dialog_inlineProgressBackground)));
        progressBar = new ProgressBar(context);
        container.addView(progressBar, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        return container;
    }

    private ViewGroup createButtonsLayout() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(dp(16));
        paint.setTypeface(AndroidUtilities.bold());
        int buttonsWidth = measureButton(paint, positiveLabel)
                + measureButton(paint, negativeLabel)
                + measureButton(paint, neutralLabel);
        int buttonCount = (positiveLabel == null ? 0 : 1)
                + (negativeLabel == null ? 0 : 1)
                + (neutralLabel == null ? 0 : 1);
        if (buttonCount > 1) {
            buttonsWidth += dp(8) * (buttonCount - 1);
        }
        boolean vertical = buttonsWidth > AndroidUtilities.displaySize.x - dp(64);
        ViewGroup layout;
        if (vertical) {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            layout = linearLayout;
        } else {
            layout = new ButtonsLayout(context);
        }
        layout.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView positive = createButton(positiveLabel, BUTTON_POSITIVE, positiveListener, redButton == BUTTON_POSITIVE);
        TextView negative = createButton(negativeLabel, BUTTON_NEGATIVE, negativeListener, redButton == BUTTON_NEGATIVE);
        TextView neutral = createButton(neutralLabel, BUTTON_NEUTRAL, neutralListener, redButton == BUTTON_NEUTRAL);
        if (vertical) {
            addVerticalButton((LinearLayout) layout, negative);
            addVerticalButton((LinearLayout) layout, neutral);
            addVerticalButton((LinearLayout) layout, positive);
        } else {
            addHorizontalButton((ButtonsLayout) layout, positive);
            addHorizontalButton((ButtonsLayout) layout, negative);
            addHorizontalButton((ButtonsLayout) layout, neutral);
        }
        return layout;
    }

    private int measureButton(TextPaint paint, CharSequence label) {
        return label == null ? 0 : (int) paint.measureText(label, 0, label.length()) + dp(24);
    }

    private TextView createButton(CharSequence label, int which,
            DialogInterface.OnClickListener listener, boolean red) {
        if (label == null) {
            return null;
        }
        int color = getThemedColor(red ? Theme.key_text_RedBold : Theme.key_dialogButton);
        TextView button = new TextView(context);
        button.setMinWidth(dp(64));
        button.setTag(which);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        button.setTextColor(color);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(AndroidUtilities.bold());
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setSingleLine(true);
        button.setText(label);
        button.setBackground(Theme.getRoundRectSelectorDrawable(dp(20), color));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setOnClickListener(view -> {
            if (listener != null) {
                listener.onClick(AlertDialog.this, which);
            }
            if (dismissDialogByButtons && isShowing()) {
                if (which == BUTTON_NEGATIVE) {
                    cancel();
                } else {
                    dismiss();
                }
            }
        });
        return button;
    }

    private void addHorizontalButton(ButtonsLayout layout, TextView button) {
        if (button != null) {
            layout.addView(button, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), Gravity.TOP));
        }
    }

    private void addVerticalButton(LinearLayout layout, TextView button) {
        if (button == null) {
            return;
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        if (layout.getChildCount() > 0) {
            params.topMargin = dp(6);
        }
        layout.addView(button, params);
    }

    private void configureWindow(Window window) {
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        WindowManager.LayoutParams params = window.getAttributes();
        if (progressViewStyle == ALERT_TYPE_MESSAGE) {
            int calculatedWidth = AndroidUtilities.displaySize.x - dp(48);
            int maxWidth = AndroidUtilities.isTablet() ? dp(446) : dp(356);
            params.width = (width > 0 ? width : Math.min(maxWidth, calculatedWidth))
                    + backgroundHorizontalPadding;
            params.dimAmount = 0.5f;
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            if (customView == null || !canTextInput(customView)) {
                params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            } else {
                params.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            }
        } else {
            params.width = dp(86);
            params.height = dp(86);
            params.dimAmount = 0.5f;
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        window.setAttributes(params);
    }

    private boolean canTextInput(View view) {
        if (view.onCheckIsTextEditor()) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (canTextInput(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View content = progressViewStyle == ALERT_TYPE_MESSAGE
                ? createMessageContent()
                : createProgressContent();
        setContentView(content);
        setCancelable(canCancel);
        super.setCanceledOnTouchOutside(canCancel && canceledOnTouchOutside);
        configureWindow(getWindow());
    }

    private void showInternal() {
        delayedShowScheduled = false;
        try {
            setCancelable(canCancel);
            super.setCanceledOnTouchOutside(canCancel && canceledOnTouchOutside);
            super.show();
            shownAt = System.currentTimeMillis();
        } catch (Exception ignore) {
        }
    }

    @Override
    public void show() {
        showInternal();
    }

    @Override
    public void dismiss() {
        delayedShowScheduled = false;
        AndroidUtilities.cancelRunOnUIThread(showDelayedRunnable);
        try {
            super.dismiss();
        } catch (Exception ignore) {
        }
    }

    @Override
    public void cancel() {
        try {
            super.cancel();
        } catch (Exception ignore) {
        }
    }

    @Override
    public boolean isShowing() {
        return super.isShowing();
    }

    @Override
    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        super.setOnDismissListener(listener);
    }

    @Override
    public void setOnShowListener(DialogInterface.OnShowListener listener) {
        super.setOnShowListener(listener);
    }

    @Override
    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        super.setOnCancelListener(listener);
    }

    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
        canceledOnTouchOutside = cancel;
        super.setCanceledOnTouchOutside(cancel && canCancel);
    }

    public void setMessage(CharSequence message) {
        currentMessage = message;
        if (messageTextView != null) {
            messageTextView.setText(message);
            messageTextView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        }
    }

    public void setTitle(CharSequence title) {
        currentTitle = title;
        if (titleTextView != null) {
            titleTextView.setText(title);
        }
    }

    public View getButton(int which) {
        return buttonsLayout == null ? null : buttonsLayout.findViewWithTag(which);
    }

    public ViewGroup getButtonsLayout() {
        return buttonsLayout;
    }

    public void setProgress(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }

    public void setCanCancel(boolean cancel) {
        canCancel = cancel;
        setCancelable(cancel);
        super.setCanceledOnTouchOutside(cancel && canceledOnTouchOutside);
    }

    public void setFocusable(boolean focusable) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams params = window.getAttributes();
        if (focusable) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        window.setAttributes(params);
    }

    public void showDelayed(int delay) {
        delayedShowScheduled = true;
        AndroidUtilities.cancelRunOnUIThread(showDelayedRunnable);
        AndroidUtilities.runOnUIThread(showDelayedRunnable, delay);
    }

    public void showDelayed(long delay) {
        showDelayed((int) delay);
    }

    public void dismissUnless(int delay) {
        if (!isShowing() || delay <= 0) {
            dismiss();
            return;
        }
        long remaining = delay - (System.currentTimeMillis() - shownAt);
        if (remaining <= 0) {
            dismiss();
        } else {
            AndroidUtilities.runOnUIThread(this::dismiss, remaining);
        }
    }

    public void setDismissDialogByButtons(boolean value) {
        dismissDialogByButtons = value;
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return null;
    }

    private final class AlertDialogCell extends FrameLayout {
        private final TextView textView;
        private final ImageView imageView;

        AlertDialogCell(Context context) {
            super(context);
            setBackground(Theme.createSelectorDrawable(
                    getThemedColor(Theme.key_dialogButtonSelector), 2));
            setPadding(dp(23), 0, dp(23), 0);
            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(
                    getThemedColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, 40,
                    Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
            textView = new TextView(context);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            addView(textView, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        }

        void setTextAndIcon(CharSequence text, int icon) {
            textView.setText(text);
            if (icon != 0) {
                imageView.setImageResource(icon);
                imageView.setVisibility(VISIBLE);
                textView.setPadding(LocaleController.isRTL ? 0 : dp(56), 0,
                        LocaleController.isRTL ? dp(56) : 0, 0);
            } else {
                imageView.setVisibility(INVISIBLE);
            }
        }
    }

    private static final class DialogLayout extends LinearLayout {
        DialogLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxHeight = Math.max(dp(120), AndroidUtilities.displaySize.y - dp(48));
            int requestedHeight = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? maxHeight
                    : Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeight);
            int constrained = MeasureSpec.makeMeasureSpec(
                    requestedHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, constrained);
        }
    }

    private static final class ButtonsLayout extends FrameLayout {
        ButtonsLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            View positive = findViewWithTag(BUTTON_POSITIVE);
            View negative = findViewWithTag(BUTTON_NEGATIVE);
            View neutral = findViewWithTag(BUTTON_NEUTRAL);
            int width = right - left;
            int y = getPaddingTop();
            if (LocaleController.isRTL) {
                int start = getPaddingLeft();
                if (positive != null) {
                    positive.layout(start, y, start + positive.getMeasuredWidth(), y + positive.getMeasuredHeight());
                    start += positive.getMeasuredWidth() + dp(8);
                }
                if (negative != null) {
                    negative.layout(start, y, start + negative.getMeasuredWidth(), y + negative.getMeasuredHeight());
                }
                if (neutral != null) {
                    int x = width - getPaddingRight() - neutral.getMeasuredWidth();
                    neutral.layout(x, y, x + neutral.getMeasuredWidth(), y + neutral.getMeasuredHeight());
                }
            } else {
                int end = width - getPaddingRight();
                if (positive != null) {
                    positive.layout(end - positive.getMeasuredWidth(), y, end, y + positive.getMeasuredHeight());
                    end -= positive.getMeasuredWidth() + dp(8);
                }
                if (negative != null) {
                    negative.layout(end - negative.getMeasuredWidth(), y, end, y + negative.getMeasuredHeight());
                }
                if (neutral != null) {
                    int x = getPaddingLeft();
                    neutral.layout(x, y, x + neutral.getMeasuredWidth(), y + neutral.getMeasuredHeight());
                }
            }
        }
    }

    public static class Builder {
        private final AlertDialog dialog;

        public Builder(Context context) {
            this(context, ALERT_TYPE_MESSAGE, null);
        }

        public Builder(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, ALERT_TYPE_MESSAGE, resourcesProvider);
        }

        public Builder(Context context, int progressViewStyle) {
            this(context, progressViewStyle, null);
        }

        public Builder(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
            dialog = new AlertDialog(context, progressViewStyle, resourcesProvider);
        }

        public Context getContext() {
            return dialog.context;
        }

        public Builder setTitle(CharSequence title) {
            dialog.currentTitle = title;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            dialog.currentMessage = message;
            return this;
        }

        public Builder setView(View view) {
            dialog.customView = view;
            return this;
        }

        public Builder setView(View view, int height) {
            dialog.customView = view;
            return this;
        }

        public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener listener) {
            dialog.positiveLabel = label;
            dialog.positiveListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence label, DialogInterface.OnClickListener listener) {
            dialog.negativeLabel = label;
            dialog.negativeListener = listener;
            return this;
        }

        public Builder setNeutralButton(CharSequence label, DialogInterface.OnClickListener listener) {
            dialog.neutralLabel = label;
            dialog.neutralListener = listener;
            return this;
        }

        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) {
            dialog.setOnCancelListener(listener);
            return this;
        }

        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            dialog.setOnDismissListener(listener);
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            dialog.setCanCancel(cancelable);
            return this;
        }

        public Builder setIcon(Drawable icon) {
            return this;
        }

        public Builder setItems(CharSequence[] items, int[] icons, DialogInterface.OnClickListener listener) {
            dialog.items = items;
            dialog.itemIcons = icons;
            dialog.itemsListener = listener;
            return this;
        }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            return setItems(items, null, listener);
        }

        public Builder setWidth(int width) {
            dialog.width = width;
            return this;
        }

        public Builder makeRed(int button) {
            dialog.redButton = button;
            return this;
        }

        public Builder setTopAnimation(int resId, int size, boolean autoRepeat, int backgroundColor) {
            return setTopAnimation(resId, size, autoRepeat, backgroundColor, null);
        }

        public Builder setTopAnimation(int resId, int size, boolean autoRepeat,
                int backgroundColor, Map<String, Integer> layerColors) {
            dialog.topAnimationId = resId;
            dialog.topAnimationSize = size;
            dialog.topAnimationAutoRepeat = autoRepeat;
            dialog.topBackgroundColor = backgroundColor;
            dialog.topAnimationLayerColors = layerColors;
            return this;
        }

        public Builder setTopAnimation(int resId, int backgroundColor) {
            return setTopAnimation(resId, 94, true, backgroundColor);
        }

        public AlertDialog create() {
            return dialog;
        }

        public AlertDialog show() {
            dialog.show();
            return dialog;
        }
    }

    private static final class ApplicationContextHolder {
        static Context get() {
            return org.telegram.messenger.ApplicationLoader.applicationContext;
        }
    }
}

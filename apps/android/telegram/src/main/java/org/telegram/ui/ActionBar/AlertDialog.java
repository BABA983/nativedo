/* NativeDo functional shim for org.telegram.ui.ActionBar.AlertDialog.
 *
 * The upstream class is ~2000 lines and cascades into BottomSheet, RLottie,
 * theme animations and dozens of other UI components, so it is not vendored.
 * This shim keeps the upstream public API the web fragments call (Builder with
 * title/message/custom view/positive+negative+neutral buttons/makeRed/width, the
 * spinner-style progress dialog, showDelayed/dismissUnless) and backs it with a
 * real androidx.appcompat AlertDialog so the dialogs actually display instead of
 * crashing. It is NativeDo code (not a Telegram copy) and therefore has no
 * vendor-manifest entry. */
package org.telegram.ui.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;

public class AlertDialog extends Dialog {

    public static final int ALERT_TYPE_MESSAGE = 0;
    public static final int ALERT_TYPE_LOADING = 3;
    public static final int ALERT_TYPE_SPINNER = 1;

    private final Context context;
    private final int progressViewStyle;

    // Backing dialog created on demand from the Builder state (or for the spinner type).
    private androidx.appcompat.app.AlertDialog delegate;
    private Runnable delegateConfigurator;
    private Runnable delegateOnShowConfigurator;

    private DialogInterface.OnDismissListener dismissListener;
    private DialogInterface.OnShowListener showListener;
    private DialogInterface.OnCancelListener cancelListener;

    private boolean dismissDialogByButtons = true;
    private int width;

    private boolean delayedShowScheduled;
    private final Runnable showDelayedRunnable = this::showInternal;
    private long shownAt;
    private CharSequence currentTitle;
    private CharSequence currentMessage;
    private ProgressBar progressBar;
    private boolean canCancel = true;

    public AlertDialog(Context context, int progressViewStyle) {
        this(context, progressViewStyle, null);
    }

    public AlertDialog(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
        super(context != null ? context : ApplicationContextHolder.get());
        this.context = context != null ? context : ApplicationContextHolder.get();
        this.progressViewStyle = progressViewStyle;
    }

    void setDelegateConfigurator(Runnable configurator) {
        this.delegateConfigurator = configurator;
    }

    void setDelegateOnShowConfigurator(Runnable configurator) {
        this.delegateOnShowConfigurator = configurator;
    }

    void setWidth(int width, boolean unused) {
        this.width = width;
    }

    private androidx.appcompat.app.AlertDialog buildDelegate() {
        androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(context);
        if (progressViewStyle == ALERT_TYPE_SPINNER || progressViewStyle == ALERT_TYPE_LOADING) {
            FrameLayout container = new FrameLayout(context);
            progressBar = new ProgressBar(context);
            int pad = AndroidUtilities.dp(24);
            container.setPadding(pad, pad, pad, pad);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            container.addView(progressBar, lp);
            b.setView(container);
            b.setCancelable(canCancel);
        }
        if (currentTitle != null) {
            b.setTitle(currentTitle);
        }
        if (currentMessage != null) {
            b.setMessage(currentMessage);
        }
        if (delegateConfigurator != null) {
            // Configurator is set by Builder.create(); it applies title/message/view/buttons.
            pendingBuilder = b;
            delegateConfigurator.run();
            pendingBuilder = null;
        }
        return b.create();
    }

    private androidx.appcompat.app.AlertDialog.Builder pendingBuilder;

    androidx.appcompat.app.AlertDialog.Builder builderForConfig() {
        return pendingBuilder;
    }

    private androidx.appcompat.app.AlertDialog ensureDelegate() {
        if (delegate == null) {
            delegate = buildDelegate();
            delegate.setOnDismissListener(d -> {
                if (dismissListener != null) {
                    dismissListener.onDismiss(d);
                }
            });
            delegate.setOnShowListener(d -> {
                shownAt = System.currentTimeMillis();
                if (delegateOnShowConfigurator != null) {
                    delegateOnShowConfigurator.run();
                }
                if (showListener != null) {
                    showListener.onShow(d);
                }
            });
            delegate.setOnCancelListener(d -> {
                if (cancelListener != null) {
                    cancelListener.onCancel(d);
                }
            });
        }
        return delegate;
    }

    private void showInternal() {
        try {
            androidx.appcompat.app.AlertDialog d = ensureDelegate();
            d.show();
            shownAt = System.currentTimeMillis();
            if (width > 0 && d.getWindow() != null) {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(d.getWindow().getAttributes());
                lp.width = width;
                d.getWindow().setAttributes(lp);
            }
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
        if (delegate != null) {
            try {
                delegate.dismiss();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public boolean isShowing() {
        return delegate != null && delegate.isShowing();
    }

    @Override
    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        dismissListener = listener;
    }

    @Override
    public void setOnShowListener(DialogInterface.OnShowListener listener) {
        showListener = listener;
    }

    @Override
    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        cancelListener = listener;
    }

    public void setMessage(CharSequence message) {
        currentMessage = message;
        if (delegate != null) {
            delegate.setMessage(message);
        }
    }

    public void setTitle(CharSequence title) {
        currentTitle = title;
        if (delegate != null) {
            delegate.setTitle(title);
        }
    }

    public void setProgress(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }

    public void setCanCancel(boolean cancel) {
        canCancel = cancel;
        if (delegate != null) {
            delegate.setCancelable(cancel);
            delegate.setCanceledOnTouchOutside(cancel);
        }
    }

    public void showDelayed(int delay) {
        delayedShowScheduled = true;
        AndroidUtilities.cancelRunOnUIThread(showDelayedRunnable);
        AndroidUtilities.runOnUIThread(showDelayedRunnable, delay);
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
            AndroidUtilities.cancelRunOnUIThread(showDelayedRunnable);
            AndroidUtilities.runOnUIThread(this::dismiss, remaining);
        }
    }

    public void setDismissDialogByButtons(boolean value) {
        dismissDialogByButtons = value;
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return null;
    }

    public static class Builder {
        private final Context context;
        private final AlertDialog dialog;

        private CharSequence title;
        private CharSequence message;
        private View view;
        private int width;

        private CharSequence positiveLabel;
        private DialogInterface.OnClickListener positiveListener;
        private CharSequence negativeLabel;
        private DialogInterface.OnClickListener negativeListener;
        private CharSequence neutralLabel;
        private DialogInterface.OnClickListener neutralListener;

        private CharSequence[] items;
        private DialogInterface.OnClickListener itemsListener;

        private boolean cancelable = true;
        private int redButton = Integer.MIN_VALUE;
        private DialogInterface.OnCancelListener cancelListener;
        private DialogInterface.OnDismissListener dismissListener;

        public Builder(Context context) {
            this(context, ALERT_TYPE_MESSAGE, null);
        }

        public Builder(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, ALERT_TYPE_MESSAGE, resourcesProvider);
        }

        public Builder(Context context, int progressViewStyle) {
            this(context, progressViewStyle, null);
        }

        private Builder(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
            this.context = context;
            this.dialog = new AlertDialog(context, progressViewStyle, resourcesProvider);
        }

        public Builder setTitle(CharSequence title) { this.title = title; return this; }
        public Builder setMessage(CharSequence message) { this.message = message; return this; }
        public Builder setView(View view) { this.view = view; return this; }
        public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener listener) {
            this.positiveLabel = label; this.positiveListener = listener; return this;
        }
        public Builder setNegativeButton(CharSequence label, DialogInterface.OnClickListener listener) {
            this.negativeLabel = label; this.negativeListener = listener; return this;
        }
        public Builder setNeutralButton(CharSequence label, DialogInterface.OnClickListener listener) {
            this.neutralLabel = label; this.neutralListener = listener; return this;
        }
        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) {
            this.cancelListener = listener; return this;
        }
        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            this.dismissListener = listener; return this;
        }
        public Builder setCancelable(boolean cancelable) { this.cancelable = cancelable; return this; }
        public Builder setIcon(Drawable icon) { return this; }
        public Builder setItems(CharSequence[] items, int[] icons, DialogInterface.OnClickListener listener) {
            this.items = items; this.itemsListener = listener; return this;
        }
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
            this.items = items; this.itemsListener = listener; return this;
        }
        public Builder makeRed(int button) { this.redButton = button; return this; }
        public Builder setWidth(int width) { this.width = width; return this; }

        public AlertDialog create() {
            dialog.setOnCancelListener(cancelListener);
            dialog.setOnDismissListener(dismissListener);
            dialog.setWidth(width, false);
            dialog.setDelegateConfigurator(() -> {
                androidx.appcompat.app.AlertDialog.Builder b = dialog.builderForConfig();
                if (b == null) {
                    return;
                }
                if (title != null) {
                    b.setTitle(title);
                }
                if (message != null) {
                    b.setMessage(message);
                }
                if (view != null) {
                    if (view.getParent() instanceof ViewGroup) {
                        ((ViewGroup) view.getParent()).removeView(view);
                    }
                    b.setView(view);
                } else if (items != null) {
                    b.setItems(items, (d, which) -> {
                        if (itemsListener != null) {
                            itemsListener.onClick(d, which);
                        }
                    });
                }
                if (positiveLabel != null) b.setPositiveButton(positiveLabel, null);
                if (negativeLabel != null) b.setNegativeButton(negativeLabel, null);
                if (neutralLabel != null) b.setNeutralButton(neutralLabel, null);
                b.setCancelable(cancelable);
            });
            dialog.setDelegateOnShowConfigurator(() -> {
                if (dialog.delegate == null) return;
                configureButton(dialog.delegate.getButton(AlertDialog.BUTTON_POSITIVE), positiveListener, AlertDialog.BUTTON_POSITIVE);
                configureButton(dialog.delegate.getButton(AlertDialog.BUTTON_NEGATIVE), negativeListener, AlertDialog.BUTTON_NEGATIVE);
                configureButton(dialog.delegate.getButton(AlertDialog.BUTTON_NEUTRAL), neutralListener, AlertDialog.BUTTON_NEUTRAL);
                if (redButton != Integer.MIN_VALUE) {
                    Button button = dialog.delegate.getButton(redButton);
                    if (button != null) button.setTextColor(0xFFE53935);
                }
            });
            return dialog;
        }

        private void configureButton(Button button, DialogInterface.OnClickListener listener, int which) {
            if (button == null) return;
            button.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClick(dialog, which);
                }
                if (dialog.dismissDialogByButtons && dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        }

        public AlertDialog show() {
            AlertDialog d = create();
            d.show();
            return d;
        }
    }

    // android.app.Dialog requires a non-null Context; fall back to the application context
    // if a dialog is constructed before its activity context is available.
    private static final class ApplicationContextHolder {
        static Context get() {
            return org.telegram.messenger.ApplicationLoader.applicationContext;
        }
    }
}

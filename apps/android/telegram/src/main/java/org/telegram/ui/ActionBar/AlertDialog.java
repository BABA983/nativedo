/* NativeDo stub of org.telegram.ui.ActionBar.AlertDialog. */
package org.telegram.ui.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;

public class AlertDialog extends Dialog {

    public static final int ALERT_TYPE_LOADING = 3;
    public static final int ALERT_TYPE_SPINNER = 1;

    public AlertDialog(Context context, int progressViewStyle) {
        super(context);
    }

    public AlertDialog(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
        super(context);
    }

    public void setMessage(CharSequence message) {}
    public void setTitle(CharSequence title) {}
    public void setProgress(int progress) {}
    public void setCanCancel(boolean cancel) {}

    public static class Builder {
        public Builder(Context context) {}
        public Builder(Context context, Theme.ResourcesProvider resourcesProvider) {}
        public Builder(Context context, int progressViewStyle) {}

        public Builder setTitle(CharSequence title) { return this; }
        public Builder setMessage(CharSequence message) { return this; }
        public Builder setView(android.view.View view) { return this; }
        public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener listener) { return this; }
        public Builder setNegativeButton(CharSequence label, DialogInterface.OnClickListener listener) { return this; }
        public Builder setNeutralButton(CharSequence label, DialogInterface.OnClickListener listener) { return this; }
        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) { return this; }
        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) { return this; }
        public Builder setCancelable(boolean cancelable) { return this; }
        public Builder setIcon(Drawable icon) { return this; }
        public Builder setItems(CharSequence[] items, int[] icons, DialogInterface.OnClickListener listener) { return this; }
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) { return this; }
        public AlertDialog create() {
            return new AlertDialog(null, 0);
        }
        public AlertDialog show() {
            AlertDialog d = create();
            return d;
        }
    }
}

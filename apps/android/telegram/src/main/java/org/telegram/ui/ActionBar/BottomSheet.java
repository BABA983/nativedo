package org.telegram.ui.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.view.View;

public class BottomSheet extends Dialog {
    public interface BottomSheetDelegate {
        default void onOpenAnimationEnd() {}
        default boolean canDismiss() { return true; }
    }

    public BottomSheet(Context context) {
        super(context);
    }

    public void setDisableScroll(boolean value) {}
    public void dismiss(boolean animated) { dismiss(); }

    public static class Builder {
        private final Context context;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence title) { return this; }
        public Builder setTitleMultipleLines(boolean value) { return this; }
        public Builder setItems(CharSequence[] items, DialogInterfaceOnClickListener listener) { return this; }
        public Builder setOnPreDismissListener(OnPreDismissListener listener) { return this; }
        public Builder setApplyTopPadding(boolean value) { return this; }
        public Builder setApplyBottomPadding(boolean value) { return this; }
        public Builder setDelegate(BottomSheetDelegate delegate) { return this; }
        public Builder setCustomView(View view) { return this; }
        public BottomSheet create() { return new BottomSheet(context); }
    }

    public interface DialogInterfaceOnClickListener {
        void onClick(Dialog dialog, int which);
    }

    public interface OnPreDismissListener {
        void onPreDismiss(Dialog dialog);
    }
}

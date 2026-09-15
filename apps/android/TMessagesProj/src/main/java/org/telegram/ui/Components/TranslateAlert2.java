package org.telegram.ui.Components;

import android.app.Activity;

import org.telegram.ui.ActionBar.BaseFragment;

public class TranslateAlert2 {
    public static void showAlert(Activity activity, BaseFragment fragment, int account, String fromLang, String toLang, CharSequence text, Object entities, boolean noforwards, Object resourcesProvider, Runnable onDismiss) {
        if (onDismiss != null) {
            onDismiss.run();
        }
    }
}

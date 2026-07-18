/* NativeDo host — AlertsCreator. */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public final class AlertsCreator {

    private AlertsCreator() {}

    public static AlertDialog showSimpleAlert(BaseFragment fragment, String message) {
        return showSimpleAlert(fragment, null, message);
    }

    public static AlertDialog showSimpleAlert(BaseFragment fragment, String title, String message) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return null;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        if (title != null) {
            b.setTitle(title);
        }
        b.setMessage(message);
        b.setPositiveButton(getString(R.string.OK), null);
        AlertDialog d = b.create();
        fragment.showDialog(d);
        return d;
    }

    public static AlertDialog showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean ask) {
        return null;
    }

    public static AlertDialog showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean ask, boolean tryTelegraph, Theme.ResourcesProvider resourcesProvider) {
        return null;
    }

    public static AlertDialog createSimpleAlert(Context context, String title, String message) {
        return null;
    }

    public static void processError(int account, org.telegram.tgnet.ConnectionsManager.TL_error error, BaseFragment fragment, org.telegram.tgnet.TLRPC.TL_channels_joinChannel request, boolean showToast) {}

    /**
     * Pin-shaped add-domain dialog for WebBrowserSettings @ 12.9.0.
     * Host: parses domain from URL text and callbacks whenDone(domain).
     */
    public static void showAddBrowserException(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            boolean inAppBrowserEnabled,
            Utilities.Callback<String> whenDone
    ) {
        if (context == null || whenDone == null) {
            return;
        }
        final Activity activity = AndroidUtilities.findActivity(context);
        final View currentFocus = activity != null ? activity.getCurrentFocus() : null;
        final AlertDialog[] dialog = new AlertDialog[1];

        final AlertDialog.Builder b = new AlertDialog.Builder(context, resourcesProvider);
        b.setTitle(getString(inAppBrowserEnabled
                ? R.string.BrowserSettingsAddTitle
                : R.string.BrowserSettingsAddTitleExternal));
        b.setMessage(getString(inAppBrowserEnabled
                ? R.string.BrowserSettingsAddText
                : R.string.BrowserSettingsAddTextExternal));

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setHint(LocaleController.getString(R.string.BrowserSettingsAddHint));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setPadding(dp(16), dp(13), dp(16), dp(13));
        editText.setCursorWidth(1.5f);
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider));

        GradientDrawable fieldBackground = new GradientDrawable();
        fieldBackground.setCornerRadius(dp(22));
        fieldBackground.setColor(Theme.multAlpha(
                Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), 0.06f));
        editText.setBackground(fieldBackground);

        final Runnable done = () -> {
            final String text = editText.getText().toString().trim();
            Uri uri = Uri.parse(text);
            if (uri == null || uri.getHost() == null) {
                uri = Uri.parse("https://" + text);
            }
            if (uri == null || uri.getHost() == null) {
                AndroidUtilities.shakeView(editText);
                return;
            }
            String domain = uri.getHost().toLowerCase();
            if (domain.startsWith("www.")) {
                domain = domain.substring(4);
            }
            whenDone.run(domain);
            if (dialog[0] != null) {
                dialog[0].dismiss();
            }
            if (currentFocus != null) {
                currentFocus.requestFocus();
            }
        };

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                done.run();
                return true;
            }
            return false;
        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 9));

        b.setView(container);
        b.setPositiveButton(LocaleController.getString(R.string.Done), (dialogInterface, i) -> done.run());
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialogInterface, i) -> dialogInterface.dismiss());

        dialog[0] = b.create();
        dialog[0].setDismissDialogByButtons(false);
        dialog[0].setOnDismissListener(d -> AndroidUtilities.hideKeyboard(editText));
        dialog[0].setOnShowListener(d -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        dialog[0].show();
    }
}

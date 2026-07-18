/* NativeDo host — AlertsCreator. */
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;

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

    public static void showOpenExternalBrowserAlert(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            String url,
            boolean currentDefaultIsInApp,
            boolean showCheckbox,
            Utilities.Callback2<Boolean, Boolean> whenDone
    ) {
        if (!AndroidUtilities.isSafeToShow(context)) {
            return;
        }

        final AlertDialog[] dialog = new AlertDialog[1];
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.OpenUrlTitle));

        final TextView urlView = new TextView(context);
        urlView.setText(url);
        urlView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        urlView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        urlView.setGravity(Gravity.CENTER);
        urlView.setMaxLines(5);
        urlView.setEllipsize(TextUtils.TruncateAt.END);
        urlView.setPadding(dp(14), dp(12), dp(14), dp(12));

        final GradientDrawable urlBackground = new GradientDrawable();
        urlBackground.setCornerRadius(dp(22));
        urlBackground.setColor(Theme.multAlpha(
                Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), 0.06f));
        urlView.setBackground(urlBackground);

        final CheckBoxCell cell = new CheckBoxCell(context, 1, resourcesProvider);
        cell.setMultiline(true);
        cell.getTextView().getLayoutParams().width = LayoutHelper.MATCH_PARENT;
        cell.getTextView().setSingleLine(false);
        cell.getTextView().setMaxLines(3);
        cell.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        cell.setText(getString(currentDefaultIsInApp
                ? R.string.BrowserAlwaysOpenExternal
                : R.string.BrowserAlwaysOpenInApp), "", false, false);
        cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(urlView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                22, 4, 22, 9));
        if (showCheckbox) {
            container.addView(cell, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT, 8, 6, 8, 4));
        }

        builder.setView(container);
        builder.setWidth(Math.min(dp(320), AndroidUtilities.displaySize.x * 85 / 100));
        builder.setPositiveButton(LocaleController.getString(R.string.Open), (di, w) -> {
            whenDone.run(true, cell.isChecked());
            if (dialog[0] != null) {
                dialog[0].dismiss();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (di, w) -> {
            whenDone.run(false, cell.isChecked());
            if (dialog[0] != null) {
                dialog[0].dismiss();
            }
        });
        dialog[0] = builder.show();
    }
}

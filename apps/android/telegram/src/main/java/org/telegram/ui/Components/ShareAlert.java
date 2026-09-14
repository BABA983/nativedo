// SPDX-License-Identifier: GPL-2.0-or-later
//
// NativeDo-authored lightweight share sheet.
//
// The upstream Telegram ShareAlert is a ~3000-line forward-to-chat grid backed by
// SendMessagesHelper / MessagesController (both compile-stubs in NativeDo), so we
// do NOT vendor it. Instead we present a minimal, self-contained bottom dialog with
// the two actions that make sense for a linux.do in-app browser page: "Copy link"
// and "Share via…" (the system ACTION_SEND chooser). Depends only on core Android +
// Theme colors, so it has no fragile vendored dependencies.
//
// This is NativeDo code, not a Telegram copy; it is listed in vendor-overlay.json
// under `native`, so provenance tracking will not treat it as an upstream copy.
// (same convention as WebBookmarks.java and the AlertDialog shim).
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class ShareAlert extends Dialog {

    private final String link;
    private final Theme.ResourcesProvider resourcesProvider;

    public ShareAlert(Context context, Object obj, String url, boolean publicChannel, String copyLink, boolean fullScreen) {
        this(context, obj, url, publicChannel, copyLink, fullScreen, null);
    }

    public ShareAlert(Context context, Object obj, String url, boolean publicChannel, String copyLink, boolean fullScreen, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.link = !TextUtils.isEmpty(copyLink) ? copyLink : url;
        this.resourcesProvider = resourcesProvider;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(buildContent(context));
        setCanceledOnTouchOutside(true);

        final Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            final WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;
            params.dimAmount = 0.5f;
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(params);
        }
    }

    private View buildContent(Context context) {
        final int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);

        final LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        final GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(bgColor);
        // rounded top corners only (bottom-anchored sheet).
        cardBg.setCornerRadii(new float[]{dp(12), dp(12), dp(12), dp(12), 0, 0, 0, 0});
        card.setBackground(cardBg);
        card.setPadding(0, dp(10), 0, dp(12));

        // drag handle
        final View handle = new View(context);
        final GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(Theme.multAlpha(iconColor, 0.35f));
        handleBg.setCornerRadius(dp(2));
        handle.setBackground(handleBg);
        final LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(8);
        card.addView(handle, handleLp);

        card.addView(makeRow(context, R.drawable.msg_copy, "Copy link", textColor, iconColor, () -> {
            copyLink();
            dismiss();
        }));
        card.addView(makeRow(context, R.drawable.msg_share, "Share via…", textColor, iconColor, () -> {
            shareVia();
            dismiss();
        }));

        return card;
    }

    private View makeRow(Context context, int iconRes, String label, int textColor, int iconColor, Runnable onClick) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), 0, dp(20), 0);
        row.setMinimumHeight(dp(52));
        row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider)));

        final ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        final TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setSingleLine(true);
        final LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(18);
        row.addView(tv, tlp);

        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private void copyLink() {
        if (TextUtils.isEmpty(link)) {
            return;
        }
        try {
            final ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("link", link));
            }
            Toast.makeText(getContext(), "Link copied", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            FileLogSafe(e);
        }
    }

    private void shareVia() {
        if (TextUtils.isEmpty(link)) {
            return;
        }
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, link);
            final Intent chooser = Intent.createChooser(intent, "Share via…");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
        } catch (Exception e) {
            FileLogSafe(e);
        }
    }

    private static void FileLogSafe(Throwable t) {
        try {
            org.telegram.messenger.FileLog.e(t);
        } catch (Throwable ignore) {}
    }
}

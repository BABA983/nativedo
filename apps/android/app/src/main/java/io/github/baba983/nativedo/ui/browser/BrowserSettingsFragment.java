/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/web/WebBrowserSettings.java.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalCells;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.web.RestrictedDomainsList;
import org.telegram.ui.web.SearchEngine;

class BrowserSettingsFragment extends UniversalFragment {

    private static final int BUTTON_TOGGLE = 1;
    private static final int BUTTON_CLEAR_CACHE = 2;
    private static final int BUTTON_CLEAR_COOKIES = 3;
    private static final int BUTTON_ADD_DOMAIN = 4;
    private static final int BUTTON_CLEAR_LIST = 5;
    private static final int BUTTON_SEARCH_ENGINE = 6;
    private static final int BUTTON_CLEAR_HISTORY = 7;
    private static final int BUTTON_OPEN_HISTORY = 9;
    private static final int BUTTON_DOMAIN = 10;

    private final BrowserFeatureSheet.UrlLoader urlLoader;

    private static final ExecutorService SIZE_EXECUTOR = Executors.newSingleThreadExecutor();

    private long cacheSize = -1L;
    private long cookiesSize = -1L;
    private boolean sizesLoading;

    BrowserSettingsFragment(BrowserFeatureSheet.UrlLoader urlLoader) {
        this.urlLoader = urlLoader;
    }

    @Override
    protected CharSequence getTitle() {
        Context context = getContext();
        return context == null ? "" : context.getString(R.string.app_browser_settings);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean inAppBrowser = BrowserStorage.openExternalLinksInApp(context);
        items.add(UItem.asCheck(BUTTON_TOGGLE, context.getString(R.string.app_browser_settings_enable_title)).setChecked(inAppBrowser));
        items.add(UItem.asShadow(context.getString(R.string.app_browser_settings_enable_subtitle)));

        if (!inAppBrowser) {
            return;
        }

        ensureSizesLoaded();

        items.add(UItem.asButton(
                BUTTON_CLEAR_COOKIES,
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.app_browser_settings_clear_cookies_title),
                cookiesSize > 0 ? Formatter.formatShortFileSize(context, cookiesSize) : ""
        ));
        items.add(UItem.asButton(
                BUTTON_CLEAR_CACHE,
                android.R.drawable.ic_menu_manage,
                context.getString(R.string.app_browser_settings_clear_cache_title),
                cacheSize > 0 ? Formatter.formatShortFileSize(context, cacheSize) : ""
        ));
        items.add(UItem.asShadow(context.getString(R.string.app_browser_settings_cookies_cache_subtitle)));

        int historySize = BrowserStorage.getHistory(context).size();
        if (historySize > 0) {
            items.add(UItem.asButton(BUTTON_OPEN_HISTORY, android.R.drawable.ic_menu_recent_history, context.getString(R.string.app_browser_settings_history_show)));
            items.add(UItem.asButton(
                    BUTTON_CLEAR_HISTORY,
                    android.R.drawable.ic_menu_delete,
                    context.getString(R.string.app_browser_settings_clear_history_title),
                    context.getResources().getQuantityString(R.plurals.app_browser_settings_history_pages, historySize, historySize)
            ));
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asHeader(context.getString(R.string.app_browser_settings_restricted_domains_title)));
        ArrayList<String> domains = RestrictedDomainsList.getInstance().getRestrictedDomainsList();
        items.add(UItem.asButton(
                BUTTON_ADD_DOMAIN,
                android.R.drawable.ic_input_add,
                context.getString(R.string.app_browser_settings_add_restricted_domain),
                context.getString(R.string.app_browser_settings_restricted_domains_count, domains.size())
        ).accent());
        for (String domain : domains) {
            ArrayList<String> rowDomains = new ArrayList<>();
            rowDomains.add(domain);
            BrowserMetadataCache.Metadata meta = BrowserMetadataCache.get(context, domain);
            CharSequence title;
            android.graphics.Bitmap favicon;
            if (meta != null) {
                title = !TextUtils.isEmpty(meta.siteName)
                        ? meta.siteName
                        : (TextUtils.isEmpty(meta.title) ? "" : meta.title);
                favicon = meta.favicon;
            } else {
                title = "";
                favicon = null;
            }
            items.add(UItem.asWebsite(BUTTON_DOMAIN, title, rowDomains, favicon));
        }
        if (!domains.isEmpty()) {
            items.add(UItem.asButton(BUTTON_CLEAR_LIST, android.R.drawable.ic_menu_delete, context.getString(R.string.app_browser_settings_clear_list)).red());
        }
        items.add(UItem.asShadow(context.getString(R.string.app_browser_settings_restricted_domains_subtitle)));

        items.add(UItem.asButton(
                BUTTON_SEARCH_ENGINE,
                android.R.drawable.ic_menu_search,
                context.getString(R.string.app_browser_settings_search_engine_title),
                labelForEngine(BrowserStorage.getSearchEngine(context))
        ));
        items.add(UItem.asShadow(context.getString(R.string.app_browser_settings_search_engine_subtitle)));
    }

    /**
     * Mirrors WebBrowserSettings.loadSizes(): walks WebView cookie/cache directories on a
     * background thread so the Settings rows can render real sizes via Formatter.formatShortFileSize.
     * Telegram uses Utilities.globalQueue + AndroidUtilities.runOnUIThread; NativeDo uses a single
     * Executor + AndroidUtilities.runOnUIThread.
     */
    private void ensureSizesLoaded() {
        if (sizesLoading || cacheSize >= 0L) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        sizesLoading = true;
        Context appContext = context.getApplicationContext();
        SIZE_EXECUTOR.execute(() -> {
            long localCacheSize = 0L;
            File webviewDb = appContext.getDatabasePath("webview.db");
            if (webviewDb != null && webviewDb.exists()) {
                localCacheSize += webviewDb.length();
            }
            File webviewCacheDb = appContext.getDatabasePath("webviewCache.db");
            if (webviewCacheDb != null && webviewCacheDb.exists()) {
                localCacheSize += webviewCacheDb.length();
            }
            File appWebview = new File(appContext.getApplicationInfo().dataDir, "app_webview");
            if (appWebview.exists()) {
                localCacheSize += getDirectorySize(appWebview, false);
            }
            File webViewCache = new File(appContext.getApplicationInfo().dataDir, "cache/WebView");
            if (webViewCache.exists()) {
                localCacheSize += getDirectorySize(webViewCache, null);
            }

            long localCookiesSize = 0L;
            if (appWebview.exists()) {
                localCookiesSize += getDirectorySize(appWebview, true);
            }

            final long finalCacheSize = localCacheSize;
            final long finalCookiesSize = localCookiesSize;
            AndroidUtilities.runOnUIThread(() -> {
                sizesLoading = false;
                cacheSize = finalCacheSize;
                cookiesSize = finalCookiesSize;
                updateRows();
            });
        });
    }

    /**
     * Mirrors WebBrowserSettings.getDirectorySize(File, Boolean): cookies==null counts every file,
     * cookies==true keeps only files whose name starts with "Cookies", cookies==false skips them.
     */
    private static long getDirectorySize(@Nullable File dir, @Nullable Boolean cookies) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        long size = 0L;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File child : files) {
                    size += getDirectorySize(child, cookies);
                }
            }
        } else {
            if (cookies != null && cookies != dir.getName().startsWith("Cookies")) {
                return size;
            }
            size += dir.length();
        }
        return size;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        switch (item.id) {
            case BUTTON_TOGGLE:
                BrowserStorage.setOpenExternalLinksInApp(context, !BrowserStorage.openExternalLinksInApp(context));
                updateRows();
                break;
            case BUTTON_OPEN_HISTORY:
                openHistory();
                break;
            case BUTTON_CLEAR_HISTORY:
                showConfirmDialog(
                        context.getString(R.string.app_browser_settings_clear_history_title),
                        context.getString(R.string.app_browser_settings_clear_history_confirm),
                        context.getString(R.string.app_browser_settings_clear_action),
                        () -> {
                            BrowserStorage.clearHistory(context);
                            updateRows();
                        }
                );
                break;
            case BUTTON_CLEAR_COOKIES:
                showConfirmDialog(
                        context.getString(R.string.app_browser_settings_clear_cookies_title),
                        context.getString(R.string.app_browser_settings_clear_cookies_confirm),
                        context.getString(R.string.app_browser_settings_clear_action),
                        () -> {
                            clearCookies();
                            invalidateSizes();
                            updateRows();
                        }
                );
                break;
            case BUTTON_CLEAR_CACHE:
                showConfirmDialog(
                        context.getString(R.string.app_browser_settings_clear_cache_title),
                        context.getString(R.string.app_browser_settings_clear_cache_confirm),
                        context.getString(R.string.app_browser_settings_clear_action),
                        () -> {
                            clearCache();
                            invalidateSizes();
                            updateRows();
                        }
                );
                break;
            case BUTTON_ADD_DOMAIN:
                showAddDomainDialog();
                break;
            case BUTTON_CLEAR_LIST:
                RestrictedDomainsList.getInstance().clearRestrictedDomains();
                updateRows();
                break;
            case BUTTON_DOMAIN:
                String domain = item.textValue == null ? null : item.textValue.toString();
                showDomainOptions(view, TextUtils.isEmpty(domain) ? null : () -> {
                    RestrictedDomainsList.getInstance().removeRestrictedDomain(domain);
                    updateRows();
                });
                break;
            case BUTTON_SEARCH_ENGINE:
                showSearchEngineDialog();
                break;
            default:
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite)) > .721f;
    }

    private void updateRows() {
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void invalidateSizes() {
        cacheSize = -1L;
        cookiesSize = -1L;
        sizesLoading = false;
    }

    private void openHistory() {
        final BrowserListFragment[] fragment = new BrowserListFragment[1];
        fragment[0] = new BrowserListFragment(BrowserListFragment.Mode.HISTORY, entry -> {
            closePushedFragment();
            finishFragment();
            if (entry != null && urlLoader != null) {
                urlLoader.load(entry.url);
            }
        });
        presentFragment(fragment[0]);
    }

    private void closePushedFragment() {
        if (parentLayout instanceof ActionBarLayout && parentLayout.getFragmentStack().size() > 1) {
            ((ActionBarLayout) parentLayout).closeLastFragment(false, true);
        }
    }

    private String labelForEngine(SearchEngine engine) {
        return engine.name;
    }

    private void clearCookies() {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } catch (Exception ignored) {
        }
        try {
            Context context = getContext();
            if (context != null) {
                File dir = new File(context.getApplicationInfo().dataDir, "app_webview");
                deleteDirectory(dir, true);
            }
        } catch (Exception ignored) {
        }
    }

    private void clearCache() {
        Context context = getContext();
        try {
            WebStorage.getInstance().deleteAllData();
            WebView webView = new WebView(context);
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
        } catch (Exception ignored) {
        }
        try {
            File dir = new File(context.getApplicationInfo().dataDir, "app_webview");
            deleteDirectory(dir, false);
        } catch (Exception ignored) {
        }
        try {
            File dir = new File(context.getApplicationInfo().dataDir, "cache/WebView");
            deleteDirectory(dir, null);
        } catch (Exception ignored) {
        }
        BrowserMetadataCache.clear(context);
    }

    private void deleteDirectory(@Nullable File file, @Nullable Boolean cookies) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child, cookies);
                }
            }
            File[] remaining = file.listFiles();
            if (remaining == null || remaining.length == 0) {
                file.delete();
            }
            return;
        }
        if (cookies != null && cookies != file.getName().startsWith("Cookies")) {
            return;
        }
        file.delete();
    }

    private void showAddDomainDialog() {
        Context context = getContext();
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        EditText input = new EditText(context);
        input.setHint(R.string.app_browser_settings_add_restricted_domain_hint);
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackground(createDialogInputBackground());
        input.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(12));
        content.setBackground(createDialogBackground());

        TextView title = new TextView(context);
        title.setText(R.string.app_browser_settings_add_restricted_domain);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.app_browser_settings_add_restricted_domain_subtitle);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        subtitleLp.topMargin = AndroidUtilities.dp(8);
        content.addView(subtitle, subtitleLp);

        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(50));
        inputLp.topMargin = AndroidUtilities.dp(18);
        content.addView(input, inputLp);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(56));
        buttonsLp.topMargin = AndroidUtilities.dp(8);
        content.addView(buttons, buttonsLp);

        buttons.addView(createDialogButton(context, context.getString(android.R.string.cancel), false, dialog::dismiss));
        buttons.addView(createDialogButton(context, context.getString(R.string.app_browser_address_submit), true, () -> {
            String domain = RestrictedDomainsList.normalizeRestrictedDomain(input.getText().toString());
            if (TextUtils.isEmpty(domain)) {
                input.setError(context.getString(R.string.app_browser_settings_add_restricted_domain_invalid));
                return;
            }
            RestrictedDomainsList.getInstance().addRestrictedDomain(domain);
            dialog.dismiss();
            updateRows();
        }));

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                buttons.getChildAt(1).performClick();
                return true;
            }
            return false;
        });

        dialog.setContentView(content);
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setDimAmount(0.42f);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setLayout(
                Math.min(context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(48), AndroidUtilities.dp(336)),
                WindowManager.LayoutParams.WRAP_CONTENT
        );
        input.requestFocus();
    }

    private void showConfirmDialog(String title, String message, String actionText, Runnable onConfirm) {
        Context context = getContext();
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(12));
        content.setBackground(createDialogBackground());

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(titleView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        messageLp.topMargin = AndroidUtilities.dp(10);
        content.addView(messageView, messageLp);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(56));
        buttonsLp.topMargin = AndroidUtilities.dp(12);
        content.addView(buttons, buttonsLp);

        buttons.addView(createDialogButton(context, context.getString(android.R.string.cancel), false, dialog::dismiss));
        buttons.addView(createDialogButton(context, actionText, true, () -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        }));

        dialog.setContentView(content);
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setDimAmount(0.42f);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setLayout(
                Math.min(context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(48), AndroidUtilities.dp(336)),
                WindowManager.LayoutParams.WRAP_CONTENT
        );
    }

    private GradientDrawable createDialogInputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        drawable.setCornerRadius(AndroidUtilities.dp(12));
        drawable.setStroke(AndroidUtilities.dp(1), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 210));
        return drawable;
    }

    private GradientDrawable createDialogBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        drawable.setCornerRadius(AndroidUtilities.dp(22));
        return drawable;
    }

    private TextView createDialogButton(Context context, String text, boolean primary, Runnable onClick) {
        int primaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        TextView textView = new TextView(context);
        textView.setText(text.toUpperCase());
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(primary ? primaryColor : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        textView.setMinWidth(AndroidUtilities.dp(64));
        textView.setMinHeight(AndroidUtilities.dp(40));
        textView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        textView.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(primaryColor, primary ? 28 : 18), Theme.RIPPLE_MASK_ALL));
        textView.setOnClickListener(v -> onClick.run());
        return textView;
    }

    private void showSearchEngineDialog() {
        Context context = getContext();
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(8));
        content.setBackground(createDialogBackground());

        TextView titleView = new TextView(context);
        titleView.setText(R.string.app_browser_settings_search_engine_title);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), AndroidUtilities.dp(8));
        content.addView(titleView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        SearchEngine current = BrowserStorage.getSearchEngine(context);
        SearchEngine[] engines = BrowserStorage.getSearchEngines();
        for (int i = 0; i < engines.length; i++) {
            SearchEngine engine = engines[i];
            UniversalCells.RadioCell cell = new UniversalCells.RadioCell(context);
            cell.bind(labelForEngine(engine), null, engine == current, i != engines.length - 1);
            cell.setOnClickListener(v -> {
                BrowserStorage.setSearchEngine(context, engine);
                dialog.dismiss();
                updateRows();
            });
            content.addView(cell, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        dialog.setContentView(content);
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setDimAmount(0.42f);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.getWindow().setLayout(
                Math.min(context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(48), AndroidUtilities.dp(336)),
                WindowManager.LayoutParams.WRAP_CONTENT
        );
    }

    private void showDomainOptions(View anchor, @Nullable Runnable removeAction) {
        if (!(fragmentView instanceof ViewGroup) || anchor == null || removeAction == null) {
            return;
        }
        ItemOptions.makeOptions((ViewGroup) fragmentView, anchor)
                .add(getContext().getString(R.string.app_browser_settings_remove_restricted_domain), removeAction)
                .setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
                .show();
    }
}

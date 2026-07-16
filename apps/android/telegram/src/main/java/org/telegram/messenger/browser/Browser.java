/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.browser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.LaunchActivity;

import java.net.IDN;
import java.util.List;
import java.util.regex.Pattern;

public class Browser {

    // --- CustomTabs session management (deferred — no support.customtabs stubs yet) ---

    public static void bindCustomTabsService(Activity activity) {
        // CustomTabs support deferred; no-op until support.customtabs stubs exist.
    }

    public static void unbindCustomTabsService(Activity activity) {
        // CustomTabs support deferred; no-op until support.customtabs stubs exist.
    }

    // --- URL opening ---

    public static void openUrl(Context context, String url) {
        if (url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), true);
    }

    public static void openUrl(Context context, Uri uri) {
        openUrl(context, uri, true);
    }

    public static void openUrl(Context context, String url, boolean allowCustom) {
        if (context == null || url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), allowCustom);
    }

    public static void openUrl(final Context context, final String url, final boolean allowCustom, boolean tryTelegraph) {
        if (context == null || url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), allowCustom, tryTelegraph);
    }

    public static void openUrl(Context context, Uri uri, boolean allowCustom) {
        openUrl(context, uri, allowCustom, true);
    }

    public static void openUrl(Context context, Uri uri, boolean allowCustom, boolean tryTelegraph) {
        openUrl(context, uri, allowCustom, tryTelegraph, false, null, null, false, true, false);
    }

    /**
     * Main URL dispatch. Simplified from upstream: no CustomTabs, no Telegram internal routing,
     * no Telegraph/Instant View. Falls through to external browser via ACTION_VIEW.
     */
    public static void openUrl(final Context context, Uri uri, boolean _allowCustom, boolean tryTelegraph, boolean forceNotInternalForApps, Progress inCaseLoading, String browser, boolean allowIntent, boolean allowInAppBrowser, boolean forceRequest) {
        if (context == null || uri == null) {
            return;
        }
        String browserPackage = getBrowserPackageName(browser);
        boolean isHttpOrHttps = false;
        try {
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
            if (scheme != null && scheme.contains(".")) {
                return;
            }
            isHttpOrHttps = "http".equals(scheme) || "https".equals(scheme);
            if (isHttpOrHttps) {
                try {
                    uri = uri.normalizeScheme();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (isHttpOrHttps && allowInAppBrowser && SharedConfig.inappBrowser && TextUtils.isEmpty(browserPackage)) {
            Progress progress = inCaseLoading != null ? inCaseLoading : new Progress();
            if (openInTelegramBrowser(context instanceof Activity ? (Activity) context : null, uri.toString(), progress)) {
                return;
            }
        }
        // NativeDo keeps CustomTabs and Telegram internal routing deferred. If the
        // Telegram in-app browser cannot handle the URL, fall back to ACTION_VIEW.
        try {
            openInExternalBrowser(context, uri.toString(), allowIntent, browserPackage);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // --- Progress class (full upstream implementation) ---

    public static class Progress {

        private Runnable onInitListener;
        private Runnable onCancelListener;
        private Runnable onEndListener;

        public Progress() {

        }

        public Progress(Runnable init, Runnable end) {
            this.onInitListener = init;
            this.onEndListener = end;
        }

        public void init() {
            if (onInitListener != null) {
                onInitListener.run();
                onInitListener = null;
            }
        }
        public void end() {
            end(false);
        }
        public void end(boolean replaced) {
            if (onEndListener != null) {
                onEndListener.run();
            }
        }

        public void cancel() {
            cancel(false);
        }
        public void cancel(boolean replaced) {
            if (onCancelListener != null) {
                onCancelListener.run();
            }
            end(replaced);
        }

        public Progress onCancel(Runnable onCancelListener) {
            this.onCancelListener = onCancelListener;
            return this;
        }

        public Progress onEnd(Runnable onEndListener) {
            this.onEndListener = onEndListener;
            return this;
        }
    }

    // --- Telegram-specific stubs (return false / no-op for NativeDo) ---

    public static boolean isTelegraphUrl(String url, boolean equals) {
        return false;
    }

    public static boolean isTelegraphUrl(String url, boolean equals, boolean forceHttps) {
        return false;
    }

    public static String extractUsername(String link) {
        return null;
    }

    public static boolean urlMustNotHaveConfirmation(String url) {
        return false;
    }

    /**
     * NativeDo is a browser app — no URL is "internal" to us.
     */
    public static boolean isInternalUrl(String url, boolean[] forceBrowser) {
        return false;
    }

    public static boolean isInternalUrl(String url, boolean all, boolean[] forceBrowser) {
        return false;
    }

    public static boolean isPassportUrl(String url) {
        return false;
    }

    public static boolean isTMe(String url) {
        return false;
    }

    public static boolean isInternalUri(Uri uri, boolean[] forceBrowser) {
        return false;
    }

    public static boolean isInternalUri(Uri uri, boolean all, boolean[] forceBrowser) {
        return false;
    }

    /** Telegram-style in-app browser entry backed by the current NativeDo ActionBarLayout. */
    public static boolean openInTelegramBrowser(Activity activity, String url, Object progress) {
        if (LaunchActivity.instance != null) {
            BottomSheetTabs tabs = LaunchActivity.instance.getBottomSheetTabs();
            if (tabs != null && tabs.tryReopenTab(url) != null) {
                return true;
            }
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || TextUtils.isEmpty(url)) {
            return false;
        }
        Progress browserProgress = progress instanceof Progress ? (Progress) progress : null;
        return fragment.createArticleViewer(false).open(url, browserProgress);
    }

    /** Telegram-specific: route URL through LaunchActivity as internal intent. No-op for NativeDo. */
    public static boolean openAsInternalIntent(Context context, String url) {
        return false;
    }

    public static boolean openAsInternalIntent(Context context, String url, boolean forceNotInternalForApps) {
        return false;
    }

    public static boolean openAsInternalIntent(Context context, String url, boolean forceNotInternalForApps, boolean forceRequest, Progress progress) {
        return false;
    }

    // --- Tonsite stubs (TON blockchain — not applicable to NativeDo) ---

    public static boolean isTonsite(String url) {
        return false;
    }

    private static Pattern domainPattern;
    public static boolean isTonsitePunycode(String url) {
        return false;
    }

    // --- Real implementations ---

    /**
     * Open a URL in an external browser. Real implementation from upstream.
     */
    public static boolean openInExternalBrowser(Context context, String url, boolean allowIntent) {
        return openInExternalBrowser(context, url, allowIntent, null);
    }

    public static boolean openInExternalBrowser(Context context, String url, boolean allowIntent, String browser) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            final boolean isIntentScheme = uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("intent");
            if (isIntentScheme && !allowIntent) return false;
            final Intent intent = isIntentScheme ?
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) :
                    new Intent(Intent.ACTION_VIEW, uri);
            if (!TextUtils.isEmpty(browser)) {
                intent.setPackage(browser);
            }
            intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
            intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.getPackageName());
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    /**
     * Open a URL in an external (non-browser) app if one is available.
     * Real implementation from upstream.
     */
    public static boolean openInExternalApp(Context context, String url, boolean allowIntent) {
        if (url == null) return false;
        try {
            if (isTonsite(url) || isInternalUrl(url, null)) return false;
            Uri uri = Uri.parse(url);
            url = Browser.replace(
                uri,
                uri.getScheme() == null ? "https" : uri.getScheme(),
                    null, uri.getHost() != null ? uri.getHost().toLowerCase() : uri.getHost(),
                TextUtils.isEmpty(uri.getPath()) ? "/" : uri.getPath()
            );
            uri = Uri.parse(url);
            final boolean isIntentScheme = url.startsWith("intent://") || uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("intent");
            if (isIntentScheme && !allowIntent) return false;
            final Intent intent = isIntentScheme ?
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) :
                new Intent(Intent.ACTION_VIEW, uri);
            if (!isIntentScheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
            } else if (!isIntentScheme && !hasAppToOpen(context, url)) {
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            FileLog.e(e, false);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    /**
     * Check whether a non-browser app is available to handle the given URL.
     * Real implementation from upstream.
     */
    public static boolean hasAppToOpen(Context context, String url) {
        if (url == null) return false;

        String[] browserPackageNames = null;
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
            List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(browserIntent, 0);
            if (list != null && !list.isEmpty()) {
                browserPackageNames = new String[list.size()];
                for (int a = 0; a < list.size(); a++) {
                    browserPackageNames[a] = list.get(a).activityInfo.packageName;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("default browser name = " + browserPackageNames[a]);
                    }
                }
            }
        } catch (Exception ignore) {}

        List<ResolveInfo> allActivities = null;
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            allActivities = context.getPackageManager().queryIntentActivities(viewIntent, 0);
            if (browserPackageNames != null) {
                for (int a = 0; a < allActivities.size(); a++) {
                    for (int b = 0; b < browserPackageNames.length; b++) {
                        if (browserPackageNames[b].equals(allActivities.get(a).activityInfo.packageName)) {
                            allActivities.remove(a);
                            a--;
                            break;
                        }
                    }
                }
            } else {
                for (int a = 0; a < allActivities.size(); a++) {
                    final String packageName = allActivities.get(a).activityInfo.packageName.toLowerCase();
                    if (isBrowserPackageName(packageName)) {
                        allActivities.remove(a);
                        a--;
                    }
                }
            }
            if (BuildVars.LOGS_ENABLED) {
                for (int a = 0; a < allActivities.size(); a++) {
                    FileLog.d("device has " + allActivities.get(a).activityInfo.packageName + " to open " + url);
                }
            }
        } catch (Exception ignore) {}

        return allActivities != null && !allActivities.isEmpty();
    }

    /**
     * Map common browser names to their Android package names.
     * Real implementation from upstream.
     */
    public static String getBrowserPackageName(String browser) {
        if (browser == null) return null;
        switch (browser) {
            case "google-chrome":
            case "chrome":
                return "com.android.chrome";
            case "mozilla-firefox":
            case "firefox":
                return "org.mozilla.firefox";
            case "microsoft-edge":
            case "edge":
                return "com.microsoft.emmx";
            case "opera":
                return "com.opera.browser";
            case "opera-mini":
                return "com.opera.mini.native";
            case "brave":
            case "brave-browser":
                return "com.brave.browser";
            case "duckduckgo":
            case "duckduckgo-browser":
                return "com.duckduckgo.mobile.android";
            case "samsung":
            case "samsung-browser":
                return "com.sec.android.app.sbrowser";
            case "vivaldi":
            case "vivaldi-browser":
                return "com.vivaldi.browser";
            case "kiwi":
            case "kiwi-browser":
                return "com.kiwibrowser.browser";
            case "uc":
            case "uc-browser":
                return "com.UCMobile.intl";
            case "tor":
            case "tor-browser":
                return "org.torproject.torbrowser";
        }
        return null;
    }

    /**
     * Heuristic check whether a package name belongs to a web browser.
     * Real implementation from upstream.
     */
    public static boolean isBrowserPackageName(String name) {
        return name != null && (
            name.contains("browser") ||
            name.contains("chrome") ||
            name.contains("firefox") ||
            "com.microsoft.emmx".equals(name) ||
            "com.opera.mini.native".equals(name) ||
            "com.duckduckgo.mobile.android".equals(name) ||
            "com.UCMobile.intl".equals(name)
        );
    }

    /**
     * Check whether a punycode hostname is allowed to be decoded to Unicode.
     * Real implementation from upstream.
     */
    public static boolean isPunycodeAllowed(String host) {
        if (host == null) return true;
        String[] levels = host.split("\\.");
        if (levels.length <= 0) return true;
        String topLevel = levels[levels.length - 1];
        return topLevel.startsWith("xn--");
    }

    /**
     * Convert an IDN hostname to Unicode display form, with punycode safety check.
     * Real implementation from upstream.
     */
    public static String IDN_toUnicode(String host) {
        try {
            host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (!isPunycodeAllowed(host)) return host;
        try {
            host = IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return host;
    }

    // --- URI manipulation (real implementations from upstream) ---

    public static String replaceHostname(Uri originalUri, String newHostname, String newScheme) {
        return replace(originalUri, newScheme, null, newHostname, null);
    }

    public static String replace(Uri originalUri, String newScheme, String newUserInfo, String newHostname, String newPath) {
        final StringBuilder modifiedUriBuilder = new StringBuilder();
        final String scheme = newScheme == null ? originalUri.getScheme() : newScheme;
        if (scheme != null) {
            modifiedUriBuilder.append(scheme).append("://");
        }
        if (newUserInfo == null) {
            if (originalUri.getUserInfo() != null) {
                modifiedUriBuilder.append(originalUri.getUserInfo()).append("@");
            }
        } else if (!TextUtils.isEmpty(newUserInfo)) {
            modifiedUriBuilder.append(newUserInfo).append("@");
        }
        if (newHostname == null) {
            if (originalUri.getHost() != null) {
                modifiedUriBuilder.append(originalUri.getHost());
            }
        } else {
            modifiedUriBuilder.append(newHostname);
        }
        if (originalUri.getPort() != -1) {
            modifiedUriBuilder.append(":").append(originalUri.getPort());
        }
        if (newPath != null) {
            modifiedUriBuilder.append(newPath);
        } else if (originalUri.getPath() != null) {
            modifiedUriBuilder.append(originalUri.getPath());
        }
        if (originalUri.getQuery() != null) {
            modifiedUriBuilder.append("?").append(originalUri.getQuery());
        }
        if (originalUri.getFragment() != null) {
            modifiedUriBuilder.append("#").append(originalUri.getFragment());
        }
        return modifiedUriBuilder.toString();
    }

}

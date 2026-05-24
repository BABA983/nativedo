/* NativeDo stub of org.telegram.messenger.browser.Browser. */
package org.telegram.messenger.browser;

import android.content.Context;
import android.net.Uri;

public class Browser {
    public static void openUrl(Context context, String url) {
        openUrl(context, url, true);
    }
    public static void openUrl(Context context, Uri uri) {}
    public static void openUrl(Context context, String url, boolean allowCustom) {}
    public static void openUrl(Context context, String url, boolean allowCustom, boolean tryTelegraph) {}
    public static boolean isInternalUrl(String url, boolean[] forceBrowser) { return false; }
    public static boolean isInternalUri(Uri uri, boolean[] forceBrowser) { return false; }
}

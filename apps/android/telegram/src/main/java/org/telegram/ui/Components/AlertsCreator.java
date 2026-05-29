/* NativeDo stub — AlertsCreator. */
package org.telegram.ui.Components;

import android.content.Context;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.BaseFragment;

public final class AlertsCreator {

    private AlertsCreator() {}

    public static AlertDialog showSimpleAlert(BaseFragment fragment, String message) { return null; }
    public static AlertDialog showSimpleAlert(BaseFragment fragment, String title, String message) { return null; }
    public static AlertDialog showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean ask) { return null; }
    public static AlertDialog showOpenUrlAlert(BaseFragment fragment, String url, boolean punycode, boolean ask, boolean tryTelegraph, Theme.ResourcesProvider resourcesProvider) { return null; }
    public static AlertDialog createSimpleAlert(Context context, String title, String message) { return null; }
    public static void processError(int account, org.telegram.tgnet.ConnectionsManager.TL_error error, BaseFragment fragment, org.telegram.tgnet.TLRPC.TL_channels_joinChannel request, boolean showToast) {}
}

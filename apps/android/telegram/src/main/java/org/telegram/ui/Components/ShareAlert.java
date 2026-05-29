package org.telegram.ui.Components;

import android.app.Dialog;
import android.content.Context;

public class ShareAlert extends Dialog {
    public ShareAlert(Context context, Object obj, String url, boolean publicChannel, String copyLink, boolean fullScreen) {
        super(context);
    }

    public ShareAlert(Context context, Object obj, String url, boolean publicChannel, String copyLink, boolean fullScreen, Object resourcesProvider) {
        super(context);
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.widget.LinearLayout;

/**
 * Minimal host stub for RecyclerListView section geometry.
 * Upstream JoinToSendSettingsView is Telegram channel join-permission UI;
 * NativeDo only needs {@link #getBottomInfoMargin()} when such a row is present.
 */
public class JoinToSendSettingsView extends LinearLayout {
    public JoinToSendSettingsView(Context context) {
        super(context);
    }

    /** Extra bottom inset for section background under this row (upstream default). */
    public float getBottomInfoMargin() {
        return 0f;
    }
}

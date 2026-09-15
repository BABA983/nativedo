/* NativeDo stub — DialogsActivity. */
package org.telegram.ui;

import android.os.Bundle;

import org.telegram.ui.ActionBar.BaseFragment;

public class DialogsActivity extends BaseFragment {

    public static final int DIALOGS_TYPE_DEFAULT = 0;
    public static final int DIALOGS_TYPE_FORWARD = 3;
    public static final int DIALOGS_TYPE_USERS_ONLY = 4;
    public static final int DIALOGS_TYPE_CHANNELS_ONLY = 5;
    public static final int DIALOGS_TYPE_GROUPS_ONLY = 6;
    public static final int DIALOGS_TYPE_BLOCK = 7;
    public static final int DIALOGS_TYPE_IMPORT = 13;

    public DialogsActivity(Bundle args) {}
    public DialogsActivity() {}

    public interface DialogsActivityDelegate {
        boolean didSelectDialogs(DialogsActivity fragment, java.util.ArrayList<Long> dids, CharSequence message, boolean param, java.util.List<?> topics);
    }

    public void setDelegate(DialogsActivityDelegate delegate) {}
}

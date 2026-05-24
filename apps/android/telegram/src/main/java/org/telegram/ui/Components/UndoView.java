/* NativeDo stub of org.telegram.ui.Components.UndoView. */
package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;

public class UndoView extends FrameLayout {
    public UndoView(Context context) {
        super(context);
    }

    public void showWithAction(long did, int action, Object infoObject) {}
    public void showWithAction(long did, int action, Object infoObject, Runnable actionRunnable) {}
    public void showWithAction(long did, int action, Object infoObject, Runnable actionRunnable, Runnable cancelRunnable) {}
    public void showWithAction(long did, int action, Runnable actionRunnable) {}
    public void hide(boolean apply, int animated) {}
    public boolean isShown() { return false; }
}

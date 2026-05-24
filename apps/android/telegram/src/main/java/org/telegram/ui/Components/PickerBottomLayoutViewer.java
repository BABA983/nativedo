/* NativeDo stub — picker bottom bar in photo picker. */
package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;

public class PickerBottomLayoutViewer extends FrameLayout {
    public android.widget.TextView cancelButton;
    public android.widget.TextView doneButton;
    public android.widget.TextView doneButtonBadgeTextView;
    public android.widget.TextView doneButtonTextView;

    public PickerBottomLayoutViewer(Context context) { super(context); }
    public PickerBottomLayoutViewer(Context context, boolean darkTheme) { super(context); }
    public void updateSelectedCount(int count, boolean disable) {}
    public void doneButtonClicked() {}
    public void cancelButtonClicked() {}
}

/* NativeDo stub — Cells/CheckBoxCell. */
package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.Theme;

public class CheckBoxCell extends FrameLayout {
    public CheckBoxCell(Context context) { super(context); }
    public CheckBoxCell(Context context, int type) { super(context); }
    public CheckBoxCell(Context context, int type, int padding, Theme.ResourcesProvider rp) { super(context); }
    public void setText(CharSequence text, String value, boolean checked, boolean divider) {}
    public void setChecked(boolean checked, boolean animated) {}
    public boolean isChecked() { return false; }
}

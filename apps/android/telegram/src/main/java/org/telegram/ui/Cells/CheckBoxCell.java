/* NativeDo stub — Cells/CheckBoxCell. */
package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.TextView;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.Theme;

public class CheckBoxCell extends FrameLayout {
    private final TextView textView;
    public CheckBoxCell(Context context) { super(context); textView = new TextView(context); }
    public CheckBoxCell(Context context, int type) { super(context); textView = new TextView(context); }
    public CheckBoxCell(Context context, int type, Theme.ResourcesProvider rp) { super(context); textView = new TextView(context); }
    public CheckBoxCell(Context context, int type, int padding, Theme.ResourcesProvider rp) { super(context); textView = new TextView(context); }
    public void setText(CharSequence text, String value, boolean checked, boolean divider) { textView.setText(text); }
    public void setChecked(boolean checked, boolean animated) {}
    public boolean isChecked() { return false; }
    public void setMultiline(boolean multiline) {}
    public TextView getTextView() { return textView; }
}

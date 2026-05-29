package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.FrameLayout;

import org.telegram.ui.Components.LayoutHelper;

public class TextCheckCell extends FrameLayout {
    private final CheckBox checkBox;

    public TextCheckCell(Context context) {
        super(context);
        checkBox = new CheckBox(context);
        addView(checkBox, LayoutHelper.createFrame(48, 48));
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked);
    }

    public void setBackgroundColorAnimated(boolean checked, int color) {
        setBackgroundColor(color);
    }
}

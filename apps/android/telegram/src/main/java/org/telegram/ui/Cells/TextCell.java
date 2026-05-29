package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.ui.Components.LayoutHelper;

public class TextCell extends FrameLayout {
    private final TextView valueView;

    public TextCell(Context context) {
        super(context);
        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 20, 0, 120, 0));
        valueView = new TextView(context);
        valueView.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        addView(valueView, LayoutHelper.createFrame(120, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP, 0, 0, 20, 0));
    }

    public void setValue(CharSequence value, boolean animated) {
        valueView.setText(value);
    }
}

package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.telegram.ui.Components.LayoutHelper;

public class RadioColorCell extends FrameLayout {
    private final TextView textView;
    private final RadioButton radioButton;

    public RadioColorCell(Context context) {
        super(context);
        textView = new TextView(context);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 20, 0, 64, 0));
        radioButton = new RadioButton(context);
        addView(radioButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
    }

    public void setTextAndValue(CharSequence text, boolean checked) {
        textView.setText(text);
        radioButton.setChecked(checked);
    }

    public void setCheckColor(int color, int checkedColor) {
    }
}

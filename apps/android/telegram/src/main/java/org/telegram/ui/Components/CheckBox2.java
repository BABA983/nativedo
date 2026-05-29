package org.telegram.ui.Components;

import android.content.Context;
import android.widget.CheckBox;

public class CheckBox2 extends CheckBox {
    public CheckBox2(Context context, int size, org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider) {
        super(context);
    }

    public void setColor(int background, int backgroundKey, int checkKey) {
    }

    public void setDrawUnchecked(boolean value) {
    }

    public void setDrawBackgroundAsArc(int value) {
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked);
    }
}

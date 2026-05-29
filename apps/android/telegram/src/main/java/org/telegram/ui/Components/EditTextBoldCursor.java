/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.widget.EditText;

/** NativeDo minimal port of Telegram's EditTextBoldCursor for FragmentSearchField. */
public class EditTextBoldCursor extends EditText {
    public EditTextBoldCursor(Context context) {
        super(context);
    }

    public void setClipToPadding(boolean clipToPadding) {
        // TextView does not expose ViewGroup's clipToPadding; FragmentSearchField
        // still clips the visible text region through setClipBounds().
    }

    public void setCursorWidth(float width) {
        // Native Android EditText cursor is sufficient for this milestone.
    }

    public void setCursorColor(int color) {
        if (color == Color.TRANSPARENT) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getTextCursorDrawable().setTint(color);
        }
    }

    public void setHandlesColor(int color) {
    }

    public void setHintColor(int color) {
        setHintTextColor(color);
    }

    public void setHintText(CharSequence hint) {
        setHint(hint);
    }

    public void setLineColors(int color, int activeColor, int errorColor) {
    }
}

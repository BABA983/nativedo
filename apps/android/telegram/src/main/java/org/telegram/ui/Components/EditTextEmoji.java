package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;

public class EditTextEmoji extends View {
    public EditTextEmoji(Context context) {
        super(context);
    }

    public boolean isWaitingForKeyboardOpen() {
        return false;
    }

    public boolean isPopupShowing() {
        return false;
    }

    public boolean isAnimatePopupClosing() {
        return false;
    }

    public void hideEmojiView() {
    }

    public int getEmojiPadding() {
        return 0;
    }

    public boolean isPopupView(View child) {
        return false;
    }
}

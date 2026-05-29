/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.view.View;

/**
 * NativeDo stub of org.telegram.ui.Stories.recorder.HintView2.
 * Upstream renders the Telegram tooltip pill. NativeDo only needs the type
 * signature so PhotoViewer compiles.
 */
public class HintView2 extends View {

    public static final int DIRECTION_BOTTOM = 0;
    public static final int DIRECTION_TOP = 1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = 3;

    public HintView2(Context context, int direction) {
        super(context);
    }

    public HintView2 setText(CharSequence text) { return this; }
    public HintView2 setMaxWidthPx(int px) { return this; }
    public HintView2 setMultilineText(boolean multiline) { return this; }
    public HintView2 setRounding(float rounding) { return this; }
    @Override
    public void setPadding(int left, int top, int right, int bottom) { super.setPadding(left, top, right, bottom); }
    public HintView2 setJointPx(float jointTranslation, float joint) { return this; }
    public HintView2 setDirection(int dir) { return this; }
    public HintView2 setBgColor(int color) { return this; }
    public HintView2 setTextColor(int color) { return this; }
    public HintView2 setCloseButton(boolean enabled) { return this; }
    public HintView2 setIcon(int resId) { return this; }
    public static float measureCorrectly(String text, android.text.TextPaint paint) { return text == null || paint == null ? 0 : paint.measureText(text); }
    public HintView2 setDuration(long ms) { return this; }
    public HintView2 setHideByTouch(boolean h) { return this; }
    public HintView2 setOnHiddenListener(Runnable r) { return this; }
    public boolean show() { return true; }
    public void hide() {}
}

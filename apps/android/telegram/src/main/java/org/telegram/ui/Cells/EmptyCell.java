// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/Cells/EmptyCell.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: measurement, drawing
// HOST-WIRING:
//   - org.telegram.messenger.* -> io.github.baba983.nativedo.core.*
//   - org.telegram.ui.ActionBar.Theme -> org.telegram.ui.ActionBar.Theme
//   - org.telegram.ui.Components.* -> org.telegram.ui.Components.*
// ADAPTED-BACKEND:
//   - LocaleController.isRTL hard-coded false.
// ADAPTED-PRODUCT:
//   - None.
// OMITTED:
//   - getThemeDescriptions() (NativeDo not using ThemeDescription system).
//   - AnimatedEmojiSpan (out of scope).

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

public class EmptyCell extends FrameLayout {

    private int cellHeight;

    public EmptyCell(Context context) {
        this(context, 8);
    }

    public EmptyCell(Context context, int height) {
        super(context);
        cellHeight = height;
    }

    public void setHeight(int height) {
        if (cellHeight != height) {
            cellHeight = height;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY));
    }
}

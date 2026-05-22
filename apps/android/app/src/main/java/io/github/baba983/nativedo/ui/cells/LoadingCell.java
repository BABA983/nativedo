// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/Cells/LoadingCell.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: measurement, drawing
// HOST-WIRING:
//   - org.telegram.messenger.* -> io.github.baba983.nativedo.core.*
//   - org.telegram.ui.ActionBar.Theme -> io.github.baba983.nativedo.ui.actionbar.Theme
//   - org.telegram.ui.Components.* -> io.github.baba983.nativedo.ui.components.*
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

package io.github.baba983.nativedo.ui.cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import io.github.baba983.nativedo.telegram.messenger.AndroidUtilities;
import io.github.baba983.nativedo.ui.components.LayoutHelper;
import io.github.baba983.nativedo.ui.components.RadialProgressView;

public class LoadingCell extends FrameLayout {

    private RadialProgressView progressBar;
    private int height;

    public LoadingCell(Context context) {
        this(context, AndroidUtilities.dp(40), AndroidUtilities.dp(54));
    }

    public LoadingCell(Context context, int size, int h) {
        super(context);

        height = h;

        progressBar = new RadialProgressView(context);
        progressBar.setSize(size);
        addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}

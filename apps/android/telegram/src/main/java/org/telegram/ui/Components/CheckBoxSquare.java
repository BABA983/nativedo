/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CheckBoxSquare extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean checked;
    private int uncheckedColorKey = Theme.key_checkbox;
    private int checkedColorKey = Theme.key_checkbox;
    private int checkColorKey = Theme.key_checkboxCheck;

    public CheckBoxSquare(Context context, boolean alert, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(2f));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setColors(int uncheckedColor, int checkedColor, int checkColor) {
        uncheckedColorKey = uncheckedColor;
        checkedColorKey = checkedColor;
        checkColorKey = checkColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int inset = AndroidUtilities.dp(1);
        rect.set(inset, inset, getMeasuredWidth() - inset, getMeasuredHeight() - inset);
        if (checked) {
            backgroundPaint.setStyle(Paint.Style.FILL);
            backgroundPaint.setColor(Theme.getColor(checkedColorKey, resourcesProvider));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), backgroundPaint);

            checkPaint.setColor(Theme.getColor(checkColorKey, resourcesProvider));
            path.reset();
            path.moveTo(AndroidUtilities.dp(4.5f), AndroidUtilities.dp(9.5f));
            path.lineTo(AndroidUtilities.dp(7.5f), AndroidUtilities.dp(12.5f));
            path.lineTo(AndroidUtilities.dp(14f), AndroidUtilities.dp(5.5f));
            canvas.drawPath(path, checkPaint);
        } else {
            backgroundPaint.setStyle(Paint.Style.STROKE);
            backgroundPaint.setColor(Theme.getColor(uncheckedColorKey, resourcesProvider));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), backgroundPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.CheckBox");
        info.setCheckable(true);
        info.setChecked(checked);
    }
}

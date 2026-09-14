/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java.
 * It keeps the dialog list's moving-cell draw order and pull-offset recycling contract.
 */
package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.MovableCell;
import org.telegram.ui.Components.RecyclerListView;

public class TopicDialogsRecyclerView extends RecyclerListView {
    private final Paint movingCellBackgroundPaint = new Paint();
    private final RectF movingCellBounds = new RectF();
    private float viewsOffset;

    public TopicDialogsRecyclerView(@NonNull Context context) {
        super(context);
    }

    public void setViewsOffset(float viewsOffset) {
        this.viewsOffset = viewsOffset;
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).setTranslationY(viewsOffset);
        }
        invalidate();
    }

    public float getViewsOffset() {
        return viewsOffset;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        child.setTranslationY(viewsOffset);
        child.setTranslationX(0f);
        child.setAlpha(1f);
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);
        view.setTranslationY(0f);
        view.setTranslationX(0f);
        view.setAlpha(1f);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!drawMovingViewsOverlayed()) {
            return;
        }

        movingCellBackgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        for (int index = 0; index < getChildCount(); index++) {
            View view = getChildAt(index);
            if (!(view instanceof MovableCell) || !((MovableCell) view).isMoving()) {
                continue;
            }
            if (view.getAlpha() != 1f) {
                movingCellBounds.set(
                        view.getX(),
                        view.getY(),
                        view.getX() + view.getMeasuredWidth(),
                        view.getY() + view.getMeasuredHeight());
                canvas.saveLayerAlpha(movingCellBounds, (int) (255 * view.getAlpha()), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(view.getX(), view.getY());
            canvas.drawRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), movingCellBackgroundPaint);
            view.draw(canvas);
            canvas.restore();
        }
        invalidate();
    }

    private boolean drawMovingViewsOverlayed() {
        return getItemAnimator() != null && getItemAnimator().isRunning();
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (drawMovingViewsOverlayed() &&
                child instanceof MovableCell &&
                ((MovableCell) child).isMoving()) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }
}

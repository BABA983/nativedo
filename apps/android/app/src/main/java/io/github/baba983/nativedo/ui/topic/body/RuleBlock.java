/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockDividerCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.TextSelectionHelper;

final class RuleBlock implements PostBodyBlock {
    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int measuredHeight = AndroidUtilities.dp(18f);

    RuleBlock(PostBodyBuildContext context) {
        paint.setColor(context.getDividerColor());
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        int width = parentWidth / 3;
        rect.set(width, AndroidUtilities.dp(8f), width * 2f, AndroidUtilities.dp(10f));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), paint);
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        return null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        return null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        return null;
    }

    @Override
    public void onAttachedToWindow() {
    }

    @Override
    public void onDetachedFromWindow() {
    }

    @Override
    public void applyTheme() {
    }
}

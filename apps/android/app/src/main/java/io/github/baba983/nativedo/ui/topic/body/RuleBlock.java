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
    private static final float MEASURED_HEIGHT_DP = 18f;
    private static final float BAR_TOP_DP = 8f;
    private static final float BAR_BOTTOM_DP = 10f;
    private static final float BAR_RADIUS_DP = 1f;

    private final RectF rect = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int measuredHeight = measuredHeightPx();

    private final PostBodyBuildContext context;

    RuleBlock(PostBodyBuildContext context) {
        this.context = context;
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
        rect.set(width, barTopPx(), width * 2f, barBottomPx());
        canvas.drawRoundRect(rect, barRadiusPx(), barRadiusPx(), paint);
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
        paint.setColor(context.getDividerColor());
    }

    static int measuredHeightPx() {
        return AndroidUtilities.dp(MEASURED_HEIGHT_DP);
    }

    static int barTopPx() {
        return AndroidUtilities.dp(BAR_TOP_DP);
    }

    static int barBottomPx() {
        return AndroidUtilities.dp(BAR_BOTTOM_DP);
    }

    static int barRadiusPx() {
        return AndroidUtilities.dp(BAR_RADIUS_DP);
    }

    static int barLeftPx(int parentWidth) {
        return parentWidth / 3;
    }

    static int barRightPx(int parentWidth) {
        return barLeftPx(parentWidth) * 2;
    }
}

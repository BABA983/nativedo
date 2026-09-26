/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's RichMessageLayout.RichDocumentBlock (its URL case).
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.MediaActionDrawable;

/**
 * A Discourse attachment (`a.attachment`), drawn the way the rich body draws it: a circular file
 * glyph, the file name in bold and the size under it. The geometry mirrors
 * `RichMessageLayout.RichDocumentBlock`'s URL case (dp(42) glyph at dp(16)/dp(9), the name at
 * fontSize-1 bold, the size at fontSize-3, dp(60) tall), so the two bodies show the same row.
 *
 * The block carries a URL, not a Telegram file: a press opens it through the app's link entry and a
 * hold copies it, which is the rule the run and the rich row follow. Nothing is downloaded and no
 * `DownloadController` observer is registered.
 */
final class AttachmentBlock implements PostBodyBlock {
    private static final float ROW_HEIGHT_DP = 60f;
    private static final float TOP_GAP_DP = 8f;
    private static final float GLYPH_SIZE_DP = 42f;
    /** The glyph starts at the body's text column, so the row lines up with the paragraphs. */
    private static final float GLYPH_X_DP = 18f;
    private static final float GLYPH_Y_DP = 9f;
    private static final float TEXT_SPACING_DP = 14f;
    private static final float TITLE_TOP_DP = 11f;
    private static final float SIZE_GAP_DP = 2f;
    private static final float NAME_SIZE_OFFSET_DP = 1f;
    private static final float SIZE_SIZE_OFFSET_DP = 3f;

    private final PostBodyBuildContext context;
    private final String url;
    private final String name;
    private final String sizeText;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final MediaActionDrawable glyph = new MediaActionDrawable();
    private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sizePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout nameLayout;
    private StaticLayout sizeLayout;

    private boolean pressed;
    private Runnable longPressRunnable;
    private boolean longPressFired;

    AttachmentBlock(String name, String url, String sizeText, PostBodyBuildContext context) {
        this.name = name;
        this.url = url;
        this.sizeText = sizeText;
        this.context = context;
        // The row draws with chat resources (the file glyph is `Theme.chat_filePath`, the colours are
        // chat keys), so make sure they exist first - the same lazy init PollBlock performs.
        Theme.createChatResources(context.getHost().getContext(), false);
        namePaint.setTypeface(AndroidUtilities.bold());
        glyph.setIcon(MediaActionDrawable.ICON_FILE, false);
        applyTheme();
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        final int textX = textXPx();
        final int textWidth = Math.max(AndroidUtilities.dp(40f), maxWidthPx - textX - AndroidUtilities.dp(12f));
        final CharSequence title = TextUtils.ellipsize(name, namePaint, textWidth, TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(title, namePaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        sizeLayout = new StaticLayout(sizeText == null ? "" : sizeText, sizePaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        return measuredHeightPx();
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        if (nameLayout == null) {
            return;
        }
        final int glyphLeft = AndroidUtilities.dp(GLYPH_X_DP);
        final int glyphTop = topGapPx() + AndroidUtilities.dp(GLYPH_Y_DP);
        final int glyphSize = AndroidUtilities.dp(GLYPH_SIZE_DP);
        canvas.drawCircle(glyphLeft + glyphSize / 2f, glyphTop + glyphSize / 2f, glyphSize / 2f, circlePaint);
        glyph.setBounds(glyphLeft, glyphTop, glyphLeft + glyphSize, glyphTop + glyphSize);
        glyph.draw(canvas);

        final int textX = textXPx();
        final int titleY = topGapPx() + AndroidUtilities.dp(TITLE_TOP_DP);
        canvas.save();
        canvas.translate(textX, titleY);
        nameLayout.draw(canvas);
        canvas.restore();
        canvas.save();
        canvas.translate(textX, titleY + nameLayout.getHeight() + AndroidUtilities.dp(SIZE_GAP_DP));
        sizeLayout.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        final boolean inside = localX >= AndroidUtilities.dp(GLYPH_X_DP) && localY >= topGapPx() && localY <= topGapPx() + AndroidUtilities.dp(ROW_HEIGHT_DP);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && inside) {
            pressed = true;
            scheduleLongPress();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pressed && !inside) {
            pressed = false;
            cancelLongPress();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && pressed) {
            pressed = false;
            cancelLongPress();
            if (inside && !longPressFired) {
                context.getDelegate().onLinkClick(url);
            }
            longPressFired = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            cancelLongPress();
            longPressFired = false;
        }
        return pressed;
    }

    /** The held row copies its URL, on the same timer shape the run and the rich row use. */
    private void scheduleLongPress() {
        cancelLongPress();
        longPressRunnable = () -> {
            longPressRunnable = null;
            if (!pressed) {
                return;
            }
            longPressFired = true;
            context.getHost().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            context.getDelegate().onLinkLongPress(url);
        };
        context.getHost().postDelayed(longPressRunnable, android.view.ViewConfiguration.getLongPressTimeout());
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            context.getHost().removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        // The row's text is chrome, not body text: a selection drag over it must not pick the file
        // name up as if it were part of the post.
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
        cancelLongPress();
    }

    @Override
    public void applyTheme() {
        circlePaint.setColor(Theme.getColor(Theme.key_chat_inLoader));
        glyph.setColor(Theme.getColor(Theme.key_chat_inMediaIcon));
        final float bodySize = SharedConfig.fontSize;
        namePaint.setTextSize(AndroidUtilities.dp(Math.max(12f, bodySize - NAME_SIZE_OFFSET_DP)));
        namePaint.setColor(Theme.getColor(Theme.key_chat_inFileNameText));
        sizePaint.setTextSize(AndroidUtilities.dp(Math.max(11f, bodySize - SIZE_SIZE_OFFSET_DP)));
        sizePaint.setColor(Theme.getColor(Theme.key_chat_inTimeText));
        nameLayout = null;
    }

    static int measuredHeightPx() {
        return topGapPx() + AndroidUtilities.dp(ROW_HEIGHT_DP);
    }

    private static int topGapPx() {
        return AndroidUtilities.dp(TOP_GAP_DP);
    }

    private static int textXPx() {
        return AndroidUtilities.dp(GLYPH_X_DP + GLYPH_SIZE_DP + TEXT_SPACING_DP);
    }
}

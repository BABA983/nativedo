/*
 * This file is part of NativeDo.
 *
 * NativeDo-scoped subset of Telegram for Android's PollButtonDrawable.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components.poll.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;

import java.util.List;

public class PollButtonDrawable extends PollButtonDrawableBase {
    private final AnimatedTextView.AnimatedTextDrawable votersCountDrawable;
    private final ImageReceiver imageReceiver;
    private final View parent;
    private boolean votersVisible;
    private boolean hasMediaPadding;
    private int votersCount;

    public PollButtonDrawable(int currentAccount, View parent) {
        super(null);
        this.parent = parent;
        votersCountDrawable = new AnimatedTextView.AnimatedTextDrawable();
        votersCountDrawable.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        votersCountDrawable.setTextSize(dp(11));
        votersCountDrawable.setCallback(parent);
        imageReceiver = new ImageReceiver(parent);
        imageReceiver.setRoundRadius(dp(5));
    }

    public void setVotersVisible(boolean visible, boolean animated) {
        votersVisible = visible;
    }

    public void attach() {
        imageReceiver.onAttachedToWindow();
    }

    public void detach() {
        imageReceiver.onDetachedFromWindow();
    }

    public void setVotersCountTextColor(int color) {
        votersCountDrawable.setTextColor(color);
    }

    public void setHasMediaPadding(boolean hasMediaPadding) {
        this.hasMediaPadding = hasMediaPadding;
    }

    public void setMedia(MessageObject messageObject, TLRPC.MessageMedia media, Object parentObject, String attachPath, boolean animated) {
        imageReceiver.clearImage();
    }

    public boolean isHasMedia() {
        return false;
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    public void setVotersCount(int count, boolean animated) {
        votersCount = Math.max(count, 0);
        votersCountDrawable.setText(votersCount > 0 ? String.valueOf(votersCount) : null, animated);
    }

    public void setRecentVoters(List<TLRPC.Peer> recentVoters, boolean animated) {
    }

    public float getVotersCountTargetWidth() {
        return votersCountDrawable.getAnimateToWidth();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        draw(canvas, null);
    }

    public void draw(@NonNull Canvas canvas, @Nullable Paint avatarsPaint) {
        if (!votersVisible || votersCount <= 0) {
            return;
        }
        int rightPadding = dp(hasMediaPadding ? 56.33f : 19);
        int cy = getBounds().bottom - dp(23);
        votersCountDrawable.setAlpha(getAlpha());
        votersCountDrawable.setBounds(getBounds().left, cy + dp(15), getBounds().right - rightPadding, cy - dp(15));
        votersCountDrawable.draw(canvas);
    }

    @Override
    public boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == votersCountDrawable;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}

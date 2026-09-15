/*
 * Stub ChatMessageCell implementing IMessageCell for RecyclerAnimationScrollHelper.
 * Full ChatMessageCell port is deferred; animation/list helpers only need the interface.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

import org.telegram.messenger.MessageObject;

public class ChatMessageCell extends FrameLayout implements IMessageCell {
    public ChatMessageCell(Context context) {
        super(context);
    }

    @Override
    public MessageObject getMessageObject() {
        return null;
    }

    @Override
    public float getDeltaTop() {
        return 0f;
    }

    @Override
    public float getDeltaLeft() {
        return 0f;
    }

    @Override
    public float getDeltaBottom() {
        return 0f;
    }

    @Override
    public float getDeltaRight() {
        return 0f;
    }

    /** Upstream setAnimationRunning — no-op until full cell port. */
    @Override
    public void setAnimationRunning(boolean animationRunning, boolean willRemoved) {
    }

    /** Stub of upstream sizing helper used by ImageLoader. */
    public static org.telegram.ui.Components.Point getMessageSize(int w, int h) {
        return new org.telegram.ui.Components.Point(w, h);
    }
}

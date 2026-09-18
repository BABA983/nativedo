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

    // --- pin surface the rich-message renderer reads (host stub growth) ---

    public int namesOffset;
    public int childPosition;
    public int textY;
    public int visibleHeight;
    private int textX;
    private boolean pinnedTop;

    public boolean isPinnedTop() {
        return pinnedTop;
    }

    public int getTextX() {
        return textX;
    }

    public int getTextY() {
        return textY;
    }

    /**
     * Stub of the pin's bot-button progress probe: the pin asks SendMessagesHelper whether a callback
     * or location button is mid-send, and this product ships no bot keyboards, so nothing is sending.
     */
    public final boolean drawButtonProgress(org.telegram.tgnet.tl.TL_keyboard.KeyboardButtonProto button) {
        return false;
    }

    /** Pin transition state, reduced to the one field the rich-message renderer reads. */
    public class TransitionParams {
        public float animateChangeProgress = 1f;
    }

    /**
     * Pin delegate interface, reduced to the methods the rich-message renderer calls: the defaults are
     * upstream's (defaults that do nothing), so a host that implements nothing still compiles.
     */
    public interface ChatMessageCellDelegate {

        default boolean openArticlePhoto(ChatMessageCell cell, org.telegram.tgnet.tl.TL_iv.PageBlock block) {
            return false;
        }

        default void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
        }

        default void forceUpdate(ChatMessageCell cell, boolean anchorScroll, boolean scrollByTop) {
        }

        default void didPressBotButton(ChatMessageCell cell, org.telegram.tgnet.tl.TL_keyboard.KeyboardButtonProto button) {
        }

        default void didLongPressBotButton(ChatMessageCell cell, org.telegram.tgnet.tl.TL_keyboard.KeyboardButtonProto button) {
        }

        default boolean isProgressLoading(ChatMessageCell cell, int type) {
            return false;
        }

        default void didPressUrl(ChatMessageCell cell, android.text.style.CharacterStyle url, boolean longPress) {
        }

        default void didPressShowMore(ChatMessageCell cell) {
        }

        default void didPressRichDocumentOptions(ChatMessageCell cell, org.telegram.tgnet.TLRPC.Document document, float x, float y) {
        }

        default void didPressAppUpdateButton() {
        }

        default boolean canToggleRichMessageCheckbox(ChatMessageCell cell) {
            return false;
        }

        default void didToggleRichMessageCheckbox(ChatMessageCell cell, boolean checked, Runnable revertOnError) {
        }

        default boolean didPressAnimatedEmoji(ChatMessageCell cell, org.telegram.ui.Components.AnimatedEmojiSpan span) {
            return false;
        }

        default boolean canSaveRichDocument(ChatMessageCell cell) {
            return false;
        }
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import android.os.Bundle;

/**
 * NativeDo stub of org.telegram.ui.ChatActivity.
 * Upstream is the chat screen (~25k lines). NativeDo only needs the type
 * signature so PhotoViewer's parentChatActivity field compiles.
 */
public class ChatActivity extends BaseFragment {

    public Object chatInfo;
    public TLRPC.Chat currentChat;
    public TLRPC.User currentUser;

    public ChatActivity() {
    }

    public ChatActivity(Bundle args) {
    }

    public static ChatActivity of(long dialogId, int messageId) {
        return new ChatActivity();
    }

    public TLRPC.User getCurrentUser() {
        return currentUser;
    }

    public TLRPC.Chat getCurrentChat() {
        return currentChat;
    }

    public long getDialogId() {
        return 0;
    }

    public boolean isInScheduleMode() {
        return false;
    }

    public boolean canScheduleMessage() {
        return false;
    }

    public org.telegram.ui.Components.ChatActivityEnterView getChatActivityEnterView() {
        return null;
    }

    public int getChatMode() {
        return 0;
    }

    public org.telegram.messenger.MessageObject editingMessageObject;
    public Object quickReplyShortcut;

    public org.telegram.ui.Components.UndoView getUndoView() {
        return null;
    }

    public org.telegram.messenger.AccountInstance getAccountInstance() {
        return org.telegram.messenger.AccountInstance.getInstance(org.telegram.messenger.UserConfig.selectedAccount);
    }

    public org.telegram.messenger.MessageObject getReplyMessage() {
        return null;
    }

    public org.telegram.messenger.MessageObject getThreadMessage() {
        return null;
    }

    public Object getReplyQuote() {
        return null;
    }

    public int getQuickReplyId() {
        return 0;
    }

    /** Pin constant: the progress spinner state for a full-article load. */
    public static final int PROGRESS_FULL_ARTICLE = 7;
}

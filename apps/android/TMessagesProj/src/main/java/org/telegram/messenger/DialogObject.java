/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

/**
 * NativeDo stub of org.telegram.messenger.DialogObject.
 * Upstream provides helpers for Telegram dialog IDs (chat/channel/user/encrypted).
 * NativeDo only needs the predicates FileLoader references.
 */
public final class DialogObject {

    private DialogObject() {
    }

    public static boolean isEncryptedDialog(long dialogId) {
        return false;
    }

    public static boolean isChatDialog(long dialogId) {
        return dialogId < 0;
    }

    public static boolean isUserDialog(long dialogId) {
        return dialogId > 0;
    }

    public static long makeEncryptedDialogId(int chatId) {
        return ((long) chatId) | 0x200000000L;
    }

    public static long getPeerDialogId(org.telegram.tgnet.TLRPC.Peer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer.user_id != 0) {
            return peer.user_id;
        } else if (peer.chat_id != 0) {
            return -peer.chat_id;
        } else {
            return -peer.channel_id;
        }
    }
}

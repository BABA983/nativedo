/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.ChatObject (chat/channel helpers).
 * Upstream has many static predicates; we expose the few that ImageLocation
 * touches. All return safe defaults for LinuxDo (we don't yet model Telegram
 * channels/megagroups).
 */
public final class ChatObject {

    private ChatObject() {
    }

    public static boolean isChannel(TLRPC.Chat chat) {
        return false;
    }

    public static boolean isMegagroup(TLRPC.Chat chat) {
        return false;
    }

    public static boolean isChannelAndNotMegaGroup(TLRPC.Chat chat) {
        return false;
    }

    public static boolean isPublic(TLRPC.Chat chat) {
        return false;
    }
}

/*
 * This file is part of NativeDo.
 *
 * Host hook for {@code messages.getPollVotes}: NativeDo has no MTProto backend, so the
 * vendored poll UI (org/telegram/ui/Components/PollVotesAlert.java) asks the app layer to
 * answer from the site's polls API instead. The provider is installed by the app.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger.nativedo;

import org.telegram.tgnet.TLRPC;

public final class PollVotesBridge {

    public interface Provider {
        /** Answers {@code request} asynchronously; returns false when the poll is unknown. */
        boolean loadVotes(TLRPC.TL_messages_getPollVotes request, Callback callback);
    }

    public interface Callback {
        void onVotes(TLRPC.TL_messages_votesList result);
    }

    public interface UserOpener {
        /** Opens the host's user screen for a poll voter; false when it cannot. */
        boolean openUser(TLRPC.User user);
    }

    private static Provider provider;
    private static UserOpener userOpener;

    private PollVotesBridge() {
    }

    public static void setProvider(Provider value) {
        provider = value;
    }

    public static void setUserOpener(UserOpener value) {
        userOpener = value;
    }

    public static boolean loadVotes(TLRPC.TL_messages_getPollVotes request, Callback callback) {
        final Provider local = provider;
        return local != null && local.loadVotes(request, callback);
    }

    public static boolean openUser(TLRPC.User user) {
        final UserOpener local = userOpener;
        return local != null && user != null && local.openUser(user);
    }
}

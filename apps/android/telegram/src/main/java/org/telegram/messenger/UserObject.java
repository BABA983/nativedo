/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.UserObject helpers.
 */
public final class UserObject {

    private UserObject() {
    }

    public static String getUserName(TLRPC.User user) {
        if (user == null) return "";
        if (user.first_name == null && user.last_name == null) return "";
        if (user.last_name == null) return user.first_name == null ? "" : user.first_name;
        if (user.first_name == null) return user.last_name;
        return user.first_name + " " + user.last_name;
    }

    public static String getFirstName(TLRPC.User user) {
        return user == null ? "" : (user.first_name == null ? "" : user.first_name);
    }

    public static String getFirstName(TLRPC.User user, boolean allowShort) {
        return getFirstName(user);
    }

    public static boolean isDeleted(TLRPC.User user) {
        return false;
    }

    public static boolean isContact(TLRPC.User user) {
        return false;
    }

    public static boolean isUserSelf(TLRPC.User user) {
        return false;
    }

    public static boolean isReplyUser(TLRPC.User user) {
        return false;
    }

    public static boolean isAnonymous(TLRPC.User user) {
        return false;
    }
}

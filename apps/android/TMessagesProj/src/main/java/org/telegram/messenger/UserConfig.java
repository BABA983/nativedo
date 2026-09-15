/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.UserConfig.
 *
 * <p>Upstream stores per-account login state, current user info, premium flags,
 * notification tokens, etc. NativeDo runs single-account (account 0) and
 * doesn't yet model Telegram-shaped user state. We expose only the constants
 * and getters the image pipeline references.
 */
public class UserConfig {

    public static final int MAX_ACCOUNT_COUNT = 1;
    public static int selectedAccount = 0;

    private static final UserConfig[] Instance = new UserConfig[MAX_ACCOUNT_COUNT];

    public final int currentAccount;
    public long clientUserId;
    public boolean configLoaded;

    private UserConfig(int account) {
        this.currentAccount = account;
    }

    public static UserConfig getInstance(int account) {
        UserConfig local = Instance[account];
        if (local == null) {
            synchronized (UserConfig.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new UserConfig(account);
                }
            }
        }
        return local;
    }

    public long getClientUserId() {
        return clientUserId;
    }

    public TLRPC.User getCurrentUser() {
        return null;
    }

    public boolean isClientActivated() {
        return false;
    }

    public boolean isPremium() {
        return false;
    }

    public static boolean hasPremiumOnAccounts() {
        return false;
    }

    public void saveConfig(boolean withFile) {
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

/**
 * NativeDo stub of org.telegram.messenger.AccountInstance.
 * Upstream binds together per-account controllers. NativeDo only needs the
 * getInstance + getUserConfig path for compile-time resolution.
 */
public class AccountInstance {

    private static final AccountInstance[] Instance = new AccountInstance[UserConfig.MAX_ACCOUNT_COUNT];

    public final int currentAccount;

    private AccountInstance(int account) {
        this.currentAccount = account;
    }

    public static AccountInstance getInstance(int account) {
        AccountInstance local = Instance[account];
        if (local == null) {
            synchronized (AccountInstance.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new AccountInstance(account);
                }
            }
        }
        return local;
    }

    public UserConfig getUserConfig() {
        return UserConfig.getInstance(currentAccount);
    }

    public MessagesController getMessagesController() {
        return MessagesController.getInstance(currentAccount);
    }

    public MediaDataController getMediaDataController() {
        return MediaDataController.getInstance(currentAccount);
    }

    public NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(currentAccount);
    }

    public DownloadController getDownloadController() {
        return DownloadController.getInstance(currentAccount);
    }

    public FileLoader getFileLoader() {
        return FileLoader.getInstance(currentAccount);
    }

    public FileRefController getFileRefController() {
        return FileRefController.getInstance(currentAccount);
    }
}

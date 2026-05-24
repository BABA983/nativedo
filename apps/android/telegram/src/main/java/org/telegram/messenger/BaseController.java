/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

/**
 * NativeDo stub of org.telegram.messenger.BaseController.
 *
 * <p>Upstream binds together all per-account Telegram controllers via
 * AccountInstance. NativeDo runs single-account (0) and exposes only the
 * accessors FileLoader/ImageLoader call.
 */
public class BaseController {

    protected final int currentAccount;

    public BaseController(int num) {
        this.currentAccount = num;
    }

    protected MessagesController getMessagesController() {
        return MessagesController.getInstance(currentAccount);
    }

    protected MediaDataController getMediaDataController() {
        return MediaDataController.getInstance(currentAccount);
    }

    protected DownloadController getDownloadController() {
        return DownloadController.getInstance(currentAccount);
    }

    protected NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(currentAccount);
    }

    protected UserConfig getUserConfig() {
        return UserConfig.getInstance(currentAccount);
    }

    protected FileLoader getFileLoader() {
        return FileLoader.getInstance(currentAccount);
    }

    protected FileRefController getFileRefController() {
        return FileRefController.getInstance(currentAccount);
    }
}

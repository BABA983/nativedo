/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

/**
 * NativeDo stub of org.telegram.messenger.SaveToGallerySettingsHelper.
 * Upstream decides per-peer/per-content auto-save behaviour. NativeDo never
 * auto-saves to gallery, so {@link #needSave} always returns false.
 */
public final class SaveToGallerySettingsHelper {

    private SaveToGallerySettingsHelper() {
    }

    public static boolean needSave(int flag, FilePathDatabase.FileMeta metadata, MessageObject messageObject, int currentAccount) {
        return false;
    }
}

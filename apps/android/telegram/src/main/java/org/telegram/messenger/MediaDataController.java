/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.MediaDataController.
 * Upstream manages stickers, animated emojis, GIF favorites, and sticker-set
 * lookup. NativeDo doesn't render Telegram stickers; only the methods
 * ImageLocation references are exposed.
 */
public final class MediaDataController {

    public static final int MEDIA_PHOTOVIDEO = 0;
    public static final int MEDIA_FILE = 1;
    public static final int MEDIA_AUDIO = 2;
    public static final int MEDIA_URL = 3;
    public static final int MEDIA_MUSIC = 4;
    public static final int MEDIA_GIF = 5;
    public static final int MEDIA_PHOTOS_ONLY = 6;
    public static final int MEDIA_VIDEOS_ONLY = 7;

    private static final MediaDataController[] Instance = new MediaDataController[UserConfig.MAX_ACCOUNT_COUNT];

    private MediaDataController() {
    }

    public static MediaDataController getInstance(int account) {
        MediaDataController local = Instance[account];
        if (local == null) {
            synchronized (MediaDataController.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new MediaDataController();
                }
            }
        }
        return local;
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Document document) {
        return null;
    }

    public void setPlaceholderImage(org.telegram.ui.Components.BackupImageView imageView, String packName, String emoji, String size) {
    }

    public void loadMedia(long dialogId, int count, int maxId, int minDate, int type, int fromCache, int classGuid, int requestIndex, int queryId, Object delegate, String query) {
    }

    public static long calcHash(long hash, long id) {
        hash ^= hash >>> 21;
        hash ^= hash << 35;
        hash ^= hash >>> 4;
        return hash + id;
    }

    public static long calcHash(long hash, int id) {
        return calcHash(hash, (long) id);
    }
}

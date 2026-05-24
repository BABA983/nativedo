/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.SendMessagesHelper.
 * Upstream manages MTProto message sending. NativeDo doesn't send via MTProto.
 * Only the API surface ImageLoader/FileLoader reference is preserved.
 */
public class SendMessagesHelper {

    private static final SendMessagesHelper[] Instance = new SendMessagesHelper[UserConfig.MAX_ACCOUNT_COUNT];

    private SendMessagesHelper() {
    }

    public static SendMessagesHelper getInstance(int account) {
        SendMessagesHelper local = Instance[account];
        if (local == null) {
            synchronized (SendMessagesHelper.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new SendMessagesHelper();
                }
            }
        }
        return local;
    }

    public void onResume() {
    }

    public void cleanup() {
    }

    public static android.graphics.Bitmap createVideoThumbnail(String filePath, int kind) {
        return null;
    }

    public static android.graphics.Bitmap createVideoThumbnailAtTime(String filePath, long time, int[] orientation, boolean precise) {
        return null;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;

/**
 * NativeDo stub of org.telegram.messenger.FileRefController.
 * Upstream re-fetches MTProto file references that have expired. NativeDo
 * doesn't use MTProto, so the controller is a no-op singleton; ImageLocation
 * touches only its presence and the {@code isFileRef*} predicates.
 */
public class FileRefController {

    private static final FileRefController[] Instance = new FileRefController[1];

    public static FileRefController getInstance(int account) {
        FileRefController local = Instance[0];
        if (local == null) {
            synchronized (FileRefController.class) {
                local = Instance[0];
                if (local == null) {
                    Instance[0] = local = new FileRefController();
                }
            }
        }
        return local;
    }

    public boolean isFileRefError(String error) {
        return false;
    }

    public void requestReference(Object parentObject, Object... args) {
    }

    public static boolean isFileRefErrorMessage(String error) {
        return false;
    }

    public static String getKeyForParentObject(Object parentObject) {
        return null;
    }
}

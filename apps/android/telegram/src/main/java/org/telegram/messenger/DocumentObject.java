/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.DocumentObject helpers.
 * Only the inner classes ImageLocation/ImageReceiver reference are exposed.
 */
public final class DocumentObject {

    private DocumentObject() {
    }

    /** Upstream ThemeDocument carries a theme bundle inside a document. */
    public static class ThemeDocument extends TLRPC.Document {
        public TLRPC.ThemeSettings themeSettings;
        public Object wallpaper;
        public int accentColor;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * NativeDo-trimmed stub of org.telegram.messenger.WebFile.
 * Only fields ImageLoader/ImageLocation/FileLoader actually access are kept.
 * Factory methods that depend on TLRPC inner classes (TL_inputWebFileGeoPointLocation,
 * GeoPoint, WebDocument, ...) are dropped — LinuxDo doesn't load images via
 * Telegram's MTProto web-file path.
 */
public class WebFile extends TLObject {

    public String url;
    public int size;
    public String mime_type;
    public org.telegram.tgnet.TLRPC.InputWebFileLocation location;
    public boolean noproxy;

    public static WebFile createWithGeoPoint(TLRPC.GeoPoint point, int w, int h, int zoom, int scale) {
        return new WebFile();
    }
}

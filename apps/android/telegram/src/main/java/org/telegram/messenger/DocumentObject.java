/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class DocumentObject {

    private DocumentObject() {
    }

    /** Upstream ThemeDocument carries a theme bundle inside a document. */
    public static class ThemeDocument extends TLRPC.Document {
        public TLRPC.ThemeSettings themeSettings;
        public Object wallpaper;
        public int accentColor;
    }

    public static SvgHelper.SvgDrawable getSvgThumb(ArrayList<TLRPC.PhotoSize> sizes, int colorKey, float alpha) {
        return getSvgThumb(sizes, colorKey, alpha, false);
    }

    public static SvgHelper.SvgDrawable getSvgThumb(ArrayList<TLRPC.PhotoSize> sizes, int colorKey, float alpha, boolean usePhotoSize) {
        if (sizes == null) {
            return null;
        }
        int w = 512;
        int h = 512;
        TLRPC.TL_photoPathSize photoPathSize = null;
        for (int a = 0, count = sizes.size(); a < count; a++) {
            TLRPC.PhotoSize photoSize = sizes.get(a);
            if (photoSize instanceof TLRPC.TL_photoPathSize) {
                photoPathSize = (TLRPC.TL_photoPathSize) photoSize;
            } else if (photoSize instanceof TLRPC.TL_photoSize && usePhotoSize) {
                w = photoSize.w;
                h = photoSize.h;
            }
        }
        if (photoPathSize != null && photoPathSize.svgPath != null && w != 0 && h != 0) {
            SvgHelper.SvgDrawable pathThumb = SvgHelper.getDrawableByPath(photoPathSize.svgPath, w, h);
            if (pathThumb != null) {
                pathThumb.setupGradient(colorKey, alpha, false);
            }
            return pathThumb;
        }
        return null;
    }
}

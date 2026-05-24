/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.view.TextureView;

/**
 * NativeDo stub of org.telegram.ui.Components.VideoEditTextureView.
 * Upstream renders video frames with filter shaders for the editor. NativeDo
 * doesn't edit video — only the type signature is preserved.
 */
public class VideoEditTextureView extends TextureView {

    public VideoEditTextureView(Context context) {
        super(context);
    }

    public VideoEditTextureView(Context context, java.io.File file) {
        super(context);
    }

    public int getVideoWidth() {
        return 0;
    }

    public int getVideoHeight() {
        return 0;
    }

    public void release() {
    }
}

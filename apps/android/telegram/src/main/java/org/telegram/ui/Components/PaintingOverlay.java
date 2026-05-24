/* NativeDo stub of org.telegram.ui.Components.PaintingOverlay. */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.FrameLayout;

import java.util.ArrayList;

import org.telegram.messenger.VideoEditedInfo;

public class PaintingOverlay extends FrameLayout {
    public PaintingOverlay(Context context) {
        super(context);
    }
    public void setBitmap(Bitmap bitmap) {}
    public void setData(String path, ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean hasAnimated) {}
    public void setData(String path, ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean hasAnimated, boolean force) {}
    public void reset() {}
    public boolean isFresh() { return false; }
    public void hideEntities() {}
    public void showAll() {}
}

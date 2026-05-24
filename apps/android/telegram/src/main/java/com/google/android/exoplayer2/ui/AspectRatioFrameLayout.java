// NativeDo stub. PhotoViewer subclasses this to host SurfaceView and override
// onMeasure. We provide the same setAspectRatio API surface so subclasses compile.
package com.google.android.exoplayer2.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class AspectRatioFrameLayout extends FrameLayout {

    public static final int RESIZE_MODE_FIT = 0;
    public static final int RESIZE_MODE_FIXED_WIDTH = 1;
    public static final int RESIZE_MODE_FIXED_HEIGHT = 2;
    public static final int RESIZE_MODE_FILL = 3;
    public static final int RESIZE_MODE_ZOOM = 4;

    private float videoAspectRatio;
    private int resizeMode = RESIZE_MODE_FIT;

    public interface AspectRatioListener {
        void onAspectRatioUpdated(float targetAspectRatio, float naturalAspectRatio, boolean aspectRatioMismatch);
    }

    public AspectRatioFrameLayout(Context context) {
        super(context);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAspectRatio(float widthHeightRatio) {
        if (this.videoAspectRatio != widthHeightRatio) {
            this.videoAspectRatio = widthHeightRatio;
            requestLayout();
        }
    }

    public void setAspectRatio(float widthHeightRatio, int rotation) {
        setAspectRatio(widthHeightRatio);
    }

    public float getAspectRatio() {
        return videoAspectRatio;
    }

    public int getResizeMode() {
        return resizeMode;
    }

    public void setResizeMode(int mode) {
        if (this.resizeMode != mode) {
            this.resizeMode = mode;
            requestLayout();
        }
    }

    public void setAspectRatioListener(AspectRatioListener listener) {
    }
}

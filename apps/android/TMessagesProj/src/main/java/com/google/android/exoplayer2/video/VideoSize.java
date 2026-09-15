// NativeDo stub. PhotoViewer reads `vs.width` / `vs.height`.
package com.google.android.exoplayer2.video;

public final class VideoSize {
    public final int width;
    public final int height;
    public final int unappliedRotationDegrees;
    public final float pixelWidthHeightRatio;

    public VideoSize(int width, int height) {
        this(width, height, 0, 1f);
    }

    public VideoSize(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        this.width = width;
        this.height = height;
        this.unappliedRotationDegrees = unappliedRotationDegrees;
        this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    }
}

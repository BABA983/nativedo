// NativeDo stub.
package com.google.android.exoplayer2.video;

import com.google.android.exoplayer2.Format;

public interface VideoFrameMetadataListener {
    void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, Format format, android.media.MediaFormat mediaFormat);
}

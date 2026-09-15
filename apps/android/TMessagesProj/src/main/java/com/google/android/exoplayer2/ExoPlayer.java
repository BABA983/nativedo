// NativeDo stub. LinuxDo doesn't play videos; ExoPlayer state constants are
// the only thing PhotoViewer's video branches actually read at compile time.
package com.google.android.exoplayer2;

public interface ExoPlayer {
    int STATE_IDLE = 1;
    int STATE_BUFFERING = 2;
    int STATE_READY = 3;
    int STATE_ENDED = 4;
}

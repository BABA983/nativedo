// NativeDo stub. Player is an interface that an ExoPlayer impl satisfies.
// LinuxDo only references the type — implementations are no-ops.
package com.google.android.exoplayer2;

public interface Player {
    int STATE_IDLE = 1;
    int STATE_BUFFERING = 2;
    int STATE_READY = 3;
    int STATE_ENDED = 4;

    interface Listener {
        default void onPlaybackStateChanged(int state) {}
        default void onIsPlayingChanged(boolean isPlaying) {}
    }
}

// NativeDo stub. LinuxDo does not play videos: media3's ExoPlayer state
// constants are the only thing vendored Telegram code reads at compile time
// (VideoPlayerHolderBase's state checks). If playback is ever implemented,
// take androidx.media3 from Maven and delete this file.
package androidx.media3.exoplayer;

public interface ExoPlayer {
    int STATE_IDLE = 1;
    int STATE_BUFFERING = 2;
    int STATE_READY = 3;
    int STATE_ENDED = 4;
}

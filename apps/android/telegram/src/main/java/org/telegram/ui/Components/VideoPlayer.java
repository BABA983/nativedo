package org.telegram.ui.Components;

import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

public class VideoPlayer {
    public boolean allowMultipleInstances;

    public interface VideoPlayerDelegate {
        default void onStateChanged(boolean playWhenReady, int playbackState) {}
        default void onError(VideoPlayer player, Exception e) {}
        default void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {}
        default void onRenderedFirstFrame() {}
        default void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surfaceTexture) {}
        default boolean onSurfaceDestroyed(android.graphics.SurfaceTexture surfaceTexture) { return false; }
    }

    public VideoPlayer(boolean autoplay, boolean audioDisabled) {}
    public void setDelegate(VideoPlayerDelegate delegate) {}
    public void setPlaybackSpeed(float speed) {}
    public void preparePlayer(Uri uri, String type) {}
    public void preparePlayer(Uri uri, String type, int priority, int streamType) {}
    public void setPlayWhenReady(boolean value) {}
    public void setWorkerQueue(Object queue) {}
    public void setSurface(Surface surface) {}
    public void setSurfaceView(SurfaceView surfaceView) {}
    public void setTextureView(TextureView textureView) {}
    public void play() {}
    public void pause() {}
    public boolean isPlaying() { return false; }
    public boolean createdWithAudioTrack() { return false; }
    public void setVolume(float volume) {}
    public void seekTo(long position) {}
    public void seekTo(long position, boolean fast) {}
    public void seekTo(long position, boolean fast, Runnable done) { if (done != null) done.run(); }
    public long getCurrentPosition() { return 0L; }
    public long getDuration() { return 0L; }
    public void releasePlayer(boolean async) {}
    public void setIsStory() {}
}

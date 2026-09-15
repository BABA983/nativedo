package org.telegram.ui.Components;

import android.content.Context;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class WebPlayerView extends FrameLayout {
    public interface WebPlayerViewDelegate {
        default void onInitFailed() {}
        default void onVideoSizeChanged(float aspectRatio, int rotation) {}
        default void onInlineSurfaceTextureReady() {}
        default TextureView onSwitchToFullscreen(View controlsView, boolean fullscreen, float aspectRatio, int rotation, boolean byButton) { return null; }
        default void prepareToSwitchInlineMode(boolean inline, Runnable switchInlineModeRunnable, float aspectRatio, boolean animated) {}
        default TextureView onSwitchInlineMode(View controlsView, boolean inline, int videoWidth, int videoHeight, int rotation, boolean animated) { return null; }
        default void onSharePressed() {}
        default void onPlayStateChanged(WebPlayerView playerView, boolean playing) {}
        default boolean checkInlinePermissions() { return false; }
        default ViewGroup getTextureViewContainer() { return null; }
    }

    public WebPlayerView(Context context, boolean allowInlineAnimation, boolean openUrl, WebPlayerViewDelegate delegate) {
        super(context);
    }

    public void setDelegate(WebPlayerViewDelegate delegate) {}
    public void destroy() {}
    public void exitFullscreen() {}
    public boolean isPlaying() { return false; }
    public void play() {}
    public void pause() {}
    public boolean loadVideo(String url, org.telegram.tgnet.TLRPC.Photo photo, org.telegram.tgnet.TLRPC.WebPage webPage, Object parentObject, boolean autoplay) { return false; }
}

package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.MessageObject;

public class PinchToZoomHelper {
    public interface Callback {
        default void onZoomStarted(MessageObject messageObject) {}
    }

    public interface ClipBoundsListener {
        void run(int[] topBottom);
    }

    public PinchToZoomHelper(View parent, View container) {}
    public void setClipBoundsListener(ClipBoundsListener listener) {}
    public void setCallback(Callback callback) {}
    public boolean checkPinchToZoom(android.view.MotionEvent event) { return false; }
    public boolean checkPinchToZoom(android.view.MotionEvent event, View view, Object imageReceiver, View parentView, View textureView, MessageObject messageObject) { return false; }
    public boolean checkPinchToZoom(android.view.MotionEvent event, View view, android.graphics.RectF rect, android.graphics.RectF imageRect, Object receiver, Object parentObject, MessageObject messageObject, int[] coords, boolean isVideo, boolean allow) { return false; }
    public boolean isInOverlayMode() { return false; }
    public boolean isInOverlayModeFor(View view) { return false; }
    public boolean onTouchEvent(android.view.MotionEvent event) { return false; }
}

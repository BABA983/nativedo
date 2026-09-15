/* NativeDo stub — ContentPreviewViewer. */
package org.telegram.ui.Components;

public class ContentPreviewViewer {
    private static final ContentPreviewViewer Instance = new ContentPreviewViewer();

    public static ContentPreviewViewer getInstance() {
        return Instance;
    }

    public boolean onInterceptTouchEvent(android.view.MotionEvent event, android.view.View listView, int height, Object delegate, Object resourcesProvider) {
        return false;
    }

    public void onTouch(android.view.MotionEvent event, android.view.View listView, int height, Object delegate, Object resourcesProvider) {}
    public void destroyForFragment(Object fragment) {}
    public void reset() {}
}

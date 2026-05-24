/* NativeDo stub — Components/OtherDocumentPlaceholderDrawable. */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;

public class OtherDocumentPlaceholderDrawable extends Drawable {
    public OtherDocumentPlaceholderDrawable(Context context, android.view.View parentView, MessageObject messageObject) {}
    public OtherDocumentPlaceholderDrawable(Context context, android.view.View parentView, MessageObject messageObject, Theme.ResourcesProvider resourcesProvider) {}
    @Override public void draw(Canvas canvas) {}
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter colorFilter) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    public void onAttachedToWindow() {}
    public void onDetachedFromWindow() {}
}

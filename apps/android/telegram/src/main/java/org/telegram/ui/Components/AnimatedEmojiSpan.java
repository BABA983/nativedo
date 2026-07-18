package org.telegram.ui.Components;

import android.text.Layout;
import android.view.View;
import android.widget.TextView;
import android.content.Context;

public class AnimatedEmojiSpan {

    /** Pin ImageReceiver implements this for animated-emoji invalidation. Host stub. */
    public interface InvalidateHolder {
        void invalidate();
    }

    public static class TextViewEmojis extends TextView {
        public TextViewEmojis(Context context) {
            super(context);
        }
    }

    public static class EmojiGroupedSpans {
        public void release() {}
        public void replaceFontMetrics(android.graphics.Paint.FontMetricsInt fontMetricsInt) {}
        public void clearPositions() {}
    }

    public static EmojiGroupedSpans update(int cacheType, View parent, EmojiGroupedSpans drawables, Layout layout) {
        return drawables == null ? new EmojiGroupedSpans() : drawables;
    }

    public static EmojiGroupedSpans update(int cacheType, View parent, EmojiGroupedSpans drawables, java.util.ArrayList<? extends Layout> layouts) {
        return drawables == null ? new EmojiGroupedSpans() : drawables;
    }

    public static void release(View parent, EmojiGroupedSpans drawables) {}
    public static void release(EmojiGroupedSpans drawables) {}

    public static void drawAnimatedEmojis(android.graphics.Canvas canvas, Layout layout, EmojiGroupedSpans drawables, float drawingYOffset, java.util.List<?> spoilers, float top, float bottom, float drawingYOffsetMore, float alpha) {}
    public static void drawAnimatedEmojis(android.graphics.Canvas canvas, Layout layout, EmojiGroupedSpans drawables, float drawingYOffset, java.util.List<?> spoilers, float top, float bottom, float drawingYOffsetMore, float alpha, android.graphics.ColorFilter colorFilter) {}
}

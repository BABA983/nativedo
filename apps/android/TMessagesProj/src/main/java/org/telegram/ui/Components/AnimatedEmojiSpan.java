package org.telegram.ui.Components;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;
import android.content.Context;

public class AnimatedEmojiSpan extends CharacterStyle {

    @Override
    public void updateDrawState(TextPaint tp) {}

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
        /** Pin holder list; this stub never fills it, so the renderer's isEmpty() checks are stable. */
        public final java.util.ArrayList<Object> holders = new java.util.ArrayList<>();
        public boolean isEmpty() { return holders.isEmpty(); }
        public void release() {}
        public void replaceFontMetrics(android.graphics.Paint.FontMetricsInt fontMetricsInt) {}
        public void clearPositions() {}
        public void recordPositions(boolean save) {}
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

    /** Pin signature: re-measures the emoji for the paint that will draw it. */
    public void applyFontMetrics(android.graphics.Paint.FontMetricsInt fontMetricsInt, int cacheType) {}

    // --- pin surface the rich-message renderer reads. This class stays a host stub: it stores the
    // span's identity/metrics but does not lay out or draw an emoji yet (the emoji family is a
    // separate alignment project). ---

    public long documentId;
    private float scale;
    private android.graphics.Paint.FontMetricsInt fontMetrics;
    private boolean preserveFontMetrics;
    private int minimumLineHeight;
    public float size = org.telegram.messenger.AndroidUtilities.dp(20);
    public int cacheType = -1;

    public AnimatedEmojiSpan(long documentId, android.graphics.Paint.FontMetricsInt fontMetrics) {
        this(documentId, 1.2f, fontMetrics);
    }

    public AnimatedEmojiSpan(long documentId, float scale, android.graphics.Paint.FontMetricsInt fontMetrics) {
        this.documentId = documentId;
        this.scale = scale;
        this.fontMetrics = fontMetrics;
        if (fontMetrics != null) {
            size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            if (size == 0) {
                size = org.telegram.messenger.AndroidUtilities.dp(20);
            }
        }
    }

    public AnimatedEmojiSpan setSize(int size) {
        this.size = size;
        return this;
    }

    public long getDocumentId() {
        return documentId;
    }

    public void setPreserveFontMetrics(boolean value) {
        preserveFontMetrics = value;
    }

    public void setMinimumLineHeight(int value) {
        minimumLineHeight = value;
    }

    /** Stub measure: the pin sizes the span from the emoji drawable, which this port does not load. */
    public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
        if (fontMetrics != null && fm != null) {
            fm.top = fontMetrics.top;
            fm.ascent = fontMetrics.ascent;
            fm.descent = fontMetrics.descent;
            fm.bottom = fontMetrics.bottom;
        }
        return (int) Math.max(size, 1);
    }

    public static EmojiGroupedSpans update(int cacheType, View view, boolean invalidateParent, EmojiGroupedSpans drawables, Layout... layouts) {
        return drawables == null ? new EmojiGroupedSpans() : drawables;
    }
}

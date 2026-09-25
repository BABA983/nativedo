package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

/**
 * NativeDo host surface for upstream's emoji span.
 *
 * <p>Upstream resolves {@code document_id} through Telegram's emoji/document store and draws the
 * result; this tree has no such store, so the image comes from a resolver the app installs
 * ({@link InlineImageResolver}) - the same seam shape as {@code RichMediaUploadBridge}. The rich
 * renderer builds the span exactly as it does upstream ({@code RichMessageLayout} and
 * {@code RichTextStyle} both do), and everything this class reads is the pin's surface: the
 * document id, the scale, the font metrics, {@code setSize}, the {@code EmojiGroupedSpans}
 * lifecycle.</p>
 *
 * <p>A span with no image behind its id (no resolver installed, or an id the app does not know)
 * draws the alt text it covers instead of a hole, so a line never loses the run and copy/selection
 * keep the alt.</p>
 */
public class AnimatedEmojiSpan extends ReplacementSpan {

    /**
     * The app's end of the span: a registered id -> the image to draw at that size. Installed once
     * by {@code :app}; {@code :TMessagesProj} only sees this interface.
     */
    public interface InlineImageResolver {
        /** The image for a registered id at this size, or null when the id is not one of ours. */
        Drawable imageFor(long documentId, int sizePx);

        /** The span is laid out inside {@code host}: a URL image can start loading now. */
        void attach(long documentId, int sizePx, View host);

        /** {@code host} is going away; release whatever {@link #attach} started. */
        void detach(long documentId);
    }

    private static volatile InlineImageResolver inlineImageResolver;

    public static void setInlineImageResolver(InlineImageResolver resolver) {
        inlineImageResolver = resolver;
    }

    public static InlineImageResolver getInlineImageResolver() {
        return inlineImageResolver;
    }

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
        /** Pin holder list; this port fills it with the spans whose images it attached. */
        public final java.util.ArrayList<Object> holders = new java.util.ArrayList<>();
        public boolean isEmpty() { return holders.isEmpty(); }
        public void release() { holders.clear(); }
        public void replaceFontMetrics(android.graphics.Paint.FontMetricsInt fontMetricsInt) {}
        public void clearPositions() {}
        public void recordPositions(boolean save) {}
    }

    public static EmojiGroupedSpans update(int cacheType, View parent, EmojiGroupedSpans drawables, Layout layout) {
        return update(cacheType, parent, false, drawables, layout);
    }

    public static EmojiGroupedSpans update(int cacheType, View parent, EmojiGroupedSpans drawables, java.util.ArrayList<? extends Layout> layouts) {
        Layout[] array = layouts == null ? new Layout[0] : layouts.toArray(new Layout[0]);
        return update(cacheType, parent, false, drawables, array);
    }

    /**
     * Pin signature. Upstream collects the layout's animated emoji drawables so the draw pass can
     * paint them; this port binds each span's URL image to the parent view instead, so a load can
     * invalidate the view and the span's own {@link #draw} paints the bitmap.
     */
    public static EmojiGroupedSpans update(int cacheType, View parent, boolean invalidateParent, EmojiGroupedSpans drawables, Layout... layouts) {
        if (drawables == null) {
            drawables = new EmojiGroupedSpans();
        }
        final InlineImageResolver resolver = inlineImageResolver;
        if (resolver == null || parent == null || layouts == null) {
            return drawables;
        }
        for (Layout layout : layouts) {
            if (layout == null || !(layout.getText() instanceof Spanned)) {
                continue;
            }
            final CharSequence text = layout.getText();
            for (AnimatedEmojiSpan span : ((Spanned) text).getSpans(0, text.length(), AnimatedEmojiSpan.class)) {
                if (span.documentId == 0 || drawables.holders.contains(span)) {
                    continue;
                }
                resolver.attach(span.documentId, Math.max(1, Math.round(span.size)), parent);
                drawables.holders.add(span);
            }
        }
        return drawables;
    }

    public static void release(View parent, EmojiGroupedSpans drawables) {
        final InlineImageResolver resolver = inlineImageResolver;
        if (drawables == null) {
            return;
        }
        if (resolver != null) {
            for (Object holder : drawables.holders) {
                if (holder instanceof AnimatedEmojiSpan) {
                    resolver.detach(((AnimatedEmojiSpan) holder).documentId);
                }
            }
        }
        drawables.holders.clear();
    }

    public static void release(EmojiGroupedSpans drawables) {
        if (drawables != null) {
            drawables.holders.clear();
        }
    }

    public static void drawAnimatedEmojis(android.graphics.Canvas canvas, Layout layout, EmojiGroupedSpans drawables, float drawingYOffset, java.util.List<?> spoilers, float top, float bottom, float drawingYOffsetMore, float alpha) {}

    public static void drawAnimatedEmojis(android.graphics.Canvas canvas, Layout layout, EmojiGroupedSpans drawables, float drawingYOffset, java.util.List<?> spoilers, float top, float bottom, float drawingYOffsetMore, float alpha, android.graphics.ColorFilter colorFilter) {}

    // --- the pin surface the rich-message renderer reads ---

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

    /** Pin body: the span re-measures itself for the metrics of the paint that will draw it. */
    public void replaceFontMetrics(android.graphics.Paint.FontMetricsInt newMetrics) {
        this.fontMetrics = newMetrics;
        if (fontMetrics != null) {
            size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
            if (size == 0) {
                size = org.telegram.messenger.AndroidUtilities.dp(20);
            }
        }
    }

    /** Pin field the rich-message HTML export reads. */
    public boolean standard;

    public void setPreserveFontMetrics(boolean value) {
        preserveFontMetrics = value;
    }

    public void setMinimumLineHeight(int value) {
        minimumLineHeight = value;
    }

    /** Pin signature: re-measures the emoji for the paint that will draw it. */
    public void applyFontMetrics(android.graphics.Paint.FontMetricsInt fontMetricsInt, int cacheType) {
        replaceFontMetrics(fontMetricsInt);
    }

    private Drawable resolvedImage() {
        final InlineImageResolver resolver = inlineImageResolver;
        if (resolver == null || documentId == 0) {
            return null;
        }
        return resolver.imageFor(documentId, Math.max(1, Math.round(size)));
    }

    /**
     * The pin reserves a square the emoji's size and lets the line grow to fit it. With no image the
     * span measures the alt text it covers, so the run is never swallowed.
     */
    @Override
    public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
        final Drawable image = resolvedImage();
        if (image == null) {
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            return (int) Math.ceil(paint.measureText(text, start, end));
        }
        final int side = Math.max(1, Math.round(size));
        image.setBounds(0, 0, side, side);
        if (fm != null) {
            final android.graphics.Paint.FontMetricsInt paintMetrics = paint.getFontMetricsInt();
            fm.top = paintMetrics.top;
            fm.ascent = paintMetrics.ascent;
            fm.descent = paintMetrics.descent;
            fm.bottom = paintMetrics.bottom;
            fm.leading = paintMetrics.leading;
            if (!preserveFontMetrics) {
                final int textHeight = paintMetrics.descent - paintMetrics.ascent;
                int wanted = Math.max(side, minimumLineHeight);
                if (wanted > textHeight) {
                    final int extra = wanted - textHeight;
                    fm.ascent = paintMetrics.ascent - extra / 2;
                    fm.descent = paintMetrics.descent + (extra - extra / 2);
                    fm.top = Math.min(fm.top, fm.ascent);
                    fm.bottom = Math.max(fm.bottom, fm.descent);
                }
            }
        }
        return side;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        final Drawable image = resolvedImage();
        if (image == null) {
            canvas.drawText(text, start, end, x, y, paint);
            return;
        }
        final int side = Math.max(1, Math.round(size));
        image.setBounds(0, 0, side, side);
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float centerY = y + (metrics.ascent + metrics.descent) / 2f;
        canvas.save();
        canvas.translate(x, centerY - side / 2f);
        image.draw(canvas);
        canvas.restore();
    }
}

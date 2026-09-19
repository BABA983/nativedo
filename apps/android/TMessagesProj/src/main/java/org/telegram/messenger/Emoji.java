/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;


/**
 * NativeDo shim of org.telegram.messenger.Emoji helpers.
 * Upstream loads emoji glyph atlases. NativeDo keeps system emoji drawing, but
 * still wraps emoji codepoint runs in Telegram-shaped spans so text layout uses
 * the same replacement-span contract as ArticleViewer.
 */
public final class Emoji {

    /** Upstream flag toggled by SimpleTextView during emoji drawing. */
    public static float emojiDrawingYOffset;
    public static boolean emojiDrawingUseAlpha = true;

    private Emoji() {
    }

    public static class EmojiDrawable extends Drawable {
        @Override public void draw(Canvas canvas) {}
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        public boolean isLoaded() { return true; }
    }

    public static class EmojiSpan extends ReplacementSpan {
        private final EmojiDrawable drawable = new EmojiDrawable();
        public Paint.FontMetricsInt fontMetrics;
        public float scale = 1f;
        public int size = AndroidUtilities.dp(20);
        public String emoji;
        public boolean drawn;
        public float lastDrawX;
        public float lastDrawY;
        private final int verticalAlignment;

        private boolean preserveFontMetrics;
        private int minimumLineHeight;

        public EmojiSpan(Paint.FontMetricsInt original) {
            this(original, DynamicDrawableSpan.ALIGN_BOTTOM);
        }

        public EmojiSpan setPreserveFontMetrics(boolean preserveFontMetrics) {
            this.preserveFontMetrics = preserveFontMetrics;
            return this;
        }

        public EmojiSpan setMinimumLineHeight(int minimumLineHeight) {
            this.minimumLineHeight = minimumLineHeight;
            return this;
        }

        public EmojiSpan(Paint.FontMetricsInt original, int verticalAlignment) {
            this.verticalAlignment = verticalAlignment;
            fontMetrics = original;
            if (original != null) {
                size = Math.abs(original.descent) + Math.abs(original.ascent);
                if (size == 0) {
                    size = AndroidUtilities.dp(20);
                }
            }
        }

        public void replaceFontMetrics(Paint.FontMetricsInt newMetrics, int newSize) {
            fontMetrics = newMetrics;
            size = newSize;
        }

        public void replaceFontMetrics(Paint.FontMetricsInt newMetrics) {
            fontMetrics = newMetrics;
            if (fontMetrics != null) {
                size = Math.abs(fontMetrics.descent) + Math.abs(fontMetrics.ascent);
                if (size == 0) {
                    size = AndroidUtilities.dp(20);
                }
            }
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            int scaledSize = (int) (scale * size);
            if (fm != null) {
                if (fontMetrics != null) {
                    fm.ascent = fontMetrics.ascent;
                    fm.descent = fontMetrics.descent;
                    fm.top = fontMetrics.top;
                    fm.bottom = fontMetrics.bottom;
                    fm.leading = fontMetrics.leading;
                } else {
                    int offset = AndroidUtilities.dp(8);
                    int halfSize = AndroidUtilities.dp(10);
                    fm.top = -halfSize - offset;
                    fm.bottom = halfSize - offset;
                    fm.ascent = fm.top;
                    fm.descent = fm.bottom;
                    fm.leading = 0;
                }
            }
            drawable.setBounds(0, 0, scaledSize, scaledSize);
            return scaledSize;
        }

        private float emojiTop(int y, int bottom, int scaledSize) {
            if (verticalAlignment == DynamicDrawableSpan.ALIGN_BASELINE) {
                return y - scaledSize;
            }
            return bottom - scaledSize;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            lastDrawX = x + scale * size / 2f;
            lastDrawY = top + (bottom - top) / 2f;
            drawn = true;

            int scaledSize = (int) (scale * size);
            float originalTextSize = paint.getTextSize();
            boolean needRestore = false;
            float translateY = emojiDrawingYOffset - (size - scaledSize) / 2f;
            if (translateY != 0f) {
                needRestore = true;
                canvas.save();
                canvas.translate(0f, translateY);
            }
            if (scale != 1f) {
                paint.setTextSize(originalTextSize * scale);
            }
            float textWidth = paint.measureText(text, start, end);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float emojiTop = emojiTop(y, bottom, scaledSize);
            float drawY = emojiTop + (scaledSize - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
            canvas.drawText(text, start, end, x + (scaledSize - textWidth) / 2f, drawY, paint);
            if (scale != 1f) {
                paint.setTextSize(originalTextSize);
            }
            if (needRestore) {
                canvas.restore();
            }
        }

        public Drawable getDrawable() {
            return drawable;
        }
    }

    public static boolean isValidEmoji(CharSequence code) {
        if (code == null || code.length() == 0) {
            return false;
        }
        ArrayList<EmojiSpanRange> ranges = parseEmojis(code);
        return ranges.size() == 1 && ranges.get(0).start == 0 && ranges.get(0).end == code.length();
    }

    public static CharSequence replaceEmoji(CharSequence text, Paint.FontMetricsInt fontMetrics, boolean createNew) {
        return replaceEmoji(text, fontMetrics, createNew, null);
    }

    public static CharSequence replaceEmoji(CharSequence text, Paint.FontMetricsInt fontMetrics, boolean createNew, float scale) {
        return replaceEmoji(text, fontMetrics, createNew, null, DynamicDrawableSpan.ALIGN_BOTTOM, scale, 0);
    }

    public static CharSequence replaceEmoji(CharSequence text, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly) {
        return replaceEmoji(text, fontMetrics, createNew, emojiOnly, DynamicDrawableSpan.ALIGN_BOTTOM);
    }

    public static CharSequence replaceEmoji(CharSequence text, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly, int alignment) {
        return replaceEmoji(text, fontMetrics, createNew, emojiOnly, alignment, 1.0f, 0);
    }

    public static void replaceEmoji(SpannableStringBuilder builder, Paint.FontMetricsInt fontMetrics, boolean createNew) {
        replaceEmoji((CharSequence) builder, fontMetrics, false);
    }

    public static CharSequence replaceEmoji(CharSequence text, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly, int alignment, float scale, int minusLimit) {
        if (text == null || text.length() == 0) {
            return text;
        }
        Spannable spannable;
        if (!createNew && text instanceof Spannable) {
            spannable = (Spannable) text;
        } else {
            spannable = Spannable.Factory.getInstance().newSpannable(text);
        }

        ArrayList<EmojiSpanRange> ranges = parseEmojis(spannable, emojiOnly);
        if (ranges.isEmpty()) {
            return text;
        }
        int limitCount = (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH ? 100 : 50) - minusLimit;
        for (int i = 0; i < ranges.size(); i++) {
            try {
                EmojiSpanRange range = ranges.get(i);
                if (!hasReplacementSpan(spannable, range.start, range.end)) {
                    EmojiSpan span = new EmojiSpan(fontMetrics, alignment);
                    span.emoji = range.code == null ? null : range.code.toString();
                    span.scale = scale;
                    spannable.setSpan(span, range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if ((Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 29) && (i + 1) >= limitCount) {
                break;
            }
        }
        return spannable;
    }

    private static boolean hasReplacementSpan(Spannable spannable, int start, int end) {
        ReplacementSpan[] spans = spannable.getSpans(start, end, ReplacementSpan.class);
        for (ReplacementSpan span : spans) {
            int spanStart = spannable.getSpanStart(span);
            int spanEnd = spannable.getSpanEnd(span);
            if (spanStart < end && spanEnd > start) {
                return true;
            }
        }
        return false;
    }

    public static boolean fullyConsistsOfEmojis(CharSequence text) {
        int[] emojiOnly = new int[1];
        parseEmojis(text, emojiOnly);
        return emojiOnly[0] > 0;
    }

    public static ArrayList<EmojiSpanRange> parseEmojis(CharSequence text) {
        return parseEmojis(text, null);
    }

    public static ArrayList<EmojiSpanRange> parseEmojis(CharSequence text, int[] emojiOnly) {
        ArrayList<EmojiSpanRange> emojis = new ArrayList<>();
        if (text == null || text.length() == 0) {
            return emojis;
        }

        try {
            long buf = 0;
            char c;
            int startIndex = -1;
            int startLength = 0;
            int previousGoodIndex = 0;
            StringBuilder emojiCode = new StringBuilder(16);
            int length = text.length();
            boolean doneEmoji = false;
            boolean notOnlyEmoji;
            boolean resetStartIndex = false;

            for (int i = 0; i < length; i++) {
                c = text.charAt(i);
                notOnlyEmoji = false;
                if (c >= 0xD83C && c <= 0xD83E || (buf != 0 && (buf & 0xFFFFFFFF00000000L) == 0 && (buf & 0xFFFF) == 0xD83C && (c >= 0xDDE6 && c <= 0xDDFF))) {
                    if (startIndex == -1) {
                        startIndex = i;
                    } else if (resetStartIndex) {
                        startIndex = i;
                        startLength = 0;
                        resetStartIndex = false;
                    }
                    emojiCode.append(c);
                    startLength++;
                    buf <<= 16;
                    buf |= c;
                } else if ((emojiCode.length() > 0 && (c == 0x2640 || c == 0x2642 || c == 0x2695)) || (buf > 0 && (c & 0xF000) == 0xD000)) {
                    emojiCode.append(c);
                    startLength++;
                    buf = 0;
                    doneEmoji = true;
                } else if (c == 0x20E3) {
                    if (i > 0) {
                        char c2 = text.charAt(previousGoodIndex);
                        if ((c2 >= '0' && c2 <= '9') || c2 == '#' || c2 == '*') {
                            startIndex = previousGoodIndex;
                            startLength = i - previousGoodIndex + 1;
                            emojiCode.append(c2);
                            emojiCode.append(c);
                            doneEmoji = true;
                            resetStartIndex = false;
                        }
                    }
                } else if ((c == 0x00A9 || c == 0x00AE || c >= 0x203C && c <= 0x3299) && isEmojiDataChar(c)) {
                    if (startIndex == -1) {
                        startIndex = i;
                    } else if (resetStartIndex) {
                        startIndex = i;
                        startLength = 0;
                        resetStartIndex = false;
                    }
                    startLength++;
                    emojiCode.append(c);
                    doneEmoji = true;
                } else if (startIndex != -1) {
                    emojiCode.setLength(0);
                    startIndex = -1;
                    startLength = 0;
                    doneEmoji = false;
                    resetStartIndex = false;
                } else if (c != 0xfe0f && c != '\n' && c != ' ' && c != '\t') {
                    notOnlyEmoji = true;
                }

                if (doneEmoji && i + 2 < length) {
                    char next = text.charAt(i + 1);
                    if (next == 0xD83C) {
                        next = text.charAt(i + 2);
                        if (next >= 0xDFFB && next <= 0xDFFF) {
                            emojiCode.append(text.subSequence(i + 1, i + 3));
                            startLength += 2;
                            i += 2;
                        }
                    } else if (emojiCode.length() >= 2 && emojiCode.charAt(0) == 0xD83C && emojiCode.charAt(1) == 0xDFF4 && next == 0xDB40) {
                        i++;
                        while (true) {
                            if (i < text.length()) {
                                emojiCode.append(text.charAt(i));
                            }
                            if (i + 1 < text.length()) {
                                emojiCode.append(text.charAt(i + 1));
                            }
                            startLength += 2;
                            i += 2;
                            if (i >= text.length() || text.charAt(i) != 0xDB40) {
                                i--;
                                break;
                            }
                        }
                    }
                }

                previousGoodIndex = i;
                char prevCh = c;
                for (int a = 0; a < 3; a++) {
                    if (i + 1 < length) {
                        c = text.charAt(i + 1);
                        if (a == 1) {
                            if (c == 0x200D && emojiCode.length() > 0) {
                                notOnlyEmoji = false;
                                emojiCode.append(c);
                                i++;
                                startLength++;
                                doneEmoji = false;
                            }
                        } else if (prevCh == '*' || prevCh == '#' || prevCh >= '0' && prevCh <= '9') {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                startIndex = previousGoodIndex;
                                resetStartIndex = true;
                                i++;
                                startLength++;
                                if (!doneEmoji) {
                                    doneEmoji = i + 1 >= length;
                                }
                            }
                        } else if (startIndex != -1) {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                i++;
                                startLength++;
                                if (!doneEmoji) {
                                    doneEmoji = i + 1 >= length;
                                }
                            }
                        }
                    }
                }

                if (notOnlyEmoji && emojiOnly != null) {
                    emojiOnly[0] = 0;
                    emojiOnly = null;
                }
                if (doneEmoji && i + 2 < length && text.charAt(i + 1) == 0xD83C) {
                    char next = text.charAt(i + 2);
                    if (next >= 0xDFFB && next <= 0xDFFF) {
                        emojiCode.append(text.subSequence(i + 1, i + 3));
                        startLength += 2;
                        i += 2;
                    }
                }
                if (doneEmoji) {
                    if (emojiOnly != null) {
                        emojiOnly[0]++;
                    }
                    if (startIndex >= 0 && startIndex + startLength <= length) {
                        emojis.add(new EmojiSpanRange(startIndex, startIndex + startLength, emojiCode.subSequence(0, emojiCode.length())));
                    }
                    startLength = 0;
                    startIndex = -1;
                    emojiCode.setLength(0);
                    doneEmoji = false;
                    resetStartIndex = false;
                }
            }
            if (emojiOnly != null && emojiCode.length() != 0) {
                emojiOnly[0] = 0;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return emojis;
    }

    private static boolean isEmojiDataChar(char c) {
        return c == 0x00A9 ||
                c == 0x00AE ||
                c == 0x203C ||
                c == 0x2049 ||
                c == 0x2122 ||
                c == 0x2139 ||
                (c >= 0x2194 && c <= 0x2199) ||
                c == 0x21A9 ||
                c == 0x21AA ||
                (c >= 0x231A && c <= 0x231B) ||
                c == 0x2328 ||
                c == 0x23CF ||
                (c >= 0x23E9 && c <= 0x23F3) ||
                (c >= 0x23F8 && c <= 0x23FA) ||
                c == 0x24C2 ||
                (c >= 0x25AA && c <= 0x25AB) ||
                c == 0x25B6 ||
                c == 0x25C0 ||
                (c >= 0x25FB && c <= 0x25FE) ||
                (c >= 0x2600 && c <= 0x27BF) ||
                c == 0x2934 ||
                c == 0x2935 ||
                (c >= 0x2B05 && c <= 0x2B07) ||
                c == 0x2B1B ||
                c == 0x2B1C ||
                c == 0x2B50 ||
                c == 0x2B55 ||
                c == 0x3030 ||
                c == 0x303D ||
                c == 0x3297 ||
                c == 0x3299;
    }

    public static class EmojiSpanRange {
        public final int start;
        public final int end;
        public final CharSequence code;

        public EmojiSpanRange(int start, int end, CharSequence code) {
            this.start = start;
            this.end = end;
            this.code = code;
        }
    }
}

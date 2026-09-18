/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import android.text.style.CharacterStyle;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;

import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

/**
 * NativeDo stub of org.telegram.messenger.MediaDataController.
 * Upstream manages stickers, animated emojis, GIF favorites, and sticker-set
 * lookup. NativeDo doesn't render Telegram stickers; only the methods
 * ImageLocation references are exposed.
 */
public final class MediaDataController {

    public static final int MEDIA_PHOTOVIDEO = 0;
    public static final int MEDIA_FILE = 1;
    public static final int MEDIA_AUDIO = 2;
    public static final int MEDIA_URL = 3;
    public static final int MEDIA_MUSIC = 4;
    public static final int MEDIA_GIF = 5;
    public static final int MEDIA_PHOTOS_ONLY = 6;
    public static final int MEDIA_VIDEOS_ONLY = 7;

    private static final MediaDataController[] Instance = new MediaDataController[UserConfig.MAX_ACCOUNT_COUNT];

    private MediaDataController() {
    }

    public static MediaDataController getInstance(int account) {
        MediaDataController local = Instance[account];
        if (local == null) {
            synchronized (MediaDataController.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new MediaDataController();
                }
            }
        }
        return local;
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Document document) {
        return null;
    }

    public void setPlaceholderImage(org.telegram.ui.Components.BackupImageView imageView, String packName, String emoji, String size) {
    }

    public void loadMedia(long dialogId, int count, int maxId, int minDate, int type, int fromCache, int classGuid, int requestIndex, int queryId, Object delegate, String query) {
    }

    public static long calcHash(long hash, long id) {
        hash ^= hash >>> 21;
        hash ^= hash << 35;
        hash ^= hash >>> 4;
        return hash + id;
    }

    public static long calcHash(long hash, int id) {
        return calcHash(hash, (long) id);
    }

    /**
     * NativeDo port of upstream's formatting primitive: applies (or replaces) a
     * text-style run over a range, splitting the runs it crosses. Used by the
     * composer's style buttons.
     */
    private static CharacterStyle createNewSpan(CharacterStyle baseSpan, TextStyleSpan.TextStyleRun textStyleRun, TextStyleSpan.TextStyleRun newStyleRun, boolean allowIntersection) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(textStyleRun);
        if (newStyleRun != null) {
            if (allowIntersection) {
                run.merge(newStyleRun);
            } else {
                run.replace(newStyleRun);
            }
        }
        if (baseSpan instanceof TextStyleSpan) {
            return new TextStyleSpan(run);
        } else if (baseSpan instanceof URLSpanReplacement) {
            URLSpanReplacement span = (URLSpanReplacement) baseSpan;
            return new URLSpanReplacement(span.getURL(), run);
        }
        return null;
    }

    public static void addStyleToText(TextStyleSpan span, int start, int end, Spannable editable, boolean allowIntersection) {
        try {
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    CharacterStyle oldSpan = spans[a];
                    TextStyleSpan.TextStyleRun textStyleRun;
                    TextStyleSpan.TextStyleRun newStyleRun = span != null ? span.getTextStyleRun() : new TextStyleSpan.TextStyleRun();
                    if (oldSpan instanceof TextStyleSpan) {
                        TextStyleSpan textStyleSpan = (TextStyleSpan) oldSpan;
                        textStyleRun = textStyleSpan.getTextStyleRun();
                    } else if (oldSpan instanceof URLSpanReplacement) {
                        URLSpanReplacement urlSpanReplacement = (URLSpanReplacement) oldSpan;
                        textStyleRun = urlSpanReplacement.getTextStyleRun();
                        if (textStyleRun == null) {
                            textStyleRun = new TextStyleSpan.TextStyleRun();
                        }
                    } else {
                        continue;
                    }
                    if (textStyleRun == null) {
                        continue;
                    }
                    int spanStart = editable.getSpanStart(oldSpan);
                    int spanEnd = editable.getSpanEnd(oldSpan);
                    editable.removeSpan(oldSpan);
                    if (spanStart > start && end > spanEnd) {
                        editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (span != null) {
                            editable.setSpan(new TextStyleSpan(new TextStyleSpan.TextStyleRun(newStyleRun)), spanEnd, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        end = spanStart;
                    } else {
                        int startTemp = start;
                        if (spanStart <= start) {
                            if (spanStart != start) {
                                editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (spanEnd > start) {
                                if (span != null) {
                                    editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), start, Math.min(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                start = spanEnd;
                            }
                        }
                        if (spanEnd >= end) {
                            if (spanEnd != end) {
                                editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (end > spanStart && spanEnd <= startTemp) {
                                if (span != null) {
                                    editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, Math.min(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                end = spanStart;
                            }
                        }
                    }
                }
            }
            if (span != null && start < end && start < editable.length()) {
                editable.setSpan(span, start, Math.min(editable.length(), end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}

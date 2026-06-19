/*
 * SPDX-FileCopyrightText: 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import io.github.baba983.nativedo.core.ui.PostBodyLinkHighlightSpan;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;

final class ArticleLinkPressState {
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private LinkRegion pressedLink;
    private LinkSpanDrawable<ClickableSpan> pressedLinkDrawable;
    private LinkSpanDrawable<ClickableSpan> releasedLinkDrawable;
    private float downX;
    private float downY;
    private boolean longPressFired;

    boolean isPressed() {
        return pressedLink != null;
    }

    boolean draw(Canvas canvas, Runnable invalidate) {
        boolean needsInvalidate = false;
        if (releasedLinkDrawable != null) {
            if (!releasedLinkDrawable.draw(canvas)) {
                releasedLinkDrawable.reset();
                releasedLinkDrawable = null;
            } else {
                needsInvalidate = true;
            }
        }
        if (pressedLinkDrawable != null && pressedLinkDrawable.draw(canvas)) {
            needsInvalidate = true;
        }
        if (needsInvalidate) {
            invalidate.run();
        }
        return needsInvalidate;
    }

    boolean onTouch(
            MotionEvent event,
            float localX,
            float localY,
            StaticLayout layout,
            BuiltText builtText,
            PostBodyBuildContext context,
            int touchSlop,
            Runnable invalidate
    ) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                LinkRegion link = findLinkAt(layout, builtText, localX, localY);
                if (link == null) {
                    return false;
                }
                pressedLink = link;
                downX = localX;
                downY = localY;
                longPressFired = false;
                if (pressedLinkDrawable != null) {
                    pressedLinkDrawable.reset();
                }
                pressedLinkDrawable = new LinkSpanDrawable<>(null, null, localX, localY, true);
                pressedLinkDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) & 0x33ffffff);
                LinkPath path = pressedLinkDrawable.obtainNewPath();
                if (!fillCustomHighlightPath(path, layout, link)) {
                    path.setCurrentLayout(layout, link.getStart(), 0f);
                    layout.getSelectionPath(link.getStart(), link.getEnd(), path);
                }
                longPressHandler.postDelayed(() -> {
                    LinkRegion current = pressedLink;
                    if (current == null) {
                        return;
                    }
                    longPressFired = true;
                    context.getDelegate().onLinkLongPress(current.getUrl());
                    clear(false, invalidate);
                }, ViewConfiguration.getLongPressTimeout());
                invalidate.run();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressedLink == null) {
                    return false;
                }
                if (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop) {
                    clear(false, invalidate);
                    return false;
                }
                if (findLinkAt(layout, builtText, localX, localY) != pressedLink) {
                    clear(false, invalidate);
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP:
                LinkRegion linkPressed = pressedLink;
                if (linkPressed == null) {
                    return false;
                }
                LinkRegion releasedLink = findLinkAt(layout, builtText, localX, localY);
                boolean clicked = !longPressFired && releasedLink == linkPressed;
                clear(clicked, invalidate);
                if (clicked) {
                    linkPressed.getOnClick().invoke();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (pressedLink == null) {
                    return false;
                }
                clear(false, invalidate);
                return true;
            default:
                return pressedLink != null;
        }
    }

    void reset() {
        longPressHandler.removeCallbacksAndMessages(null);
        pressedLink = null;
        if (pressedLinkDrawable != null) {
            pressedLinkDrawable.reset();
            pressedLinkDrawable = null;
        }
        if (releasedLinkDrawable != null) {
            releasedLinkDrawable.reset();
            releasedLinkDrawable = null;
        }
    }

    private void clear(boolean release, Runnable invalidate) {
        longPressHandler.removeCallbacksAndMessages(null);
        pressedLink = null;
        LinkSpanDrawable<ClickableSpan> drawable = pressedLinkDrawable;
        pressedLinkDrawable = null;
        if (release && drawable != null) {
            drawable.release();
            if (releasedLinkDrawable != null) {
                releasedLinkDrawable.reset();
            }
            releasedLinkDrawable = drawable;
        } else if (drawable != null) {
            drawable.reset();
        }
        invalidate.run();
    }

    private static LinkRegion findLinkAt(StaticLayout layout, BuiltText builtText, float x, float y) {
        if (layout == null || builtText == null || y < 0 || y > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical((int) y);
        LinkRegion customBoundsLink = findCustomBoundsLinkAt(layout, builtText, line, x, y);
        if (customBoundsLink != null) {
            return customBoundsLink;
        }
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null;
        }
        int offset = layout.getOffsetForHorizontal(line, x);
        LinkRegion result = null;
        for (LinkRegion link : builtText.getLinks()) {
            if (offset >= link.getStart() && offset < link.getEnd()) {
                result = preferredLink(result, link);
            }
        }
        return result;
    }

    private static LinkRegion findCustomBoundsLinkAt(StaticLayout layout, BuiltText builtText, int line, float x, float y) {
        if (!(layout.getText() instanceof Spanned)) {
            return null;
        }
        Spanned spanned = (Spanned) layout.getText();
        LinkRegion result = null;
        for (LinkRegion link : builtText.getLinks()) {
            PostBodyLinkHighlightSpan[] spans = spanned.getSpans(link.getStart(), link.getEnd(), PostBodyLinkHighlightSpan.class);
            for (PostBodyLinkHighlightSpan span : spans) {
                int start = spanned.getSpanStart(span);
                int end = spanned.getSpanEnd(span);
                if (start < 0 || end <= start || end <= link.getStart() || start >= link.getEnd()) {
                    continue;
                }
                if (line != layout.getLineForOffset(start) || line != layout.getLineForOffset(Math.max(start, end - 1))) {
                    continue;
                }
                RectF bounds = span.linkHighlightBounds(
                        layout.getPaint(),
                        spanned,
                        start,
                        end,
                        layout.getPrimaryHorizontal(start),
                        layout.getLineTop(line),
                        layout.getLineBaseline(line),
                        layout.getLineBottom(line)
                );
                if (bounds != null && bounds.contains(x, y)) {
                    result = preferredLink(result, link);
                }
            }
        }
        return result;
    }

    private static LinkRegion preferredLink(LinkRegion current, LinkRegion candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return current.getStart() > candidate.getStart() || candidate.getEnd() > current.getEnd()
                ? candidate
                : current;
    }

    private static boolean fillCustomHighlightPath(LinkPath path, StaticLayout layout, LinkRegion link) {
        if (layout == null || link == null || !(layout.getText() instanceof Spanned)) {
            return false;
        }
        Spanned spanned = (Spanned) layout.getText();
        PostBodyLinkHighlightSpan[] spans = spanned.getSpans(link.getStart(), link.getEnd(), PostBodyLinkHighlightSpan.class);
        if (spans.length == 0) {
            return false;
        }
        int linkStart = link.getStart();
        int linkEnd = link.getEnd();
        int line = layout.getLineForOffset(linkStart);
        if (line != layout.getLineForOffset(Math.max(linkStart, linkEnd - 1))) {
            return false;
        }
        java.util.Arrays.sort(spans, (first, second) -> {
            int firstStart = spanned.getSpanStart(first);
            int secondStart = spanned.getSpanStart(second);
            if (firstStart != secondStart) {
                return firstStart - secondStart;
            }
            return spanned.getSpanEnd(first) - spanned.getSpanEnd(second);
        });
        boolean addedCustomPath = false;
        int cursor = linkStart;
        for (PostBodyLinkHighlightSpan span : spans) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            if (start < 0 || end <= start || end <= linkStart || start >= linkEnd) {
                continue;
            }
            if (line != layout.getLineForOffset(start) || line != layout.getLineForOffset(Math.max(start, end - 1))) {
                continue;
            }
            int segmentStart = Math.max(linkStart, start);
            int segmentEnd = Math.min(linkEnd, end);
            if (cursor < segmentStart) {
                path.setCurrentLayout(layout, cursor, 0f);
                layout.getSelectionPath(cursor, segmentStart, path);
            }
            RectF bounds = span.linkHighlightBounds(
                    layout.getPaint(),
                    spanned,
                    start,
                    end,
                    layout.getPrimaryHorizontal(start),
                    layout.getLineTop(line),
                    layout.getLineBaseline(line),
                    layout.getLineBottom(line)
            );
            if (bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) {
                continue;
            }
            float radius = span.linkHighlightCornerRadius();
            if (radius > 0f) {
                path.addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW);
            } else {
                path.addRect(bounds, android.graphics.Path.Direction.CW);
            }
            addedCustomPath = true;
            cursor = Math.max(cursor, segmentEnd);
        }
        if (!addedCustomPath) {
            return false;
        }
        if (cursor < linkEnd) {
            path.setCurrentLayout(layout, cursor, 0f);
            layout.getSelectionPath(cursor, linkEnd, path);
        }
        return true;
    }
}

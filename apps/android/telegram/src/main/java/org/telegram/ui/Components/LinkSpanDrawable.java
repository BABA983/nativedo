package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.text.Layout;
import android.text.style.CharacterStyle;

import java.util.ArrayList;

public class LinkSpanDrawable<S extends CharacterStyle> {
    private final S span;

    public LinkSpanDrawable(S span, Object resourcesProvider, float touchX, float touchY) {
        this.span = span;
    }

    public S getSpan() {
        return span;
    }

    public void setColor(int color) {}

    public LinkPath obtainNewPath() {
        return new LinkPath(true);
    }

    public static class LinkCollector {
        public void addLink(LinkSpanDrawable<?> drawable, Object owner) {}
        public void removeLink(LinkSpanDrawable<?> drawable) {}
        public void clear() {}
        public boolean draw(Canvas canvas, Object owner) { return false; }
        public void addLoading(LoadingDrawable drawable, Object owner) {}
        public void removeLoading(LoadingDrawable drawable, boolean animated) {}
        public static LoadingDrawable makeLoading(Layout layout, CharacterStyle span, int offset) {
            return new LoadingDrawable(null);
        }
    }
}

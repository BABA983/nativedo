package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.StaticLayout;
import android.text.style.ReplacementSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;

public class TextSelectionHelper {
    public interface TextLayoutBlock {
        StaticLayout getLayout();
        int getX();
        int getY();
        int getRow();
        CharSequence getPrefix();
    }

    public interface ArticleSelectableView {
        void fillTextLayoutBlocks(ArrayList<TextLayoutBlock> blocks);
    }

    public interface Callback {
        default void onStateChanged(boolean isSelected) {}
        default void onTextCopied() {}
    }

    public interface TranslateCallback {
        void run(CharSequence text, String fromLang, String toLang, Runnable onAlertDismiss);
    }

    public static class IgnoreCopySpannable extends ReplacementSpan {
        @Override
        public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
            return 0;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, android.graphics.Paint paint) {}
    }

    public static class TextSelectionOverlay extends View {
        public TextSelectionOverlay(Context context) { super(context); }
        public boolean checkOnTap(MotionEvent event) { return false; }
    }

    public static class ArticleTextSelectionHelper {
        public final ArrayList<TextLayoutBlock> arrayList = new ArrayList<>();
        public LinearLayoutManager layoutManager;
        private TextSelectionOverlay overlay;

        public void setParentView(ViewGroup parentView) {}
        public void setCallback(Callback callback) {}
        public void setOnTranslate(TranslateCallback callback) {}
        public TextSelectionOverlay getOverlayView(Context context) {
            if (overlay == null) {
                overlay = new TextSelectionOverlay(context);
            }
            return overlay;
        }
        public boolean isInSelectionMode() { return false; }
        public boolean isSelectable(View view) { return view instanceof ArticleSelectableView; }
        public void trySelect(View view) {}
        public void clear() {}
        public void clear(boolean animated) {}
        public void setMaybeView(int x, int y, View view) {}
        public void setTopOffset(int topOffset) {}
        public void invalidate() {}
        public void stopScrolling() {}
        public void onParentScrolled() {}
        public void draw(Canvas canvas, ArticleSelectableView view, int index) {}
    }
}

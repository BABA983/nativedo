package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import android.graphics.Paint;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ArticleViewer;

public class TableLayout extends LinearLayout {
    public interface TableLayoutDelegate {
        ArticleViewer.DrawingText createTextLayout(TLRPC.TL_pageTableCell cell, int maxWidth);
        Paint getLinePaint();
        Paint getHalfLinePaint();
        Paint getHeaderPaint();
        Paint getStripPaint();
        void onLayoutChild(ArticleViewer.DrawingText text, int x, int y);
    }

    public static class Child extends LinearLayout {
        public int x;
        public int y;
        public int width;
        public int height;
        public ArticleViewer.DrawingText textLayout;

        public Child(Context context) { super(context); }
        public int getTextX() { return 0; }
        public int getTextY() { return 0; }
        public int getRow() { return y; }
        public void setSelectionIndex(int index) {}
    }

    public TableLayout(Context context, TableLayoutDelegate delegate, Object textSelectionHelper) {
        super(context);
    }

    public void removeAllChildrens() { removeAllViews(); }
    public void setDrawLines(boolean value) {}
    public void setStriped(boolean value) {}
    public void setRtl(boolean value) {}
    public void setColumnCount(int count) {}
    public void setRowOrderPreserved(boolean value) {}
    public void addChild(View view, int col, int row, int colspan) { addView(view); }
    public void addChild(org.telegram.tgnet.TLRPC.TL_pageTableCell cell, int col, int row, int colspan) { addView(new Child(getContext())); }
    public void addChild(int col, int row, int colspan, int rowspan) { addView(new Child(getContext())); }
    @Override public Child getChildAt(int index) { return (Child) super.getChildAt(index); }
}

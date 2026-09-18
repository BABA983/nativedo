/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockTableCell and
 * TableLayout geometry patterns.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.TableLayout;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class TableBlock implements EmbeddedPostBodyBlock, TableLayout.TableLayoutDelegate {
    private static final int MIN_COLUMN_DP = 72;
    private static final int MAX_COLUMN_DP = 240;
    static final float LIST_TOP_PADDING_DP = 8f;
    static final float LIST_BOTTOM_PADDING_DP = 8f;
    static final float SCROLL_HORIZONTAL_PADDING_DP = 18f;
    static final float CELL_HORIZONTAL_PADDING_DP = 8f;
    static final float CELL_VERTICAL_PADDING_DP = 7f;
    static final float MIN_ROW_HEIGHT_DP = 36f;
    static final float MIN_CELL_TEXT_WIDTH_DP = 40f;
    static final float TABLE_CORNER_RADIUS_DP = 3f;
    static final float NESTED_BLOCK_HORIZONTAL_INSET_DP = 16f;

    private static final class TableCellLayout {
        final CookedPostBlock.TableCell cell;
        final int row;
        final int column;
        final int rowSpan;
        final int columnSpan;
        final boolean header;
        StaticLayout layout;
        int x;
        int y;
        int width;
        int height;
        int textX;
        int textY;
        int textWidth;
        int textHeight;
        int textLeft;
        final boolean richInline;
        final ArticleLinkPressState linkPressState = new ArticleLinkPressState();
        final ArrayList<SpoilerEffect> spoilers = new ArrayList<>();
        final Stack<SpoilerEffect> spoilersPool = new Stack<>();
        final Path spoilerBoundsPath = new Path();
        final Path spoilerRipplePath = new Path();
        final String spoilerStateKey;
        BuiltText builtText;
        BuiltText.Handle handle;
        boolean spoilersRevealed;
        boolean pressedSpoiler;
        float downX;
        float downY;
        final List<PostBodyBlock> blocks;
        final int[] blockTops;
        final int[] blockHeights;

        TableCellLayout(CookedPostBlock.TableCell cell, int row, int column, int rowSpan, int columnSpan, boolean header, boolean richInline, String spoilerStateKey, boolean spoilersRevealed, List<PostBodyBlock> blocks) {
            this.cell = cell;
            this.row = row;
            this.column = column;
            this.rowSpan = rowSpan;
            this.columnSpan = columnSpan;
            this.header = header;
            this.richInline = richInline;
            this.spoilerStateKey = spoilerStateKey;
            this.spoilersRevealed = spoilersRevealed;
            this.blocks = blocks;
            this.blockTops = new int[blocks.size()];
            this.blockHeights = new int[blocks.size()];
        }

        boolean hasNestedBlocks() {
            return !blocks.isEmpty();
        }

        boolean hasRichInlineText() {
            return richInline;
        }
    }

    private static final class TableSelectionBlock implements TextSelectionHelper.TextLayoutBlock {
        private final StaticLayout layout;
        private final int x;
        private final int y;
        private final int row;
        private final CharSequence prefix;
        private final StaticLayout prefixLayout;
        private final int prefixX;
        private final int prefixY;
        private final Rect clipBounds;

        TableSelectionBlock(StaticLayout layout, int x, int y, int row) {
            this(layout, x, y, row, null);
        }

        TableSelectionBlock(StaticLayout layout, int x, int y, int row, CharSequence prefix) {
            this(layout, x, y, row, prefix, null, x, y, null);
        }

        TableSelectionBlock(StaticLayout layout, int x, int y, int row, CharSequence prefix, Rect clipBounds) {
            this(layout, x, y, row, prefix, null, x, y, clipBounds);
        }

        TableSelectionBlock(StaticLayout layout, int x, int y, int row, CharSequence prefix, StaticLayout prefixLayout, int prefixX, int prefixY, Rect clipBounds) {
            this.layout = layout;
            this.x = x;
            this.y = y;
            this.row = row;
            this.prefix = prefix;
            this.prefixLayout = prefixLayout;
            this.prefixX = prefixX;
            this.prefixY = prefixY;
            this.clipBounds = clipBounds;
        }

        @Override
        public StaticLayout getLayout() {
            return layout;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getRow() {
            return row;
        }

        @Override
        public CharSequence getPrefix() {
            return prefix;
        }

        @Override
        public StaticLayout getPrefixLayout() {
            return prefixLayout;
        }

        @Override
        public int getPrefixX() {
            return prefixX;
        }

        @Override
        public int getPrefixY() {
            return prefixY;
        }

        @Override
        public Rect getClipBounds() {
            return clipBounds;
        }
    }

    private final CookedPostBlock.Table block;
    private final PostBodyBuildContext context;
    private final List<GalleryImage> gallery;
    private final PostBodyBlockState inlineState;
    private final String stateKey;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headerPaint;
    private final TextPaint bodyPaint;
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] radii = new float[8];
    private final int listTopPadding = listTopPaddingPx();
    private final int listBottomPadding = listBottomPaddingPx();
    private final int scrollHorizontalPadding = scrollHorizontalPaddingPx();
    private final HorizontalScrollView scrollView;
    private final View tableContainer;
    private final TableLayout tableLayout;
    private final PostBodyTextBuilder textBuilder;
    private final List<TableCellLayout> cells;
    private final boolean usesTelegramTableLayout;
    private int[] rowHeights = new int[0];
    private int[] columnWidths = new int[0];
    private int[] rowTops = new int[0];
    private int[] columnLefts = new int[0];
    private int contentWidth;
    private int viewportWidth;
    private int tableHeight;
    private int measuredHeight;
    private int activeCellTouchIndex = -1;
    private int activeBlockTouchIndex = -1;

    TableBlock(CookedPostBlock.Table block, PostBodyBuildContext context, List<GalleryImage> gallery, PostBodyBlockState inlineState, String stateKey) {
        this.block = block;
        this.context = context;
        this.gallery = gallery;
        this.inlineState = inlineState;
        this.stateKey = stateKey;
        applyContentColors();
        headerPaint = new TextPaint(context.getQuotePaint());
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        bodyPaint = new TextPaint(context.getQuotePaint());
        textBuilder = new PostBodyTextBuilder(context);
        cells = buildCellLayouts(tableRows());
        usesTelegramTableLayout = canUseTelegramTableLayout(cells);
        tableLayout = new TableLayout(context.getHost().getContext(), this, null);
        tableLayout.setOrientation(TableLayout.HORIZONTAL);
        tableLayout.setRowOrderPreserved(true);
        if (usesTelegramTableLayout) {
            setTelegramTableRows();
        }
        scrollView = new HorizontalScrollView(context.getHost().getContext()) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                boolean intercept = super.onInterceptTouchEvent(ev);
                if (scrollContentMeasuredWidth() > getMeasuredWidth() - scrollOverflowThresholdInsetPx() && intercept && TableBlock.this.context.getHost().getParent() != null) {
                    TableBlock.this.context.getHost().getParent().requestDisallowInterceptTouchEvent(true);
                }
                return intercept;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (scrollContentMeasuredWidth() <= getMeasuredWidth() - scrollOverflowThresholdInsetPx()) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                cancelActiveChildTouch();
                scrollContentView().invalidate();
                TableBlock.this.context.getSelectionGeometryInvalidator().invoke();
            }

            @Override
            protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
                cancelActiveChildTouch();
                scrollContentView().invalidate();
                return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (usesTelegramTableLayout) {
                    tableLayout.measure(
                            View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight(), View.MeasureSpec.UNSPECIFIED),
                            heightMeasureSpec
                    );
                    setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), tableLayout.getMeasuredHeight());
                    return;
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scrollView.setPadding(scrollHorizontalPadding, 0, scrollHorizontalPadding, 0);
        scrollView.setClipToPadding(false);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        tableContainer = new View(context.getHost().getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(Math.max(contentWidth, 1), Math.max(tableHeight, 1));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                drawTable(canvas);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return handleContentTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
            }
        };
        scrollView.addView(scrollContentView(), new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT
        ));
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        viewportWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        if (usesTelegramTableLayout) {
            scrollView.measure(
                    View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            layoutTelegramTableForCanvas();
            measuredHeight = listTopPadding + scrollView.getMeasuredHeight() + listBottomPadding;
            return measuredHeight;
        }
        if (cells.isEmpty()) {
            tableHeight = 0;
            measuredHeight = 0;
            return measuredHeight;
        }

        int rowCount = rowCount(cells);
        int columnCount = columnCount(cells);
        int horizontalPadding = cellHorizontalPaddingPx();
        int verticalPadding = cellVerticalPaddingPx();
        int minColumnWidth = AndroidUtilities.dp(MIN_COLUMN_DP);
        int maxColumnWidth = AndroidUtilities.dp(MAX_COLUMN_DP);
        int[] desiredColumnWidths = new int[columnCount];
        int[] minimumColumnWidths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            desiredColumnWidths[i] = minColumnWidth;
            minimumColumnWidths[i] = minColumnWidth;
        }

        for (TableCellLayout cell : cells) {
            TextPaint paint = cell.header ? headerPaint : bodyPaint;
            int desired = Math.round(paint.measureText(cell.cell.getText())) + horizontalPadding * 2;
            int longestWord = longestWordWidth(cell.cell.getText(), paint) + horizontalPadding * 2;
            distributeSpanDeficit(
                    desiredColumnWidths,
                    cell.column,
                    cell.columnSpan,
                    clamp(desired, minColumnWidth * cell.columnSpan, maxColumnWidth * cell.columnSpan)
            );
            distributeSpanDeficit(
                    minimumColumnWidths,
                    cell.column,
                    cell.columnSpan,
                    clamp(longestWord, minColumnWidth * cell.columnSpan, maxColumnWidth * cell.columnSpan)
            );
        }

        int tableViewportWidth = viewportWidth - scrollOverflowThresholdInsetPx();
        columnWidths = distributeColumnWidths(minimumColumnWidths, desiredColumnWidths, tableViewportWidth);
        columnLefts = cumulativeLefts(columnWidths);
        contentWidth = Math.max(sum(columnWidths), tableViewportWidth);

        rowHeights = new int[rowCount];
        for (int i = 0; i < rowHeights.length; i++) {
            rowHeights[i] = minRowHeightPx();
        }
        for (TableCellLayout cell : cells) {
            TextPaint paint = cell.header ? headerPaint : bodyPaint;
            int availableWidth = Math.max(spanSize(columnWidths, cell.column, cell.columnSpan) - horizontalPadding * 2, minCellTextWidthPx());
            if (cell.hasNestedBlocks()) {
                cell.layout = null;
                detachRichInlineHandle(cell);
                cell.builtText = null;
                cell.textHeight = measureNestedBlocks(cell, availableWidth);
                cell.textWidth = availableWidth;
                cell.textLeft = 0;
            } else {
                buildInlineCellLayout(cell, paint, availableWidth);
            }
            distributeRowSpanDeficit(rowHeights, cell.row, cell.rowSpan, cell.textHeight + verticalPadding * 2);
        }

        rowTops = cumulativeLefts(rowHeights);
        tableHeight = sum(rowHeights);
        for (TableCellLayout cell : cells) {
            cell.x = columnLefts[cell.column];
            cell.y = rowTops[cell.row];
            cell.width = spanSize(columnWidths, cell.column, cell.columnSpan);
            cell.height = spanSize(rowHeights, cell.row, cell.rowSpan);
            updateTextPosition(cell, horizontalPadding, verticalPadding);
        }
        tableContainer.requestLayout();
        scrollView.measure(
                View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        measuredHeight = listTopPadding + scrollView.getMeasuredHeight() + listBottomPadding;
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        if (measuredHeight <= 0) {
            return;
        }
        if (usesTelegramTableLayout) {
            drawTelegramTable(canvas, parentWidth);
            return;
        }
        int saveCount = canvas.save();
        canvas.clipRect(0f, 0f, parentWidth, measuredHeight);
        canvas.translate(scrollHorizontalPadding - scrollView.getScrollX(), listTopPadding);
        drawTable(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        return handleContentTouch(event, localX + scrollView.getScrollX() - scrollHorizontalPadding, localY - listTopPadding);
    }

    @Override
    public View embeddedView() {
        return scrollView;
    }

    @Override
    public void layoutEmbeddedView(int left, int top, int width, int height) {
        scrollView.layout(left, top + listTopPadding, left + width, top + listTopPadding + scrollView.getMeasuredHeight());
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        Rect clipBounds = selectionClipBounds(x, y);
        if (usesTelegramTableLayout) {
            int contentLeft = tableContentLeft();
            for (int i = 0, count = tableLayout.getChildCount(); i < count; i++) {
                TableLayout.Child child = tableLayout.getChildAt(i);
                if (child == null || child.textLayout == null) {
                    continue;
                }
                blocks.add(new TableSelectionBlock(
                        child.textLayout.getLayout(),
                        x + contentLeft + child.getTextX() - scrollView.getScrollX(),
                        y + listTopPadding + tableLayout.getTop() + child.getTextY(),
                        child.getRow(),
                        null,
                        clipBounds
                ));
            }
            return;
        }
        for (TableCellLayout cell : cells) {
            int contentLeft = tableContentLeft();
            if (cell.hasNestedBlocks()) {
                for (int i = 0; i < cell.blocks.size(); i++) {
                    int before = blocks.size();
                    cell.blocks.get(i).fillTextLayoutBlocks(
                            blocks,
                            x + contentLeft + cell.x + cell.textX - scrollView.getScrollX(),
                            y + listTopPadding + tableContainer.getTop() + cell.y + cell.textY + cell.blockTops[i]
                    );
                    for (int j = before; j < blocks.size(); j++) {
                        TextSelectionHelper.TextLayoutBlock current = blocks.get(j);
                        blocks.set(j, new TableSelectionBlock(
                                current.getLayout(),
                                current.getX(),
                                current.getY(),
                                cell.row + 10,
                                current.getPrefix(),
                                current.getPrefixLayout(),
                                current.getPrefixX(),
                                current.getPrefixY(),
                                clipBounds
                        ));
                    }
                }
            } else if (cell.layout != null) {
                blocks.add(new TableSelectionBlock(
                        cell.layout,
                        x + contentLeft + cell.x + cell.textX - scrollView.getScrollX(),
                        y + listTopPadding + tableContainer.getTop() + cell.y + cell.textY,
                        cell.row + 10,
                        null,
                        clipBounds
                ));
            }
        }
    }

    private Rect selectionClipBounds(int x, int y) {
        int top = y + listTopPadding;
        return new Rect(x, top, x + viewportWidth, top + scrollView.getMeasuredHeight());
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        for (TableCellLayout cell : cells) {
            if (!cell.hasNestedBlocks()) {
                continue;
            }
            for (int i = 0; i < cell.blocks.size(); i++) {
                RectF bounds = cell.blocks.get(i).imageBoundsInBlock(imageUrl);
                if (bounds != null) {
                    RectF result = new RectF(bounds);
                    result.offset(tableContentLeft() + cell.x + cell.textX - scrollView.getScrollX(), listTopPadding + cell.y + cell.textY + cell.blockTops[i]);
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        for (TableCellLayout cell : cells) {
            if (!cell.hasNestedBlocks()) {
                continue;
            }
            for (PostBodyBlock child : cell.blocks) {
                ImageReceiver receiver = child.imageReceiverForUrl(imageUrl);
                if (receiver != null) {
                    return receiver;
                }
            }
        }
        return null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        ImageReceiver receiver = imageReceiverForUrl(imageUrl);
        return receiver != null ? receiver.getBitmapSafe() : null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        ImageReceiver receiver = imageReceiverForUrl(imageUrl);
        if (receiver == null) {
            return null;
        }
        int[] radius = receiver.getRoundRadius(true);
        return radius != null ? radius.clone() : null;
    }

    @Override
    public void onAttachedToWindow() {
        for (TableCellLayout cell : cells) {
            if (cell.hasRichInlineText() && cell.handle == null && cell.builtText != null) {
                cell.handle = cell.builtText.attach(context.getHost());
            }
            for (int i = 0; i < cell.spoilers.size(); i++) {
                cell.spoilers.get(i).setParentView(context.getHost());
            }
            for (PostBodyBlock child : cell.blocks) {
                child.onAttachedToWindow();
            }
        }
    }

    @Override
    public void onDetachedFromWindow() {
        activeCellTouchIndex = -1;
        activeBlockTouchIndex = -1;
        for (TableCellLayout cell : cells) {
            if (cell.handle != null) {
                cell.handle.detach();
                cell.handle = null;
            }
            cell.linkPressState.reset();
            cell.pressedSpoiler = false;
            for (PostBodyBlock child : cell.blocks) {
                child.onDetachedFromWindow();
            }
        }
    }

    @Override
    public void applyTheme() {
        applyContentColors();
        for (TableCellLayout cell : cells) {
            for (PostBodyBlock child : cell.blocks) {
                child.applyTheme();
            }
        }
        scrollContentView().invalidate();
        scrollView.invalidate();
    }

    /**
     * Table surface and grid from the 12.10.2 palette; header and zebra stripes
     * stay derived from the secondary text colour, as they were.
     */
    private void applyContentColors() {
        backgroundPaint.setColor(context.getTableBackgroundColor());
        dividerPaint.setColor(context.getTableBorderColor());
        dividerPaint.setStrokeWidth(AndroidUtilities.dp(1f));
        borderPaint.setColor(context.getTableBorderColor());
        borderPaint.setStrokeWidth(AndroidUtilities.dp(1f));
        borderPaint.setStyle(Paint.Style.STROKE);
        headerBackgroundPaint.setColor(Theme.multAlpha(context.getSecondaryTextColor(), 0.10f));
        stripPaint.setColor(Theme.multAlpha(context.getSecondaryTextColor(), 0.045f));
    }

    @Override
    public ArticleViewer.DrawingText createTextLayout(TL_iv.pageTableCell cell, int maxWidth) {
        if (cell == null) {
            return null;
        }
        TextPaint paint = cell.header ? headerPaint : bodyPaint;
        CharSequence source = richTextPlain(cell.text);
        if (source.length() == 0) {
            source = " ";
        }
        ArticleViewer viewer = ArticleViewer.getInstance();
        ArticleViewer.DrawingText drawingText = viewer.new DrawingText(viewer);
        drawingText.textLayout = ArticleTextLayout.builder(source, paint, Math.max(maxWidth, 1))
                .setAlignment(alignment(cell))
                .build();
        return drawingText;
    }

    @Override
    public Paint getLinePaint() {
        return borderPaint;
    }

    @Override
    public Paint getHalfLinePaint() {
        return dividerPaint;
    }

    @Override
    public Paint getHeaderPaint() {
        return headerBackgroundPaint;
    }

    @Override
    public Paint getStripPaint() {
        return stripPaint;
    }

    @Override
    public void onLayoutChild(TableLayout.CellText text, int x, int y) {
        // Text selection geometry is collected from TableLayout.Child after measure.
    }

    private void drawTable(Canvas canvas) {
        if (measuredHeight <= 0) {
            return;
        }
        float radius = tableCornerRadiusPx();
        rect.set(0f, 0f, contentWidth, tableHeight);
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint);
        drawCellBackgrounds(canvas, radius);
        drawGrid(canvas, radius);
        for (TableCellLayout cell : cells) {
            canvas.save();
            canvas.translate(cell.x + cell.textX, cell.y + cell.textY);
            if (cell.hasNestedBlocks()) {
                int nestedWidth = Math.max(cell.width - nestedBlockHorizontalInsetPx(), minCellTextWidthPx());
                for (int i = 0; i < cell.blocks.size(); i++) {
                    canvas.save();
                    canvas.translate(0f, cell.blockTops[i]);
                    cell.blocks.get(i).draw(canvas, nestedWidth);
                    canvas.restore();
                }
            } else if (cell.layout != null) {
                if (cell.hasRichInlineText()) {
                    cell.linkPressState.draw(canvas, scrollContentView()::invalidate);
                    SpoilerTextLayout.draw(canvas, cell.layout, cell.spoilers, cell.spoilerBoundsPath, cell.spoilerRipplePath);
                } else {
                    cell.layout.draw(canvas);
                }
            }
            canvas.restore();
        }
    }

    private int tableContentLeft() {
        int left = scrollContentView().getLeft();
        return left != 0 ? left : scrollHorizontalPadding;
    }

    private View scrollContentView() {
        return usesTelegramTableLayout ? tableLayout : tableContainer;
    }

    private int scrollContentMeasuredWidth() {
        return scrollContentView().getMeasuredWidth();
    }

    private void drawTelegramTable(Canvas canvas, int parentWidth) {
        int saveCount = canvas.save();
        canvas.clipRect(0f, 0f, parentWidth, measuredHeight);
        canvas.translate(scrollHorizontalPadding - scrollView.getScrollX(), listTopPadding);
        layoutTelegramTableForCanvas();
        tableLayout.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    private void layoutTelegramTableForCanvas() {
        int width = tableLayout.getMeasuredWidth();
        int height = tableLayout.getMeasuredHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (tableLayout.getWidth() != width || tableLayout.getHeight() != height) {
            tableLayout.layout(0, 0, width, height);
        }
    }

    private static boolean canUseTelegramTableLayout(List<TableCellLayout> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        for (TableCellLayout cell : cells) {
            if (cell.hasNestedBlocks() || cell.hasRichInlineText()) {
                return false;
            }
        }
        return true;
    }

    private void setTelegramTableRows() {
        tableLayout.removeAllChildrens();
        tableLayout.setDrawLines(true);
        tableLayout.setStriped(true);
        tableLayout.setRtl(false);

        List<List<CookedPostBlock.TableCell>> rows = tableRows();
        ArrayList<Integer> rowSpans = new ArrayList<>();
        int maxCols = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<CookedPostBlock.TableCell> row = rows.get(rowIndex);
            int column = 0;
            boolean header = !block.getHeaders().isEmpty() && rowIndex == 0;
            for (CookedPostBlock.TableCell cell : row) {
                int colspan = Math.max(cell.getColspan(), 1);
                int rowspan = Math.max(cell.getRowspan(), 1);
                column = nextFreeColumn(rowSpans, column, rowIndex, colspan);
                ensureSize(rowSpans, column + colspan);
                tableLayout.addChild(toPageTableCell(cell, header), column, rowIndex, colspan);
                for (int i = column; i < column + colspan; i++) {
                    rowSpans.set(i, Math.max(rowSpans.get(i), rowIndex + rowspan));
                }
                column += colspan;
            }
            maxCols = Math.max(maxCols, rowSpans.size());
        }
        tableLayout.setColumnCount(maxCols);
    }

    private static TL_iv.pageTableCell toPageTableCell(CookedPostBlock.TableCell source, boolean header) {
        TL_iv.pageTableCell cell = new TL_iv.pageTableCell();
        TL_iv.textPlain text = new TL_iv.textPlain();
        text.text = source.getText();
        cell.text = text;
        cell.header = header;
        cell.align_center = source.getAlign() == CookedPostBlock.TableCellAlign.Center;
        cell.align_right = source.getAlign() == CookedPostBlock.TableCellAlign.End;
        cell.colspan = Math.max(source.getColspan(), 1);
        cell.rowspan = Math.max(source.getRowspan(), 1);
        return cell;
    }

    private static CharSequence richTextPlain(TL_iv.RichText text) {
        if (text instanceof TL_iv.textPlain) {
            String value = ((TL_iv.textPlain) text).text;
            return value != null ? value : "";
        }
        if (text != null && text.text != null) {
            return richTextPlain(text.text);
        }
        if (text != null && !text.texts.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (TL_iv.RichText child : text.texts) {
                builder.append(richTextPlain(child));
            }
            return builder;
        }
        return "";
    }

    private List<TableCellLayout> buildCellLayouts(List<List<CookedPostBlock.TableCell>> rows) {
        ArrayList<TableCellLayout> result = new ArrayList<>();
        ArrayList<Integer> rowSpans = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<CookedPostBlock.TableCell> row = rows.get(rowIndex);
            boolean header = !block.getHeaders().isEmpty() && rowIndex == 0;
            int column = 0;
            for (CookedPostBlock.TableCell tableCell : row) {
                int columnSpan = Math.max(tableCell.getColspan(), 1);
                int rowSpan = Math.max(tableCell.getRowspan(), 1);
                column = nextFreeColumn(rowSpans, column, rowIndex, columnSpan);
                ensureSize(rowSpans, column + columnSpan);
                boolean richInline = hasRichInlineContent(tableCell);
                String spoilerStateKey = richInline
                        ? TextLayoutBlock.inlineSpoilerStateKey("table-cell:" + stateKey + "." + rowIndex + "." + column, tableCell.getHtml())
                        : null;
                result.add(new TableCellLayout(
                        tableCell,
                        rowIndex,
                        column,
                        rowSpan,
                        columnSpan,
                        header,
                        richInline,
                        spoilerStateKey,
                        inlineState != null && spoilerStateKey != null && inlineState.getRevealedInlineSpoilersByKey().contains(spoilerStateKey),
                        createCellBlocks(tableCell, rowIndex, column)
                ));
                for (int i = column; i < column + columnSpan; i++) {
                    rowSpans.set(i, Math.max(rowSpans.get(i), rowIndex + rowSpan));
                }
                column += columnSpan;
            }
        }
        return result;
    }

    private List<PostBodyBlock> createCellBlocks(CookedPostBlock.TableCell cell, int row, int column) {
        if (cell.getBlocks() == null || cell.getBlocks().isEmpty()) {
            return Collections.emptyList();
        }
        if (cell.getBlocks().size() == 1 && cell.getBlocks().get(0) instanceof CookedPostBlock.Text) {
            return Collections.emptyList();
        }
        return PostBodyBlockFactory.INSTANCE.build(cell.getBlocks(), context, inlineState, gallery, stateKey + ".cell." + row + "." + column, false);
    }

    private static boolean hasRichInlineContent(CookedPostBlock.TableCell cell) {
        if (cell.getBlocks() == null || cell.getBlocks().size() != 1 || !(cell.getBlocks().get(0) instanceof CookedPostBlock.Text)) {
            return false;
        }
        String html = cell.getHtml();
        return html != null && html.indexOf('<') >= 0;
    }

    private void buildInlineCellLayout(TableCellLayout cell, TextPaint paint, int availableWidth) {
        if (cell.hasRichInlineText()) {
            if (cell.handle != null) {
                cell.handle.detach();
                cell.handle = null;
            }
            cell.builtText = textBuilder.build(cell.cell.getHtml(), paint);
            CharSequence source = cell.builtText.getSpannable();
            if (source.length() == 0) {
                source = " ";
            }
            cell.layout = ArticleTextLayout.builder(source, paint, availableWidth)
                    .setAlignment(alignment(cell.cell))
                    .build();
            SpoilerTextLayout.collect(context.getHost(), cell.layout, cell.spoilersPool, cell.spoilers, cell.spoilersRevealed);
            if (context.getHost().isAttachedToWindow()) {
                cell.handle = cell.builtText.attach(context.getHost());
            }
        } else {
            detachRichInlineHandle(cell);
            cell.builtText = null;
            clearInlineSpoilers(cell);
            String source = cell.cell.getText().trim().isEmpty() ? " " : cell.cell.getText();
            cell.layout = ArticleTextLayout.builder(source, paint, availableWidth)
                    .setAlignment(alignment(cell.cell))
                    .build();
        }
        updateTextMetrics(cell);
    }

    private static void detachRichInlineHandle(TableCellLayout cell) {
        if (cell.handle != null) {
            cell.handle.detach();
            cell.handle = null;
        }
    }

    private static void clearInlineSpoilers(TableCellLayout cell) {
        cell.spoilersPool.addAll(cell.spoilers);
        cell.spoilers.clear();
        cell.pressedSpoiler = false;
    }

    private int measureNestedBlocks(TableCellLayout cell, int maxWidth) {
        int y = 0;
        for (int i = 0; i < cell.blocks.size(); i++) {
            cell.blockTops[i] = y;
            int height = cell.blocks.get(i).measure(maxWidth);
            cell.blockHeights[i] = height;
            y += height;
        }
        return y;
    }

    private boolean handleContentTouch(MotionEvent event, float contentX, float contentY) {
        if (activeCellTouchIndex >= 0 && event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return dispatchActiveChildTouch(event, contentX, contentY);
        }
        for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
            TableCellLayout cell = cells.get(cellIndex);
            if (!cell.hasNestedBlocks() && !cell.hasRichInlineText()) {
                continue;
            }
            if (contentX < cell.x || contentX > cell.x + cell.width || contentY < cell.y || contentY > cell.y + cell.height) {
                continue;
            }
            float localX = contentX - cell.x - cell.textX;
            float localY = contentY - cell.y - cell.textY;
            if (cell.hasRichInlineText() && cell.layout != null) {
                boolean handled = handleRichInlineCellTouch(cell, event, localX, localY);
                if (handled && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    activeCellTouchIndex = cellIndex;
                    activeBlockTouchIndex = -1;
                }
                return handled;
            }
            for (int i = 0; i < cell.blocks.size(); i++) {
                int top = cell.blockTops[i];
                int bottom = top + cell.blockHeights[i];
                if (localY >= top && localY <= bottom) {
                    boolean handled = cell.blocks.get(i).onTouch(event, localX, localY - top);
                    if (handled && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        activeCellTouchIndex = cellIndex;
                        activeBlockTouchIndex = i;
                    }
                    return handled;
                }
            }
        }
        return false;
    }

    private boolean dispatchActiveChildTouch(MotionEvent event, float contentX, float contentY) {
        int cellIndex = activeCellTouchIndex;
        int blockIndex = activeBlockTouchIndex;
        if (cellIndex < 0 || cellIndex >= cells.size()) {
            activeCellTouchIndex = -1;
            activeBlockTouchIndex = -1;
            return false;
        }
        TableCellLayout cell = cells.get(cellIndex);
        if (blockIndex == -1 && cell.hasRichInlineText() && cell.layout != null) {
            float localX = contentX - cell.x - cell.textX;
            float localY = contentY - cell.y - cell.textY;
            boolean handled = handleRichInlineCellTouch(cell, event, localX, localY);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
                activeCellTouchIndex = -1;
                activeBlockTouchIndex = -1;
            }
            return handled;
        }
        if (blockIndex < 0 || blockIndex >= cell.blocks.size()) {
            activeCellTouchIndex = -1;
            activeBlockTouchIndex = -1;
            return false;
        }
        float localX = contentX - cell.x - cell.textX;
        float localY = contentY - cell.y - cell.textY;
        boolean handled = cell.blocks.get(blockIndex).onTouch(event, localX, localY - cell.blockTops[blockIndex]);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
            activeCellTouchIndex = -1;
            activeBlockTouchIndex = -1;
        }
        return handled;
    }

    private void cancelActiveChildTouch() {
        int cellIndex = activeCellTouchIndex;
        int blockIndex = activeBlockTouchIndex;
        activeCellTouchIndex = -1;
        activeBlockTouchIndex = -1;
        if (cellIndex < 0 || cellIndex >= cells.size()) {
            return;
        }
        TableCellLayout cell = cells.get(cellIndex);
        if (blockIndex == -1 && cell.hasRichInlineText()) {
            MotionEvent cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
            if (cell.layout != null) {
                handleRichInlineCellTouch(cell, cancelEvent, 0f, 0f);
            } else {
                cell.linkPressState.reset();
                cell.pressedSpoiler = false;
            }
            cancelEvent.recycle();
            return;
        }
        if (blockIndex < 0 || blockIndex >= cell.blocks.size()) {
            return;
        }
        MotionEvent cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        cell.blocks.get(blockIndex).onTouch(cancelEvent, 0f, 0f);
        cancelEvent.recycle();
    }

    private boolean handleRichInlineCellTouch(TableCellLayout cell, MotionEvent event, float localX, float localY) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (cell.linkPressState.onTouch(event, localX, localY, cell.layout, cell.builtText, context, touchSlop(), scrollContentView()::invalidate)) {
                    return true;
                }
                if (!SpoilerTextLayout.contains(cell.spoilers, localX, localY)) {
                    return false;
                }
                cell.pressedSpoiler = true;
                cell.downX = localX;
                cell.downY = localY;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (cell.linkPressState.isPressed()) {
                    return cell.linkPressState.onTouch(event, localX, localY, cell.layout, cell.builtText, context, touchSlop(), scrollContentView()::invalidate);
                }
                if (!cell.pressedSpoiler) {
                    return false;
                }
                if (Math.abs(localX - cell.downX) > touchSlop() || Math.abs(localY - cell.downY) > touchSlop()) {
                    cell.pressedSpoiler = false;
                    return false;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (cell.linkPressState.isPressed()) {
                    return cell.linkPressState.onTouch(event, localX, localY, cell.layout, cell.builtText, context, touchSlop(), scrollContentView()::invalidate);
                }
                if (!cell.pressedSpoiler) {
                    return false;
                }
                cell.pressedSpoiler = false;
                return SpoilerTextLayout.reveal(context.getHost(), cell.layout, cell.spoilersPool, cell.spoilers, localX, localY, () -> markCellSpoilersRevealed(cell));
            case MotionEvent.ACTION_CANCEL:
                if (cell.linkPressState.isPressed()) {
                    return cell.linkPressState.onTouch(event, localX, localY, cell.layout, cell.builtText, context, touchSlop(), scrollContentView()::invalidate);
                }
                if (cell.pressedSpoiler) {
                    cell.pressedSpoiler = false;
                    return true;
                }
                return false;
            default:
                return cell.linkPressState.isPressed() || cell.pressedSpoiler;
        }
    }

    private void markCellSpoilersRevealed(TableCellLayout cell) {
        cell.spoilersRevealed = true;
        if (inlineState != null && cell.spoilerStateKey != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(cell.spoilerStateKey);
        }
        scrollContentView().invalidate();
        context.getHost().invalidate();
    }

    private int touchSlop() {
        return ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
    }

    private int nextFreeColumn(ArrayList<Integer> rowSpans, int column, int row, int columnSpan) {
        int current = column;
        while (true) {
            ensureSize(rowSpans, current + columnSpan);
            boolean free = true;
            for (int i = current; i < current + columnSpan; i++) {
                if (rowSpans.get(i) > row) {
                    free = false;
                    current = i + 1;
                    break;
                }
            }
            if (free) {
                return current;
            }
        }
    }

    private void drawCellBackgrounds(Canvas canvas, float radius) {
        for (TableCellLayout cell : cells) {
            Paint paint = null;
            if (cell.header) {
                paint = headerBackgroundPaint;
            } else if (cell.row % 2 == 0) {
                paint = stripPaint;
            }
            if (paint == null) {
                continue;
            }
            rect.set(cell.x, cell.y, cell.x + cell.width, cell.y + cell.height);
            setCellRadii(cell, radius);
            path.reset();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }

    private void drawGrid(Canvas canvas, float radius) {
        for (int i = 1; i < columnLefts.length; i++) {
            float x = columnLefts[i];
            canvas.drawLine(x, 0f, x, tableHeight, dividerPaint);
        }
        for (int i = 1; i < rowTops.length; i++) {
            float y = rowTops[i];
            canvas.drawLine(0f, y, contentWidth, y, dividerPaint);
        }
        rect.set(0f, 0f, contentWidth, tableHeight);
        canvas.drawRoundRect(rect, radius, radius, borderPaint);
    }

    private void setCellRadii(TableCellLayout cell, float radius) {
        boolean left = cell.x == 0;
        boolean top = cell.y == 0;
        boolean right = cell.x + cell.width == contentWidth;
        boolean bottom = cell.y + cell.height == tableHeight;
        radii[0] = radii[1] = left && top ? radius : 0f;
        radii[2] = radii[3] = right && top ? radius : 0f;
        radii[4] = radii[5] = right && bottom ? radius : 0f;
        radii[6] = radii[7] = left && bottom ? radius : 0f;
    }

    private List<List<CookedPostBlock.TableCell>> tableRows() {
        List<List<CookedPostBlock.TableCell>> rows = new ArrayList<>();
        if (!block.getHeaders().isEmpty()) {
            rows.add(block.getHeaders());
        }
        rows.addAll(block.getRows());
        return rows;
    }

    private int maxScroll() {
        return Math.max(contentWidth - viewportWidth, 0);
    }

    private void setParentInterceptBlocked(boolean blocked) {
        if (context.getHost().getParent() != null) {
            context.getHost().getParent().requestDisallowInterceptTouchEvent(blocked);
        }
    }

    private static Layout.Alignment alignment(CookedPostBlock.TableCell cell) {
        if (cell.getAlign() == CookedPostBlock.TableCellAlign.Center) {
            return Layout.Alignment.ALIGN_CENTER;
        }
        if (cell.getAlign() == CookedPostBlock.TableCellAlign.End) {
            return Layout.Alignment.ALIGN_OPPOSITE;
        }
        return Layout.Alignment.ALIGN_NORMAL;
    }

    private static Layout.Alignment alignment(TL_iv.pageTableCell cell) {
        if (cell.align_right) {
            return Layout.Alignment.ALIGN_OPPOSITE;
        }
        if (cell.align_center) {
            return Layout.Alignment.ALIGN_CENTER;
        }
        return Layout.Alignment.ALIGN_NORMAL;
    }

    private static int longestWordWidth(String text, TextPaint paint) {
        int longest = 0;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return longest;
        }
        String[] words = trimmed.split("\\s+");
        for (String word : words) {
            longest = Math.max(longest, Math.round(paint.measureText(word)));
        }
        return longest;
    }

    private static void updateTextMetrics(TableCellLayout cell) {
        cell.textLeft = 0;
        cell.textWidth = 0;
        if (cell.layout != null) {
            for (int i = 0; i < cell.layout.getLineCount(); i++) {
                int lineLeft = (int) Math.ceil(cell.layout.getLineLeft(i));
                cell.textLeft = i == 0 ? lineLeft : Math.min(cell.textLeft, lineLeft);
                cell.textWidth = (int) Math.ceil(Math.max(cell.layout.getLineWidth(i), cell.textWidth));
            }
            cell.textHeight = cell.layout.getHeight();
        } else {
            cell.textHeight = 0;
        }
    }

    private static void updateTextPosition(TableCellLayout cell, int horizontalPadding, int verticalPadding) {
        if (cell.cell.getAlign() == CookedPostBlock.TableCellAlign.End) {
            cell.textX = cell.width - cell.textWidth - horizontalPadding - cell.textLeft;
        } else if (cell.cell.getAlign() == CookedPostBlock.TableCellAlign.Center) {
            cell.textX = Math.round((cell.width - cell.textWidth) / 2f) - cell.textLeft;
        } else {
            cell.textX = horizontalPadding - cell.textLeft;
        }
        cell.textY = verticalPadding;
    }

    private static void distributeSpanDeficit(int[] widths, int start, int span, int targetWidth) {
        int safeSpan = Math.max(span, 1);
        int end = Math.min(start + safeSpan, widths.length);
        int current = 0;
        for (int i = start; i < end; i++) {
            current += widths[i];
        }
        int remaining = targetWidth - current;
        int index = start;
        while (remaining > 0 && end > start) {
            widths[index] += 1;
            remaining--;
            index++;
            if (index >= end) {
                index = start;
            }
        }
    }

    private static void distributeRowSpanDeficit(int[] heights, int start, int span, int targetHeight) {
        int safeSpan = Math.max(span, 1);
        int end = Math.min(start + safeSpan, heights.length);
        int current = 0;
        for (int i = start; i < end; i++) {
            current += heights[i];
        }
        int remaining = targetHeight - current;
        int index = start;
        while (remaining > 0 && end > start) {
            heights[index] += 1;
            remaining--;
            index++;
            if (index >= end) {
                index = start;
            }
        }
    }

    private static int[] distributeColumnWidths(int[] minimumWidths, int[] desiredWidths, int targetWidth) {
        int[] widths = desiredWidths.clone();
        int desiredTotal = sum(widths);
        if (desiredTotal >= targetWidth) {
            return widths;
        }
        int minimumTotal = sum(minimumWidths);
        if (minimumTotal >= targetWidth) {
            return minimumWidths;
        }
        int remaining = targetWidth - desiredTotal;
        int index = 0;
        while (remaining > 0 && widths.length > 0) {
            widths[index % widths.length] += 1;
            index++;
            remaining--;
        }
        return widths;
    }

    private static int rowCount(List<TableCellLayout> cells) {
        int count = 0;
        for (TableCellLayout cell : cells) {
            count = Math.max(count, cell.row + cell.rowSpan);
        }
        return count;
    }

    private static int columnCount(List<TableCellLayout> cells) {
        int count = 0;
        for (TableCellLayout cell : cells) {
            count = Math.max(count, cell.column + cell.columnSpan);
        }
        return count;
    }

    private static int spanSize(int[] sizes, int start, int span) {
        int total = 0;
        int end = Math.min(start + Math.max(span, 1), sizes.length);
        for (int i = start; i < end; i++) {
            total += sizes[i];
        }
        return total;
    }

    private static int[] cumulativeLefts(int[] sizes) {
        int[] result = new int[sizes.length];
        int value = 0;
        for (int i = 0; i < sizes.length; i++) {
            result[i] = value;
            value += sizes[i];
        }
        return result;
    }

    private static void ensureSize(ArrayList<Integer> values, int size) {
        while (values.size() < size) {
            values.add(0);
        }
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    static int listTopPaddingPx() {
        return AndroidUtilities.dp(LIST_TOP_PADDING_DP);
    }

    static int listBottomPaddingPx() {
        return AndroidUtilities.dp(LIST_BOTTOM_PADDING_DP);
    }

    static int scrollHorizontalPaddingPx() {
        return AndroidUtilities.dp(SCROLL_HORIZONTAL_PADDING_DP);
    }

    static int scrollOverflowThresholdInsetPx() {
        return scrollHorizontalPaddingPx() * 2;
    }

    static int cellHorizontalPaddingPx() {
        return AndroidUtilities.dp(CELL_HORIZONTAL_PADDING_DP);
    }

    static int cellVerticalPaddingPx() {
        return AndroidUtilities.dp(CELL_VERTICAL_PADDING_DP);
    }

    static int minRowHeightPx() {
        return AndroidUtilities.dp(MIN_ROW_HEIGHT_DP);
    }

    static int minCellTextWidthPx() {
        return AndroidUtilities.dp(MIN_CELL_TEXT_WIDTH_DP);
    }

    static int tableCornerRadiusPx() {
        return AndroidUtilities.dp(TABLE_CORNER_RADIUS_DP);
    }

    static int nestedBlockHorizontalInsetPx() {
        return AndroidUtilities.dp(NESTED_BLOCK_HORIZONTAL_INSET_DP);
    }

    static int nestedBlockSpacingPx() {
        return 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

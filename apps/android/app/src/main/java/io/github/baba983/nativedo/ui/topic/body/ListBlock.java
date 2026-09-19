/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer list item block cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.TextSelectionHelper;

final class ListBlock implements EmbeddedPostBodyBlock {

    private static final class ItemTextBlock extends TextLayoutBlock {
        ItemTextBlock(String html, PostBodyBuildContext context) {
            super(html, context, context.getParagraphPaint());
        }

        ItemTextBlock(String html, PostBodyBuildContext context, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
            super(html, context, context.getParagraphPaint(), inlineState, inlineSpoilerStateKey);
        }

        @Override
        protected int topPaddingPx() {
            return 0;
        }

        @Override
        protected int bottomPaddingPx() {
            return dp(8f);
        }
    }

    private static final class ItemLayout {
        final List<PostBodyBlock> blocks;
        final int[] blockTops;
        final int[] blockHeights;
        final int[] blockLefts;
        final int[] blockWidths;

        ItemLayout(List<PostBodyBlock> blocks) {
            this.blocks = blocks;
            this.blockTops = new int[blocks.size()];
            this.blockHeights = new int[blocks.size()];
            this.blockLefts = new int[blocks.size()];
            this.blockWidths = new int[blocks.size()];
        }
    }

    private final class ListBlockView extends ViewGroup {
        ListBlockView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), measuredHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutEmbeddedChildren();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawList(canvas, getMeasuredWidth(), true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return ListBlock.this.onTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
        }
    }

    private final CookedPostBlock.ListItems block;
    private final PostBodyBuildContext context;
    private final PostBodyBlockState inlineState;
    private final String stateKey;
    private final int listLevel;
    private final int lastItemBottomPadding = AndroidUtilities.dp(8f);
    private final TextPaint bulletPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint numberPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final List<ItemLayout> itemLayouts;
    private final ListBlockView listView;
    private final int[] itemTops;
    private final int[] itemHeights;
    private final StaticLayout[] markerLayouts;
    private final ArrayList<TextSelectionHelper.TextLayoutBlock> markerProbeBlocks = new ArrayList<>();
    private int activeItemTouchIndex = -1;
    private int activeBlockTouchIndex = -1;
    private int measuredHeight;
    private int textWidth;
    private int markerColumnWidth = AndroidUtilities.dp(32f);

    ListBlock(
            CookedPostBlock.ListItems block,
            PostBodyBuildContext context,
            List<GalleryImage> gallery,
            PostBodyBlockState inlineState,
            String stateKey,
            int listLevel
    ) {
        this.block = block;
        this.context = context;
        this.inlineState = inlineState;
        this.stateKey = stateKey;
        this.listLevel = Math.max(0, listLevel);
        bulletPaint.setColor(context.getTextColor());
        bulletPaint.setTextSize(AndroidUtilities.dp(19f));
        numberPaint.setColor(context.getTextColor());
        numberPaint.setTextSize(AndroidUtilities.dp(16f));

        List<CookedPostBlock.ListItem> items = block.getItems();
        itemLayouts = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            CookedPostBlock.ListItem item = items.get(index);
            List<PostBodyBlock> childBlocks;
            String itemKey = stateKey + ".item." + index;
            if (item.getBlocks().size() == 1 && item.getBlocks().get(0) instanceof CookedPostBlock.Text) {
                childBlocks = Collections.<PostBodyBlock>singletonList(new ItemTextBlock(
                        ((CookedPostBlock.Text) item.getBlocks().get(0)).getHtml(),
                        context,
                        inlineState,
                        TextLayoutBlock.inlineSpoilerStateKey("list-text:" + itemKey, ((CookedPostBlock.Text) item.getBlocks().get(0)).getHtml())
                ));
            } else if (!item.getBlocks().isEmpty()) {
                childBlocks = new ArrayList<>();
                for (int blockIndex = 0; blockIndex < item.getBlocks().size(); blockIndex++) {
                    CookedPostBlock child = item.getBlocks().get(blockIndex);
                    if (child instanceof CookedPostBlock.Text) {
                        String html = ((CookedPostBlock.Text) child).getHtml();
                        childBlocks.add(new ItemTextBlock(
                                html,
                                context,
                                inlineState,
                                TextLayoutBlock.inlineSpoilerStateKey("list-text:" + itemKey + "." + blockIndex, html)
                        ));
                    } else {
                        childBlocks.addAll(PostBodyBlockFactory.INSTANCE.build(
                                Collections.singletonList(child),
                                context,
                                inlineState,
                                gallery,
                                itemKey + "." + blockIndex,
                                false,
                                this.listLevel + 1
                        ));
                    }
                }
            } else {
                childBlocks = Collections.<PostBodyBlock>singletonList(new ItemTextBlock(
                        item.getHtml(),
                        context,
                        inlineState,
                        TextLayoutBlock.inlineSpoilerStateKey("list-text:" + itemKey, item.getHtml())
                ));
            }
            itemLayouts.add(new ItemLayout(childBlocks));
        }
        listView = new ListBlockView(context.getHost().getContext());
        addEmbeddedChildren(listView);
        itemTops = new int[itemLayouts.size()];
        itemHeights = new int[itemLayouts.size()];
        markerLayouts = new StaticLayout[itemLayouts.size()];
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }

    @Override
    public int measure(int maxWidthPx) {
        int maxMarkerWidth = (int) Math.ceil(numberPaint.measureText("00."));
        for (int i = 0; i < block.getItems().size(); i++) {
            String marker = selectionPrefixForItem(i, block.getOrdered());
            TextPaint markerPaint = block.getOrdered() ? numberPaint : bulletPaint;
            maxMarkerWidth = Math.max(maxMarkerWidth, (int) Math.ceil(markerPaint.measureText(marker)));
        }
        for (int i = 0; i < block.getItems().size(); i++) {
            String marker = selectionPrefixForItem(i, block.getOrdered());
            TextPaint markerPaint = block.getOrdered() ? numberPaint : bulletPaint;
            markerLayouts[i] = ArticleTextLayout.builder(marker, markerPaint, maxMarkerWidth + AndroidUtilities.dp(2f))
                    .build();
        }
        int textX = markerTextXPx(maxMarkerWidth, block.getOrdered(), listLevel);
        markerColumnWidth = textX;
        textWidth = Math.max(maxWidthPx - textX - trailingTextInsetPx(), AndroidUtilities.dp(80f));
        int y = itemLayouts.isEmpty() ? 0 : firstItemTopPaddingPx(listLevel);
        for (int i = 0; i < itemLayouts.size(); i++) {
            itemTops[i] = y;
            int height = measureItem(itemLayouts.get(i), textWidth);
            itemHeights[i] = height;
            y += height;
        }
        if (!itemLayouts.isEmpty()) {
            y += lastItemBottomPadding;
        }
        measuredHeight = y;
        listView.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        drawList(canvas, parentWidth, false);
    }

    @Override
    public View embeddedView() {
        return listView;
    }

    @Override
    public void layoutEmbeddedView(int left, int top, int width, int height) {
        listView.layout(left, top, left + width, top + height);
        layoutEmbeddedChildren();
    }

    private void drawList(Canvas canvas, int parentWidth, boolean skipEmbeddedChildren) {
        for (int i = 0; i < itemLayouts.size(); i++) {
            ItemLayout item = itemLayouts.get(i);
            int top = itemTops[i];
            StaticLayout markerLayout = markerLayouts[i];
            if (markerLayout == null) {
                continue;
            }
            float markerTop = markerTopForItem(item, top, markerLayout);
            float markerRight = markerRightPx(markerColumnWidth, block.getOrdered());
            float markerWidth = markerLayout.getLineWidth(0);
            canvas.save();
            canvas.translate(markerRight - markerWidth, markerTop);
            markerLayout.draw(canvas);
            canvas.restore();
            for (int blockIndex = 0; blockIndex < item.blocks.size(); blockIndex++) {
                PostBodyBlock child = item.blocks.get(blockIndex);
                if (skipEmbeddedChildren && child instanceof EmbeddedPostBodyBlock) {
                    continue;
                }
                canvas.save();
                canvas.translate(markerColumnWidth + item.blockLefts[blockIndex], top + item.blockTops[blockIndex]);
                child.draw(canvas, item.blockWidths[blockIndex]);
                canvas.restore();
            }
        }
    }

    private float markerTopForItem(ItemLayout item, int itemTop, StaticLayout markerLayout) {
        for (int blockIndex = 0; blockIndex < item.blocks.size(); blockIndex++) {
            PostBodyBlock child = item.blocks.get(blockIndex);
            if (child instanceof DetailsBlock) {
                return markerTopForVerticallyAlignedBlock(
                        itemTop,
                        item.blockTops[blockIndex],
                        item.blockHeights[blockIndex],
                        markerLayout,
                        block.getOrdered()
                );
            }
            markerProbeBlocks.clear();
            child.fillTextLayoutBlocks(markerProbeBlocks, 0, 0);
            for (int textIndex = 0; textIndex < markerProbeBlocks.size(); textIndex++) {
                TextSelectionHelper.TextLayoutBlock textBlock = markerProbeBlocks.get(textIndex);
                Layout textLayout = textBlock.getLayout();
                if (textLayout == null || textLayout.getLineCount() == 0) {
                    continue;
                }
                return markerTopForFirstTextLine(
                        itemTop,
                        item.blockTops[blockIndex],
                        textBlock.getY(),
                        markerLayout.getLineAscent(0),
                        textLayout.getLineAscent(0),
                        block.getOrdered()
                );
            }
        }
        return itemTop;
    }

    static float markerTopForFirstTextLine(int itemTop, int blockTop, int childTextTop, int markerAscent, int childAscent, boolean ordered) {
        float unorderedOffset = ordered ? 0f : AndroidUtilities.dp(2.5f) - AndroidUtilities.dp(1f);
        return itemTop + blockTop + childTextTop + markerAscent - childAscent + unorderedOffset;
    }

    static float markerTopForVerticallyAlignedBlock(int itemTop, int blockTop, int blockHeight, StaticLayout markerLayout, boolean ordered) {
        float unorderedOffset = ordered ? 0f : -AndroidUtilities.dp(4f);
        return itemTop + blockTop + (blockHeight - markerLayout.getHeight()) / 2f + unorderedOffset;
    }

    static String selectionPrefixForItem(int index, boolean ordered) {
        return ordered ? (index + 1) + "." : "•";
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        if (activeItemTouchIndex >= 0 && event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return dispatchActiveChildTouch(event, localX, localY);
        }
        float itemX = localX - markerColumnWidth;
        if (itemX < 0f) {
            return false;
        }
        for (int i = 0; i < itemLayouts.size(); i++) {
            ItemLayout item = itemLayouts.get(i);
            int top = itemTops[i];
            int bottom = top + itemHeights[i];
            if (localY >= top && localY <= bottom) {
                float itemY = localY - top;
                for (int blockIndex = 0; blockIndex < item.blocks.size(); blockIndex++) {
                    int blockTop = item.blockTops[blockIndex];
                    int blockBottom = blockTop + item.blockHeights[blockIndex];
                    if (itemY >= blockTop && itemY <= blockBottom) {
                        boolean handled = item.blocks.get(blockIndex).onTouch(
                                event,
                                itemX - item.blockLefts[blockIndex],
                                itemY - blockTop
                        );
                        if (handled && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            activeItemTouchIndex = i;
                            activeBlockTouchIndex = blockIndex;
                        }
                        return handled;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        for (int i = 0; i < itemLayouts.size(); i++) {
            ItemLayout item = itemLayouts.get(i);
            int before = blocks.size();
            for (int blockIndex = 0; blockIndex < item.blocks.size(); blockIndex++) {
                item.blocks.get(blockIndex).fillTextLayoutBlocks(
                        blocks,
                        x + markerColumnWidth + item.blockLefts[blockIndex],
                        y + itemTops[i] + item.blockTops[blockIndex]
                );
            }
            String prefix = selectionPrefixForItem(i, block.getOrdered());
            StaticLayout prefixLayout = markerLayouts[i];
            int prefixX = x;
            int prefixY = y;
            if (prefixLayout != null) {
                float markerTop = markerTopForItem(item, itemTops[i], prefixLayout);
                float markerRight = markerRightPx(markerColumnWidth, block.getOrdered());
                prefixX = x + Math.round(markerRight - (float) Math.ceil(prefixLayout.getLineWidth(0)));
                prefixY = y + Math.round(markerTop);
            }
            for (int j = before; j < blocks.size(); j++) {
                TextSelectionHelper.TextLayoutBlock current = blocks.get(j);
                blocks.set(j, new PostSelectionLayoutBlock(
                        current.getLayout(),
                        current.getX(),
                        current.getY(),
                        current.getRow(),
                        j == before ? prefix : null
                ));
            }
        }
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        for (int itemIndex = 0; itemIndex < itemLayouts.size(); itemIndex++) {
            ItemLayout item = itemLayouts.get(itemIndex);
            for (int blockIndex = 0; blockIndex < item.blocks.size(); blockIndex++) {
                RectF bounds = item.blocks.get(blockIndex).imageBoundsInBlock(imageUrl);
                if (bounds != null) {
                    RectF result = new RectF(bounds);
                    result.offset(markerColumnWidth + item.blockLefts[blockIndex], itemTops[itemIndex] + item.blockTops[blockIndex]);
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        for (ItemLayout item : itemLayouts) {
            for (PostBodyBlock child : item.blocks) {
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
        for (ItemLayout item : itemLayouts) {
            for (PostBodyBlock child : item.blocks) {
                child.onAttachedToWindow();
            }
        }
    }

    @Override
    public void onDetachedFromWindow() {
        activeItemTouchIndex = -1;
        activeBlockTouchIndex = -1;
        for (ItemLayout item : itemLayouts) {
            for (PostBodyBlock child : item.blocks) {
                child.onDetachedFromWindow();
            }
        }
    }

    @Override
    public void applyTheme() {
        for (ItemLayout item : itemLayouts) {
            for (PostBodyBlock child : item.blocks) {
                child.applyTheme();
            }
        }
        listView.invalidate();
    }

    private boolean dispatchActiveChildTouch(MotionEvent event, float localX, float localY) {
        int itemIndex = activeItemTouchIndex;
        int blockIndex = activeBlockTouchIndex;
        if (itemIndex < 0 || itemIndex >= itemLayouts.size()) {
            activeItemTouchIndex = -1;
            activeBlockTouchIndex = -1;
            return false;
        }
        ItemLayout item = itemLayouts.get(itemIndex);
        if (blockIndex < 0 || blockIndex >= item.blocks.size()) {
            activeItemTouchIndex = -1;
            activeBlockTouchIndex = -1;
            return false;
        }
        float itemX = localX - markerColumnWidth;
        float itemY = localY - itemTops[itemIndex];
        boolean handled = item.blocks.get(blockIndex).onTouch(
                event,
                itemX - item.blockLefts[blockIndex],
                itemY - item.blockTops[blockIndex]
        );
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
            activeItemTouchIndex = -1;
            activeBlockTouchIndex = -1;
        }
        return handled;
    }

    private int measureItem(ItemLayout item, int maxWidth) {
        int y = 0;
        for (int i = 0; i < item.blocks.size(); i++) {
            item.blockTops[i] = y;
            item.blockLefts[i] = blockLeft(item.blocks.get(i));
            item.blockWidths[i] = blockWidth(item.blocks.get(i), maxWidth);
            int height = item.blocks.get(i).measure(item.blockWidths[i]);
            item.blockHeights[i] = height;
            y += height;
        }
        return y;
    }

    private void addEmbeddedChildren(ViewGroup parent) {
        for (ItemLayout item : itemLayouts) {
            for (PostBodyBlock child : item.blocks) {
                if (child instanceof EmbeddedPostBodyBlock) {
                    View view = ((EmbeddedPostBodyBlock) child).embeddedView();
                    if (view.getParent() instanceof ViewGroup) {
                        ((ViewGroup) view.getParent()).removeView(view);
                    }
                    parent.addView(view);
                }
            }
        }
    }

    private void layoutEmbeddedChildren() {
        for (int itemIndex = 0; itemIndex < itemLayouts.size(); itemIndex++) {
            ItemLayout item = itemLayouts.get(itemIndex);
            for (int blockIndex = 0; blockIndex < item.blocks.size(); blockIndex++) {
                PostBodyBlock child = item.blocks.get(blockIndex);
                if (child instanceof EmbeddedPostBodyBlock) {
                    ((EmbeddedPostBodyBlock) child).layoutEmbeddedView(
                            markerColumnWidth + item.blockLefts[blockIndex],
                            itemTops[itemIndex] + item.blockTops[blockIndex],
                            item.blockWidths[blockIndex],
                            item.blockHeights[blockIndex]
                    );
                }
            }
        }
    }

    private int blockLeft(PostBodyBlock child) {
        String name = child.getClass().getSimpleName();
        if ("ListBlock".equals(name)) {
            return -markerColumnWidth;
        }
        if ("TableBlock".equals(name)) {
            return -AndroidUtilities.dp(18f);
        }
        return 0;
    }

    private int blockWidth(PostBodyBlock child, int maxWidth) {
        String name = child.getClass().getSimpleName();
        if ("ListBlock".equals(name)) {
            return maxWidth + markerColumnWidth;
        }
        if ("TableBlock".equals(name)) {
            return maxWidth + AndroidUtilities.dp(36f);
        }
        return maxWidth;
    }

    private int listLevelInsetPx() {
        return listLevelInsetPx(block.getOrdered(), listLevel);
    }

    static int markerTextGapPx() {
        return markerTextGapPx(true);
    }

    static int markerTextGapPx(boolean ordered) {
        return AndroidUtilities.dp(ordered ? 6f : 9f);
    }

    static float markerRightPx(int markerColumnWidth, boolean ordered) {
        return markerColumnWidth - markerTextGapPx(ordered);
    }

    static int trailingTextInsetPx() {
        return AndroidUtilities.dp(18f);
    }

    static int markerTextXPx(int maxMarkerWidth, boolean ordered, int listLevel) {
        return AndroidUtilities.dp(24f) + maxMarkerWidth + listLevelInsetPx(ordered, listLevel);
    }

    static int listLevelInsetPx(boolean ordered, int listLevel) {
        return Math.max(0, listLevel) * AndroidUtilities.dp(ordered ? 20f : 12f);
    }

    static int firstItemTopPaddingPx(int listLevel) {
        return listLevel == 0 ? AndroidUtilities.dp(10f) : 0;
    }
}

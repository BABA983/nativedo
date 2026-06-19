/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ArticleViewer.BlockCollageCell.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.CookedPostBodyParserKt;
import io.github.baba983.nativedo.core.html.GalleryImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class CollageBlock implements EmbeddedPostBodyBlock {
    private static final int MAX_TILES_PER_ROW = 3;
    private static final float MAX_SIZE_WIDTH = 1000f;
    private static final float MAX_SIZE_HEIGHT = 814f;

    private static final class LayoutAttempt {
        final int[] lineCounts;
        final float[] heights;

        LayoutAttempt(int[] lineCounts, float[] heights) {
            this.lineCounts = lineCounts;
            this.heights = heights;
        }
    }

    private static final class Tile {
        final CookedPostBlock.Image image;
        final String imageUrl;
        final String targetUrl;
        final ImageReceiver receiver;
        final RadialProgress2 radialProgress;
        final RectF rect = new RectF();

        Tile(CookedPostBlock.Image image, String imageUrl, String targetUrl, ImageReceiver receiver, RadialProgress2 radialProgress) {
            this.image = image;
            this.imageUrl = imageUrl;
            this.targetUrl = targetUrl;
            this.receiver = receiver;
            this.radialProgress = radialProgress;
        }

        float aspectRatio() {
            Float aspect = image.getAspectRatio();
            return aspect != null ? clamp(aspect, 0.45f, 2.4f) : 1f;
        }
    }

    private final List<GalleryImage> gallery;
    private final PostBodyBuildContext context;
    private final PostBodyBlockState inlineState;
    private final String inlineSpoilerStateKey;
    private final String captionHtml;
    private final int spacing = AndroidUtilities.dp(2f);
    private final int listTopPadding = AndroidUtilities.dp(8f);
    private final int bottomPadding = AndroidUtilities.dp(16f);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint captionPaint;
    private final PostBodyTextBuilder textBuilder;
    private final ArticleLinkPressState linkPressState = new ArticleLinkPressState();
    private final int touchSlop;
    private final List<Tile> tiles = new ArrayList<>();
    private final RectF captionRect = new RectF();
    private final ArrayList<SpoilerEffect> spoilers = new ArrayList<>();
    private final Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private final Path spoilerBoundsPath = new Path();
    private final Path spoilerRipplePath = new Path();
    private final TileAdapter innerAdapter = new TileAdapter();
    private CollageBlockView blockView;
    private RecyclerListView innerListView;
    private BuiltText builtCaption;
    private BuiltText.Handle captionHandle;
    private StaticLayout captionLayout;
    private int gridHeight;
    private int contentBottom;
    private int captionX;
    private int captionY;
    private int measuredHeight;
    private int pressedTileIndex = -1;
    private float downX;
    private float downY;
    private boolean spoilersRevealed;
    private boolean pressedSpoiler;

    CollageBlock(CookedPostBlock.Collage block, List<GalleryImage> gallery, PostBodyBuildContext context, PostBodyBlockState inlineState, String inlineSpoilerStateKey) {
        this.gallery = gallery;
        this.context = context;
        this.inlineState = inlineState;
        this.inlineSpoilerStateKey = inlineSpoilerStateKey;
        captionHtml = captionHtmlFor(block.getImages());
        captionPaint = new TextPaint(context.getMediaCaptionPaint());
        textBuilder = new PostBodyTextBuilder(context);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
        spoilersRevealed = inlineState != null
                && inlineSpoilerStateKey != null
                && inlineState.getRevealedInlineSpoilersByKey().contains(inlineSpoilerStateKey);
        placeholderPaint.setColor(context.getMediaPlaceholderColor());
        for (CookedPostBlock.Image image : block.getImages()) {
            String imageUrl = CookedPostBodyParserKt.resolvePostImageUrl(context.getBaseUrl(), image.getSrc());
            String href = image.getHref();
            String hrefUrl = href != null ? CookedPostBodyParserKt.resolvePostImageUrl(context.getBaseUrl(), href) : null;
            String targetUrl = hrefUrl != null && CookedPostBodyParserKt.isLikelyPostImageUrl(hrefUrl) ? hrefUrl : imageUrl;
            ImageReceiver receiver = context.getImagePool().obtain("collage:" + targetUrl);
            receiver.setRoundRadius(0);
            RadialProgress2 radialProgress = new RadialProgress2(context.getHost());
            radialProgress.setProgressColor(0xffffffff);
            radialProgress.setColors(0x66000000, 0x7f000000, 0xffffffff, 0xffd9d9d9);
            radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, false, false);
            Tile tile = new Tile(image, imageUrl, targetUrl, receiver, radialProgress);
            tiles.add(tile);
            receiver.setDelegate((imageReceiver, set, thumb, memCache) -> invalidateTile(tiles.indexOf(tile)));
            receiver.setVisibleInvalidate(() -> invalidateTile(tiles.indexOf(tile)));
            context.getImagePool().setImage(receiver, imageUrl, null);
        }
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        if (tiles.isEmpty()) {
            measuredHeight = 0;
            return measuredHeight;
        }
        int availableWidth = Math.max(maxWidthPx, AndroidUtilities.dp(120f));
        switch (tiles.size()) {
            case 1:
                gridHeight = layoutSingle(availableWidth);
                break;
            case 2:
                gridHeight = layoutTwo(availableWidth);
                break;
            case 3:
                gridHeight = layoutThree(availableWidth);
                break;
            case 4:
                gridHeight = layoutFour(availableWidth);
                break;
            default:
                gridHeight = layoutRows(availableWidth);
                break;
        }
        contentBottom = listTopPadding + gridHeight;
        measureCaption(availableWidth);
        measuredHeight = contentBottom
                + (captionLayout != null ? captionLayout.getHeight() : 0)
                + bottomPadding;
        if (innerListView != null) {
            innerListView.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(gridHeight, View.MeasureSpec.EXACTLY)
            );
            innerListView.requestLayout();
        }
        if (blockView != null) {
            blockView.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
            );
        }
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        canvas.save();
        canvas.translate(0f, listTopPadding);
        for (Tile tile : tiles) {
            drawTile(canvas, tile, tile.rect, null);
        }
        canvas.restore();
        drawCaption(canvas);
    }

    @Override
    public View embeddedView() {
        return ensureBlockView();
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        if (captionLayout != null
                && (captionRect.contains(localX, localY) || linkPressState.isPressed() || pressedSpoiler)) {
            if (onCaptionTouch(event, localX - captionX, localY - captionY)) {
                return true;
            }
        }
        int index = tileIndexAt(localX, localY);
        if (index < 0) {
            if (pressedTileIndex != -1
                    && (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL
                    || event.getActionMasked() == MotionEvent.ACTION_UP)) {
                pressedTileIndex = -1;
                context.getHost().invalidate();
            }
            return false;
        }
        return handleTileTouch(index, event);
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        if (captionLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(captionLayout, x + captionX, y + captionY, 0, null));
        }
    }

    @Override
    public void onAttachedToWindow() {
        for (Tile tile : tiles) {
            tile.receiver.onAttachedToWindow();
            tile.radialProgress.onAttachedToWindow();
        }
        if (captionHandle == null && builtCaption != null) {
            captionHandle = builtCaption.attach(context.getHost());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        pressedTileIndex = -1;
        if (captionHandle != null) {
            captionHandle.detach();
            captionHandle = null;
        }
        linkPressState.reset();
        pressedSpoiler = false;
        if (innerListView != null) {
            innerListView.stopScroll();
        }
        for (Tile tile : tiles) {
            tile.radialProgress.onDetachedFromWindow();
            tile.receiver.onDetachedFromWindow();
        }
    }

    private void measureCaption(int availableWidth) {
        captionX = AndroidUtilities.dp(18f);
        captionY = contentBottom;
        if (captionHandle != null) {
            captionHandle.detach();
            captionHandle = null;
        }
        if (captionHtml != null) {
            BuiltText builtText = textBuilder.build(captionHtml, captionPaint);
            builtCaption = builtText;
            captionLayout = ArticleTextLayout.builder(
                            builtText.getSpannable(),
                            captionPaint,
                            Math.max(availableWidth - AndroidUtilities.dp(36f), AndroidUtilities.dp(80f))
                    )
                    .build();
            captionRect.set(captionX, captionY, captionX + captionLayout.getWidth(), captionY + captionLayout.getHeight());
            SpoilerTextLayout.collect(context.getHost(), captionLayout, spoilersPool, spoilers, spoilersRevealed);
            if (context.getHost().isAttachedToWindow()) {
                captionHandle = builtText.attach(context.getHost());
            }
        } else {
            captionLayout = null;
            builtCaption = null;
            captionRect.setEmpty();
            spoilersPool.addAll(spoilers);
            spoilers.clear();
        }
    }

    private void drawCaption(Canvas canvas) {
        if (captionLayout == null) {
            return;
        }
        canvas.save();
        canvas.translate(captionX, captionY);
        linkPressState.draw(canvas, this::invalidateBlock);
        SpoilerTextLayout.draw(canvas, captionLayout, spoilers, spoilerBoundsPath, spoilerRipplePath);
        canvas.restore();
    }

    @Override
    public void applyTheme() {
    }

    RectF imageBoundsInCell(int index) {
        if (index < 0 || index >= tiles.size()) {
            return null;
        }
        RectF bounds = new RectF(tiles.get(index).rect);
        bounds.offset(0f, listTopPadding);
        return bounds;
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        return tileBoundsInCell(imageUrl);
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        for (Tile tile : tiles) {
            if (tile.targetUrl.equals(imageUrl)) {
                return tile.receiver;
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
        return receiver != null ? copyRadius(receiver) : null;
    }

    private int layoutSingle(int availableWidth) {
        int height = clamp(
                Math.round(availableWidth / tiles.get(0).aspectRatio()),
                AndroidUtilities.dp(140f),
                AndroidUtilities.dp(420f)
        );
        tiles.get(0).rect.set(0f, 0f, availableWidth, height);
        return height;
    }

    private int layoutTwo(int availableWidth) {
        float firstAspect = tiles.get(0).aspectRatio();
        float secondAspect = tiles.get(1).aspectRatio();
        float maxHeight = availableWidth * MAX_SIZE_HEIGHT / MAX_SIZE_WIDTH;
        float averageAspectRatio = (1f + firstAspect + secondAspect) / 2f;
        float maxAspectRatio = MAX_SIZE_WIDTH / MAX_SIZE_HEIGHT;
        String proportions = "" + aspectKind(firstAspect) + aspectKind(secondAspect);
        if ("ww".equals(proportions) && averageAspectRatio > 1.4f * maxAspectRatio && firstAspect - secondAspect < 0.2f) {
            int height = Math.max(
                    Math.round(Math.min(availableWidth / firstAspect, Math.min(availableWidth / secondAspect, maxHeight / 2f))),
                    AndroidUtilities.dp(120f)
            );
            tiles.get(0).rect.set(0f, 0f, availableWidth, height);
            tiles.get(1).rect.set(0f, height + spacing, availableWidth, height * 2f + spacing);
            return height * 2 + spacing;
        } else if ("ww".equals(proportions) || "qq".equals(proportions)) {
            int width = (availableWidth - spacing) / 2;
            int height = Math.max(
                    Math.round(Math.min(width / firstAspect, Math.min(width / secondAspect, maxHeight))),
                    AndroidUtilities.dp(120f)
            );
            tiles.get(0).rect.set(0f, 0f, width, height);
            tiles.get(1).rect.set(width + spacing, 0f, availableWidth, height);
            return height;
        } else {
            int secondWidth = clamp(
                    Math.round(Math.max(0.4f * availableWidth, Math.round(availableWidth / firstAspect / (1f / firstAspect + 1f / secondAspect)))),
                    AndroidUtilities.dp(80f),
                    availableWidth - spacing - AndroidUtilities.dp(80f)
            );
            int firstWidth = availableWidth - spacing - secondWidth;
            int height = Math.max(
                    Math.round(Math.min(maxHeight, Math.min(firstWidth / firstAspect, secondWidth / secondAspect))),
                    AndroidUtilities.dp(120f)
            );
            tiles.get(0).rect.set(0f, 0f, firstWidth, height);
            tiles.get(1).rect.set(firstWidth + spacing, 0f, availableWidth, height);
            return height;
        }
    }

    private int layoutThree(int availableWidth) {
        float firstAspect = tiles.get(0).aspectRatio();
        float secondAspect = tiles.get(1).aspectRatio();
        float thirdAspect = tiles.get(2).aspectRatio();
        float maxHeight = availableWidth * MAX_SIZE_HEIGHT / MAX_SIZE_WIDTH;
        if (firstAspect < 0.85f) {
            float thirdHeight = Math.min(maxHeight * 0.5f, Math.round(thirdAspect * availableWidth / (thirdAspect + secondAspect)));
            float secondHeight = maxHeight - thirdHeight;
            int rightWidth = Math.round(Math.max(AndroidUtilities.dp(80f), Math.min(availableWidth * 0.5f, Math.round(Math.min(thirdHeight * thirdAspect, secondHeight * secondAspect)))));
            int leftWidth = Math.max(
                    Math.round(Math.min(maxHeight * firstAspect + AndroidUtilities.dp(40f), availableWidth - rightWidth - spacing)),
                    AndroidUtilities.dp(80f)
            );
            int height = Math.round(maxHeight);
            int topRightHeight = Math.round(secondHeight);
            tiles.get(0).rect.set(0f, 0f, leftWidth, height);
            tiles.get(1).rect.set(leftWidth + spacing, 0f, availableWidth, topRightHeight);
            tiles.get(2).rect.set(leftWidth + spacing, topRightHeight + spacing, availableWidth, height);
            return height;
        } else {
            int topHeight = Math.max(Math.round(Math.min(availableWidth / firstAspect, maxHeight * 0.66f)), AndroidUtilities.dp(120f));
            int halfWidth = (availableWidth - spacing) / 2;
            int bottomHeight = Math.max(
                    Math.round(Math.min(maxHeight - topHeight, Math.min(halfWidth / secondAspect, halfWidth / thirdAspect))),
                    AndroidUtilities.dp(120f)
            );
            tiles.get(0).rect.set(0f, 0f, availableWidth, topHeight);
            tiles.get(1).rect.set(0f, topHeight + spacing, halfWidth, topHeight + spacing + bottomHeight);
            tiles.get(2).rect.set(halfWidth + spacing, topHeight + spacing, availableWidth, topHeight + spacing + bottomHeight);
            return topHeight + spacing + bottomHeight;
        }
    }

    private int layoutFour(int availableWidth) {
        float firstAspect = tiles.get(0).aspectRatio();
        float maxHeight = availableWidth * MAX_SIZE_HEIGHT / MAX_SIZE_WIDTH;
        if (firstAspect > 1.2f) {
            int topHeight = Math.max(Math.round(Math.min(availableWidth / firstAspect, maxHeight * 0.66f)), AndroidUtilities.dp(120f));
            int bottomHeight = Math.round(availableWidth / (tiles.get(1).aspectRatio() + tiles.get(2).aspectRatio() + tiles.get(3).aspectRatio()));
            bottomHeight = Math.max(Math.min(Math.round(maxHeight) - topHeight, bottomHeight), AndroidUtilities.dp(84f));
            int firstWidth = Math.max(AndroidUtilities.dp(72f), Math.min(Math.round(availableWidth * 0.4f), Math.round(bottomHeight * tiles.get(1).aspectRatio())));
            int thirdWidth = Math.min(
                    Math.max(Math.max(AndroidUtilities.dp(72f), Math.round(availableWidth * 0.33f)), Math.round(bottomHeight * tiles.get(3).aspectRatio())),
                    availableWidth - spacing * 2 - firstWidth - AndroidUtilities.dp(72f)
            );
            int secondWidth = availableWidth - spacing * 2 - firstWidth - thirdWidth;
            tiles.get(0).rect.set(0f, 0f, availableWidth, topHeight);
            int bottomTop = topHeight + spacing;
            tiles.get(1).rect.set(0f, bottomTop, firstWidth, bottomTop + bottomHeight);
            tiles.get(2).rect.set(firstWidth + spacing, bottomTop, firstWidth + spacing + secondWidth, bottomTop + bottomHeight);
            tiles.get(3).rect.set(firstWidth + spacing * 2f + secondWidth, bottomTop, availableWidth, bottomTop + bottomHeight);
            return bottomTop + bottomHeight;
        }
        int rightWidth = Math.min(
                Math.max(AndroidUtilities.dp(80f), Math.round(maxHeight / (1f / tiles.get(1).aspectRatio() + 1f / tiles.get(2).aspectRatio() + 1f / tiles.get(3).aspectRatio()))),
                Math.round(availableWidth * 0.45f)
        );
        int leftWidth = availableWidth - spacing - rightWidth;
        float height0 = Math.min(0.33f, Math.max(AndroidUtilities.dp(120f), rightWidth / tiles.get(1).aspectRatio()) / maxHeight);
        float height1 = Math.min(0.33f, Math.max(AndroidUtilities.dp(120f), rightWidth / tiles.get(2).aspectRatio()) / maxHeight);
        float height2 = 1f - height0 - height1;
        int firstHeight = Math.round(maxHeight);
        int rightHeight0 = Math.round(height0 * firstHeight);
        int rightHeight1 = Math.round(height1 * firstHeight);
        tiles.get(0).rect.set(0f, 0f, leftWidth, firstHeight);
        tiles.get(1).rect.set(leftWidth + spacing, 0f, availableWidth, rightHeight0);
        tiles.get(2).rect.set(leftWidth + spacing, rightHeight0 + spacing, availableWidth, rightHeight0 + spacing + rightHeight1);
        tiles.get(3).rect.set(leftWidth + spacing, rightHeight0 + rightHeight1 + spacing * 2f, availableWidth, firstHeight);
        return firstHeight;
    }

    private int layoutRows(int availableWidth) {
        if (tiles.size() <= 12) {
            LayoutAttempt attempt = optimalLayoutAttempt(availableWidth);
            if (attempt != null) {
                return layoutAttempt(availableWidth, attempt);
            }
        }
        return layoutRowsGreedy(availableWidth);
    }

    private int layoutRowsGreedy(int availableWidth) {
        int targetRowHeight = AndroidUtilities.dp(180f);
        int minRowHeight = AndroidUtilities.dp(120f);
        int maxRowHeight = AndroidUtilities.dp(260f);
        int index = 0;
        int y = 0;
        while (index < tiles.size()) {
            float rowAspectSum = 0f;
            int rowEnd = index;
            while (rowEnd < tiles.size() && rowEnd - index < MAX_TILES_PER_ROW) {
                rowAspectSum += tiles.get(rowEnd).aspectRatio();
                rowEnd++;
                int projectedHeight = Math.round((availableWidth - spacing * (rowEnd - index - 1)) / rowAspectSum);
                if (projectedHeight <= targetRowHeight || rowEnd == tiles.size()) {
                    break;
                }
            }
            int rowCount = rowEnd - index;
            int rowWidth = availableWidth - spacing * (rowCount - 1);
            int rowHeight = clamp(Math.round(rowWidth / rowAspectSum), minRowHeight, maxRowHeight);
            int x = 0;
            int remainingWidth = rowWidth;
            for (int tileIndex = index; tileIndex < rowEnd; tileIndex++) {
                boolean isLast = tileIndex == rowEnd - 1;
                int tileWidth = isLast
                        ? remainingWidth
                        : Math.max(Math.round(rowHeight * tiles.get(tileIndex).aspectRatio()), AndroidUtilities.dp(64f));
                tiles.get(tileIndex).rect.set(x, y, x + tileWidth, y + rowHeight);
                x += tileWidth + spacing;
                remainingWidth -= tileWidth;
            }
            y += rowHeight;
            index = rowEnd;
            if (index < tiles.size()) {
                y += spacing;
            }
        }
        return y;
    }

    private LayoutAttempt optimalLayoutAttempt(int availableWidth) {
        float averageAspectRatio = 1f;
        for (Tile tile : tiles) {
            averageAspectRatio += tile.aspectRatio();
        }
        averageAspectRatio /= Math.max(tiles.size(), 1);

        float[] croppedRatios = new float[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            float aspect = tiles.get(i).aspectRatio();
            croppedRatios[i] = averageAspectRatio > 1.1f ? Math.max(1f, aspect) : Math.min(1f, aspect);
            croppedRatios[i] = clamp(croppedRatios[i], 0.66667f, 1.7f);
        }

        ArrayList<LayoutAttempt> attempts = new ArrayList<>();
        for (int firstLine = 1; firstLine < croppedRatios.length; firstLine++) {
            int secondLine = croppedRatios.length - firstLine;
            if (firstLine > 3 || secondLine > 3) {
                continue;
            }
            attempts.add(new LayoutAttempt(
                    new int[]{firstLine, secondLine},
                    new float[]{
                            multiHeight(croppedRatios, 0, firstLine, availableWidth),
                            multiHeight(croppedRatios, firstLine, croppedRatios.length, availableWidth)
                    }
            ));
        }

        for (int firstLine = 1; firstLine < croppedRatios.length - 1; firstLine++) {
            for (int secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                int thirdLine = croppedRatios.length - firstLine - secondLine;
                if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) {
                    continue;
                }
                attempts.add(new LayoutAttempt(
                        new int[]{firstLine, secondLine, thirdLine},
                        new float[]{
                                multiHeight(croppedRatios, 0, firstLine, availableWidth),
                                multiHeight(croppedRatios, firstLine, firstLine + secondLine, availableWidth),
                                multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length, availableWidth)
                        }
                ));
            }
        }

        for (int firstLine = 1; firstLine < croppedRatios.length - 2; firstLine++) {
            for (int secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                for (int thirdLine = 1; thirdLine < croppedRatios.length - firstLine - secondLine; thirdLine++) {
                    int fourthLine = croppedRatios.length - firstLine - secondLine - thirdLine;
                    if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
                        continue;
                    }
                    attempts.add(new LayoutAttempt(
                            new int[]{firstLine, secondLine, thirdLine, fourthLine},
                            new float[]{
                                    multiHeight(croppedRatios, 0, firstLine, availableWidth),
                                    multiHeight(croppedRatios, firstLine, firstLine + secondLine, availableWidth),
                                    multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine, availableWidth),
                                    multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.length, availableWidth)
                            }
                    ));
                }
            }
        }

        LayoutAttempt optimal = null;
        float optimalDiff = 0f;
        float maxHeight = availableWidth / 3f * 4f;
        float minLineHeight = AndroidUtilities.dp(100f);
        for (LayoutAttempt attempt : attempts) {
            float height = 0f;
            float minHeight = Float.MAX_VALUE;
            for (float lineHeight : attempt.heights) {
                height += lineHeight;
                minHeight = Math.min(minHeight, lineHeight);
            }
            float diff = Math.abs(height - maxHeight);
            if (attempt.lineCounts.length > 1
                    && (attempt.lineCounts[0] > attempt.lineCounts[1]
                    || attempt.lineCounts.length > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]
                    || attempt.lineCounts.length > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
                diff *= 1.2f;
            }
            if (minHeight < minLineHeight) {
                diff *= 1.5f;
            }
            if (optimal == null || diff < optimalDiff) {
                optimal = attempt;
                optimalDiff = diff;
            }
        }
        return optimal;
    }

    private int layoutAttempt(int availableWidth, LayoutAttempt attempt) {
        int index = 0;
        int y = 0;
        for (int lineIndex = 0; lineIndex < attempt.lineCounts.length; lineIndex++) {
            int count = attempt.lineCounts[lineIndex];
            int rowWidth = availableWidth - spacing * (count - 1);
            float rowAspectSum = 0f;
            for (int i = 0; i < count; i++) {
                rowAspectSum += tiles.get(index + i).aspectRatio();
            }
            int rowHeight = Math.max(AndroidUtilities.dp(100f), Math.round(attempt.heights[lineIndex]));
            int x = 0;
            int remainingWidth = rowWidth;
            for (int i = 0; i < count; i++) {
                int tileIndex = index + i;
                boolean last = i == count - 1;
                int tileWidth = last
                        ? remainingWidth
                        : Math.max(AndroidUtilities.dp(64f), Math.round(rowWidth * tiles.get(tileIndex).aspectRatio() / rowAspectSum));
                tiles.get(tileIndex).rect.set(x, y, x + tileWidth, y + rowHeight);
                x += tileWidth + spacing;
                remainingWidth -= tileWidth;
            }
            index += count;
            y += rowHeight;
            if (lineIndex < attempt.lineCounts.length - 1) {
                y += spacing;
            }
        }
        return y;
    }

    private static float multiHeight(float[] ratios, int start, int end, int width) {
        float sum = 0f;
        for (int i = start; i < end; i++) {
            sum += ratios[i];
        }
        int count = Math.max(end - start, 1);
        return (width - AndroidUtilities.dp(2f) * (count - 1)) / Math.max(sum, 0.1f);
    }

    private int tileIndexAt(float x, float y) {
        float gridY = y - listTopPadding;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).rect.contains(x, gridY)) {
                return i;
            }
        }
        return -1;
    }

    private RectF tileBoundsInCell(String imageUrl) {
        for (Tile tile : tiles) {
            if (tile.targetUrl.equals(imageUrl)) {
                RectF bounds = new RectF(tile.rect);
                bounds.offset(0f, listTopPadding);
                return bounds;
            }
        }
        return null;
    }

    private void drawTile(Canvas canvas, Tile tile, RectF drawRect, View invalidateView) {
        boolean hidden = tile.targetUrl.equals(context.getHiddenImageUrlProvider().invoke());
        boolean loading = !tile.receiver.hasBitmapImage() || tile.receiver.getCurrentAlpha() != 1.0f;
        if (hidden || loading) {
            canvas.drawRect(drawRect, placeholderPaint);
        }
        if (!hidden) {
            tile.receiver.setImageCoords(drawRect);
            tile.receiver.draw(canvas);
            updateProgressState(tile, drawRect, loading);
            tile.radialProgress.draw(canvas);
            if (loading) {
                invalidateTileView(invalidateView);
            }
        }
    }

    private boolean handleTileTouch(int index, MotionEvent event) {
        if (index < 0 || index >= tiles.size()) {
            return false;
        }
        Tile tile = tiles.get(index);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressedTileIndex = index;
            invalidateTile(index);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            boolean wasPressed = pressedTileIndex == index;
            pressedTileIndex = -1;
            invalidateTile(index);
            if (wasPressed) {
                openTile(tile);
            }
            return wasPressed;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (pressedTileIndex == index) {
                pressedTileIndex = -1;
                invalidateTile(index);
                return true;
            }
            return false;
        }
        return pressedTileIndex == index;
    }

    private void openTile(Tile tile) {
        context.getDelegate().onImageClick(
                tile.targetUrl,
                gallery,
                tileBoundsInCell(tile.targetUrl),
                context.getHost(),
                this::tileBoundsInCell,
                this::imageReceiverForUrl,
                imageUrl -> {
                    ImageReceiver receiver = imageReceiverForUrl(imageUrl);
                    return receiver != null ? receiver.getBitmapSafe() : null;
                },
                imageUrl -> {
                    ImageReceiver receiver = imageReceiverForUrl(imageUrl);
                    return receiver != null ? copyRadius(receiver) : null;
                }
        );
    }

    private void invalidateTile(int index) {
        invalidateBlock();
        if (index < 0) {
            return;
        }
        if (innerListView != null) {
            RecyclerView.ViewHolder holder = innerListView.findViewHolderForAdapterPosition(index);
            if (holder != null) {
                holder.itemView.invalidate();
            }
        }
    }

    private void invalidateTileView(View invalidateView) {
        if (invalidateView != null) {
            invalidateView.invalidate();
        } else {
            invalidateBlock();
        }
    }

    private void invalidateBlock() {
        context.getHost().invalidate();
        if (blockView != null) {
            blockView.invalidate();
        }
    }

    private CollageBlockView ensureBlockView() {
        if (blockView != null) {
            return blockView;
        }
        blockView = new CollageBlockView(context.getHost().getContext());
        RecyclerListView listView = ensureInnerListView();
        if (listView.getParent() != blockView) {
            if (listView.getParent() instanceof ViewGroup) {
                ((ViewGroup) listView.getParent()).removeView(listView);
            }
            blockView.addView(listView);
        }
        return blockView;
    }

    private RecyclerListView ensureInnerListView() {
        if (innerListView != null) {
            return innerListView;
        }
        innerListView = new RecyclerListView(context.getHost().getContext()) {
            @Override
            public boolean canScrollVertically(int direction) {
                return false;
            }

            @Override
            public boolean canScrollHorizontally(int direction) {
                return false;
            }
        };
        innerListView.setLayoutManager(new CollageLayoutManager());
        innerListView.setAdapter(innerAdapter);
        innerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        innerListView.setNestedScrollingEnabled(false);
        innerListView.setClipToPadding(false);
        return innerListView;
    }

    private boolean onCaptionTouch(MotionEvent event, float localX, float localY) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock)) {
                    return true;
                }
                if (SpoilerTextLayout.contains(spoilers, localX, localY)) {
                    pressedSpoiler = true;
                    downX = localX;
                    downY = localY;
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock);
                }
                if (pressedSpoiler) {
                    if (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop) {
                        pressedSpoiler = false;
                        return false;
                    }
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock);
                }
                if (pressedSpoiler) {
                    pressedSpoiler = false;
                    return SpoilerTextLayout.reveal(context.getHost(), captionLayout, spoilersPool, spoilers, localX, localY, this::markSpoilersRevealed);
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                if (linkPressState.isPressed()) {
                    return linkPressState.onTouch(event, localX, localY, captionLayout, builtCaption, context, touchSlop, this::invalidateBlock);
                }
                if (pressedSpoiler) {
                    pressedSpoiler = false;
                    return true;
                }
                return false;
            default:
                return linkPressState.isPressed() || pressedSpoiler;
        }
    }

    private void markSpoilersRevealed() {
        spoilersRevealed = true;
        if (inlineState != null && inlineSpoilerStateKey != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(inlineSpoilerStateKey);
        }
        invalidateBlock();
    }

    private static String captionHtmlFor(List<CookedPostBlock.Image> images) {
        StringBuilder builder = new StringBuilder();
        for (CookedPostBlock.Image image : images) {
            String html = image.getCaptionHtml();
            if (TextUtils.isEmpty(html) || html.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("<br>");
            }
            builder.append(html);
        }
        return builder.length() > 0 ? builder.toString() : null;
    }

    private static char aspectKind(float aspectRatio) {
        if (aspectRatio > 1.2f) {
            return 'w';
        }
        if (aspectRatio < 0.8f) {
            return 'n';
        }
        return 'q';
    }

    private static int[] copyRadius(ImageReceiver receiver) {
        int[] radius = receiver.getRoundRadius(true);
        return radius != null ? radius.clone() : null;
    }

    private void updateProgressState(Tile tile, RectF drawRect, boolean loading) {
        int buttonSize = AndroidUtilities.dp(48f);
        int buttonX = Math.round(drawRect.left + (drawRect.width() - buttonSize) / 2f);
        int buttonY = Math.round(drawRect.top + (drawRect.height() - buttonSize) / 2f);
        tile.radialProgress.setProgressRect(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
        tile.radialProgress.setIcon(loading ? MediaActionDrawable.ICON_EMPTY : MediaActionDrawable.ICON_NONE, true, false);
        tile.radialProgress.setProgress(loading ? 0f : 1f, false);
    }

    private final class CollageLayoutManager extends RecyclerView.LayoutManager {
        @Override
        public RecyclerView.LayoutParams generateDefaultLayoutParams() {
            return new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            );
        }

        @Override
        public boolean canScrollVertically() {
            return false;
        }

        @Override
        public boolean canScrollHorizontally() {
            return false;
        }

        @Override
        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            detachAndScrapAttachedViews(recycler);
            if (state.getItemCount() == 0) {
                return;
            }
            for (int i = 0; i < state.getItemCount(); i++) {
                View view = recycler.getViewForPosition(i);
                addView(view);
                RectF rect = tiles.get(i).rect;
                int left = Math.round(rect.left);
                int top = Math.round(rect.top);
                int right = Math.round(rect.right);
                int bottom = Math.round(rect.bottom);
                view.measure(
                        View.MeasureSpec.makeMeasureSpec(Math.max(1, right - left), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(Math.max(1, bottom - top), View.MeasureSpec.EXACTLY)
                );
                layoutDecorated(view, left, top, right, bottom);
            }
        }
    }

    private final class TileAdapter extends RecyclerView.Adapter<TileHolder> {
        @Override
        public TileHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new TileHolder(new TileView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(TileHolder holder, int position) {
            holder.tileView.setTileIndex(position);
        }

        @Override
        public int getItemCount() {
            return tiles.size();
        }
    }

    private final class CollageBlockView extends ViewGroup {
        CollageBlockView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = View.MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, measuredHeight);
            if (innerListView != null) {
                innerListView.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(gridHeight, View.MeasureSpec.EXACTLY)
                );
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (innerListView != null) {
                innerListView.layout(0, listTopPadding, right - left, contentBottom);
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            drawCaption(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return CollageBlock.this.onTouch(event, event.getX(), event.getY()) || super.onTouchEvent(event);
        }
    }

    private static final class TileHolder extends RecyclerView.ViewHolder {
        final TileView tileView;

        TileHolder(TileView itemView) {
            super(itemView);
            tileView = itemView;
        }
    }

    private final class TileView extends View {
        private final RectF localRect = new RectF();
        private int tileIndex = -1;

        TileView(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
        }

        void setTileIndex(int tileIndex) {
            this.tileIndex = tileIndex;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (tileIndex < 0 || tileIndex >= tiles.size()) {
                return;
            }
            localRect.set(0f, 0f, getWidth(), getHeight());
            drawTile(canvas, tiles.get(tileIndex), localRect, this);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (tileIndex < 0 || tileIndex >= tiles.size()) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && (event.getX() < 0f || event.getX() > getWidth() || event.getY() < 0f || event.getY() > getHeight())) {
                if (pressedTileIndex == tileIndex) {
                    pressedTileIndex = -1;
                    invalidateTile(tileIndex);
                }
                return false;
            }
            return handleTileTouch(tileIndex, event);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

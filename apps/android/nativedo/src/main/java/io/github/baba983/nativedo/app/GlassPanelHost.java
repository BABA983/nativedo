package io.github.baba983.nativedo.app;

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.utils.RectFMergeBounding;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.WallpaperBitmapProvider;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.reference.ReferenceList;

/**
 * The blur/glass background of a floating panel, in one reusable place on our side.
 *
 * <p>Upstream's own wiring for a screen whose panels float over a scrolling list: a
 * {@link DownscaleScrollableNoiseSuppressor}-backed source that records the content under the
 * panels' own rectangles at a 1/8 scale, drawn over the wallpaper chain ({@code setUnderSource}),
 * so a translucent capsule's gaps show the wallpaper and never the sharp list. A chat builds it at
 * ChatActivity.java:2620-2661, its list at DialogsActivity.java:2700-2810 and its own blur-rect
 * path at 930-950; a panel wears a drawable made from the source, exactly like upstream's
 * {@code FilterTabsView.setBlurredBackground} and {@code ActionBar.setupGlass} do.</p>
 *
 * <p>The source exists only while the chat blur is on ({@code SharedConfig.chatBlurEnabled()}), so
 * a panel whose screen cannot blur - a LOW-performance device, where upstream's own provider paints
 * an opaque fill - stands over the flat colour source below, which is what upstream's own
 * {@code iBlur3SourceColor} is for, and so does a platform that cannot render a node at all
 * ({@code RenderEffect} needs API 31).</p>
 */
public final class GlassPanelHost {

    /** What the source records: the content the panels float over. */
    private View recordedContent;
    /** The view whose coordinate space the source records in - the panels' positions are too. */
    private final ViewGroup positionRoot;
    /**
     * The wallpaper the panels stand on, as upstream reads it (ChatActivity:17229-17245 feeds its
     * source from the screen's background drawable). Drawn unblurred under the recorded content, so
     * a capsule's translucent gaps are wallpaper and not the sharp list under it.
     */
    private @Nullable Drawable wallpaperDrawable;
    private final WallpaperBitmapProvider wallpaperProvider = new WallpaperBitmapProvider();
    private final BlurredBackgroundSourceColor colorSource = new BlurredBackgroundSourceColor();
    /** Upstream's {@code navbarContentSourceWallpaper}: a source that can be swapped under both. */
    private final BlurredBackgroundSourceWrapped wallpaperSource = new BlurredBackgroundSourceWrapped();
    /** The blur source; null while the device cannot blur. */
    private final @Nullable BlurredBackgroundSourceRenderNode renderNode;
    private final @Nullable DownscaleScrollableNoiseSuppressor scrollableNoiseSuppressor;
    /** The panels' source: the recording over the wallpaper. */
    private final BlurredBackgroundDrawableViewFactory factory;
    /**
     * What a fading band draws from: the wallpaper itself, with no recording in it. ChatActivity
     * hands its band {@code navbarContentDrawableFactory}, the factory over the wallpaper source
     * and not over the frosted node (6970, 2652).
     */
    private final BlurredBackgroundDrawableViewFactory wallpaperFactory;
    private final ViewPositionWatcher positionWatcher;
    /**
     * The views that drew a panel from this source. A view's display list is cached, so a panel
     * keeps the frame it was last recorded with until it is told to redraw - ChatActivity keeps
     * its own list of glass-attached views for exactly that ({@code glassAttachedViews},
     * ChatActivity.java:2653/46715) and every {@code factory.create(view)} registers here.
     */
    private final ReferenceList<View> attachedViews = new ReferenceList<>();
    /** The drawables made from this source, so a colour change reaches all of them. */
    private final List<BlurredBackgroundDrawable> drawables = new ArrayList<>();
    /** The recording under the panels, read through this screen's own coordinates. */
    private final IBlur3Capture capture = this::captureContent;
    private final ArrayList<RectF> visiblePositions = new ArrayList<>();
    private final ArrayList<RectF> mergedPositions = new ArrayList<>();
    private final PointF contentPoint = new PointF();
    private final RectF savedPosition = new RectF();
    /**
     * A rectangle the screen reads the recording under with {@link #drawSource} - the header band of
     * a screen that draws one. DialogsActivity keeps the same list of blurred rectangles
     * ({@code iBlur3Positions}, 14154-14202): the source records where it is read, and a band that
     * read a rectangle no panel covers would have nothing to read.
     */
    private final RectF sourceRect = new RectF();
    /** Whether the panels' rectangles have to be read again before the next recording. */
    private boolean positionsDirty = true;
    private boolean recordingEnabled = true;

    /**
     * @param recordedContent the content the panels float over; the source records only the part of
     *                        this view that lies under a panel.
     * @param positionRoot    the view whose coordinate space the panels' positions are measured in.
     */
    public GlassPanelHost(View recordedContent, ViewGroup positionRoot) {
        this.recordedContent = recordedContent;
        this.positionRoot = positionRoot;
        this.positionWatcher = new ViewPositionWatcher(positionRoot);
        colorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        wallpaperSource.setSource(colorSource);
        if (Build.VERSION.SDK_INT >= 31 && SharedConfig.chatBlurEnabled()) {
            DownscaleScrollableNoiseSuppressor noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            BlurredBackgroundSourceRenderNode node = new BlurredBackgroundSourceRenderNode(wallpaperSource);
            // What the chat header's glass is made of: the frosted source.
            // DownscaleScrollableNoiseSuppressor's SourcePart blurs dpf2(40) and raises the
            // saturation by 3 in its simpleMode branch (407-426), so a capsule samples a strongly
            // frosted, saturated image instead of the barely-softened one an 8dp blur gives - and
            // only of the rects the panels occupy, at 1/8 the pixels (504-509).
            node.setScrollableNoiseSuppressor(
                    noiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
            node.setUnderSource(wallpaperSource);
            node.setOnDrawablesRelativePositionChangeListener(() -> positionsDirty = true);
            scrollableNoiseSuppressor = noiseSuppressor;
            renderNode = node;
            factory = new BlurredBackgroundDrawableViewFactory(node);
        } else {
            scrollableNoiseSuppressor = null;
            renderNode = null;
            factory = new BlurredBackgroundDrawableViewFactory(colorSource);
        }
        wallpaperFactory = new BlurredBackgroundDrawableViewFactory(wallpaperSource);
        factory.setSourceRootView(positionWatcher, positionRoot);
        factory.setLinkedViewsRef(attachedViews);
        factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        wallpaperFactory.setSourceRootView(positionWatcher, positionRoot);
        wallpaperFactory.setLinkedViewsRef(attachedViews);
    }

    /** Swaps what is recorded, when the panel that is up changes with the screen's state. */
    public void setRecordedContent(View content) {
        recordedContent = content;
        positionsDirty = true;
    }

    /**
     * The wallpaper the panels stand on; the screen hands it the same drawable it gave its
     * background view (ChatActivity reads it from the background drawable itself, 17229-17245).
     */
    public void setWallpaperDrawable(@Nullable Drawable drawable) {
        wallpaperDrawable = drawable;
        updateWallpaperSource();
    }

    /**
     * The rectangle a screen reads the recording under, in the position root's coordinates; empty
     * while the screen draws no such band. It is recorded under like a panel's own rectangle.
     */
    public void setSourceRect(@Nullable android.graphics.Rect rect) {
        if (rect == null) {
            if (sourceRect.isEmpty()) {
                return;
            }
            sourceRect.setEmpty();
        } else {
            final float left = rect.left - AndroidUtilities.dp(48);
            final float top = rect.top - AndroidUtilities.dp(48);
            final float right = rect.right + AndroidUtilities.dp(48);
            final float bottom = rect.bottom + AndroidUtilities.dp(48);
            if (sourceRect.left == left && sourceRect.top == top
                    && sourceRect.right == right && sourceRect.bottom == bottom) {
                return;
            }
            sourceRect.set(left, top, right, bottom);
        }
        positionsDirty = true;
    }

    /**
     * A panel's background, positioned under the panel: the caller sets its radius and padding the
     * way upstream does (18dp / 6.666dp for a capsule) and gives it to the panel.
     */
    public BlurredBackgroundDrawable createPanel(View panel, BlurredBackgroundColorProvider provider) {
        BlurredBackgroundDrawable drawable = factory.create(panel, provider);
        drawables.add(drawable);
        return drawable;
    }

    /**
     * The raw factory, for an upstream component that builds its own drawables from one - the chat
     * header's {@code ActionBar.setupGlass} takes the factory itself and makes the back, center and
     * menu capsules with it.
     */
    public BlurredBackgroundDrawableViewFactory getFactory() {
        return factory;
    }

    /**
     * The factory over the wallpaper alone, for the fading band: ChatActivity's band is built from
     * {@code navbarContentDrawableFactory} (6970), whose source is the wallpaper chain - so the band
     * fades the posts into the wallpaper instead of sampling a blurred recording of the posts that
     * is itself scrolling (BlurredBackgroundWithFadeDrawable's own fast way, 100-180).
     */
    public BlurredBackgroundDrawableViewFactory getWallpaperFactory() {
        return wallpaperFactory;
    }

    /** Whether a render node is recording; false on a device that cannot blur. */
    public boolean hasSource() {
        return renderNode != null;
    }

    /**
     * The recorded content itself, drawn into a rectangle the way {@code DialogsActivity}'s
     * blur-rect override reads its frosted source under the header's glass (930-950). The
     * rectangle is in the position root's coordinates, which is where the source was recorded.
     */
    public void drawSource(Canvas canvas, float left, float top, float right, float bottom) {
        if (renderNode == null || right <= left || bottom <= top) {
            return;
        }
        renderNode.draw(canvas, left, top, right, bottom);
    }

    /**
     * Records what is behind the panels, from the draw pass that is about to draw them -
     * DialogsActivity's own {@code blur3_InvalidateBlur} sits in its content view's
     * {@code dispatchDraw} for the same reason (930-950, 4025, 14163). Called before the frame
     * draws its children, so a panel samples the content and never itself.
     */
    public void record() {
        if (scrollableNoiseSuppressor == null || renderNode == null || !recordingEnabled
                || !recordedContent.isHardwareAccelerated()) {
            return;
        }
        int width = positionRoot.getMeasuredWidth();
        int height = positionRoot.getMeasuredHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        // A wallpaper image is sampled centre-cropped over the screen, which is a size the source
        // has to be told (ChatActivity does the same from its own onMeasure, 18249-18252).
        if (wallpaperSource.getSource() instanceof BlurredBackgroundSourceBitmap) {
            ((BlurredBackgroundSourceBitmap) wallpaperSource.getSource()).setParentSize(width, height, 0);
        }
        if (positionsDirty) {
            positionsDirty = false;
            int count = 0;
            if (!sourceRect.isEmpty()) {
                while (visiblePositions.size() < 1) {
                    visiblePositions.add(new RectF());
                }
                visiblePositions.get(0).set(sourceRect);
                count = 1;
            }
            count += renderNode.getVisiblePositions(visiblePositions, count, AndroidUtilities.dp(48));
            scrollableNoiseSuppressor.setupRenderNodes(
                    mergedPositions, RectFMergeBounding.mergeOverlapping(visiblePositions, count, mergedPositions));
        }
        if (scrollableNoiseSuppressor.getRenderNodesCount() == 0) {
            positionsDirty = true;
            return;
        }
        // The recording is what the panels draw; a panel that drew from it keeps the frame it was
        // recorded with, so it is told to draw again when the recording or its own rect changed.
        if (scrollableNoiseSuppressor.invalidateResultRenderNodes(capture, width, height)) {
            renderNode.invalidateDisplayListForDrawables();
            invalidateAttachedViews();
        }
    }

    /** The part of what is recorded that a panel covers - only that is ever rasterized. */
    private void captureContent(Canvas canvas, RectF position) {
        if (!ViewPositionWatcher.computeCoordinatesInParent(recordedContent, positionRoot, contentPoint)) {
            return;
        }
        canvas.save();
        canvas.clipRect(position);
        canvas.translate(contentPoint.x, contentPoint.y);
        if (recordedContent instanceof IBlur3Capture) {
            // A list draws only the children that meet the rectangle (RecyclerListView.capture).
            savedPosition.set(position);
            position.offset(-contentPoint.x, -contentPoint.y);
            ((IBlur3Capture) recordedContent).capture(canvas, position);
            position.set(savedPosition);
        } else {
            recordedContent.draw(canvas);
        }
        canvas.restore();
    }

    /** Stops recording while no panel is visible, and starts again when one returns. */
    public void setRecordingEnabled(boolean enabled) {
        recordingEnabled = enabled;
        if (enabled) {
            positionsDirty = true;
        }
    }

    /**
     * Invalidates every view that draws one of this source's panels - the ones given to
     * {@link #createPanel} and the ones an upstream component built from {@link #getFactory}
     * (the bar's capsules, the fading band). ChatActivity drives the same call from its chat
     * list's scroll invalidation ({@code invalidateAllGlassAttachedViews}, 46715, called from
     * 6792/6872 and 47143): without it a panel keeps the frame it was last recorded with,
     * because the view's display list is cached until it is invalidated.
     */
    public void invalidateAttachedViews() {
        factory.invalidateAllLinkedViews();
        wallpaperFactory.invalidateAllLinkedViews();
    }

    /** Re-reads the theme colours, for the day/night switch. */
    public void updateColors() {
        colorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (wallpaperDrawable != null) {
            updateWallpaperSource();
        }
        for (int index = 0; index < drawables.size(); index++) {
            drawables.get(index).updateColors();
        }
        positionsDirty = true;
    }

    /** Re-reads the wallpaper source from the drawable the screen gave, and repaints what samples it. */
    private void updateWallpaperSource() {
        final BlurredBackgroundSource source = wallpaperDrawable == null
                ? colorSource
                : wallpaperProvider.updateSourceFromBackgroundViewDrawable(wallpaperDrawable);
        // A drawable the provider could not turn into pixels would leave the band and the capsules'
        // under-layer empty; the theme's own colour is what stands in for it.
        final boolean usable = !(source instanceof BlurredBackgroundSourceBitmap)
                || ((BlurredBackgroundSourceBitmap) source).getBitmap() != null;
        wallpaperSource.setSource(usable ? source : colorSource);
        if (renderNode != null) {
            renderNode.invalidateDisplayListForDrawables();
        }
        invalidateAttachedViews();
    }
}

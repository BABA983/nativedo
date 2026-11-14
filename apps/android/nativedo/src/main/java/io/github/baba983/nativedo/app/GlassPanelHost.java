package io.github.baba983.nativedo.app;

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.WallpaperBitmapProvider;
import org.telegram.utils.glass.GlassEngine;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.positions.GlassPositionsMerger;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.utils.glass.source.GlassSourceWrapped;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.reference.ReferenceList;

/**
 * The blur/glass background of a floating panel, in one reusable place on our side.
 *
 * <p>Upstream's own wiring for a screen whose panels float over a scrolling list, as of Telegram
 * 13.2.1: a {@link GlassEngine} owns the drawables' positions, their display lists and their theme
 * updates; a {@link DownscaleScrollableNoiseSuppressor}-backed source records the content under the
 * panels' own rectangles at a 1/8 scale, drawn over the wallpaper chain ({@code setUnderSource}),
 * so a translucent capsule's gaps show the wallpaper and never the sharp list. A chat builds it at
 * ChatActivity:2596-2645 and drives its captures at 47832-47847; the dialogs list builds its own
 * pair at DialogsActivity:2703-2722 and captures at 13832. A panel wears a drawable made from the
 * source, exactly like upstream's {@code FilterTabsView.setBlurredBackground} and
 * {@code ActionBar.setupGlass} do.</p>
 *
 * <p>Two render-node sources share one suppressor, the way upstream keeps a glass and a frosted
 * source (ChatActivity:2602-2618, DialogsActivity:2706-2713): the drawable picks the glass source
 * under the liquid-glass setting, the frosted one for a plain blur, and the flat colour source when
 * the device cannot render a node at all ({@code RenderEffect} needs API 31) or the chat blur is
 * off. The colour source is registered with the engine, so a day/night switch reaches it without
 * the screen rebuilding anything.</p>
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
    /** Owns the panels' positions, display lists and theme updates, as every upstream screen's. */
    private final GlassEngine engine = new GlassEngine();
    private final GlassSourceColor colorSource;
    /** Upstream's {@code navbarContentSourceWallpaper}: a source that can be swapped under both. */
    private final GlassSourceWrapped wallpaperSource = new GlassSourceWrapped();
    /** The liquid-glass and the frosted source; null while the device cannot blur. */
    private final @Nullable GlassSourceRenderNode glassSource;
    private final @Nullable GlassSourceRenderNode frostedSource;
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
     * keeps the frame it was last recorded with until it is told to redraw - the engine keeps the
     * same list for its registered drawables, and every {@code factory.create(view)} registers
     * here as well so a wallpaper swap can repaint them.
     */
    private final ReferenceList<View> attachedViews = new ReferenceList<>();
    /** The drawables made from this source, so a colour change reaches all of them. */
    private final List<BlurredBackgroundDrawable> drawables = new ArrayList<>();
    /** The recording under the panels, read through this screen's own coordinates. */
    private final IBlur3Capture capture = this::captureContent;
    private final PointF contentPoint = new PointF();
    private final RectF savedPosition = new RectF();
    /**
     * A rectangle the screen reads the recording under with {@link #drawSource} - the header band of
     * a screen that draws one. The engine records it alongside the panels' own rectangles
     * ({@code setExternalCapturePosition}, BaseFragment:1517).
     */
    private final RectF externalCapturePosition = new RectF();
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
        // The engine re-reads the colour when the theme generation changes (GlassEngine.onDraw),
        // so a day/night switch reaches every panel without this host doing anything.
        colorSource = engine.createThemedColorSource(() -> Theme.getColor(Theme.key_windowBackgroundWhite));
        wallpaperSource.setSource(colorSource);
        // Upstream merges the panels' rectangles the same way: one bounding box per axis, so the
        // header band and the capsules over it are recorded as one region.
        engine.setPositionsMerger(new GlassPositionsMerger(Float.POSITIVE_INFINITY, AndroidUtilities.dp(48)));
        if (Build.VERSION.SDK_INT >= 31 && SharedConfig.chatBlurEnabled()) {
            DownscaleScrollableNoiseSuppressor noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            GlassSourceRenderNode frosted = new GlassSourceRenderNode(wallpaperSource);
            // What the chat header's glass is made of: the frosted source.
            // DownscaleScrollableNoiseSuppressor's SourcePart blurs dpf2(40) and raises the
            // saturation by 3 in its simpleMode branch, so a capsule samples a strongly frosted,
            // saturated image instead of the barely-softened one an 8dp blur gives - and only of
            // the rects the panels occupy, at 1/8 the pixels.
            frosted.setScrollableNoiseSuppressor(
                    noiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
            frosted.setUnderSource(wallpaperSource);
            GlassSourceRenderNode glass = new GlassSourceRenderNode(wallpaperSource);
            glass.setScrollableNoiseSuppressor(
                    noiseSuppressor, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
            glass.setUnderSource(wallpaperSource);
            scrollableNoiseSuppressor = noiseSuppressor;
            frostedSource = frosted;
            glassSource = glass;
            engine.addNoiseSuppressor(noiseSuppressor);
            engine.setGlassInvalidationListener(this::invalidateBlurCaptures);
            // A drawable picks its source by the glass settings: liquid glass, frosted, or the
            // flat colour when neither can be rendered (BlurredBackgroundDrawable.pickSource).
            factory = new BlurredBackgroundDrawableViewFactory(glass, frosted, colorSource);
        } else {
            scrollableNoiseSuppressor = null;
            frostedSource = null;
            glassSource = null;
            factory = new BlurredBackgroundDrawableViewFactory(colorSource);
        }
        wallpaperFactory = new BlurredBackgroundDrawableViewFactory(wallpaperSource);
        factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        factory.setGlassEngine(engine);
        factory.setSourceRootView(positionWatcher, positionRoot);
        factory.setLinkedViewsRef(attachedViews);
        wallpaperFactory.setGlassEngine(engine);
        wallpaperFactory.setSourceRootView(positionWatcher, positionRoot);
        wallpaperFactory.setLinkedViewsRef(attachedViews);
        // The engine computes the panels' rectangles in the root's post-draw pass, the way
        // BaseFragment.onViewCreated attaches every fragment's engine (BaseFragment:330).
        engine.setRoot(positionRoot);
    }

    /** Swaps what is recorded, when the panel that is up changes with the screen's state. */
    public void setRecordedContent(View content) {
        recordedContent = content;
        engine.invalidate();
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
     * while the screen draws no such band. The engine records it alongside the panels' rectangles.
     */
    public void setSourceRect(@Nullable Rect rect) {
        if (rect == null) {
            externalCapturePosition.setEmpty();
        } else {
            externalCapturePosition.set(rect);
        }
        engine.setExternalCapturePosition(externalCapturePosition);
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
        return frostedSource != null;
    }

    /**
     * The recorded content itself, drawn into a rectangle the way {@code DialogsActivity}'s
     * blur-rect override reads its frosted source under the header's glass (930-950). The
     * rectangle is in the position root's coordinates, which is where the source was recorded.
     */
    public void drawSource(Canvas canvas, float left, float top, float right, float bottom) {
        if (frostedSource == null || right <= left || bottom <= top) {
            return;
        }
        frostedSource.draw(canvas, left, top, right, bottom);
    }

    /**
     * Tells the engine the frame is being drawn, from the draw pass that is about to draw the
     * panels - DialogsActivity's own {@code blur3_InvalidateBlur} sits in its content view's
     * {@code dispatchDraw} for the same reason (930-950, 4025, 14163). The engine records what is
     * behind the panels in its post-draw pass, so a panel samples the content and never itself.
     */
    public void record() {
        if (scrollableNoiseSuppressor == null || !recordingEnabled) {
            return;
        }
        engine.invalidate();
    }

    /**
     * The engine's post-draw pass: the panels' rectangles moved or appeared, or what is under them
     * changed. Upstream's screens answer it the same way - ChatActivity:47832-47847,
     * DialogsActivity:13832, ChatAttachAlert and ShareAlert.
     */
    private void invalidateBlurCaptures(int flags) {
        if (scrollableNoiseSuppressor == null) {
            return;
        }
        final int width = positionRoot.getWidth();
        final int height = positionRoot.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        // A wallpaper image is sampled centre-cropped over the screen, which is a size the source
        // has to be told (ChatActivity does the same from its own onMeasure, 18249-18252).
        final GlassSource wallpaper = wallpaperSource.getSource();
        if (wallpaper instanceof GlassSourceBitmap) {
            ((GlassSourceBitmap) wallpaper).setParentSize(width, height, 0);
        }
        if (BitwiseUtils.hasFlag(flags, GlassEngine.FLAG_INVALIDATED_POSITIONS)) {
            final GlassPositionsArray positions = engine.getAllVisibleDrawablesWithDisplayListPositionsMerged();
            // The capture covers the panel plus the margin its glass may grow into; upstream's
            // factories reserve the same 48dp (DialogsActivity:2714-2718, ChatActivity:47841).
            positions.outset(AndroidUtilities.dp(48), AndroidUtilities.dp(48));
            scrollableNoiseSuppressor.setupRenderNodes(positions);
        }
        scrollableNoiseSuppressor.invalidateResultRenderNodes(capture, width, height);
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
            engine.invalidate();
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
        if (wallpaperDrawable != null) {
            updateWallpaperSource();
        }
        for (int index = 0; index < drawables.size(); index++) {
            drawables.get(index).updateColors();
        }
        engine.invalidate();
    }

    /** Re-reads the wallpaper source from the drawable the screen gave, and repaints what samples it. */
    private void updateWallpaperSource() {
        final GlassSource source = wallpaperDrawable == null
                ? colorSource
                : wallpaperProvider.updateSourceFromBackgroundViewDrawable(wallpaperDrawable);
        // A drawable the provider could not turn into pixels would leave the band and the capsules'
        // under-layer empty; the theme's own colour is what stands in for it.
        final boolean usable = !(source instanceof GlassSourceBitmap)
                || ((GlassSourceBitmap) source).getBitmap() != null;
        wallpaperSource.setSource(usable ? source : colorSource);
        if (frostedSource != null) {
            frostedSource.invalidateDisplayListForDrawables();
        }
        if (glassSource != null) {
            glassSource.invalidateDisplayListForDrawables();
        }
        invalidateAttachedViews();
        engine.invalidate();
    }
}

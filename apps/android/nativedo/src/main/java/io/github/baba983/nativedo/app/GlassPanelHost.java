package io.github.baba983.nativedo.app;

import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.utils.RenderNodeEffects;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * The blur/glass background of a floating panel, in one reusable place on our side.
 *
 * <p>This is the wiring upstream's own screens use for their floating panels, gathered the way
 * {@code DialogsActivity} builds it (its {@code iBlur3SourceGlass} render node and
 * {@code iBlur3FactoryLiquidGlass} factory at DialogsActivity.java:2757-2809, its filter strip's
 * background at 5151-5155): a source render node that records the content a panel stands over, a
 * factory that turns that source into a drawable, and a {@link ViewPositionWatcher} that keeps the
 * drawable sampling the region under the panel as it moves. A panel wears the returned drawable as
 * its background, exactly like upstream's {@code FilterTabsView.setBlurredBackground} and
 * {@code FragmentSearchField.setupBlurredBackground} do.</p>
 *
 * <p>The home screen's category strip is the first user; the topic screen's action-bar capsules are
 * the next. The source exists only while the chat blur is on (ChatActivity.java:2620-2661 builds
 * its sources behind {@code SharedConfig.chatBlurEnabled()}), so a panel whose screen cannot blur -
 * a LOW-performance device, where upstream's own provider paints an opaque fill - stands over the
 * flat colour source below, which is what upstream's own {@code iBlur3SourceColor} is for. When the
 * platform cannot render a node at all ({@code RenderEffect} needs API 31) the factory is given
 * that same colour.</p>
 */
public final class GlassPanelHost {

    /** What the source records: the content the panels float over. */
    private View recordedContent;
    /**
     * What stands behind that content - the chat wallpaper - when the screen has one. A chat's
     * source draws the wallpaper chain under the posts (ChatActivity's navbar source, 17239-17241);
     * without one the recording's own base colour is what shows through.
     */
    private @Nullable View backgroundView;
    private final BlurredBackgroundSourceColor colorSource = new BlurredBackgroundSourceColor();
    /** The blur source; null while the platform cannot render one. */
    private final @Nullable BlurredBackgroundSourceRenderNode renderNode;
    private final BlurredBackgroundDrawableViewFactory factory;
    private final ViewPositionWatcher positionWatcher;
    /** The drawables made from this source, so a colour change reaches all of them. */
    private final List<BlurredBackgroundDrawable> drawables = new ArrayList<>();
    private boolean recordingEnabled = true;

    /**
     * @param recordedContent the content the panels float over; the source records this view's own
     *                        drawing each frame, so the content must sit at the position root's
     *                        origin (a full-size child of it) for the sample offsets to line up.
     * @param positionRoot    the view whose coordinate space the panels' positions are measured in.
     */
    public GlassPanelHost(View recordedContent, ViewGroup positionRoot) {
        this.recordedContent = recordedContent;
        this.positionWatcher = new ViewPositionWatcher(positionRoot);
        colorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (Build.VERSION.SDK_INT >= 31 && SharedConfig.chatBlurEnabled()) {
            BlurredBackgroundSourceRenderNode node = new BlurredBackgroundSourceRenderNode(colorSource);
            // What the chat header's glass is made of: the frosted source.
            // DownscaleScrollableNoiseSuppressor's SourcePart blurs dpf2(40) and raises the
            // saturation by 3 (simpleMode branch, 410-416), so a capsule samples a strongly
            // frosted, saturated image instead of the barely-softened one an 8dp blur gives.
            node.setBlur(AndroidUtilities.dpf2(40), RenderNodeEffects.getSaturationX3RenderEffect());
            renderNode = node;
        } else {
            renderNode = null;
        }
        factory = new BlurredBackgroundDrawableViewFactory(renderNode != null ? renderNode : colorSource);
        factory.setSourceRootView(positionWatcher, positionRoot);
        factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
    }

    /** Swaps what is recorded, when the panel that is up changes with the screen's state. */
    public void setRecordedContent(View content) {
        recordedContent = content;
    }

    /** What the recording draws under the content; a chat screen hands it its wallpaper view. */
    public void setBackgroundView(@Nullable View backgroundView) {
        this.backgroundView = backgroundView;
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
     * Records what is behind the panels. Called from the position root's draw pass, before it draws
     * its children, so a panel samples the content and never itself.
     */
    public void record() {
        if (!recordingEnabled || renderNode == null || !recordedContent.isHardwareAccelerated()) {
            return;
        }
        int width = recordedContent.getWidth();
        int height = recordedContent.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Canvas recording = renderNode.beginRecording(width, height);
        recording.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (backgroundView != null && backgroundView.getWidth() > 0 && backgroundView.getHeight() > 0) {
            // The chat's source draws the wallpaper under the posts (ChatActivity:17239-17241):
            // what a capsule samples is the wallpaper chain plus the list, never a colour fill.
            backgroundView.draw(recording);
        }
        recordedContent.draw(recording);
        renderNode.endRecording();
        renderNode.invalidateDisplayListForDrawables();
    }

    /** Stops recording while no panel is visible, and starts again when one returns. */
    public void setRecordingEnabled(boolean enabled) {
        recordingEnabled = enabled;
    }

    /** Re-reads the theme colours, for the day/night switch. */
    public void updateColors() {
        colorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        for (int index = 0; index < drawables.size(); index++) {
            drawables.get(index).updateColors();
        }
    }
}

package io.github.baba983.nativedo.app;

import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.telegram.messenger.LiteMode;
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
 * the next. When the platform cannot render a node ({@code RenderEffect} needs API 31) or the
 * window is not hardware accelerated, the factory falls back to a flat colour source, which is what
 * upstream's own {@code iBlur3SourceColor} is for.</p>
 */
public final class GlassPanelHost {

    /** What the source records: the content the panels float over. */
    private View recordedContent;
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
        if (Build.VERSION.SDK_INT >= 31) {
            colorSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            BlurredBackgroundSourceRenderNode node = new BlurredBackgroundSourceRenderNode(colorSource);
            node.setBlur(org.telegram.messenger.AndroidUtilities.dp(8));
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

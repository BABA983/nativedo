package io.github.baba983.nativedo.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;

/**
 * Debug-only probe for the topic screen's glass pipeline, started directly with adb. It is not
 * part of the app's navigation: it builds the same wiring the topic screen builds - a content
 * frame whose background is the chat wallpaper, a post-like list it records through
 * {@link GlassPanelHost}, and an {@link ActionBar} wearing the chat header's capsules from that
 * host - so the blurred branch can be seen on a device whose measured class would not take it
 * (pass EXTRA_FORCE_PERF_CLASS).
 *
 * <p>The content is deliberately post-like: white cards with dark text over the wallpaper, with
 * the wallpaper showing in the gaps. That is the contrast a capsule's blur has to show.</p>
 */
public class GlassProbeActivity extends Activity {

    public static final String EXTRA_FORCE_PERF_CLASS = "forcePerfClass";
    public static final String EXTRA_NIGHT = "night";

    private GlassPanelHost glassHost;
    private PostsView content;

    /** A list of white cards over the wallpaper; its own drawing is what the capsules sample. */
    private static class PostsView extends View {
        private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        PostsView(android.content.Context context) {
            super(context);
            card.setColor(0xFFFFFFFF);
            text.setColor(0xFF16202C);
            text.setTextSize(AndroidUtilities.dp(17));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int pad = AndroidUtilities.dp(12);
            final int step = AndroidUtilities.dp(150);
            int index = 0;
            for (int y = AndroidUtilities.dp(8); y < getHeight(); y += step + AndroidUtilities.dp(10)) {
                rect.set(pad, y, getWidth() - pad, y + step);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(14), AndroidUtilities.dp(14), card);
                canvas.drawText("linux.do 帖子 " + (++index), pad + AndroidUtilities.dp(14), y + AndroidUtilities.dp(34), text);
                for (int line = 0; line < 3; line++) {
                    canvas.drawText("正文内容正文内容正文内容正文内容 " + line,
                            pad + AndroidUtilities.dp(14), y + AndroidUtilities.dp(66 + line * 26), text);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        setTheme(org.telegram.messenger.R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);

        boolean force = getIntent() != null && getIntent().getBooleanExtra(EXTRA_FORCE_PERF_CLASS, false);
        boolean night = getIntent() != null && getIntent().getBooleanExtra(EXTRA_NIGHT, false);
        if (night) {
            // The probe is usually the process's first activity, so the theme preference is still
            // what getCurrentTheme() will read; the app's day theme is put back in onDestroy.
            getSharedPreferences("mainconfig", MODE_PRIVATE).edit().putString("theme", "Dark Blue").apply();
        }
        if (force) {
            // Upstream's own debug override (SettingsActivity's "Force performance class"),
            // plus the chat-blur wire the user-facing Power Saving page toggles.
            SharedConfig.overrideDevicePerformanceClass(SharedConfig.PERFORMANCE_CLASS_HIGH);
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
        }

        AndroidUtilities.enableEdgeToEdge(getWindow());
        Theme.createCommonResources(this);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);
        Theme.createCommonMessageResources();
        Theme.loadWallpaper(true);

        final SizeNotifierFrameLayout root = new SizeNotifierFrameLayout(this) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (glassHost != null) {
                    glassHost.record();
                }
                super.dispatchDraw(canvas);
            }
        };
        root.setClipChildren(false);
        content = new PostsView(this);
        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ActionBar actionBar = new ActionBar(this);
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setBackground(null);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.createMenu().addItem(1, org.telegram.messenger.R.drawable.ic_ab_other);
        actionBar.setTitle("glass probe");

        glassHost = new GlassPanelHost(content, root);
        actionBar.setupGlass(
                glassHost.getFactory(),
                BlurredBackgroundProviderImpl.topPanelChatActivity(null));
        root.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        setContentView(root);

        // The topic frame's own path (ChatActivity.updateBackground): the wallpaper under the
        // posts, and the source records that view when the screen has one.
        android.graphics.drawable.Drawable wallpaper = Theme.getCachedWallpaper();
        if (wallpaper != null) {
            root.setBackgroundImage(wallpaper, Theme.isWallpaperMotion());
            glassHost.setBackgroundView(root.backgroundView);
        }

        Log.d("GlassProbe", "perfClass=" + SharedConfig.getDevicePerformanceClass()
                + " chatBlurEnabled=" + SharedConfig.chatBlurEnabled()
                + " hasSource=" + glassHost.hasSource()
                + " liquidGlass=" + LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)
                + " dark=" + Theme.isCurrentThemeDark()
                + " wallpaper=" + (wallpaper == null ? "null" : wallpaper.getClass().getSimpleName()));
        root.postDelayed(() -> Log.d("GlassProbe", "after-load wallpaper="
                + (Theme.getCachedWallpaperNonBlocking() == null ? "null" : Theme.getCachedWallpaperNonBlocking().getClass().getSimpleName())
                + " source=" + (root.backgroundView == null ? "null" : "BackgroundView")), 4000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_NIGHT, false)) {
            // The emulator/owner state stays on the day theme the app had.
            getSharedPreferences("mainconfig", MODE_PRIVATE).edit().putString("theme", "Blue").apply();
        }
    }
}

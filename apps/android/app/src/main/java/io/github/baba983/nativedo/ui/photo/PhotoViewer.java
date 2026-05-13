// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/photo-viewer.md
// UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java; org/telegram/ui/ChatActivity.java photoViewerProvider/getPlaceForPhoto
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// UPSTREAM-SHAPED-WORKFLOW: provider source geometry, drag-down parent scale, grouped thumbnails,
//                           top-right action menu, close fallback, and back-before-host routing.
// EXACT-SUBSYSTEMS: gesture arbitration (GestureDetector2 + ACTION_MOVE moving discriminator
//                   + dp(12) cross threshold), horizontal page commit (width/3 distance OR
//                   dp(650) velocity), drag-down parent scale (ZOOM_SCALE=.1 against half
//                   container height), open/close source geometry (lerp provider viewRect
//                   <-> currentRect), windowView/containerView/FrameLayoutDrawer scaffold.
// HOST-WIRING:
//   - Uses Telegram-shaped windowView/containerView/FrameLayoutDrawer hierarchy; NativeDo's
//     PhotoViewer root is only the WindowManager host wrapper.
//   - Attaches to the Activity's WindowManager as a LAST_APPLICATION_WINDOW with the same
//     flags Telegram uses (FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR | FLAG_ALT_FOCUSABLE_IM
//     | FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, SOFT_INPUT_ADJUST_RESIZE, layoutInDisplayCutoutMode
//     SHORT_EDGES, SYSTEM_UI_FLAG_LAYOUT_STABLE | LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION),
//     mirroring PhotoViewer.java:5010-5023. MainActivity still checks activeViewer() before
//     ActionBarLayout for back-press routing, since NativeDo lacks Telegram's PhotoViewer
//     ApplicationLoader-level KeyEventReceiver.
//   - Uses NativeDo's existing Telegram-derived ActionBar/Menu slice for PhotoViewer chrome,
//     with code-drawn drawables replacing unavailable Telegram PNG resources.
// ADAPTED-BACKEND:
//   - URL-backed PhotoEntry objects plus NativeDo authenticated image OkHttp client and
//     memory/disk URL cache replace Telegram MessageObject/ImageLocation/FileLoader/ImageReceiver.
//   - SourceImageReceiver/imageDrawRect adapt Compose/View sources to Telegram's
//     ImageReceiver.setVisible(...) and getDrawRegion() close-animation call sites.
//   - MediaStore save and URL share replace Telegram MediaController/FileLoader save/share paths.
// ADAPTED-PRODUCT:
//   - Topic and browser media expose currently rendered cooked/instant-view images as URL entries.
// OMITTED:
//   - Telegram account storage, chat message model, video/document playback, spoilers, edit/crop,
//     send/select modes, PiP, BlurringShader thumbnail, exact ImageReceiver thumbnail loading,
//     full action-mode menu, exact ClippingImageView/ImageReceiver close transport, and full
//     ActionBar internals.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/PhotoViewer.java and org/telegram/ui/PinchToZoomHelper.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.photo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Window;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coil3.BitmapImage;
import coil3.Image;
import coil3.SingletonImageLoader;
import coil3.memory.MemoryCache;

import io.github.baba983.nativedo.NativeDoApp;
import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.core.ImageLoader;
import io.github.baba983.nativedo.core.ImageReceiver;
import io.github.baba983.nativedo.core.ui.RoundedImageView;
import io.github.baba983.nativedo.ui.actionbar.ActionBar;
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenu;
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenuItem;
import io.github.baba983.nativedo.ui.actionbar.ActionBarMenuSubItem;
import io.github.baba983.nativedo.ui.actionbar.BackDrawable;
import io.github.baba983.nativedo.ui.components.AnimatedTextDrawable;
import io.github.baba983.nativedo.ui.components.AnimationProperties;
import io.github.baba983.nativedo.ui.components.ClippingImageView;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.GestureDetector2;
import io.github.baba983.nativedo.ui.components.LayoutHelper;
import io.github.baba983.nativedo.ui.components.SizeNotifierFrameLayout;

public final class PhotoViewer extends FrameLayout implements GestureDetector2.OnGestureListener, GestureDetector2.OnDoubleTapListener {

    // PORT-MODE: Feature Workflow Port / ADAPTED-BACKEND (paged image transport)
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java leftImage/centerImage/rightImage + FileLoader
    // ADAPTED-BACKEND: Telegram-named ImageReceiver triple is retained; URL-backed threaded
    // loader populates the receivers instead of FileLoader/ImageLocation.
    // PORT-MODE: Exact Component Port (singleton API)
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:4022-4068 + 4608-4660
    // Telegram exposes PhotoViewer.getInstance() as a per-process singleton, with
    // setParentActivity(Activity) binding the lazily-created instance to the host
    // activity. We mirror those names. `activeViewer` is the same field as Instance,
    // kept under the legacy name for back-compat with MainActivity's predictive-back
    // hooks; new call sites should use getInstance() / setParentActivity().
    @Nullable private static volatile PhotoViewer Instance;
    @Nullable private static PhotoViewer activeViewer;
    @Nullable private Activity parentActivity;
    private static final ExecutorService LOAD_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 3.0f;
    private static final long OPEN_DURATION = 200L;
    private static final long CLOSE_DURATION = 200L;
    private static final long MOVE_DURATION = 250L;
    // Telegram: dp(15) gap between paged images and dp(650) velocity threshold for page commit.
    private static final int PAGE_SPACING_DP = 15;
    private static final int PAGE_COMMIT_VELOCITY_DP = 650;
    private static final float PARENT_ZOOM_SCALE = 0.1f;
    private static final int GALLERY_MENU_SAVE = 2;
    private static final int GALLERY_MENU_SHARE = 9;

    private final BackgroundDrawable backgroundDrawable = new BackgroundDrawable(Color.BLACK);
    private final RectF baseRect = new RectF();
    private final RectF currentRect = new RectF();
    private final RectF animationRect = new RectF();
    private final RectF sourceTransitionRect = new RectF();
    private final GestureDetector2 gestureDetector;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private final Scroller scroller;
    private final float touchSlop;
    public final FrameLayout windowView;
    private final FrameLayoutDrawer containerView;
    private final FrameLayout actionBarContainer;
    private final AnimatedCounterView titleView;
    private final TextView subtitleView;
    private final ActionBarMenuItem menuItem;
    // Field name kept identical to Telegram PhotoViewer.java:933 so cross-referencing the
    // upstream is mechanical.
    private final GroupedPhotosListView groupedPhotosListView;
    private final PhotoTouchView photoTouchView;
    private final Set<String> loadingThumbnails = new HashSet<>();
    private final Set<String> failedThumbnails = new HashSet<>();

    // Telegram-named center / left / right ImageReceiver slots. While scale==1 the user can
    // drag horizontally and the three slots are drawn together at translationX-W,
    // translationX, translationX+W. ADAPTED-BACKEND: each receiver is populated from URL
    // bitmaps rather than ImageLocation/FileLoader.
    private final ImageReceiver leftImage = new ImageReceiver();
    public final ImageReceiver centerImage = new ImageReceiver();
    private final ImageReceiver rightImage = new ImageReceiver();
    @Nullable private String pendingUrl;
    @Nullable private String leftPendingUrl;
    @Nullable private String rightPendingUrl;
    @Nullable private Runnable onDismiss;
    @Nullable private PhotoViewerProvider placeProvider;
    @Nullable private PlaceProviderObject placeProviderObject;
    @Nullable private PhotoEntry currentEntry;
    private List<PhotoEntry> entries = Collections.emptyList();
    private int currentIndex = -1;

    private float openProgress = 1f;
    private float actionBarAlpha = 1f;
    // 0 = no pending switch, 1 = goToNext on animation end, 2 = goToPrev on animation end.
    private int switchImageAfterAnimation;
    private boolean dismissing;
    private boolean actionBarVisible = true;
    private boolean photosListVisible;
    private boolean actionBarTextInitialized;
    private int lastActionBarTextIndex = -1;

    private float scale = 1f;
    private float translationX;
    private float translationY;
    private float animateToScale = 1f;
    private float animateToX;
    private float animateToY;
    private float animationValue;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;

    private float moveStartX;
    private float moveStartY;
    private float dragY;
    private float longPressX;
    private final Runnable longPressRunnable = this::onLongPress;
    private boolean moving;
    private boolean draggingDown;
    private boolean zooming;
    private boolean canDragDown;
    private boolean doubleTap;
    private boolean discardTap;

    private float pinchStartDistance;
    private float pinchStartScale = 1f;
    private float pinchCenterX;
    private float pinchCenterY;
    private float pinchStartX;
    private float pinchStartY;

    @Nullable private VelocityTracker velocityTracker;
    @Nullable private ValueAnimator openAnimator;
    @Nullable private ValueAnimator moveAnimator;
    @Nullable private View parentScaleView;
    private float parentScaleStartX = 1f;
    private float parentScaleStartY = 1f;
    private boolean parentScaleCaptured;
    @Nullable private PlaceProviderObject sourceHiddenObject;
    private boolean closeToSource;

    // PORT-MODE: Exact Component Port (animation transport)
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:17630-17900 (open) and 18250-18596 (close)
    // ClippingImageView + AnimatorSet morph is wired and active for the happy path
    // (bitmap != null && placeProviderObject.viewRect != null). See setupAnimatingImageView,
    // startOpenAnimation, and closePhoto for the upstream-shaped AnimatorSet construction.
    // Fallback fade animation is used only when no source rect / no bitmap is available.
    @Nullable private ClippingImageView animatingImageView;
    @Nullable private AnimatorSet imageMoveAnimation;
    private final float[][] animationValues = new float[2][14];
    private boolean imageMoveAnimating;

    // Telegram hides system bars while PhotoViewer is on screen so the black overlay covers the
    // entire display. The host window reference is kept to restore them on close.
    private boolean systemBarsHidden;
    @Nullable private Window hostWindow;
    // PORT-MODE: Feature Workflow Port / HOST-WIRING
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:4663-5023 windowView/containerView setup
    // + windowLayoutParams + wm.addView. windowView is the WindowManager root, matching Telegram;
    // this PhotoViewer instance is the owner/controller only.
    @Nullable private WindowManager hostWindowManager;
    @Nullable private WindowManager.LayoutParams windowLayoutParams;

    public PhotoViewer(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        scroller = new Scroller(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetector2(context, this);
        gestureDetector.setIsLongpressEnabled(false);
        windowView = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return !dismissing && super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return !dismissing && PhotoViewer.this.onTouchEvent(event);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                try {
                    return super.drawChild(canvas, child, drawingTime);
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                centerImage.onAttachedToWindow();
                leftImage.onAttachedToWindow();
                rightImage.onAttachedToWindow();
            }

            @Override
            protected void onDetachedFromWindow() {
                centerImage.onDetachedFromWindow();
                leftImage.onDetachedFromWindow();
                rightImage.onDetachedFromWindow();
                super.onDetachedFromWindow();
            }

            @Override
            public boolean dispatchKeyEventPreIme(android.view.KeyEvent event) {
                if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    closePhoto(true);
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }
        };
        windowView.setWillNotDraw(false);
        windowView.setFocusable(false);
        windowView.setClipChildren(false);
        windowView.setClipToPadding(false);
        windowView.setBackground(backgroundDrawable);

        animatingImageView = new ClippingImageView(context);
        animatingImageView.setVisibility(GONE);
        animatingImageView.setPivotX(0f);
        animatingImageView.setPivotY(0f);
        windowView.addView(animatingImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        containerView = new FrameLayoutDrawer(context);
        containerView.setFocusable(false);
        containerView.setClipChildren(true);
        containerView.setClipToPadding(true);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        actionBarContainer = new FrameLayout(context);
        actionBarContainer.setWillNotDraw(false);
        actionBarContainer.setClickable(true);
        actionBarContainer.setBackgroundColor(Color.TRANSPARENT);

        photoTouchView = new PhotoTouchView(context);
        containerView.addView(photoTouchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        containerView.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56 + AndroidUtilities.statusBarHeight, Gravity.TOP));

        ActionBar actionBar = new ActionBar(context);
        actionBar.setCastShadows(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(0x7f000000);
        actionBar.setItemsBackgroundColor(0x22ffffff, false);
        actionBar.setItemsColor(Color.WHITE, false);
        actionBar.setTitleColor(Color.WHITE);
        actionBar.setTitle("");
        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setColor(Color.WHITE);
        backDrawable.setRotatedColor(Color.WHITE);
        actionBar.setBackButtonDrawable(backDrawable);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto(true);
                } else if (id == GALLERY_MENU_SAVE) {
                    saveCurrentImage();
                } else if (id == GALLERY_MENU_SHARE) {
                    shareCurrentImage();
                }
            }
        });
        actionBarContainer.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        titleView = new AnimatedCounterView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18);
        actionBarContainer.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.LEFT | Gravity.TOP));
        FrameLayout.LayoutParams titleParams = (FrameLayout.LayoutParams) titleView.getLayoutParams();
        titleParams.leftMargin = AndroidUtilities.dp(72);
        titleParams.rightMargin = AndroidUtilities.dp(16);
        titleParams.topMargin = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(8);

        subtitleView = new TextView(context);
        subtitleView.setTextColor(0xccffffff);
        subtitleView.setTextSize(13);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        actionBarContainer.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT | Gravity.TOP));
        FrameLayout.LayoutParams subtitleParams = (FrameLayout.LayoutParams) subtitleView.getLayoutParams();
        subtitleParams.leftMargin = AndroidUtilities.dp(72);
        subtitleParams.rightMargin = AndroidUtilities.dp(72);
        subtitleParams.topMargin = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(32);

        ActionBarMenu menu = actionBar.createMenu();
        menuItem = menu.addItem(0, new MoreDrawable());
        menuItem.setContentDescription(context.getString(R.string.post_image_viewer_more_actions));
        ActionBarMenuSubItem saveItem = menuItem.addSubItem(
                GALLERY_MENU_SAVE,
                new SaveDrawable(),
                context.getString(R.string.post_image_viewer_action_save_image)
        );
        saveItem.setColors(0xfffafafa, 0xfffafafa);
        ActionBarMenuSubItem shareItem = menuItem.addSubItem(
                GALLERY_MENU_SHARE,
                new ShareDrawable(),
                context.getString(R.string.post_image_viewer_action_share)
        );
        shareItem.setColors(0xfffafafa, 0xfffafafa);
        menuItem.redrawPopup(0xf9222222);
        menuItem.setPopupItemsSelectorColor(0x0fffffff);

        // PORT-MODE: Exact Component Port (visibility / alpha / layout shape)
        // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:6047-6048
        // Telegram NEVER calls setVisibility(GONE)/setAlpha(0f) on the strip at construction —
        // visibility is driven by GroupedPhotosListView.fillList()'s internal show/hideAnimator
        // that animates `drawAlpha`, and is gated externally only by hasPhotos() and the
        // FrameLayoutDrawer.onMeasure() portrait check. Pre-hiding here is what was causing
        // the bottom thumbnail strip to never appear.
        // ADAPTED-PRODUCT: bottomMargin = navigationBarHeight so the strip floats above the
        // gesture nav bar. Telegram's height stays dp(68) and bottom-inset is handled by
        // fitsSystemWindows on activity decor.
        groupedPhotosListView = new GroupedPhotosListView(context, AndroidUtilities.dp(10));
        groupedPhotosListView.setClickable(true);
        groupedPhotosListView.setDelegate(new GroupedPhotosListView.GroupedPhotosListViewDelegate() {
            public int getCurrentIndex() { return currentIndex; }
            public void setCurrentIndex(int index) { switchToIndexFromStrip(index); }
            public ArrayList<PhotoEntry> getPhotos() { return new ArrayList<>(entries); }
        });
        FrameLayout.LayoutParams stripLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.LEFT | Gravity.BOTTOM);
        stripLp.bottomMargin = AndroidUtilities.navigationBarHeight;
        containerView.addView(groupedPhotosListView, stripLp);
    }

    public static boolean hasActiveViewer() {
        return activeViewer != null && !activeViewer.dismissing;
    }

    public static boolean handleActiveBack() {
        PhotoViewer viewer = activeViewer;
        return viewer != null && viewer.handleBackPressed();
    }

    // PORT-MODE: Exact Component Port (singleton API)
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:4030-4062
    // Telegram-shaped getInstance(): callers must invoke setParentActivity(Activity)
    // first so the lazily-created instance is bound to a real host with a Context.
    public static PhotoViewer getInstance() {
        PhotoViewer localInstance = Instance;
        if (localInstance == null) {
            throw new IllegalStateException(
                    "PhotoViewer.setParentActivity(activity) must be called before getInstance()");
        }
        return localInstance;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    // PORT-MODE: Exact Component Port (singleton API)
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:4608-4660
    // Telegram's setParentActivity is an instance method on a constructor-less PhotoViewer;
    // ours stays a static factory because our constructor needs a Context up-front
    // (PhotoViewer extends FrameLayout for legacy reasons). Behavior matches: same activity
    // → reuse instance; different activity → tear the old one down and rebuild.
    public static PhotoViewer setParentActivity(@NonNull Activity activity) {
        PhotoViewer localInstance = Instance;
        if (localInstance != null && localInstance.parentActivity == activity) {
            return localInstance;
        }
        if (localInstance != null) {
            localInstance.destroyInstance();
        }
        synchronized (PhotoViewer.class) {
            localInstance = Instance;
            if (localInstance == null || localInstance.parentActivity != activity) {
                localInstance = new PhotoViewer(activity);
                localInstance.parentActivity = activity;
                Instance = localInstance;
            }
        }
        return localInstance;
    }

    public static void destroyInstanceForActivity(@NonNull Activity activity) {
        PhotoViewer localInstance = Instance;
        if (localInstance != null && localInstance.parentActivity == activity) {
            localInstance.destroyInstance();
        }
    }

    private void destroyInstance() {
        if (windowView.getParent() != null && hostWindowManager != null) {
            try { hostWindowManager.removeView(windowView); } catch (Throwable ignored) {}
        }
        hostWindowManager = null;
        windowLayoutParams = null;
        hostWindow = null;
        if (activeViewer == this) {
            activeViewer = null;
        }
        synchronized (PhotoViewer.class) {
            if (Instance == this) {
                Instance = null;
            }
        }
    }

    @Nullable
    public Activity getParentActivity() {
        return parentActivity;
    }

    public static class PhotoEntry {
        public final String url;
        @Nullable public final String thumbUrl;
        @Nullable public final CharSequence title;
        @Nullable public final CharSequence subtitle;
        // Aspect ratio from Discourse's width/height attributes, used by
        // GroupedPhotosListView.onDraw to compute per-cell width like
        // Telegram's photoSize.w/h does in upstream.
        public final int widthPx;
        public final int heightPx;

        public PhotoEntry(@NonNull String url) {
            this(url, null, 0, 0, null, null);
        }

        public PhotoEntry(@NonNull String url, @Nullable String thumbUrl) {
            this(url, thumbUrl, 0, 0, null, null);
        }

        public PhotoEntry(@NonNull String url, @Nullable String thumbUrl, int widthPx, int heightPx) {
            this(url, thumbUrl, widthPx, heightPx, null, null);
        }

        public PhotoEntry(@NonNull String url, @Nullable String thumbUrl, int widthPx, int heightPx, @Nullable CharSequence title, @Nullable CharSequence subtitle) {
            this.url = url;
            this.thumbUrl = thumbUrl;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public static class PlaceProviderObject {
        // Used purely for clipping: setupAnimatingImageView() reads
        // parentView.getLocationInWindow() + getHeight() to compute clipTop /
        // clipBottom so the closing morph view does not paint above the source's
        // host viewport (e.g. under the host ActionBar). In Telegram this is the
        // chatListView. If the host's natural parentView does not align with the
        // visible content edge (e.g. content is drawn edge-to-edge with an
        // overlay ActionBar), supply parentClipRect instead.
        @Nullable public View parentView;
        // Optional override of parentView-based clip math, in window coords.
        // When non-null, setupAnimatingImageView uses this rect as the parent's
        // visible area instead of parentView.getLocationInWindow + getHeight.
        // Use this when the host's content extends behind a translucent / overlay
        // ActionBar and the natural parentView's bounds DO include the actionbar
        // strip — supply a rect that begins at actionbar bottom in window coords.
        @Nullable public RectF parentClipRect;
        // Optional Telegram-style host zoom target. Mirrors the upstream behavior at
        // PhotoViewer.java:19533-19560 where parentFragment.getFragmentView() is
        // scaled 1.0 → 1.1 during open / 1.1 → 1.0 during close. The host should
        // pass the view that contains BOTH the host ActionBar and the content
        // (e.g. the ActionBarLayout LayoutContainer or the fragment root) so the
        // ActionBar follows the zoom along with the content (user complaint #3).
        // When null, parentView is used as the zoom target — but parentView is
        // typically the clip-only view (without the ActionBar), so leaving this
        // null gives a "content scales, ActionBar stays" disjoint feel.
        @Nullable public View parentScaleView;
        @Nullable public View sourceView;
        @Nullable public ImageReceiver imageReceiver;
        @Nullable public SourceImageReceiver sourceImageReceiver;
        @Nullable public Bitmap thumb;
        @Nullable public RectF viewRect;
        @Nullable public RectF imageDrawRect;
        @Nullable public RectF visibleRect;
        public float viewX;
        public float viewY;
        public float scale = 1f;
        public float radius;
        public int index;
        public boolean allowTakeAnimation = true;

        public void setVisible(boolean visible, boolean animated) {
            if (sourceView != null) {
                sourceView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            } else if (imageReceiver != null) {
                imageReceiver.setVisible(visible, true);
            }
            if (sourceImageReceiver != null) {
                sourceImageReceiver.setVisible(visible, animated);
            }
        }

        private boolean isSameSource(@Nullable PlaceProviderObject other) {
            if (other == null) {
                return false;
            }
            if (sourceView != null && sourceView == other.sourceView) {
                return true;
            }
            if (imageReceiver != null && imageReceiver == other.imageReceiver) {
                return true;
            }
            if (sourceImageReceiver != null && sourceImageReceiver == other.sourceImageReceiver) {
                return true;
            }
            return index >= 0 && index == other.index;
        }
    }

    public interface SourceImageReceiver {
        void setVisible(boolean visible, boolean animated);
    }

    public interface PhotoViewerProvider {
        @Nullable
        PlaceProviderObject getPlaceForPhoto(int index, @NonNull PhotoEntry entry, boolean needPreview, boolean closing);

        default void willSwitchFromPhoto(int index, @NonNull PhotoEntry entry) {
        }

        default void willHidePhotoViewer() {
        }

        default CharSequence getTitleFor(int index, @NonNull PhotoEntry entry) {
            return entry.title;
        }

        default CharSequence getSubtitleFor(int index, @NonNull PhotoEntry entry) {
            return entry.subtitle;
        }

        default void onOpen() {
        }

        default void onClose() {
        }
    }

    public static class EmptyPhotoViewerProvider implements PhotoViewerProvider {
        @Nullable
        @Override
        public PlaceProviderObject getPlaceForPhoto(int index, @NonNull PhotoEntry entry, boolean needPreview, boolean closing) {
            return null;
        }
    }

    public void setOnDismissListener(@Nullable Runnable listener) {
        onDismiss = listener;
    }

    public void show(@Nullable Bitmap initial, @Nullable String url, @Nullable RectF sourceRect) {
        show(initial, url, Collections.emptyList(), sourceRect);
    }

    public void show(@Nullable Bitmap initial, @Nullable String url, @Nullable List<String> galleryUrls, @Nullable RectF sourceRect) {
        openPhoto(initial, url, galleryUrls, sourceRect);
    }

    public boolean openPhoto(@Nullable Bitmap initial, @Nullable String url, @Nullable List<String> galleryUrls, @Nullable RectF sourceRect) {
        ArrayList<PhotoEntry> photoEntries = new ArrayList<>();
        if (galleryUrls != null) {
            for (String value : galleryUrls) {
                if (value != null && value.length() > 0 && findEntry(photoEntries, value) < 0) {
                    photoEntries.add(new PhotoEntry(value));
                }
            }
        }
        if (url != null && url.length() > 0 && findEntry(photoEntries, url) < 0) {
            photoEntries.add(new PhotoEntry(url));
        }
        int index = url == null ? 0 : findEntry(photoEntries, url);
        PlaceProviderObject object = null;
        if (sourceRect != null) {
            object = new PlaceProviderObject();
            object.viewRect = new RectF(sourceRect);
            object.thumb = initial;
        }
        return openPhoto(photoEntries, Math.max(0, index), object == null ? new EmptyPhotoViewerProvider() : new StaticPlaceProvider(object));
    }

    public boolean openPhoto(@NonNull List<PhotoEntry> photoEntries, int index, @Nullable PhotoViewerProvider provider) {
        if (photoEntries.isEmpty() || index < 0 || index >= photoEntries.size()) {
            return false;
        }
        entries = new ArrayList<>(photoEntries);
        currentIndex = index;
        actionBarTextInitialized = false;
        lastActionBarTextIndex = -1;
        // PORT-MODE: ADAPTED-BACKEND (singleton state reset)
        // The Telegram singleton resets transient state at the top of onPhotoShow(...).
        // Our singleton MUST do the same because the previous close left state behind:
        //   - dismissing=true blocks all touch input AND makes closePhoto a no-op,
        //     producing the "second open is frozen" symptom.
        //   - GroupedPhotosListView keeps unusedReceivers from the last session whose
        //     bitmaps may have been GC'd; clear() drops them and forces a fresh
        //     fillList → fillImages → setImage cycle so thumbnails actually draw.
        //   - Chrome alpha/translation must be reset so togglePhotosListView's animated
        //     transition kicks in instead of early-returning on stale photosListVisible.
        dismissing = false;
        scale = 1f;
        translationX = 0f;
        translationY = 0f;
        animateToScale = 1f;
        animateToX = 0f;
        animateToY = 0f;
        moving = false;
        zooming = false;
        draggingDown = false;
        canDragDown = false;
        switchImageAfterAnimation = 0;
        actionBarVisible = true;
        photosListVisible = false;
        actionBarContainer.animate().cancel();
        groupedPhotosListView.animate().cancel();
        groupedPhotosListView.setAlpha(0f);
        groupedPhotosListView.setTranslationY(AndroidUtilities.dp(20));
        groupedPhotosListView.clear();
        actionBarContainer.setAlpha(1f);
        actionBarContainer.setTranslationY(0f);
        placeProvider = provider == null ? new EmptyPhotoViewerProvider() : provider;
        currentEntry = entries.get(currentIndex);
        placeProviderObject = placeProvider.getPlaceForPhoto(currentIndex, currentEntry, true, false);
        // Telegram PhotoViewer.java:19533-19560 scales parentFragment.getFragmentView()
        // (the host fragment root, containing both the host ActionBar and the
        // content) — NOT PhotoViewer's own containerView. The two
        // containerView.setScale(1.0f) calls at 18348/18584 are RESET ops, not the
        // scaling itself.
        // Prefer the host's explicit parentScaleView (= the view containing both
        // ActionBar and content) so the ActionBar follows the zoom; fall back to
        // parentView when the host did not supply a zoom target.
        setParentScaleView(parentScaleViewFor(placeProviderObject));
        showCurrent(placeProviderObject == null ? null : placeProviderObject.thumb, placeProviderObject);
        updatePhotosList();
        togglePhotosListView(entries.size() > 1, false);
        placeProvider.onOpen();
        // Telegram folds the WindowManager.addView call into onPhotoShow which is invoked
        // synchronously from openPhoto(...). We do the equivalent here so callers no longer
        // need a separate attachToDecor() step (and matches the upstream API surface).
        attachToDecor();
        // Bug D fix (revised): hide the source view AFTER animatingImageView paints
        // its first frame, not before. The hand-off is wired through
        // backgroundDrawable.drawRunnable inside startOpenAnimation() — see
        // Telegram PhotoViewer.java:17915. Pre-hiding here would race with
        // Compose's async recomposition (the host post-body Image fades to
        // alpha 0 only on the next snapshot apply), causing a one-frame gap
        // between source disappearance and morph appearance. Calling
        // startOpenAnimation directly (without postOnAnimation) is now safe
        // because the morph view is set up before any paint can occur.
        startOpenAnimation();
        return true;
    }

    /**
     * Attach the viewer to the activity as its own {@link WindowManager} window.
     *
     * <p>Mirrors Telegram PhotoViewer.java:5010-5023 + the wm.addView call site. Using a real
     * window (instead of a child of the activity decor view) is what gives Telegram's
     * PhotoViewer its isolated touch dispatch, immediate system-bar transitions, and correct
     * z-order over ActionBarLayout / sheets — which are the primary "feel" gaps reported when
     * the viewer was hosted inside the decor view.
     */
    public void attachToDecor() {
        Activity activity = findActivity(getContext());
        if (activity == null) {
            return;
        }
        if (windowView.getParent() != null) {
            ((ViewGroup) windowView.getParent()).removeView(windowView);
        }
        hostWindow = activity.getWindow();
        // SYSTEM_UI_FLAG_LAYOUT_STABLE | LAYOUT_FULLSCREEN | LAYOUT_HIDE_NAVIGATION mirrors
        // Telegram containerView.setSystemUiVisibility(...) at PhotoViewer.java:5008. With these
        // flags the window LAYS OUT under the system bars; immersive hide() then removes the
        // bars themselves so the black overlay covers the full display without any padding step.
        containerView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            // Defensive: WindowManager service should always exist on Activity contexts. If it
            // ever doesn't (e.g. wrapped contexts in tests), fall back to the previous decor-view
            // attach so the viewer at least opens — touch arbitration will be degraded but the
            // user is not stranded.
            ViewGroup decor = (ViewGroup) hostWindow.getDecorView();
            decor.addView(windowView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            try {
                wm.addView(windowView, params);
                hostWindowManager = wm;
                windowLayoutParams = params;
            } catch (WindowManager.BadTokenException | IllegalStateException e) {
                ViewGroup decor = (ViewGroup) hostWindow.getDecorView();
                decor.addView(windowView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
        activeViewer = this;
        windowView.bringToFront();
        windowView.requestFocus();
        enterImmersive();
    }

    /**
     * Telegram PhotoViewer hides the status and navigation bars so its black overlay can cover
     * the entire display. We use {@link WindowInsetsControllerCompat} so the user can still
     * swipe from any edge to transiently reveal the bars.
     */
    private void enterImmersive() {
        if (systemBarsHidden || hostWindow == null) {
            return;
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(hostWindow, hostWindow.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        systemBarsHidden = true;
    }

    private void exitImmersive() {
        if (!systemBarsHidden || hostWindow == null) {
            return;
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(hostWindow, hostWindow.getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
        systemBarsHidden = false;
    }

    private static int findEntry(@NonNull List<PhotoEntry> photoEntries, @NonNull String url) {
        for (int i = 0; i < photoEntries.size(); i++) {
            if (url.equals(photoEntries.get(i).url)) {
                return i;
            }
        }
        return -1;
    }

    private void showCurrent(@Nullable Bitmap initial, @Nullable PlaceProviderObject object) {
        currentEntry = currentEntry();
        pendingUrl = currentEntry == null ? null : currentEntry.url;
        Bitmap fromSource = softwareBitmap(bitmapFromSourceView(object));
        if (fromSource == null && pendingUrl != null) {
            fromSource = softwareBitmap(bitmapFromCoilMemoryCache(pendingUrl));
        }
        Bitmap cached = pendingUrl == null ? null : ImageLoader.get(pendingUrl);
        if (fromSource != null && pendingUrl != null && cached == null) {
            // Seed MEMORY_CACHE so loadIntoSlot below short-circuits and any
            // paged neighbour landing on the same URL also skips redownload.
            ImageLoader.put(pendingUrl, fromSource);
            cached = fromSource;
        }
        Bitmap fallback = fromSource != null ? fromSource : initial;
        setSlotBitmap(centerImage, currentEntry == null ? null : bestBitmapFor(currentIndex, currentEntry, fallback));
        if (cached != null && centerBitmap() != null && pendingUrl != null) {
            Bitmap thumbnail = createThumbnail(centerBitmap());
            if (thumbnail != null) {
                updateThumbnailForUrl(pendingUrl, thumbnail);
            }
        } else if (initial != null && pendingUrl != null) {
            Bitmap thumbnail = createThumbnail(initial);
            if (thumbnail != null) {
                updateThumbnailForUrl(pendingUrl, thumbnail);
            }
        }
        placeProviderObject = object;
        resetTransform();
        if (cached == null && pendingUrl != null) {
            loadIntoSlot(0, pendingUrl);
        }
        prefetchNeighbors();
        updateActionBarText();
        invalidateViewer();
    }

    @Nullable
    private static Bitmap bitmapFromSourceView(@Nullable PlaceProviderObject object) {
        if (object == null) {
            return null;
        }
        if (object.sourceView instanceof RoundedImageView) {
            Bitmap b = ((RoundedImageView) object.sourceView).getBitmap();
            if (b != null && !b.isRecycled() && b.getWidth() > 0 && b.getHeight() > 0) {
                return b;
            }
        }
        if (object.sourceView instanceof ImageView) {
            Drawable d = ((ImageView) object.sourceView).getDrawable();
            if (d instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                if (b != null && !b.isRecycled() && b.getWidth() > 0 && b.getHeight() > 0) {
                    return b;
                }
            }
        }
        return null;
    }

    // R3 cache unification: when the source View didn't hand us a bitmap (e.g. the
    // user tapped while Coil's async crossfade hadn't delivered setImageDrawable
    // yet, or the tap came from a Compose-rendered image without a backing View),
    // borrow the decoded Bitmap directly from Coil's SingletonImageLoader memory
    // cache. This is the same cache the post body RoundedImageView populated, so
    // first-tap morph animations and the GroupedPhotosListView strip now share
    // pixels with the inline cell.
    @Nullable
    private Bitmap bitmapFromCoilMemoryCache(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            coil3.ImageLoader loader = SingletonImageLoader.get(getContext());
            MemoryCache memoryCache = loader.getMemoryCache();
            if (memoryCache == null) {
                return null;
            }
            MemoryCache.Value value = memoryCache.get(new MemoryCache.Key(url, java.util.Collections.emptyMap()));
            if (value == null) {
                return null;
            }
            Image image = value.getImage();
            if (image instanceof BitmapImage) {
                Bitmap b = ((BitmapImage) image).getBitmap();
                if (b != null && !b.isRecycled() && b.getWidth() > 0 && b.getHeight() > 0) {
                    return b;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    private static Bitmap softwareBitmap(@Nullable Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        if (Build.VERSION.SDK_INT < 26 || source.getConfig() != Bitmap.Config.HARDWARE) {
            return source;
        }
        try {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Mirrors Telegram setImages(): keep left/center/right ImageReceivers populated for
     * currentIndex-1, currentIndex, currentIndex+1 so a horizontal swipe never reveals a black gap.
     */
    private void prefetchNeighbors() {
        // Left slot.
        String wantLeft = currentIndex > 0 ? entries.get(currentIndex - 1).url : null;
        if (wantLeft == null) {
            setSlotBitmap(leftImage, null);
            leftPendingUrl = null;
        } else if (!wantLeft.equals(leftPendingUrl) || !leftImage.hasImageSet()) {
            leftPendingUrl = wantLeft;
            PhotoEntry entry = entries.get(currentIndex - 1);
            setSlotBitmap(leftImage, bestBitmapFor(currentIndex - 1, entry, null));
            if (ImageLoader.get(wantLeft) == null) {
                loadIntoSlot(-1, wantLeft);
            }
        }
        // Right slot.
        String wantRight = currentIndex >= 0 && currentIndex + 1 < entries.size() ? entries.get(currentIndex + 1).url : null;
        if (wantRight == null) {
            setSlotBitmap(rightImage, null);
            rightPendingUrl = null;
        } else if (!wantRight.equals(rightPendingUrl) || !rightImage.hasImageSet()) {
            rightPendingUrl = wantRight;
            PhotoEntry entry = entries.get(currentIndex + 1);
            setSlotBitmap(rightImage, bestBitmapFor(currentIndex + 1, entry, null));
            if (ImageLoader.get(wantRight) == null) {
                loadIntoSlot(1, wantRight);
            }
        }
    }

    @Nullable
    private Bitmap bestBitmapFor(int index, @NonNull PhotoEntry entry, @Nullable Bitmap fallback) {
        Bitmap cached = ImageLoader.get(entry.url);
        if (cached != null) {
            return cached;
        }
        if (fallback != null) {
            return fallback;
        }
        Bitmap coilCached = softwareBitmap(bitmapFromCoilMemoryCache(entry.url));
        if (coilCached != null) {
            ImageLoader.put(entry.url, coilCached);
            return coilCached;
        }
        Bitmap thumbnail = ImageLoader.getThumbnail(entry.url);
        if (thumbnail != null) {
            return thumbnail;
        }
        return getProviderThumbnail(index, entry);
    }

    @Nullable
    private PhotoEntry currentEntry() {
        if (currentIndex < 0 || currentIndex >= entries.size()) {
            return null;
        }
        return entries.get(currentIndex);
    }

    @Nullable
    private Bitmap centerBitmap() {
        return centerImage.getBitmap();
    }

    @Nullable
    private Bitmap leftBitmap() {
        return leftImage.getBitmap();
    }

    @Nullable
    private Bitmap rightBitmap() {
        return rightImage.getBitmap();
    }

    private void setSlotBitmap(@NonNull ImageReceiver receiver, @Nullable Bitmap value) {
        receiver.setImageBitmap(value);
        receiver.setImageCoords(0, 0, getContainerViewWidth(), getContainerViewHeight());
        receiver.setVisible(true, true);
    }

    private void copySlot(@NonNull ImageReceiver from, @NonNull ImageReceiver to) {
        to.setImageBitmap(from.getBitmap());
        to.setThumbBitmap(from.getThumbBitmap());
        to.setImageCoords(0, 0, getContainerViewWidth(), getContainerViewHeight());
        to.setVisible(true, true);
    }

    private int getContainerViewWidth() {
        return containerView == null || containerView.getWidth() == 0 ? windowView.getWidth() : containerView.getWidth();
    }

    private int getContainerViewHeight() {
        return containerView == null || containerView.getHeight() == 0 ? windowView.getHeight() : containerView.getHeight();
    }

    private void invalidateViewer() {
        windowView.invalidate();
        containerView.invalidate();
    }

    /**
     * Scaffolding stub. Body must be filled by porting the source/target pose
     * setup from upstream {@code PhotoViewer.onPhotoShow} (the {@code if (object != null)}
     * branch that computes drawRegion, layoutParams, and writes
     * {@code animationValues[0]} / {@code animationValues[1]}). Column meanings
     * are dictated by {@link ClippingImageView#setAnimationProgress}:
     * <ul>
     *   <li>[0] scaleX, [1] scaleY</li>
     *   <li>[2] translationX, [3] translationY</li>
     *   <li>[4] clipHorizontal, [5] clipTop, [6] clipBottom</li>
     *   <li>[7..10] radius[0..3] (per-corner)</li>
     *   <li>[11] imageY, [12] imageX (only consulted when column count &gt; 11/12)</li>
     * </ul>
     * After filling values, the body is expected to:
     * <ol>
     *   <li>Resize {@code animatingImageView} layoutParams to the bitmap dimensions.</li>
     *   <li>Call {@code animatingImageView.setImageBitmap(new ImageReceiver.BitmapHolder(targetBitmap))}.</li>
     *   <li>Call {@code animatingImageView.setRadius(int[])} so {@code needRadius} flips on.</li>
     *   <li>Call {@code animatingImageView.setAnimationValues(animationValues, openDirection, false)}.</li>
     *   <li>Set the view to {@code VISIBLE}.</li>
     * </ol>
     * Until the body is filled, {@link #imageMoveAnimating} stays {@code false}
     * and the existing single-progress open/close animation continues to drive
     * rendering through {@link #onDraw}.
     */
    private void setupAnimatingImageView(@NonNull PlaceProviderObject sourceObject,
                                          @NonNull RectF targetRect,
                                          @NonNull Bitmap targetBitmap,
                                          boolean openDirection) {
        if (animatingImageView == null || sourceObject.viewRect == null) {
            return;
        }

        // ADAPTED-BACKEND: NativeDo does not have Telegram's ImageReceiver drawRegion / viewX / viewY
        // transport here, so we use provider-supplied absolute source geometry instead.
        final RectF drawRegion = new RectF();
        getSourceTransitionRect(targetBitmap, sourceObject, drawRegion);

        final ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
        layoutParams.width = (int) drawRegion.width();
        layoutParams.height = (int) drawRegion.height();
        if (layoutParams.width <= 0) {
            layoutParams.width = 100;
        }
        if (layoutParams.height <= 0) {
            layoutParams.height = 100;
        }

        final int sourceRadius = Math.max(0, Math.round(sourceObject.radius));
        final int[] sourceRadii = new int[] {sourceRadius, sourceRadius, sourceRadius, sourceRadius};

        animatingImageView.setAlpha(1.0f);
        animatingImageView.setPivotX(0.0f);
        animatingImageView.setPivotY(0.0f);
        animatingImageView.setAdditionalTranslationX(0f);
        animatingImageView.setAdditionalTranslationY(0f);
        animatingImageView.setLayoutParams(layoutParams);
        // Force immediate layout so getWidth()/getHeight() return the correct
        // values in the first animation frame. Without this, the first frame
        // draws with the PREVIOUS layoutParams dimensions (e.g. 1082×545 from
        // a prior image) while the animation values are computed for the NEW
        // dimensions (e.g. 646×328), causing a visible size pop.
        final int vw = layoutParams.width;
        final int vh = layoutParams.height;
        animatingImageView.measure(
                View.MeasureSpec.makeMeasureSpec(vw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(vh, View.MeasureSpec.EXACTLY));
        animatingImageView.layout(0, 0, vw, vh);
        animatingImageView.setOrientation(0, 0);
        animatingImageView.setImageBitmap(new ImageReceiver.BitmapHolder(targetBitmap));
        // Scale, translation, and radius are set per-direction below (open: source
        // position; close: fullscreen position) matching Telegram's approach.

        float scaleX = targetRect.width() / layoutParams.width;
        float scaleY = targetRect.height() / layoutParams.height;
        float scale = Math.min(scaleX, scaleY);
        // Bug C fix (revised): targetRect IS currentRect, and updateCurrentRect()
        // already bakes in (translationX, translationY) — see updateCurrentRect's
        // centerX/centerY calculation. Adding translationX/Y as an extra offset
        // here would double-count it, putting the morph view's first frame at
        // 2 * drag distance instead of matching the bitmap's actual visible
        // position. The original Bug C fix predates updateCurrentRect picking up
        // translation, but we never removed the offset when updateCurrentRect was
        // updated. Drop it: xPos/yPos already match currentRect (= bitmap's
        // current visible position) for both open (translation=0) and close
        // (close-from-drag with non-zero translation).
        float yPos = targetRect.top + (targetRect.height() - layoutParams.height * scale) / 2.0f;
        float xPos = targetRect.left + (targetRect.width() - layoutParams.width * scale) / 2.0f;

        int clipHorizontal = (int) Math.max(
                Math.abs(drawRegion.left - sourceObject.viewRect.left),
                Math.abs(sourceObject.viewRect.right - drawRegion.right)
        );
        int clipVertical = (int) Math.max(
                Math.abs(drawRegion.top - sourceObject.viewRect.top),
                Math.abs(sourceObject.viewRect.bottom - drawRegion.bottom)
        );
        // When drawRegion comes from imageDrawRect (the exact letterbox-aware bitmap
        // area), the morph view is already sized to the visible bitmap region — no
        // additional clip is needed. Telegram's clipVertical is computed from
        // ImageReceiver drawRegion vs getImageX/Y (both in view-local coords),
        // which represents letterbox/padding. In our adapter, drawRegion IS the
        // visible area and viewRect is the view bounds; the difference is the
        // letterbox margin, which would over-clip the morph view (causing
        // "shrinks past source then bounces back" — user complaint #2).
        if (sourceObject.imageDrawRect != null
                && sourceObject.imageDrawRect.width() > 0f
                && sourceObject.imageDrawRect.height() > 0f) {
            clipHorizontal = 0;
            clipVertical = 0;
        }

        int clipTop = clipVertical;
        int clipBottom = clipVertical;
        // Telegram PhotoViewer.java:17763 / 18437 uses object.parentView.getLocationInWindow
        // + getHeight() to compute the clip strip that hides the morph view's overflow
        // above / below the host's content viewport. parentClipRect is our additional
        // adapter for hosts whose natural parentView's bounds DO include the actionbar
        // strip (edge-to-edge content with overlay actionbar) — the host then supplies
        // a rect that begins at actionbar bottom in window coords so clipTop pushes the
        // morph view's top edge below the actionbar throughout the close animation.
        float clipParentTop = Float.NaN;
        float clipParentBottom = Float.NaN;
        if (sourceObject.parentClipRect != null) {
            clipParentTop = sourceObject.parentClipRect.top;
            clipParentBottom = sourceObject.parentClipRect.bottom;
        } else if (sourceObject.parentView != null) {
            int[] coords = new int[2];
            sourceObject.parentView.getLocationInWindow(coords);
            clipParentTop = coords[1];
            clipParentBottom = coords[1] + sourceObject.parentView.getHeight();
        }
        if (!Float.isNaN(clipParentTop) && !Float.isNaN(clipParentBottom)) {
            clipTop = (int) (clipParentTop - drawRegion.top);
            if (clipTop < 0) {
                clipTop = 0;
            }
            clipBottom = (int) (drawRegion.bottom - clipParentBottom);
            if (clipBottom < 0) {
                clipBottom = 0;
            }
            clipTop = Math.max(clipTop, clipVertical);
            clipBottom = Math.max(clipBottom, clipVertical);
        }

        // Telegram parity: ClippingImageView always interpolates from
        // animationValues[0] to [1] as progress goes 0→1. So [0] must be
        // the STARTING state and [1] must be the ENDING state, regardless
        // of direction. Previously a from/to index swap (from=1 for close)
        // put the values at the wrong indices, causing the close animation
        // to play backwards (source→fullscreen instead of fullscreen→source)
        // and placing clip values on the wrong end.
        if (openDirection) {
            // OPEN: [0] = source (start), [1] = fullscreen (end)
            // Position animatingImageView at source (Telegram:17775 reads current state)
            animatingImageView.setScaleX(1.0f);
            animatingImageView.setScaleY(1.0f);
            animatingImageView.setTranslationX(drawRegion.left);
            animatingImageView.setTranslationY(drawRegion.top);
            animatingImageView.setRadius(sourceRadii);

            animationValues[0][0] = animatingImageView.getScaleX();
            animationValues[0][1] = animatingImageView.getScaleY();
            animationValues[0][2] = animatingImageView.getTranslationX();
            animationValues[0][3] = animatingImageView.getTranslationY();
            animationValues[0][4] = clipHorizontal;
            animationValues[0][5] = clipTop;
            animationValues[0][6] = clipBottom;
            for (int a = 0; a < 4; a++) {
                animationValues[0][7 + a] = sourceRadii[a];
            }
            animationValues[0][11] = clipVertical * sourceObject.scale;
            animationValues[0][12] = clipHorizontal * sourceObject.scale;

            animationValues[1][0] = scale;
            animationValues[1][1] = scale;
            animationValues[1][2] = xPos;
            animationValues[1][3] = yPos;
            animationValues[1][4] = 0;
            animationValues[1][5] = 0;
            animationValues[1][6] = 0;
            for (int a = 0; a < 4; a++) {
                animationValues[1][7 + a] = 0;
            }
            animationValues[1][11] = 0;
            animationValues[1][12] = 0;
        } else {
            // CLOSE: [0] = fullscreen (start), [1] = source (end)
            // Position animatingImageView at fullscreen (Telegram:18420-18426)
            animatingImageView.setScaleX(scale);
            animatingImageView.setScaleY(scale);
            animatingImageView.setTranslationX(xPos);
            animatingImageView.setTranslationY(yPos);
            animatingImageView.setRadius(new int[]{0, 0, 0, 0});

            // [0] = fullscreen state: no clip, no radius
            animationValues[0][0] = animatingImageView.getScaleX();
            animationValues[0][1] = animatingImageView.getScaleY();
            animationValues[0][2] = animatingImageView.getTranslationX();
            animationValues[0][3] = animatingImageView.getTranslationY();
            animationValues[0][4] = 0;
            animationValues[0][5] = 0;
            animationValues[0][6] = 0;
            for (int a = 0; a < 4; a++) {
                animationValues[0][7 + a] = 0;
            }
            animationValues[0][11] = 0;
            animationValues[0][12] = 0;

            // [1] = source/target state: with clip to prevent ActionBar overlay
            // Telegram:18464-18475 uses object.viewX + drawRegion.left * object.scale;
            // our drawRegion is already in window coords, so just drawRegion.left.
            animationValues[1][0] = sourceObject.scale;
            animationValues[1][1] = sourceObject.scale;
            animationValues[1][2] = drawRegion.left * sourceObject.scale;
            animationValues[1][3] = drawRegion.top * sourceObject.scale;
            animationValues[1][4] = clipHorizontal * sourceObject.scale;
            animationValues[1][5] = clipTop * sourceObject.scale;
            animationValues[1][6] = clipBottom * sourceObject.scale;
            for (int a = 0; a < 4; a++) {
                animationValues[1][7 + a] = sourceRadii[a];
            }
            animationValues[1][11] = clipVertical * sourceObject.scale;
            animationValues[1][12] = clipHorizontal * sourceObject.scale;
        }

        animatingImageView.setAnimationValues(animationValues, openDirection, false);
        animatingImageView.setAnimationProgress(0f);
        animatingImageView.setVisibility(VISIBLE);
    }

    private void startOpenAnimation() {
        if (imageMoveAnimation != null) {
            imageMoveAnimation.cancel();
            imageMoveAnimation = null;
        }
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator = null;
        }
        // Telegram defers the entire animation setup inside windowView's first
        // OnPreDrawListener (PhotoViewer.java:17712) so getMeasuredWidth() / Height()
        // are valid when computing the full-screen target pose. Our PhotoViewer is
        // attached to the activity decor AFTER openPhoto returns, so the very first
        // call here sees getWidth()==0 and produces currentRect=(0,0,0,0), which
        // makes the morph view collapse to the top-left corner. Wait for the next
        // preDraw before computing animationValues.
        if (getContainerViewWidth() == 0 || getContainerViewHeight() == 0) {
            windowView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (getContainerViewWidth() > 0 && getContainerViewHeight() > 0) {
                        windowView.getViewTreeObserver().removeOnPreDrawListener(this);
                        startOpenAnimation();
                    }
                    return true;
                }
            });
            return;
        }
        closeToSource = false;
        if (centerBitmap() == null || placeProviderObject == null || placeProviderObject.viewRect == null) {
            // Fallback fade-in (no morph view): no animatingImageView is shown, so the
            // first-frame drawRunnable sync is moot. The full-screen background fades
            // 0->255 covering the source view; eager-hiding the source here avoids it
            // peeking through the partially-transparent background.
            if (placeProviderObject != null) {
                setSourceVisible(placeProviderObject, false, false);
            }
            openProgress = 0f;
            openAnimator = ValueAnimator.ofFloat(0f, 1f);
            openAnimator.setDuration(OPEN_DURATION);
            openAnimator.addUpdateListener(animation -> {
                openProgress = (float) animation.getAnimatedValue();
                updateActionBarAlpha();
                invalidateViewer();
            });
            openAnimator.start();
            return;
        }

        updateCurrentRect();
        setupAnimatingImageView(placeProviderObject, currentRect, centerBitmap(), true);
        imageMoveAnimating = true;
        openProgress = 0f;
        actionBarAlpha = 0f;
        actionBarContainer.setAlpha(0f);
        containerView.setAlpha(0f);
        backgroundDrawable.setAlpha(0);
        // Telegram PhotoViewer.java:17915 — hide the source view AFTER the first
        // animation frame is rastered (drawRunnable fires inside BackgroundDrawable.draw
        // when getAlpha() != 0). This guarantees animatingImageView has actually been
        // painted on screen before the source disappears, eliminating the cross-fade
        // gap between source recomposition and morph-view appearance.
        final PlaceProviderObject sourceForHide = placeProviderObject;
        backgroundDrawable.drawRunnable = () -> setSourceVisible(sourceForHide, false, true);
        invalidateViewer();

        ArrayList<Animator> animators = new ArrayList<>(4);
        animators.add(ObjectAnimator.ofFloat(animatingImageView, AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0.0f, 1.0f));
        animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0, 255));
        animators.add(ObjectAnimator.ofFloat(actionBarContainer, View.ALPHA, 0.0f, 1.0f));
        animators.add(ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f, 1.0f));
        // Drive openProgress smoothly via the AnimatorSet so updateParentScale
        // produces a frame-by-frame zoom on the host (mirroring upstream's use
        // of clippingImageProgress to drive parentFragment.getFragmentView()
        // setScale each draw). Without this, openProgress jumps 0→1 instantly
        // and the host scale snaps at the end of the animation.
        ValueAnimator openProgressAnim = ValueAnimator.ofFloat(0f, 1f);
        openProgressAnim.addUpdateListener(a -> {
            openProgress = (float) a.getAnimatedValue();
            invalidateViewer();
        });
        animators.add(openProgressAnim);

        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(animators);
        imageMoveAnimation.setDuration(OPEN_DURATION);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageMoveAnimating = false;
                imageMoveAnimation = null;
                if (animatingImageView != null) {
                    animatingImageView.setVisibility(GONE);
                    // Bug G fix: drop the hardware layer once the animation is done
                    // so we stop holding a GPU texture for the morph view.
                    animatingImageView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                openProgress = 1f;
                actionBarAlpha = 1f;
                backgroundDrawable.setAlpha(255);
                updateActionBarAlpha();
                invalidateViewer();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                imageMoveAnimating = false;
                imageMoveAnimation = null;
                if (animatingImageView != null) {
                    animatingImageView.setVisibility(GONE);
                }
            }
        });
        imageMoveAnimation.start();
    }

    public void closePhoto(boolean animated) {
        if (dismissing) {
            return;
        }
        dismissing = true;
        hideChromeForClose();
        if (placeProvider != null) {
            placeProvider.willHidePhotoViewer();
        }
        cancelMoveAnimation();
        scroller.abortAnimation();
        if (imageMoveAnimation != null) {
            imageMoveAnimation.cancel();
            imageMoveAnimation = null;
        }
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator = null;
        }
        switchImageAfterAnimation = 0;
        // Bug F fix: do NOT resetParentScale() here. The close AnimatorSet drives
        // openProgress 1→0; updateParentScale (called every frame from onDraw)
        // computes a zoom factor that is continuous with the drag-state zoom because
        // it now reads dragProgress directly from translationY. Resetting the host
        // scale to 1.0 here causes an instant snap, followed by another snap on the
        // next frame when updateParentScale recaptures and re-zooms — the user
        // perceives this as "the detail behind first scales up, then back". The
        // final cleanup happens in removeFromParentNow() once detach is complete.
        PlaceProviderObject closingObject = null;
        PhotoEntry entry = currentEntry();
        View scaleViewForClose = parentScaleView;
        float wasScaleX = 1f;
        float wasScaleY = 1f;
        boolean restoreScaleAfterProvider = scaleViewForClose != null && parentScaleCaptured;
        if (restoreScaleAfterProvider) {
            wasScaleX = scaleViewForClose.getScaleX();
            wasScaleY = scaleViewForClose.getScaleY();
            // Telegram PhotoViewer.java:18231-18252 temporarily removes the
            // host zoom before asking the provider for the close target. The
            // provider measures source bounds in window coordinates; if we ask
            // while our open zoom is still applied to the host, the source
            // view's getLocationInWindow returns a scaled position — the close
            // animation then visibly lands at an intermediate scaled position
            // before the host resets to 1.0.
            scaleViewForClose.setScaleX(parentScaleStartX);
            scaleViewForClose.setScaleY(parentScaleStartY);
        }
        try {
            if (placeProvider != null && entry != null) {
                closingObject = placeProvider.getPlaceForPhoto(currentIndex, entry, true, true);
            }
        } finally {
            if (restoreScaleAfterProvider) {
                scaleViewForClose.setScaleX(wasScaleX);
                scaleViewForClose.setScaleY(wasScaleY);
            }
        }
        if (isVisibleClosePlace(closingObject)) {
            placeProviderObject = closingObject;
            View closeScaleTarget = parentScaleViewFor(closingObject);
            if (closeScaleTarget != null) {
                setParentScaleView(closeScaleTarget);
            }
        } else {
            placeProviderObject = null;
        }
        if (!animated) {
            removeFromParent();
            return;
        }
        closeToSource = placeProviderObject != null && placeProviderObject.viewRect != null;
        if (centerBitmap() == null || placeProviderObject == null || placeProviderObject.viewRect == null) {
            openAnimator = ValueAnimator.ofFloat(openProgress, 0f);
            openAnimator.setDuration(CLOSE_DURATION);
            openAnimator.addUpdateListener(animation -> {
                openProgress = (float) animation.getAnimatedValue();
                updateActionBarAlpha();
                invalidateViewer();
            });
            openAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeFromParent();
                }
            });
            openAnimator.start();
            return;
        }

        updateCurrentRect();
        setupAnimatingImageView(placeProviderObject, currentRect, centerBitmap(), false);
        imageMoveAnimating = true;
        // Bug C fix (revised): do NOT reset openProgress / backgroundDrawable.alpha
        // to their "fully open" values here. During a drag-down dismiss the user has
        // already faded the background (draw() override drives alpha against
        // dragProgress). Snapping back to 255 produces a visible black flash for one
        // frame before the close animator fades it back to 0. Telegram's close path
        // uses a single-arg ObjectAnimator.ofInt(backgroundDrawable, ALPHA, 0) which
        // animates from the property's current value to 0 — see PhotoViewer.java:18505.
        // We mirror that shape below.
        openProgress = 1f;
        // Source view is restored by removeFromParent() at animation end; the
        // drawRunnable channel is reserved for the open path's first-frame sync.
        backgroundDrawable.drawRunnable = null;
        invalidateViewer();

        ArrayList<Animator> animators = new ArrayList<>(4);
        animators.add(ObjectAnimator.ofFloat(animatingImageView, AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0.0f, 1.0f));
        // Single-value ofInt animates from the property's CURRENT value to 0,
        // preserving any drag-induced fade (PhotoViewer.java:18505 upstream shape).
        animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0));
        animators.add(ObjectAnimator.ofFloat(actionBarContainer, View.ALPHA, actionBarContainer.getAlpha(), 0.0f));
        animators.add(ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f));
        // Drive openProgress 1→0 alongside the close AnimatorSet so the host
        // scale animates back to 1.0 smoothly (instead of holding at 1.1
        // throughout the morph and snapping when resetParentScale fires).
        ValueAnimator openProgressAnim = ValueAnimator.ofFloat(openProgress, 0f);
        openProgressAnim.addUpdateListener(a -> {
            openProgress = (float) a.getAnimatedValue();
            invalidateViewer();
        });
        animators.add(openProgressAnim);

        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(animators);
        imageMoveAnimation.setDuration(CLOSE_DURATION);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageMoveAnimating = false;
                imageMoveAnimation = null;
                // Bug B fix: do NOT hide animatingImageView here.
                // removeFromParent() defers parent.removeView via postOnAnimation
                // when waitForSourceDraw is true, giving Compose's async source
                // thumbnail recompose one frame to catch up. Hiding the morph view
                // synchronously here would create a visible black gap between
                // morph-gone and source-shown.
                // Bug G note: leaving the hardware layer on until removeFromParentNow
                // is intentional — keeping the cached texture for the bridging frame
                // is cheaper than dropping the layer (which forces a software re-render)
                // for the one frame before detach. The layer is released when the
                // view is detached.
                removeFromParent();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                imageMoveAnimating = false;
                imageMoveAnimation = null;
                if (animatingImageView != null) {
                    animatingImageView.setVisibility(GONE);
                    animatingImageView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            }
        });
        // Bug G fix: hardware-accelerate close morph (see startOpenAnimation).
        if (animatingImageView != null) {
            animatingImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        imageMoveAnimation.start();
    }

    private boolean isVisibleClosePlace(@Nullable PlaceProviderObject object) {
        if (object == null || object.viewRect == null || getContainerViewWidth() <= 0 || getContainerViewHeight() <= 0) {
            return false;
        }
        RectF rect = object.viewRect;
        if (rect.width() <= 0f || rect.height() <= 0f) {
            return false;
        }
        // Telegram parity: even a partly-clipped source (scrolled half off the top
        // or bottom of the host's content area, or pushed under the host's ActionBar)
        // gets a morph close. ClippingImageView's clipTop / clipBottom — computed in
        // setupAnimatingImageView from object.parentView (or parentClipRect) — is what
        // visually trims the morph view to the parent's visible area at the close end
        // pose, so the morph never has to "land" at a fully-on-screen rect to look
        // right. Previously we required the rect to be fully inside the viewport,
        // which dropped the morph to a fade-only fallback for any partially-scrolled
        // image; the user's complaint #1 ("half-off-screen → no animation") was that
        // fade fallback. Require only some intersection with the viewport so that we
        // still drop to fade for a source that has scrolled completely off-screen
        // (where there is nothing to morph toward).
        final float tolerance = AndroidUtilities.dp(2);
        if (rect.right <= -tolerance || rect.left >= getContainerViewWidth() + tolerance) {
            return false;
        }
        if (rect.bottom <= -tolerance || rect.top >= getContainerViewHeight() + tolerance) {
            return false;
        }
        return true;
    }

    private void removeFromParent() {
        boolean waitForSourceDraw = closeToSource && sourceHiddenObject != null;
        final View sourceParent = waitForSourceDraw && sourceHiddenObject != null
                ? sourceHiddenObject.parentView
                : null;
        // When the source is a real Android View (the AndroidView path used by
        // CookedImageBlock's RoundedImageView), setSourceVisible(true) below has
        // ALREADY made the source view visible — View.setVisibility is synchronous,
        // and the next paint cycle picks it up. Detach immediately so the morph
        // view and the source view do not both render at the source position for
        // any frame. Two simultaneous renderings of the same bitmap go through
        // different pipelines (ClippingImageView's BitmapShader + clipPath vs.
        // ImageView's matrix-based draw + Outline clip) and produce sub-pixel
        // mismatches that the user perceives as a "two-stage" close — morph
        // settles at source pose, then a faint flick as the source view's
        // rendering replaces it.
        final boolean syncSourcePath = sourceHiddenObject != null
                && sourceHiddenObject.sourceView != null;
        setSourceVisible(null, true, true);
        if (syncSourcePath) {
            removeFromParentNow();
            return;
        }
        if (waitForSourceDraw && windowView.getParent() != null) {
            // Compose-state-based source hiding (used by CookedCollageBlock and any
            // other image rendered via Compose Image + hiddenImageUrl): postOnAnimation
            // would only defer one Choreographer animation callback, which fires
            // BEFORE Compose's snapshot apply for the new alpha=1 state. Detaching
            // at that point reveals the host while the source Image is still at
            // alpha=0 in its previous commit, producing a one-frame blank gap.
            //
            // OnPreDrawListener fires AFTER measure/layout — by that point Compose
            // has applied the snapshot (state read inside graphicsLayer/alpha is
            // tracked) and the source Image's RenderNode has alpha=1 again.
            if (sourceParent != null && sourceParent.getViewTreeObserver().isAlive()) {
                final ViewTreeObserver observer = sourceParent.getViewTreeObserver();
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        ViewTreeObserver vto = sourceParent.getViewTreeObserver();
                        if (vto.isAlive()) {
                            vto.removeOnPreDrawListener(this);
                        } else {
                            observer.removeOnPreDrawListener(this);
                        }
                        removeFromParentNow();
                        return true;
                    }
                });
                return;
            }
            // Fallback if the source's parent observer is no longer alive
            // (parent already detached) — keep the previous one-frame defer so
            // the close path still tears down cleanly.
            postOnAnimation(this::removeFromParentNow);
            return;
        }
        removeFromParentNow();
    }

    private void removeFromParentNow() {
        // Symmetric teardown for both attach paths: WindowManager-attached (the standard
        // Telegram-shaped path) detaches via wm.removeView; the decor-view fallback path
        // (only used if wm.addView throws) detaches via parent.removeView.
        if (hostWindowManager != null && windowView.getParent() != null) {
            try {
                hostWindowManager.removeView(windowView);
            } catch (IllegalArgumentException ignored) {
                // View not actually attached to this WindowManager (e.g. activity already
                // tore it down). Nothing more to do.
            }
        } else {
            ViewGroup parent = (ViewGroup) windowView.getParent();
            if (parent != null) {
                parent.removeView(windowView);
            }
        }
        hostWindowManager = null;
        windowLayoutParams = null;
        resetParentScale();
        exitImmersive();
        hostWindow = null;
        if (activeViewer == this) {
            activeViewer = null;
        }
        if (placeProvider != null) {
            placeProvider.onClose();
        }
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    private void setSourceVisible(@Nullable PlaceProviderObject object, boolean visible, boolean animated) {
        if (visible) {
            if (sourceHiddenObject != null) {
                sourceHiddenObject.setVisible(true, animated);
                sourceHiddenObject = null;
            }
            return;
        }
        if (object == null) {
            return;
        }
        if (sourceHiddenObject != null && sourceHiddenObject != object && !sourceHiddenObject.isSameSource(object)) {
            sourceHiddenObject.setVisible(true, animated);
        }
        object.setVisible(false, animated);
        sourceHiddenObject = object;
    }

    /**
     * @param slot -1 for left neighbor, 0 for current centre, +1 for right neighbor.
     */
    private void loadIntoSlot(int slot, @NonNull String url) {
        final String requested = url;
        ImageLoader.loadAsync(requested, new ImageLoader.Callback() {
            @Override
            public void onLoaded(@Nullable Bitmap loaded) {
                if (loaded == null) {
                    return;
                }
                ImageLoader.put(requested, softwareBitmap(loaded));
                final Bitmap thumbnail = createThumbnail(loaded);
                if (slot == 0) {
                    if (!requested.equals(pendingUrl)) {
                        return;
                    }
                    setSlotBitmap(centerImage, softwareBitmap(loaded));
                    updateThumbnailForUrl(requested, thumbnail);
                    resetTransform();
                    invalidateViewer();
                } else if (slot < 0) {
                    if (!requested.equals(leftPendingUrl)) {
                        return;
                    }
                    setSlotBitmap(leftImage, softwareBitmap(loaded));
                    updateThumbnailForUrl(requested, thumbnail);
                    invalidateViewer();
                } else {
                    if (!requested.equals(rightPendingUrl)) {
                        return;
                    }
                    setSlotBitmap(rightImage, softwareBitmap(loaded));
                    updateThumbnailForUrl(requested, thumbnail);
                    invalidateViewer();
                }
            }
        });
    }

    @Nullable
    private void resetTransform() {
        scale = MIN_SCALE;
        translationX = 0;
        translationY = 0;
        animateToScale = MIN_SCALE;
        animateToX = 0;
        animateToY = 0;
        updateMinMax(scale);
        updateParentScale();
    }

    private void updateBaseRect() {
        Bitmap bitmap = centerBitmap();
        if (bitmap == null || getContainerViewWidth() == 0 || getContainerViewHeight() == 0) {
            baseRect.setEmpty();
            return;
        }
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            baseRect.setEmpty();
            return;
        }
        float fit = Math.min(getContainerViewWidth() / bitmapWidth, getContainerViewHeight() / bitmapHeight);
        float width = bitmapWidth * fit;
        float height = bitmapHeight * fit;
        baseRect.set((getContainerViewWidth() - width) / 2f, (getContainerViewHeight() - height) / 2f, (getContainerViewWidth() + width) / 2f, (getContainerViewHeight() + height) / 2f);
    }

    private void updateCurrentRect() {
        updateBaseRect();
        float width = baseRect.width() * scale;
        float height = baseRect.height() * scale;
        float centerX = getContainerViewWidth() / 2f + translationX;
        float centerY = getContainerViewHeight() / 2f + translationY;
        currentRect.set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);
    }

    private void updateMinMax(float targetScale) {
        updateBaseRect();
        float width = baseRect.width() * targetScale;
        float height = baseRect.height() * targetScale;
        if (width > getContainerViewWidth()) {
            minX = -(width - getContainerViewWidth()) / 2f;
            maxX = (width - getContainerViewWidth()) / 2f;
        } else {
            minX = maxX = 0;
        }
        if (height > getContainerViewHeight()) {
            minY = -(height - getContainerViewHeight()) / 2f;
            maxY = (height - getContainerViewHeight()) / 2f;
        } else {
            minY = maxY = 0;
        }
    }

    private void checkMinMax(boolean zoom) {
        updateMinMax(scale);
        animateTo(scale, clamp(translationX, minX, maxX), clamp(translationY, minY, maxY), zoom);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean zoom) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            return;
        }
        cancelMoveAnimation();
        float startScale = scale;
        float startX = translationX;
        float startY = translationY;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(MOVE_DURATION);
        moveAnimator.setInterpolator(interpolator);
        moveAnimator.addUpdateListener(animation -> {
            animationValue = (float) animation.getAnimatedValue();
            scale = startScale + (animateToScale - startScale) * animationValue;
            translationX = startX + (animateToX - startX) * animationValue;
            translationY = startY + (animateToY - startY) * animationValue;
            updateMinMax(scale);
            invalidateViewer();
        });
        moveAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scale = animateToScale;
                translationX = animateToX;
                translationY = animateToY;
                moveAnimator = null;
                updateMinMax(scale);
                // Telegram setCurrentPhotoIndex(): consume any pending page commit and rotate slots.
                if (switchImageAfterAnimation == 1) {
                    switchImageAfterAnimation = 0;
                    shiftSlotsForward();
                } else if (switchImageAfterAnimation == 2) {
                    switchImageAfterAnimation = 0;
                    shiftSlotsBackward();
                } else {
                    invalidateViewer();
                }
            }
        });
        moveAnimator.start();
    }

    public void setAnimationValue(float value) {
        animationValue = value;
    }

    public float getAnimationValue() {
        return animationValue;
    }

    private void cancelMoveAnimation() {
        if (moveAnimator != null) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (dismissing) {
            return true;
        }
        if (event.getPointerCount() == 1) {
            gestureDetector.onTouchEvent(event);
            if (doubleTap) {
                doubleTap = false;
                moving = false;
                zooming = false;
                return true;
            }
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            discardTap = false;
            cancelMoveAnimation();
            scroller.abortAnimation();
            if (action == MotionEvent.ACTION_DOWN) {
                moveStartX = event.getX();
                dragY = moveStartY = event.getY();
                draggingDown = false;
                canDragDown = true;
                moving = false;
                zooming = false;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(event);
            } else if (event.getPointerCount() == 2) {
                pinchStartDistance = distance(event, 0, 1);
                pinchStartScale = scale;
                pinchCenterX = (event.getX(0) + event.getX(1)) / 2f;
                pinchCenterY = (event.getY(0) + event.getY(1)) / 2f;
                pinchStartX = translationX;
                pinchStartY = translationY;
                zooming = true;
                moving = false;
                draggingDown = false;
                canDragDown = false;
                discardTap = true;
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            }
            if (action == MotionEvent.ACTION_DOWN) {
                longPressX = event.getX();
                AndroidUtilities.runOnUIThread(longPressRunnable, 300);
            } else {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(longPressX - event.getX()) > touchSlop) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            }
            if (zooming && event.getPointerCount() >= 2 && pinchStartDistance > 0) {
                discardTap = true;
                scale = distance(event, 0, 1) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - getContainerViewWidth() / 2f) - ((pinchCenterX - getContainerViewWidth() / 2f) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getContainerViewHeight() / 2f) - ((pinchCenterY - getContainerViewHeight() / 2f) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                invalidateViewer();
            } else if (event.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                float dx = Math.abs(event.getX() - moveStartX);
                float dy = Math.abs(event.getY() - dragY);
                if (dx > touchSlop || dy > touchSlop) {
                    discardTap = true;
                }
                if (canDragDown && !draggingDown && scale == MIN_SCALE && dy >= AndroidUtilities.dp(30) && dy / 2f > dx) {
                    draggingDown = true;
                    actionBarContainer.animate()
                            .alpha(0f)
                            .translationY(-AndroidUtilities.dp(24))
                            .setDuration(200)
                            .start();
                    togglePhotosListView(false, true);
                    moving = false;
                    dragY = event.getY();
                    return true;
                } else if (draggingDown) {
                    translationY = event.getY() - dragY;
                    invalidateViewer();
                } else {
                    float moveDx = moveStartX - event.getX();
                    float moveDy = moveStartY - event.getY();
                    // Telegram engages 'moving' at scale==1 only when horizontal travel exceeds vertical
                    // by dp(12); this guarantees vertical-first gestures still reach drag-down.
                    boolean enterMoving = moving
                            || scale != MIN_SCALE
                            || (scale == MIN_SCALE && Math.abs(moveDy) + AndroidUtilities.dp(12) < Math.abs(moveDx));
                    if (enterMoving) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }
                        moveStartX = event.getX();
                        moveStartY = event.getY();
                        updateMinMax(scale);
                        // Edge resistance: only divide by 3 if there's no neighbor to drag in.
                        boolean noRightNeighbor = currentIndex + 1 >= entries.size();
                        boolean noLeftNeighbor = currentIndex <= 0;
                        if ((translationX < minX && noRightNeighbor)
                                || (translationX > maxX && noLeftNeighbor)) {
                            moveDx /= 3f;
                        }
                        if (maxY == 0 && minY == 0) {
                            // At scale==1 vertical pan is owned by drag-down; do not move Y here.
                            moveDy = 0;
                        } else if (translationY < minY || translationY > maxY) {
                            moveDy /= 3f;
                        }
                        translationX -= moveDx;
                        if (scale != MIN_SCALE) {
                            translationY -= moveDy;
                        }
                        invalidateViewer();
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
            if (zooming) {
                if (scale < MIN_SCALE) {
                    updateMinMax(MIN_SCALE);
                    animateTo(MIN_SCALE, 0, 0, true);
                } else if (scale > MAX_SCALE) {
                    float targetX = (pinchCenterX - getContainerViewWidth() / 2f) - ((pinchCenterX - getContainerViewWidth() / 2f) - pinchStartX) * (MAX_SCALE / pinchStartScale);
                    float targetY = (pinchCenterY - getContainerViewHeight() / 2f) - ((pinchCenterY - getContainerViewHeight() / 2f) - pinchStartY) * (MAX_SCALE / pinchStartScale);
                    updateMinMax(MAX_SCALE);
                    animateTo(MAX_SCALE, clamp(targetX, minX, maxX), clamp(targetY, minY, maxY), true);
                } else {
                    checkMinMax(true);
                }
                zooming = false;
                moving = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - event.getY()) > getContainerViewHeight() / 6f) {
                    closePhoto(true);
                } else {
                    animateTo(MIN_SCALE, 0, 0, false);
                    if (actionBarVisible) {
                        actionBarContainer.animate().alpha(actionBarAlpha).translationY(0f).setDuration(200).start();
                    }
                    togglePhotosListView(actionBarVisible && entries.size() > 1, true);
                }
                draggingDown = false;
            } else if (moving) {
                updateMinMax(scale);
                float velocityX = 0f;
                float velocityY = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocityX = velocityTracker.getXVelocity();
                    velocityY = velocityTracker.getYVelocity();
                }
                // Telegram page-commit on UP at scale==1: distance > width/3 OR velocity > dp(650).
                if (scale == MIN_SCALE) {
                    int commitVelocity = AndroidUtilities.dp(PAGE_COMMIT_VELOCITY_DP);
                    if ((translationX < minX - getContainerViewWidth() / 3f || velocityX < -commitVelocity)
                            && rightImage.hasImageSet()) {
                        moving = false;
                        canDragDown = true;
                        goToNext();
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            recycleVelocityTracker();
                        }
                        return true;
                    }
                    if ((translationX > maxX + getContainerViewWidth() / 3f || velocityX > commitVelocity)
                            && leftImage.hasImageSet()) {
                        moving = false;
                        canDragDown = true;
                        goToPrev();
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            recycleVelocityTracker();
                        }
                        return true;
                    }
                    // No commit: animate translationX back to 0 (rubber-band release).
                    animateTo(MIN_SCALE, 0, 0, false);
                } else if (scale != MIN_SCALE) {
                    // Zoomed pan: keep Telegram's fling-then-clamp behaviour.
                    scroller.fling(
                            Math.round(translationX),
                            Math.round(translationY),
                            Math.round(velocityX),
                            Math.round(velocityY),
                            Math.round(minX),
                            Math.round(maxX),
                            Math.round(minY),
                            Math.round(maxY)
                    );
                    checkMinMax(false);
                }
                moving = false;
                canDragDown = true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                recycleVelocityTracker();
            }
        }
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!scroller.isFinished() && scroller.computeScrollOffset()) {
            translationX = scroller.getCurrX();
            translationY = scroller.getCurrY();
            invalidateViewer();
        }

        if (imageMoveAnimating) {
            // updateParentScale must run during the AnimatorSet so the host
            // scale tracks openProgress smoothly each frame (mirrors upstream's
            // pattern of recomputing parentFragment.getFragmentView() scale on
            // every draw call while clippingImageProgress is animating).
            // dragProgress is 0 here because drag and the open/close AnimatorSet
            // are mutually exclusive states. backgroundDrawable is painted by
            // the View framework via setBackground(), with its 4-rect cutout
            // around animatingImageView (see BackgroundDrawable.draw).
            updateParentScale();
            return;
        }

        float dragProgress = scale == MIN_SCALE && getContainerViewHeight() > 0
                ? clamp(Math.abs(translationY) / (getContainerViewHeight() / 2f), 0f, 1f)
                : 0f;
        updateParentScale();

        Bitmap bitmap = centerBitmap();
        if (bitmap == null) {
            return;
        }

        updateCurrentRect();
        // Open / close transition: lerp source view rect to current centred rect.
        // When no source rect is available (source view detached at close time, or open
        // started without a provider rect) we fall back to currentRect itself so the image
        // stays in place and only fades, instead of jumping or shooting off-screen.
        if (openProgress < 1f) {
            boolean hasSource = placeProviderObject != null && placeProviderObject.viewRect != null;
            if (hasSource) {
                getSourceTransitionRect(bitmap, placeProviderObject, sourceTransitionRect);
            } else {
                sourceTransitionRect.set(currentRect);
            }
            lerpRect(sourceTransitionRect, currentRect, openProgress, animationRect);
            float radius = (hasSource ? placeProviderObject.radius : 0f) * (1f - openProgress);
            // close-to-source: image stays opaque while morphing back to the thumbnail rect.
            // open or close-without-source: image alpha tracks openProgress so it fades in/out.
            boolean keepOpaque = dismissing && hasSource;
            drawTransitionReceiver(canvas, animationRect, radius, keepOpaque ? 1f : openProgress);
            return;
        }

        if (scale == MIN_SCALE && !dismissing) {
            Bitmap rightBitmap = rightBitmap();
            float rightProgress = rightBitmap != null && translationX < minX - AndroidUtilities.dp(5)
                    ? clamp((minX - translationX) / getContainerViewWidth(), 0f, 1f)
                    : 0f;
            if (rightProgress > 0f) {
                drawReceiverAt(canvas, rightImage, 0f, 0f, 0.7f + 0.3f * rightProgress, rightProgress);
                drawReceiverAt(canvas, centerImage, translationX, translationY, scale);
                groupedPhotosListView.setMoveProgress(-rightProgress);
                return;
            }
            Bitmap leftBitmap = leftBitmap();
            float leftProgress = leftBitmap != null && translationX > maxX + AndroidUtilities.dp(5)
                    ? clamp((translationX - maxX) / getContainerViewWidth(), 0f, 1f)
                    : 0f;
            if (leftProgress > 0f) {
                drawReceiverAt(canvas, centerImage, maxX, translationY, scale * (1f - 0.3f * leftProgress), 1f - leftProgress);
                drawReceiverAt(canvas, leftImage, translationX - getContainerViewWidth() - AndroidUtilities.dp(30) / 2f, 0f, MIN_SCALE);
                groupedPhotosListView.setMoveProgress(leftProgress);
                return;
            }
        }
        drawReceiverAt(canvas, centerImage, translationX, translationY, scale);
        if (scale == MIN_SCALE && !dismissing && entries.size() > 1) {
            groupedPhotosListView.setMoveProgress(getContainerViewWidth() == 0 ? 0f : clamp(translationX / getContainerViewWidth(), -1f, 1f));
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == photoTouchView) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void drawTransitionReceiver(@NonNull Canvas canvas, @NonNull RectF rect, float radius, float alpha) {
        centerImage.setImageCoords(rect);
        centerImage.setRoundRadius(radius);
        centerImage.setCurrentAlpha(clamp(alpha, 0f, 1f));
        centerImage.draw(canvas);
        centerImage.setCurrentAlpha(1f);
        centerImage.setRoundRadius(0);
    }

    /** Draws a Telegram ImageReceiver slot fitted to the container, scaled and translated. */
    private void drawReceiverAt(@NonNull Canvas canvas, @NonNull ImageReceiver receiver, float tx, float ty, float drawScale) {
        drawReceiverAt(canvas, receiver, tx, ty, drawScale, 1f);
    }

    private void drawReceiverAt(@NonNull Canvas canvas, @NonNull ImageReceiver receiver, float tx, float ty, float drawScale, float alpha) {
        Bitmap target = receiver.getBitmap();
        if (target == null || target.isRecycled() || getContainerViewWidth() <= 0 || getContainerViewHeight() <= 0) {
            return;
        }
        float fit = Math.min(getContainerViewWidth() / (float) target.getWidth(), getContainerViewHeight() / (float) target.getHeight());
        float bitmapScale = fit * drawScale;
        float width = target.getWidth() * bitmapScale;
        float height = target.getHeight() * bitmapScale;
        float left = getContainerViewWidth() / 2f + tx - width / 2f;
        float top = getContainerViewHeight() / 2f + ty - height / 2f;
        receiver.setImageCoords(left, top, width, height);
        receiver.setCurrentAlpha(alpha);
        receiver.draw(canvas);
        receiver.setCurrentAlpha(1f);
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleVelocityTracker();
        setSourceVisible(null, true, false);
        // Defensive: if we somehow detached without going through removeFromParent (e.g. host
        // activity recreation), still bring the system bars back.
        exitImmersive();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEventPreIme(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && event.getAction() == android.view.KeyEvent.ACTION_UP) {
            closePhoto(true);
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    public boolean handleBackPressed() {
        if (!dismissing) {
            closePhoto(true);
            return true;
        }
        return false;
    }

    /**
     * Telegram goToNext: animate translationX out to the left by one page width plus dp(15) gap,
     * then on animation end advance currentIndex and recycle the ImageReceiver slots so leftImage
     * takes over from the old centre, the queued rightImage becomes the new centre, and a fresh
     * right neighbor is prefetched.
     */
    private void goToNext() {
        if (currentIndex + 1 >= entries.size()) {
            return;
        }
        PhotoEntry oldEntry = currentEntry();
        if (placeProvider != null && oldEntry != null) {
            placeProvider.willSwitchFromPhoto(currentIndex, oldEntry);
        }
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - getContainerViewWidth() - AndroidUtilities.dp(PAGE_SPACING_DP), translationY, false);
    }

    private void goToPrev() {
        if (currentIndex <= 0) {
            return;
        }
        PhotoEntry oldEntry = currentEntry();
        if (placeProvider != null && oldEntry != null) {
            placeProvider.willSwitchFromPhoto(currentIndex, oldEntry);
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + getContainerViewWidth() + AndroidUtilities.dp(PAGE_SPACING_DP), translationY, false);
    }

    /** Slot rotation after a goToNext animation completes. */
    private void shiftSlotsForward() {
        copySlot(centerImage, leftImage);
        leftPendingUrl = pendingUrl;
        copySlot(rightImage, centerImage);
        pendingUrl = rightPendingUrl;
        setSlotBitmap(rightImage, null);
        rightPendingUrl = null;
        currentIndex++;
        translationX = 0;
        translationY = 0;
        scale = MIN_SCALE;
        animateToScale = MIN_SCALE;
        animateToX = 0;
        animateToY = 0;
        updatePlaceProviderObjectForCurrent(true);
        if (pendingUrl != null && ImageLoader.get(pendingUrl) == null) {
            // Right neighbor never finished loading before commit; restart the load.
            loadIntoSlot(0, pendingUrl);
        }
        prefetchNeighbors();
        updateActionBarText();
        updatePhotosListSelection();
        updateMinMax(scale);
        invalidateViewer();
    }

    /** Slot rotation after a goToPrev animation completes. */
    private void shiftSlotsBackward() {
        copySlot(centerImage, rightImage);
        rightPendingUrl = pendingUrl;
        copySlot(leftImage, centerImage);
        pendingUrl = leftPendingUrl;
        setSlotBitmap(leftImage, null);
        leftPendingUrl = null;
        currentIndex--;
        translationX = 0;
        translationY = 0;
        scale = MIN_SCALE;
        animateToScale = MIN_SCALE;
        animateToX = 0;
        animateToY = 0;
        updatePlaceProviderObjectForCurrent(true);
        if (pendingUrl != null && ImageLoader.get(pendingUrl) == null) {
            loadIntoSlot(0, pendingUrl);
        }
        prefetchNeighbors();
        updateActionBarText();
        updatePhotosListSelection();
        updateMinMax(scale);
        invalidateViewer();
    }

    private void toggleActionBar() {
        if (dismissing) {
            return;
        }
        actionBarVisible = !actionBarVisible;
        actionBarContainer.animate()
                .alpha(actionBarVisible ? actionBarAlpha : 0f)
                .translationY(actionBarVisible ? 0f : -AndroidUtilities.dp(24))
                .setDuration(200)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .start();
        togglePhotosListView(actionBarVisible && entries.size() > 1, true);
    }

    private void updateActionBarAlpha() {
        actionBarAlpha = openProgress;
        if (actionBarVisible && !dismissing) {
            actionBarContainer.setAlpha(actionBarAlpha);
            actionBarContainer.setTranslationY(0f);
        }
    }

    private void hideChromeForClose() {
        actionBarVisible = false;
        actionBarContainer.animate().cancel();
        actionBarContainer.setAlpha(0f);
        actionBarContainer.setTranslationY(-AndroidUtilities.dp(24));
        togglePhotosListView(false, false);
    }

    private void updateActionBarText() {
        PhotoEntry entry = currentEntry();
        CharSequence title = null;
        CharSequence subtitle = null;
        if (placeProvider != null && entry != null) {
            title = placeProvider.getTitleFor(currentIndex, entry);
            subtitle = placeProvider.getSubtitleFor(currentIndex, entry);
        }
        if (TextUtils.isEmpty(title)) {
            title = entries.size() > 1 ? (currentIndex + 1) + " of " + entries.size() : "Photo";
        }
        if (TextUtils.isEmpty(subtitle) && entry != null) {
            subtitle = entry.url;
        }
        boolean moveDown = lastActionBarTextIndex < 0 || currentIndex >= lastActionBarTextIndex;
        titleView.setText(title, actionBarTextInitialized, moveDown);
        subtitleView.setText(subtitle);
        lastActionBarTextIndex = currentIndex;
        actionBarTextInitialized = true;
    }

    private void updatePhotosList() {
        groupedPhotosListView.fillList();
    }

    private void switchToIndex(int index) {
        if (index == currentIndex || index < 0 || index >= entries.size()) {
            return;
        }
        PhotoEntry oldEntry = currentEntry();
        if (placeProvider != null && oldEntry != null) {
            placeProvider.willSwitchFromPhoto(currentIndex, oldEntry);
        }
        currentIndex = index;
        currentEntry = currentEntry();
        setSlotBitmap(leftImage, null);
        leftPendingUrl = null;
        setSlotBitmap(rightImage, null);
        rightPendingUrl = null;
        PlaceProviderObject object = placeProvider == null || currentEntry == null
                ? null
                : placeProvider.getPlaceForPhoto(currentIndex, currentEntry, true, false);
        showCurrent(object == null ? null : object.thumb, object);
        if (object == null) {
            setSourceVisible(null, true, true);
        } else {
            setSourceVisible(object, false, true);
        }
        updatePhotosListSelection();
    }

    private void switchToIndexFromStrip(int index) {
        if (index == currentIndex || index < 0 || index >= entries.size()) {
            return;
        }
        PhotoEntry oldEntry = currentEntry();
        if (placeProvider != null && oldEntry != null) {
            placeProvider.willSwitchFromPhoto(currentIndex, oldEntry);
        }
        currentIndex = index;
        currentEntry = currentEntry();
        setSlotBitmap(leftImage, null);
        leftPendingUrl = null;
        setSlotBitmap(rightImage, null);
        rightPendingUrl = null;
        PlaceProviderObject object = placeProvider == null || currentEntry == null
                ? null
                : placeProvider.getPlaceForPhoto(currentIndex, currentEntry, true, false);
        showCurrent(object == null ? null : object.thumb, object);
        if (object == null) {
            setSourceVisible(null, true, true);
        } else {
            setSourceVisible(object, false, true);
        }
        updatePhotosListSelection();
    }

    private void updatePlaceProviderObjectForCurrent(boolean animated) {
        currentEntry = currentEntry();
        placeProviderObject = placeProvider == null || currentEntry == null
                ? null
                : placeProvider.getPlaceForPhoto(currentIndex, currentEntry, true, false);
        View nextScaleTarget = parentScaleViewFor(placeProviderObject);
        if (nextScaleTarget != null) {
            setParentScaleView(nextScaleTarget);
        }
        if (placeProviderObject == null) {
            setSourceVisible(null, true, animated);
        } else {
            setSourceVisible(placeProviderObject, false, animated);
        }
    }

    @Nullable
    private static View parentScaleViewFor(@Nullable PlaceProviderObject object) {
        if (object == null) {
            return null;
        }
        return object.parentScaleView != null ? object.parentScaleView : object.parentView;
    }

    private void updatePhotosListSelection() {
        groupedPhotosListView.fillList();
    }

    private void updateThumbnailForUrl(@NonNull String url, @Nullable Bitmap loaded) {
        loadingThumbnails.remove(url);
        if (loaded != null) {
            Bitmap square = ImageLoader.createSquareThumbnail(loaded);
            ImageLoader.putThumbnail(url, square != null ? square : loaded);
            failedThumbnails.remove(url);
        } else {
            failedThumbnails.add(url);
        }
        groupedPhotosListView.invalidate();
    }

    private void ensureThumbnailForIndex(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        PhotoEntry entry = entries.get(index);
        if (ImageLoader.getThumbnail(entry.url) != null) {
            return;
        }
        Bitmap loaded = ImageLoader.get(entry.url);
        if (loaded != null) {
            updateThumbnailForUrl(entry.url, createThumbnail(loaded));
            return;
        }
        Bitmap providerThumb = createThumbnail(getProviderThumbnail(index, entry));
        if (providerThumb != null) {
            updateThumbnailForUrl(entry.url, providerThumb);
            return;
        }
        loadThumbnail(entry.url);
    }

    @Nullable
    private Bitmap getProviderThumbnail(int index, @NonNull PhotoEntry entry) {
        if (placeProvider == null) {
            return null;
        }
        PlaceProviderObject object = placeProvider.getPlaceForPhoto(index, entry, true, false);
        return object == null ? null : object.thumb;
    }

    private void loadThumbnail(@NonNull String url) {
        Bitmap cached = ImageLoader.getThumbnail(url);
        if (cached != null) {
            updateThumbnailForUrl(url, cached);
            return;
        }
        Bitmap fullSize = ImageLoader.get(url);
        if (fullSize != null) {
            updateThumbnailForUrl(url, createThumbnail(fullSize));
            return;
        }
        if (failedThumbnails.contains(url)) {
            return;
        }
        if (!loadingThumbnails.add(url)) {
            return;
        }
        ImageLoader.loadAsync(url, new ImageLoader.Callback() {
            @Override
            public void onLoaded(@Nullable Bitmap loaded) {
                if (loaded != null) {
                    ImageLoader.put(url, softwareBitmap(loaded));
                }
                updateThumbnailForUrl(url, createThumbnail(loaded));
            }
        });
    }

    // PORT-MODE: Exact Component Port (chrome toggle)
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java:13509-13591 (toggleActionBar)
    // Telegram animates groupedPhotosListView.ALPHA + TRANSLATION_Y as part of the
    // chrome AnimatorSet without ever toggling VISIBILITY. The actual show/hide of
    // thumbnails is driven by GroupedPhotosListView.fillList() and its internal
    // showAnimator/hideAnimator. We must do the same — flipping visibility GONE
    // suppresses fillList's animation pipeline.
    private void togglePhotosListView(boolean show, boolean animated) {
        if (photosListVisible == show) {
            return;
        }
        photosListVisible = show;
        groupedPhotosListView.animate().cancel();
        if (animated) {
            groupedPhotosListView.animate()
                    .alpha(show ? 1f : 0f)
                    .translationY(show ? 0f : AndroidUtilities.dp(20))
                    .setDuration(200)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            groupedPhotosListView.setAlpha(show ? 1f : 0f);
            groupedPhotosListView.setTranslationY(show ? 0f : AndroidUtilities.dp(20));
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void showLongPressMenu() {
        showActionDialog();
    }

    private void showActionDialog() {
        new AlertDialog.Builder(getContext())
                .setItems(
                        new CharSequence[] {
                                getContext().getString(R.string.post_image_viewer_action_save_image),
                                getContext().getString(R.string.post_image_viewer_action_share)
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                saveCurrentImage();
                            } else {
                                shareCurrentImage();
                            }
                        }
                )
                .show();
    }

    private void saveCurrentImage() {
        Bitmap current = centerBitmap();
        if (current == null) {
            showImageActionFailed();
            return;
        }
        Context appContext = getContext().getApplicationContext();
        Toast.makeText(getContext(), R.string.post_image_viewer_download_started, Toast.LENGTH_SHORT).show();
        LOAD_EXECUTOR.execute(() -> {
            boolean saved = saveBitmapToPictures(appContext, current);
            if (!saved) {
                MAIN_HANDLER.post(this::showImageActionFailed);
            }
        });
    }

    private void shareCurrentImage() {
        PhotoEntry entry = currentEntry();
        if (entry == null || TextUtils.isEmpty(entry.url)) {
            showImageActionFailed();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, entry.url);
        Intent chooser = Intent.createChooser(intent, getContext().getString(R.string.post_image_viewer_action_share));
        Activity activity = findActivity(getContext());
        if (activity == null) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            (activity == null ? getContext().getApplicationContext() : activity).startActivity(chooser);
        } catch (ActivityNotFoundException ignored) {
            showImageActionFailed();
        }
    }

    private void showImageActionFailed() {
        Toast.makeText(getContext(), R.string.post_image_viewer_action_failed, Toast.LENGTH_SHORT).show();
    }

    private static boolean saveBitmapToPictures(@NonNull Context context, @NonNull Bitmap source) {
        String fileName = "nativedo_" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NativeDo");
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return false;
            }
            boolean saved = false;
            try (OutputStream output = resolver.openOutputStream(uri)) {
                saved = output != null && source.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception ignored) {
                saved = false;
            }
            values.clear();
            if (saved) {
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return true;
            }
            resolver.delete(uri, null, null);
            return false;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "NativeDo");
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        File outputFile = new File(dir, fileName);
        boolean saved;
        try (FileOutputStream output = new FileOutputStream(outputFile)) {
            saved = source.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (Exception ignored) {
            saved = false;
        }
        if (saved) {
            MediaScannerConnection.scanFile(context, new String[] {outputFile.getAbsolutePath()}, new String[] {"image/png"}, null);
        }
        return saved;
    }

    private static float distance(@NonNull MotionEvent event, int first, int second) {
        return (float) Math.hypot(event.getX(second) - event.getX(first), event.getY(second) - event.getY(first));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void lerpRect(@NonNull RectF from, @NonNull RectF to, float progress, @NonNull RectF out) {
        out.set(
                from.left + (to.left - from.left) * progress,
                from.top + (to.top - from.top) * progress,
                from.right + (to.right - from.right) * progress,
                from.bottom + (to.bottom - from.bottom) * progress
        );
    }

    private static void getSourceTransitionRect(@NonNull Bitmap target, @NonNull PlaceProviderObject object, @NonNull RectF out) {
        if (object.imageDrawRect != null
                && object.imageDrawRect.width() > 0f
                && object.imageDrawRect.height() > 0f) {
            out.set(object.imageDrawRect);
            return;
        }
        out.set(object.viewRect);
        // Compose AsyncImage typically renders with ContentScale.Crop, where
        // the bitmap fills the entire viewRect and the part overflowing the
        // bounds is cropped. Aspect-fitting the bitmap inside viewRect (the
        // previous fallback) would shrink the morph target to a smaller-than-
        // source rect, causing the close animation to land "smaller than the
        // thumbnail" and visibly snap back when the source view re-renders.
        // For ContentScale.Fit sources the provider must supply imageDrawRect
        // explicitly (the early-return branch above handles that case).
    }

    @Nullable
    private static Bitmap createThumbnail(@Nullable Bitmap source) {
        if (source == null || source.isRecycled() || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        int maxSide = Math.max(80, AndroidUtilities.dp(96));
        int longestSide = Math.max(source.getWidth(), source.getHeight());
        if (longestSide <= maxSide) {
            return source;
        }
        float scale = maxSide / (float) longestSide;
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    private void setParentScaleView(@Nullable View view) {
        if (parentScaleView == view) {
            return;
        }
        resetParentScale();
        parentScaleView = view;
        parentScaleCaptured = false;
    }

    private void updateParentScale() {
        View view = parentScaleView;
        if (view == null || windowView.getParent() == null) {
            return;
        }
        if (!parentScaleCaptured) {
            parentScaleStartX = view.getScaleX();
            parentScaleStartY = view.getScaleY();
            parentScaleCaptured = true;
        }
        // Bug F fix: read dragProgress directly from translationY here so the
        // formula stays continuous across the drag→close transition. During drag
        // the user pulls translationY down, dragProgress grows toward 1, and zoom
        // shrinks toward 1.0. When closePhoto fires, translationY is preserved
        // (the morph view's xPos/yPos already account for it), so dragProgress
        // remains the same on the first close frame and zoom stays put — no
        // discontinuity. As openProgress animates 1→0 during close, zoom fades
        // smoothly to 1.0 regardless of the preserved drag amount.
        float dragProgress = scale == MIN_SCALE && getContainerViewHeight() > 0
                ? clamp(Math.abs(translationY) / (getContainerViewHeight() / 2f), 0f, 1f)
                : 0f;
        float zoom = 1f + (1f - dragProgress) * PARENT_ZOOM_SCALE * openProgress;
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(view.getHeight() / 2f);
        view.setScaleX(parentScaleStartX * zoom);
        view.setScaleY(parentScaleStartY * zoom);
    }

    private void resetParentScale() {
        View view = parentScaleView;
        if (view != null && parentScaleCaptured) {
            view.setScaleX(parentScaleStartX);
            view.setScaleY(parentScaleStartY);
        }
        parentScaleCaptured = false;
    }

    @Nullable
    private static Activity findActivity(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onUp(MotionEvent e) {
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    public void onLongPress() {
        if (centerBitmap() == null || dismissing || openProgress < 1f || zooming || moving || draggingDown) {
            return;
        }
        discardTap = true;
        showLongPressMenu();
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (scale != MIN_SCALE) {
            scroller.abortAnimation();
            scroller.fling(
                    Math.round(translationX),
                    Math.round(translationY),
                    Math.round(velocityX),
                    Math.round(velocityY),
                    Math.round(minX),
                    Math.round(maxX),
                    Math.round(minY),
                    Math.round(maxY)
            );
            invalidateViewer();
        }
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (discardTap) {
            return false;
        }
        if (scale > MIN_SCALE) {
            animateTo(MIN_SCALE, 0, 0, true);
        } else {
            toggleActionBar();
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (scale == MIN_SCALE && (translationY != 0f || translationX != 0f)) {
            return false;
        }
        if (imageMoveAnimating || dismissing) {
            return false;
        }
        if (scale == MIN_SCALE) {
            float targetX = (e.getX() - getContainerViewWidth() / 2f) - ((e.getX() - getContainerViewWidth() / 2f) - translationX) * (MAX_SCALE / scale);
            float targetY = (e.getY() - getContainerViewHeight() / 2f) - ((e.getY() - getContainerViewHeight() / 2f) - translationY) * (MAX_SCALE / scale);
            updateMinMax(MAX_SCALE);
            targetX = clamp(targetX, minX, maxX);
            targetY = clamp(targetY, minY, maxY);
            animateTo(MAX_SCALE, targetX, targetY, true);
        } else {
            animateTo(MIN_SCALE, 0, 0, true);
        }
        doubleTap = true;
        discardTap = true;
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private void updateBackgroundAlphaForCurrentState() {
        if (imageMoveAnimating) {
            return;
        }
        float dragProgress = scale == MIN_SCALE && getContainerViewHeight() > 0
                ? clamp(Math.abs(translationY) / (getContainerViewHeight() / 2f), 0f, 1f)
                : 0f;
        int alpha = (int) (255 * (1f - dragProgress));
        backgroundDrawable.setAlpha((int) clamp(alpha * openProgress, 0f, 255f));
    }

    private final class FrameLayoutDrawer extends SizeNotifierFrameLayout {
        private final Paint paint = new Paint();
        private boolean ignoreLayout;

        FrameLayoutDrawer(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            paint.setColor(0x33000000);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            if (getLayoutParams() != null && getLayoutParams().height > 0) {
                heightSize = getLayoutParams().height;
            }
            widthSize -= getPaddingLeft() + getPaddingRight();
            heightSize -= getPaddingBottom();
            setMeasuredDimension(widthSize, heightSize);
            ignoreLayout = true;
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }
            ignoreLayout = false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                int l = left + getPaddingLeft();
                int r = right - getPaddingRight();
                int b = bottom - getPaddingBottom();
                int width = child.getMeasuredWidth();
                int height = child.getMeasuredHeight();
                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = Gravity.TOP | Gravity.LEFT;
                }
                int childLeft;
                int childTop;
                int horizontalGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
                if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
                    childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                } else if (horizontalGravity == Gravity.RIGHT) {
                    childLeft = r - l - width - lp.rightMargin;
                } else {
                    childLeft = lp.leftMargin;
                }
                if (verticalGravity == Gravity.CENTER_VERTICAL) {
                    childTop = (b - top - height) / 2 + lp.topMargin - lp.bottomMargin;
                } else if (verticalGravity == Gravity.BOTTOM) {
                    childTop = b - top - height - lp.bottomMargin;
                } else {
                    childTop = lp.topMargin;
                }
                child.layout(childLeft + l, childTop, childLeft + width + l, childTop + height);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            updateBackgroundAlphaForCurrentState();
            PhotoViewer.this.onDraw(canvas);
            if (AndroidUtilities.statusBarHeight != 0 && actionBarContainer != null) {
                paint.setAlpha((int) (255 * actionBarContainer.getAlpha() * 0.498f));
                if (paint.getAlpha() > 0) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.statusBarHeight, paint);
                }
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child == photoTouchView) {
                return true;
            }
            try {
                return super.drawChild(canvas, child, drawingTime);
            } catch (Throwable ignored) {
                return true;
            }
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }
    }

    private final class PhotoTouchView extends View {

        PhotoTouchView(@NonNull Context context) {
            super(context);
            setClickable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return PhotoViewer.this.onTouchEvent(event);
        }
    }

    private static final class MoreDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MoreDrawable() {
            paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float radius = AndroidUtilities.dp(2);
            canvas.drawCircle(cx, cy - AndroidUtilities.dp(7), radius, paint);
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.drawCircle(cx, cy + AndroidUtilities.dp(7), radius, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class SaveDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SaveDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float cx = bounds.centerX();
            float top = bounds.centerY() - AndroidUtilities.dp(8);
            float bottom = bounds.centerY() + AndroidUtilities.dp(8);
            canvas.drawLine(cx, top, cx, bottom - AndroidUtilities.dp(5), paint);
            canvas.drawLine(cx - AndroidUtilities.dp(5), bounds.centerY() + AndroidUtilities.dp(3), cx, bottom - AndroidUtilities.dp(2), paint);
            canvas.drawLine(cx + AndroidUtilities.dp(5), bounds.centerY() + AndroidUtilities.dp(3), cx, bottom - AndroidUtilities.dp(2), paint);
            canvas.drawLine(cx - AndroidUtilities.dp(8), bottom, cx + AndroidUtilities.dp(8), bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class ShareDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ShareDrawable() {
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float leftX = cx - AndroidUtilities.dp(7);
            float rightX = cx + AndroidUtilities.dp(7);
            float topY = cy - AndroidUtilities.dp(7);
            float bottomY = cy + AndroidUtilities.dp(7);
            canvas.drawLine(leftX, cy, rightX, topY, paint);
            canvas.drawLine(leftX, cy, rightX, bottomY, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(leftX, cy, AndroidUtilities.dp(2.2f), paint);
            canvas.drawCircle(rightX, topY, AndroidUtilities.dp(2.2f), paint);
            canvas.drawCircle(rightX, bottomY, AndroidUtilities.dp(2.2f), paint);
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class StaticPlaceProvider extends EmptyPhotoViewerProvider {
        private final PlaceProviderObject object;

        private StaticPlaceProvider(@NonNull PlaceProviderObject object) {
            this.object = object;
        }

        @Nullable
        @Override
        public PlaceProviderObject getPlaceForPhoto(int index, @NonNull PhotoEntry entry, boolean needPreview, boolean closing) {
            return object;
        }
    }

    private static final class AnimatedCounterView extends View implements Drawable.Callback {
        private final AnimatedTextDrawable textDrawable = new AnimatedTextDrawable(false, true, true);
        private CharSequence text = "";

        AnimatedCounterView(@NonNull Context context) {
            super(context);
            textDrawable.setCallback(this);
            textDrawable.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            textDrawable.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        }

        void setTextColor(int color) {
            textDrawable.setTextColor(color);
        }

        void setTextSize(float sp) {
            textDrawable.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    sp,
                    getResources().getDisplayMetrics()
            ));
        }

        void setText(@Nullable CharSequence value, boolean animated, boolean moveDown) {
            text = value == null ? "" : value;
            textDrawable.setText(text, animated, moveDown);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            textDrawable.setBounds(0, 0, w, h);
            textDrawable.setOverrideFullWidth(w);
            textDrawable.setText(text, false);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            textDrawable.draw(canvas);
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidate();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            postDelayed(what, Math.max(0, when - android.os.SystemClock.uptimeMillis()));
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            removeCallbacks(what);
        }
    }

    // PORT-MODE: Exact Component Port
    // UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java#BackgroundDrawable (PhotoViewer.java:2191-2272)
    // UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
    // EXACT-SUBSYSTEMS: custom Canvas drawing (4-rect cutout around animatingImageView),
    //                   first-frame source-hide synchronization via drawRunnable.
    // HOST-WIRING:
    //   - Cutout activation predicate uses our `imageMoveAnimating` field plus
    //     `animatingImageView != null`, replacing upstream's
    //     `animationInProgress != 0 && !AndroidUtilities.isTablet() && currentPlaceObject != null`.
    //   - drawRunnable is posted via View.post(...) instead of
    //     AndroidUtilities.runOnUIThread(...) (we are already on the UI thread and
    //     this defers the runnable to after the current draw, matching upstream
    //     semantics).
    // ADAPTED-BACKEND:
    //   - None.
    // OMITTED:
    //   - LaunchActivity.drawerLayoutContainer.setAllowDrawContent gating: NativeDo
    //     does not have a drawer layout under PhotoViewer.
    //   - parentAlert.setAllowDrawContent gating: chat-attach UI not in scope.
    //   - textureViewSkipRender short-circuit: video playback not in scope yet.
    //   - !AndroidUtilities.isTablet() guard: tablet split-mode for PhotoViewer is
    //     not implemented; cutout is always active on the morph path.
    private final class BackgroundDrawable extends ColorDrawable {

        private final RectF rect = new RectF();
        private final RectF visibleRect = new RectF();
        private final Paint paint;

        @Nullable private Runnable drawRunnable;

        BackgroundDrawable(int color) {
            super(color);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
        }

        @Keep
        @Override
        public void setAlpha(int alpha) {
            super.setAlpha(alpha);
            paint.setAlpha(alpha);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (imageMoveAnimating && animatingImageView != null) {
                animatingImageView.getClippedVisibleRect(visibleRect);
                if (!visibleRect.isEmpty()) {
                    visibleRect.inset(AndroidUtilities.dp(1), AndroidUtilities.dp(1));
                    final Rect boundsRect = getBounds();
                    final float width = boundsRect.right;
                    final float height = boundsRect.bottom;
                    for (int i = 0; i < 4; i++) {
                        switch (i) {
                            case 0: // left strip
                                rect.set(0, visibleRect.top, visibleRect.left, visibleRect.bottom);
                                break;
                            case 1: // top strip
                                rect.set(0, 0, width, visibleRect.top);
                                break;
                            case 2: // right strip
                                rect.set(visibleRect.right, visibleRect.top, width, visibleRect.bottom);
                                break;
                            case 3: // bottom strip
                                rect.set(0, visibleRect.bottom, width, height);
                                break;
                        }
                        canvas.drawRect(rect, paint);
                    }
                }
            } else {
                super.draw(canvas);
            }
            if (getAlpha() != 0) {
                if (drawRunnable != null) {
                    final Runnable r = drawRunnable;
                    drawRunnable = null;
                    windowView.post(r);
                }
            }
        }
    }
}

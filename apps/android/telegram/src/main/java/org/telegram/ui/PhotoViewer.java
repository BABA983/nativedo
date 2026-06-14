/*
 * This file is part of Telegram for Android.
 *
 * Copyright (C) 2013-2026 Telegram FZ-LLC
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui;

import android.app.Activity;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.OverScroller;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GestureDetector2;
import org.telegram.ui.Components.GroupedPhotosListView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.SizeNotifierFrameLayoutPhoto;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PhotoViewer {
    private static final PhotoViewer INSTANCE = new PhotoViewer();
    private static final int GALLERY_MENU_SAVE = 2;
    private static final int GALLERY_MENU_SHARE = 9;
    private static final int GALLERY_MENU_OPEN_IN = 15;
    private static final int GALLERY_MENU_COPY = 17;
    private static final int GALLERY_MENU_CANCEL_LOADING = 8;
    private static final int PROGRESS_NONE = -1;
    private static final int PROGRESS_EMPTY = 0;
    private static final int PROGRESS_CANCEL = 1;
    private static final int PROGRESS_LOAD = 2;
    private static final int PROGRESS_PLAY = 3;
    private static final int PROGRESS_PAUSE = 4;
    private static final float ZOOM_SCALE = 0.1f;
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 3f;
    private static final int PROGRESS_CENTER_INDEX = 0;
    private static final int PROGRESS_RIGHT_INDEX = 1;
    private static final int PROGRESS_LEFT_INDEX = 2;
    private static Drawable[] progressDrawables;

    private static boolean isHttpUrl(String url) {
        return !TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String httpProgressKey(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        String key = Utilities.MD5(url);
        if (TextUtils.isEmpty(key)) {
            return url;
        }
        return key + "." + ImageLoader.getHttpUrlExtension(url, "jpg");
    }

    private static String defaultProgressKey(String url) {
        return isHttpUrl(url) ? httpProgressKey(url) : url;
    }

    private static ImageLocation imageLocationForPath(String path) {
        return TextUtils.isEmpty(path) ? null : ImageLocation.getForPath(path);
    }

    private static File cacheFileForUrl(String url, String progressKey) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        if (isHttpUrl(url)) {
            File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            if (cacheDir == null) {
                return null;
            }
            return new File(cacheDir, TextUtils.isEmpty(progressKey) ? httpProgressKey(url) : progressKey);
        }
        if (url.startsWith("file://")) {
            String path = Uri.parse(url).getPath();
            return TextUtils.isEmpty(path) ? null : new File(path);
        }
        if (url.startsWith("/")) {
            return new File(url);
        }
        return null;
    }

    private BaseFragment parentFragment;
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayoutDrawer containerView;
    public FrameLayout windowView;
    private ClippingImageView animatingImageView;
    private View navigationBar;
    private int navigationBarHeight;
    private final Rect insets = new Rect();
    private BackgroundDrawable backgroundDrawable = new BackgroundDrawable(Color.BLACK);
    private EmptyPhotoViewerProvider placeProvider;
    private PlaceProviderObject currentPlaceObject;
    private PlaceProviderObject showAfterAnimation;
    private PlaceProviderObject hideAfterAnimation;
    private ImageReceiver.BitmapHolder currentThumb;
    private PageBlocksAdapter pageBlocksAdapter;
    private ArrayList<String> imageUrls = new ArrayList<>();
    private ArrayList<UrlPhotoItem> photoItems = new ArrayList<>();
    private int currentIndex;
    private boolean isVisible;
    private boolean isVisibleOrAnimating;
    private boolean closingPhoto;
    private boolean inBubbleMode;
    private float clippingImageProgress;
    private int animationInProgress;
    private boolean openAnimationInProgress;
    private long transitionAnimationStartTime;
    private Runnable animationEndRunnable;
    private org.telegram.messenger.AnimationNotificationsLocker transitionNotificationLocker = new org.telegram.messenger.AnimationNotificationsLocker(new int[]{});
    private ActionBar actionBar;
    private ActionBarMenuItem menuItem;
    private PhotoViewerActionBarContainer actionBarContainer;
    private PhotoCountView countView;
    private ImageReceiver leftImage;
    private ImageReceiver centerImage;
    private ImageReceiver rightImage;
    private ImageReceiver[] imageReceivers;
    private ImageReceiver sideImage;
    private final NotificationCenter.NotificationCenterDelegate progressObserver = this::didReceiveFileLoadNotification;
    private final GradientDrawable[] pressedDrawable = new GradientDrawable[2];
    private final boolean[] drawPressedDrawable = new boolean[2];
    private final float[] pressedDrawableAlpha = new float[2];
    private final PhotoProgressView[] photoProgressViews = new PhotoProgressView[3];
    private final PhotoProgressView[] photoProgressViewsRaw = new PhotoProgressView[3];
    private final boolean[] photoProgressLoaded = new boolean[3];
    private FrameLayout bottomLayout;
    private CaptionScrollView captionScrollView;
    private CaptionTextViewSwitcher captionTextViewSwitcher;
    private FrameLayout captionContainer;
    private boolean captionVisible;
    private boolean didSetCaption;
    private boolean isCurrentVideo;
    private boolean needCaptionLayout;
    private boolean dontChangeCaptionPosition;
    private int keyboardSize;
    private GroupedPhotosListView groupedPhotosListView;
    private final ArrayList<ImageLocation> groupedPhotoLocations = new ArrayList<>();
    private final ArrayList<TLRPC.PageBlock> groupedPageBlocks = new ArrayList<>();
    private final RectF centerImageRect = new RectF();
    private final RectF leftImageRect = new RectF();
    private final RectF rightImageRect = new RectF();
    private final RectF targetImageRect = new RectF();
    private final float[][] animationValues = new float[2][13];
    private final String[] currentFileNames = new String[3];
    private int touchSlop;
    private int minimumVelocity;
    private int maximumVelocity;
    private AnimatorSet openAnimator;
    private AnimatorSet closeAnimator;
    private AnimatorSet actionBarAnimator;
    private AnimatorSet imageMoveAnimation;
    private OverScroller scroller;
    private VelocityTracker velocityTracker;
    private boolean closeToPlace;
    private float scale = MIN_SCALE;
    private float translationX;
    private float translationY;
    private float animateToScale = MIN_SCALE;
    private float animateToX;
    private float animateToY;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private float animationValue;
    private long animationStartTime;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;
    private boolean pendingOpenAnimation;
    private boolean closing;
    private float dragY;
    private float moveStartX;
    private float moveStartY;
    private float pinchStartDistance;
    private float pinchStartScale = MIN_SCALE;
    private float pinchCenterX;
    private float pinchCenterY;
    private float pinchStartX;
    private float pinchStartY;
    private boolean moving;
    private boolean draggingDown;
    private boolean zooming;
    private boolean doubleTap;
    private boolean invalidCoords;
    private boolean canDragDown = true;
    private boolean discardTap;
    private boolean zoomAnimation;
    private boolean isActionBarVisible = true;
    private boolean actionBarAnimating;
    private boolean centerImageLoaded;
    private boolean fancyShadows;
    private float chromeAlpha = 1f;
    private int switchImageAfterAnimation;
    private long lastDrawTime;
    private boolean pendingSaveAfterDownload;
    private GestureDetector2 gestureDetector;
    private boolean changingPage;
    private boolean attachedToWindow;
    private OnBackInvokedCallback onBackInvokedCallback;

    @Keep
    public void setAnimationValue(float value) {
        animationValue = value;
        containerView.invalidate();
    }

    @Keep
    public float getAnimationValue() {
        return animationValue;
    }

    public static PhotoViewer getInstance() {
        return INSTANCE;
    }

    public static boolean hasInstance() {
        return INSTANCE.windowView != null;
    }

    public boolean isVisibleOrAnimating() {
        return isVisibleOrAnimating;
    }

    public void setParentActivity(BaseFragment fragment) {
        parentFragment = fragment;
        Activity activity = fragment == null ? null : fragment.getParentActivity();
        if (parentActivity == activity && windowView != null) {
            return;
        }
        parentActivity = activity;
        if (activity == null) {
            return;
        }
        int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        backgroundDrawable = new BackgroundDrawable(Color.BLACK);
        backgroundDrawable.setAlpha(0);
        windowView = new FrameLayout(activity) {
            private final Paint systemBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return isVisible && super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return isVisible && PhotoViewer.this.onTouchEvent(event);
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (handleBackKeyEvent(event)) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (handleBackKeyEvent(event)) {
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                int bottomInsets = insets.bottom;
                heightSize -= bottomInsets;
                widthSize -= getPaddingLeft() + getPaddingRight();
                heightSize -= getPaddingBottom();
                setMeasuredDimension(widthSize, heightSize);
                ViewGroup.LayoutParams layoutParams = animatingImageView.getLayoutParams();
                animatingImageView.measure(MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.AT_MOST));
                containerView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
                int navHeight = navigationBarHeight;
                if (navHeight <= 0) {
                    navHeight = AndroidUtilities.navigationBarHeight;
                }
                navigationBar.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(0, navHeight), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                animatingImageView.layout(getPaddingLeft(), 0, getPaddingLeft() + animatingImageView.getMeasuredWidth(), animatingImageView.getMeasuredHeight());
                containerView.layout(getPaddingLeft(), 0, getPaddingLeft() + containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                navigationBar.layout(getPaddingLeft(), containerView.getMeasuredHeight(), navigationBar.getMeasuredWidth(), containerView.getMeasuredHeight() + navigationBar.getMeasuredHeight());
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (!isVisible || insets.bottom <= 0) {
                    return;
                }
                systemBarPaint.setColor(Color.BLACK);
                systemBarPaint.setAlpha(backgroundDrawable.getAlpha());
                canvas.drawRect(0, getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight() + insets.bottom, systemBarPaint);
            }

            private boolean handleBackKeyEvent(KeyEvent event) {
                if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    closePhoto(true, false);
                }
                return event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP;
            }
        };
        windowView.setBackground(backgroundDrawable);
        windowView.setFocusable(false);
        windowView.setWillNotDraw(false);
        windowView.setClipChildren(false);
        windowView.setClipToPadding(false);
        animatingImageView = new ClippingImageView(activity);
        animatingImageView.setAnimationValues(new float[2][13], false, false);
        animatingImageView.setVisibility(View.GONE);
        navigationBar = new View(activity);
        navigationBarHeight = AndroidUtilities.navigationBarHeight;
        navigationBar.setBackgroundColor(0x7f000000);
        navigationBar.setAlpha(0f);
        containerView = new FrameLayoutDrawer(activity, activity);
        containerView.setSystemUiVisibility(uiFlags);
        containerView.setFocusable(false);
        containerView.setClipChildren(true);
        containerView.setClipToPadding(true);
        containerView.setFitsSystemWindows(true);
        containerView.setOnApplyWindowInsetsListener((view, newInsets) -> {
            Rect oldInsets = new Rect(insets);
            insets.set(newInsets.getStableInsetLeft(), newInsets.getStableInsetTop(), newInsets.getStableInsetRight(), newInsets.getStableInsetBottom());
            navigationBarHeight = insets.bottom;
            ViewGroup.LayoutParams navigationParams = navigationBar.getLayoutParams();
            if (navigationParams != null) {
                boolean changed = navigationParams.height != navigationBarHeight;
                navigationParams.height = navigationBarHeight;
                if (navigationParams instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) navigationParams;
                    int bottomMargin = -navigationBarHeight / 2;
                    if (marginParams.bottomMargin != bottomMargin) {
                        marginParams.bottomMargin = bottomMargin;
                        changed = true;
                    }
                }
                if (changed) {
                    navigationBar.setLayoutParams(navigationParams);
                }
            }
            containerView.setPadding(newInsets.getSystemWindowInsetLeft(), 0, newInsets.getSystemWindowInsetRight(), 0);
            if (!oldInsets.equals(insets)) {
                if (windowView != null) {
                    windowView.requestLayout();
                }
                updateImageBounds();
                containerView.invalidate();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return WindowInsets.CONSUMED;
            }
            return newInsets.consumeSystemWindowInsets();
        });
        setupPhotoViewerViews(activity);
        windowView.addView(animatingImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT));
        windowView.addView(containerView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        windowView.addView(navigationBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, navigationBarHeight / AndroidUtilities.density, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            windowLayoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void setupPhotoViewerViews(Activity activity) {
        containerView.setWithoutWindow(true);
        leftImage = new ImageReceiver(containerView);
        centerImage = new ImageReceiver(containerView);
        rightImage = new ImageReceiver(containerView);
        imageReceivers = new ImageReceiver[]{leftImage, centerImage, rightImage};
        for (ImageReceiver receiver : imageReceivers) {
            receiver.setCrossfadeAlpha((byte) 2);
            receiver.setInvalidateAll(true);
        }
        pressedDrawable[0] = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x32000000, 0x00000000});
        pressedDrawable[0].setShape(GradientDrawable.RECTANGLE);
        pressedDrawable[1] = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0x32000000, 0x00000000});
        pressedDrawable[1].setShape(GradientDrawable.RECTANGLE);
        ImageReceiver.ImageReceiverDelegate imageReceiverDelegate = (imageReceiver, set, thumb, memCache) -> {
            int progressIndex = progressIndexForReceiver(imageReceiver);
            if (progressIndex >= 0) {
                photoProgressLoaded[progressIndex] = set && !thumb;
                if (imageReceiver == centerImage) {
                    centerImageLoaded = photoProgressLoaded[progressIndex];
                }
                updateProgressVisibility(progressIndex);
            }
            updateImageBounds();
            containerView.invalidate();
        };
        centerImage.setDelegate(imageReceiverDelegate);
        leftImage.setDelegate(imageReceiverDelegate);
        rightImage.setDelegate(imageReceiverDelegate);
        ViewConfiguration configuration = ViewConfiguration.get(activity);
        touchSlop = configuration.getScaledTouchSlop();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        scroller = new OverScroller(activity);
        gestureDetector = new GestureDetector2(activity, new GestureDetector2.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (!doubleTap && !drawPressedDrawable[0] && !drawPressedDrawable[1]) {
                    float x = e.getX();
                    int side = Math.min(135, getContainerViewWidth() / 8);
                    if (x < side) {
                        if (hasLeftImage()) {
                            drawPressedDrawable[0] = true;
                            containerView.invalidate();
                        }
                    } else if (x > getContainerViewWidth() - side) {
                        if (hasRightImage()) {
                            drawPressedDrawable[1] = true;
                            containerView.invalidate();
                        }
                    }
                }
                return false;
            }

            @Override
            public void onUp(MotionEvent e) {
                hidePressedDrawables();
            }

            @Override
            public void onShowPress(MotionEvent e) {
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (discardTap) {
                    return false;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                containerView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (scale != MIN_SCALE) {
                    scroller.abortAnimation();
                    scroller.fling(
                            (int) translationX,
                            (int) translationY,
                            (int) velocityX,
                            (int) velocityY,
                            (int) minX,
                            (int) maxX,
                            (int) minY,
                            (int) maxY
                    );
                    containerView.postInvalidateOnAnimation();
                }
                return false;
            }
        });
        gestureDetector.setOnDoubleTapListener(new GestureDetector2.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (discardTap) {
                    return false;
                }
                if (closing || openAnimationInProgress || moving || draggingDown) {
                    return true;
                }
                if (isPointInsideVisibleChrome(e.getY())) {
                    return true;
                }
                PhotoProgressView progressView = photoProgressViews[PROGRESS_CENTER_INDEX];
                if (progressView != null && progressView.backgroundState != PROGRESS_NONE) {
                    float dx = getContainerViewWidth() / 2f - e.getX();
                    float dy = getContainerViewHeight() / 2f - e.getY();
                    if (Math.sqrt(dx * dx + dy * dy) < AndroidUtilities.dp(50f)) {
                        if (progressView.backgroundState == PROGRESS_EMPTY) {
                            saveCurrentPhoto();
                        } else if (progressView.backgroundState == PROGRESS_CANCEL) {
                            cancelCurrentLoading();
                        }
                        return true;
                    }
                }
                int edgeSide = Math.min(135, getContainerViewWidth() / 8);
                if (imageUrls.size() > 1 && e.getY() > ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(40f)) {
                    if (e.getX() < edgeSide && hasLeftImage()) {
                        switchToNextIndex(-1, false);
                        return true;
                    } else if (e.getX() > getContainerViewWidth() - edgeSide && hasRightImage()) {
                        switchToNextIndex(1, false);
                        return true;
                    }
                }
                toggleActionBar(!isActionBarVisible, true);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (closing || openAnimationInProgress || moving || draggingDown || zooming) {
                    return true;
                }
                if (scale == MIN_SCALE && (translationY != 0f || translationX != 0f)) {
                    return false;
                }
                if (animationStartTime != 0 || imageMoveAnimation != null) {
                    return false;
                }
                PhotoProgressView progressView = photoProgressViews[PROGRESS_CENTER_INDEX];
                if (progressView != null && progressView.backgroundState != PROGRESS_NONE) {
                    float dx = getContainerViewWidth() / 2f - e.getX();
                    float dy = getContainerViewHeight() / 2f - e.getY();
                    if (Math.sqrt(dx * dx + dy * dy) < AndroidUtilities.dp(40f)) {
                        return false;
                    }
                }
                if (scale == MIN_SCALE) {
                    float atx = (e.getX() - getContainerViewWidth() / 2f) - ((e.getX() - getContainerViewWidth() / 2f) - translationX) * (MAX_SCALE / scale);
                    float aty = (e.getY() - getContainerViewHeight() / 2f) - ((e.getY() - getContainerViewHeight() / 2f) - translationY) * (MAX_SCALE / scale);
                    updateMinMax(MAX_SCALE);
                    if (atx < minX) {
                        atx = minX;
                    } else if (atx > maxX) {
                        atx = maxX;
                    }
                    if (aty < minY) {
                        aty = minY;
                    } else if (aty > maxY) {
                        aty = maxY;
                    }
                    animateTo(MAX_SCALE, atx, aty, true);
                } else {
                    animateTo(MIN_SCALE, 0f, 0f, true);
                }
                doubleTap = true;
                hidePressedDrawables();
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }

            @Override
            public boolean canDoubleTap(MotionEvent e) {
                if (!drawPressedDrawable[0] && !drawPressedDrawable[1]) {
                    float x = e.getX();
                    int side = Math.min(135, getContainerViewWidth() / 8);
                    if (x < side || x > getContainerViewWidth() - side) {
                        return imageUrls.size() <= 1;
                    }
                }
                return true;
            }
        });

        actionBar = new ActionBar(activity);
        actionBar.setOverlayTitleAnimation(true);
        actionBar.setOccupyStatusBar(true);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setTitle("");
        actionBar.setTitleColor(Color.WHITE);
        actionBar.setSubtitleColor(Color.WHITE);
        actionBar.setItemsColor(Color.WHITE, false);
        actionBar.setItemsBackgroundColor(0x30ffffff, false);
        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setColor(Color.WHITE);
        backDrawable.setRotatedColor(Color.WHITE);
        actionBar.setBackButtonDrawable(backDrawable);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    PhotoViewer.this.closePhoto(true, false);
                } else if (id == GALLERY_MENU_SAVE) {
                    saveCurrentPhoto();
                } else if (id == GALLERY_MENU_SHARE) {
                    shareCurrentUrl();
                } else if (id == GALLERY_MENU_COPY) {
                    copyCurrentUrl();
                } else if (id == GALLERY_MENU_OPEN_IN) {
                    openCurrentUrlExternally();
                } else if (id == GALLERY_MENU_CANCEL_LOADING) {
                    cancelCurrentLoading();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(containerView.getContext().getString(R.string.AccDescrMoreOptions));
        menuItem.addSubItem(GALLERY_MENU_SAVE, containerView.getResources().getDrawable(R.drawable.msg_gallery), containerView.getContext().getString(R.string.SaveToGallery)).setColors(Color.WHITE, Color.WHITE);
        menuItem.addSubItem(GALLERY_MENU_SHARE, containerView.getResources().getDrawable(R.drawable.msg_share), containerView.getContext().getString(R.string.ShareFile)).setColors(Color.WHITE, Color.WHITE);
        menuItem.addSubItem(GALLERY_MENU_COPY, containerView.getResources().getDrawable(R.drawable.msg_copy), containerView.getContext().getString(R.string.CopyLink)).setColors(Color.WHITE, Color.WHITE);
        menuItem.addSubItem(GALLERY_MENU_OPEN_IN, containerView.getResources().getDrawable(R.drawable.msg_openin), containerView.getContext().getString(R.string.OpenInExternalApp)).setColors(Color.WHITE, Color.WHITE);
        menuItem.setPopupItemsSelectorColor(0x0fffffff);
        menuItem.redrawPopup(0xf9222222);
        actionBarContainer = new PhotoViewerActionBarContainer(activity);
        actionBar.addView(actionBarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        actionBarContainer.updateRightPadding(AndroidUtilities.dp(64f), false);

        countView = new PhotoCountView(activity);
        containerView.addView(countView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        ensureProgressDrawables(activity);
        for (int i = 0; i < photoProgressViews.length; i++) {
            photoProgressViews[i] = new PhotoProgressView(containerView);
            photoProgressViewsRaw[i] = photoProgressViews[i];
            photoProgressViews[i].setBackgroundState(PROGRESS_NONE, false, true);
        }
        NotificationCenter notificationCenter = NotificationCenter.getInstance(0);
        notificationCenter.addObserver(progressObserver, NotificationCenter.fileLoadProgressChanged);
        notificationCenter.addObserver(progressObserver, NotificationCenter.fileLoaded);
        notificationCenter.addObserver(progressObserver, NotificationCenter.fileLoadFailed);

        bottomLayout = new FrameLayout(activity);
        bottomLayout.setBackgroundColor(0x7f000000);
        containerView.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        captionContainer = new FrameLayout(activity);
        captionContainer.setClipChildren(false);
        captionTextViewSwitcher = new CaptionTextViewSwitcher(activity);
        captionTextViewSwitcher.setFactory(() -> new CaptionTextView(activity));
        AlphaAnimation in = new AlphaAnimation(0f, 1f);
        in.setDuration(200);
        captionTextViewSwitcher.setInAnimation(in);
        AlphaAnimation out = new AlphaAnimation(1f, 0f);
        out.setDuration(200);
        captionTextViewSwitcher.setOutAnimation(out);
        captionTextViewSwitcher.setContainer(captionContainer);
        captionScrollView = new CaptionScrollView(activity, captionTextViewSwitcher, captionContainer);
        captionTextViewSwitcher.setScrollView(captionScrollView);
        captionContainer.addView(captionTextViewSwitcher, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        captionScrollView.addView(captionContainer, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        captionScrollView.setVisibility(View.INVISIBLE);
        containerView.addView(captionScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));

        groupedPhotosListView = new GroupedPhotosListView(activity, AndroidUtilities.dp(10));
        groupedPhotosListView.setDelegate(new GroupedPhotosListView.GroupedPhotosListViewDelegate() {
            @Override
            public int getCurrentIndex() {
                return currentIndex;
            }

            @Override
            public int getCurrentAccount() {
                return 0;
            }

            @Override
            public long getAvatarsDialogId() {
                return 0;
            }

            @Override
            public int getSlideshowMessageId() {
                return 0;
            }

            @Override
            public ArrayList<ImageLocation> getImagesArrLocations() {
                if (shouldUseGroupedPageBlocks()) {
                    return null;
                }
                return imageUrls.size() > 1 ? groupedPhotoLocations : null;
            }

            @Override
            public ArrayList<MessageObject> getImagesArr() {
                return null;
            }

            @Override
            public List<TLRPC.PageBlock> getPageBlockArr() {
                return shouldUseGroupedPageBlocks() ? groupedPageBlocks : null;
            }

            @Override
            public Object getParentObject() {
                UrlPhotoItem item = currentPhotoItem();
                return item != null && item.parentObject != null ? item.parentObject : "photoviewer_urls";
            }

            @Override
            public void setCurrentIndex(int index) {
                if (index < 0 || index >= imageUrls.size() || index == currentIndex) {
                    return;
                }
                setImageIndex(index, false, true);
            }

            @Override
            public void onShowAnimationStart() {
                containerView.requestLayout();
            }

            @Override
            public void onStopScrolling() {
            }

            @Override
            public boolean validGroupId(long groupId) {
                return false;
            }

            @Override
            public boolean forceAll() {
                return true;
            }
        });
        containerView.addView(groupedPhotosListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));
    }

    public boolean openPhoto(int index, PageBlocksAdapter adapter, EmptyPhotoViewerProvider placeProvider) {
        if (adapter == null || adapter.getItemsCount() <= 0) {
            return false;
        }
        ArrayList<UrlPhotoItem> items = new ArrayList<>();
        int selectedIndex = -1;
        int skippedBeforeSelected = 0;
        for (int i = 0; i < adapter.getItemsCount(); i++) {
            UrlPhotoItem item = photoItemForPageBlock(adapter, i);
            if (item != null) {
                if (i == index) {
                    selectedIndex = items.size();
                }
                items.add(item);
            } else if (i < index) {
                skippedBeforeSelected++;
            }
        }
        if (items.isEmpty()) {
            return false;
        }
        if (selectedIndex < 0) {
            selectedIndex = Math.max(0, Math.min(index - skippedBeforeSelected, items.size() - 1));
        }
        return openPhotoItems(items, selectedIndex, placeProvider, adapter);
    }

    public boolean openPhoto(String url, List<String> urls, EmptyPhotoViewerProvider placeProvider) {
        ArrayList<String> normalized = new ArrayList<>();
        if (urls != null) {
            for (String item : urls) {
                if (!TextUtils.isEmpty(item) && !normalized.contains(item)) {
                    normalized.add(item);
                }
            }
        }
        if (!TextUtils.isEmpty(url) && !normalized.contains(url)) {
            normalized.add(0, url);
        }
        int index = Math.max(0, normalized.indexOf(url));
        return openPhotoUrls(normalized, index, placeProvider);
    }

    public boolean openPhotoUrls(List<String> urls, int index, EmptyPhotoViewerProvider placeProvider) {
        if (urls == null) {
            return false;
        }
        ArrayList<UrlPhotoItem> items = new ArrayList<>();
        for (String url : urls) {
            if (!TextUtils.isEmpty(url)) {
                items.add(new UrlPhotoItem(url, null, 0, 0));
            }
        }
        return openPhotoItems(items, index, placeProvider);
    }

    public boolean openPhotoItems(List<UrlPhotoItem> items, int index, EmptyPhotoViewerProvider placeProvider) {
        return openPhotoItems(items, index, placeProvider, null);
    }

    private boolean openPhotoItems(List<UrlPhotoItem> items, int index, EmptyPhotoViewerProvider placeProvider, PageBlocksAdapter adapter) {
        Activity activity = parentActivity;
        if (activity == null && parentFragment != null) {
            activity = parentFragment.getParentActivity();
        }
        if (activity == null || items == null || items.isEmpty()) {
            return false;
        }
        closePhoto(false, false);
        if (windowView == null) {
            setParentActivity(parentFragment);
        }
        if (windowView == null || windowLayoutParams == null || containerView == null) {
            return false;
        }
        this.placeProvider = placeProvider;
        pageBlocksAdapter = adapter;
        imageUrls = new ArrayList<>();
        photoItems = new ArrayList<>();
        for (UrlPhotoItem item : items) {
            if (item != null && !TextUtils.isEmpty(item.url)) {
                photoItems.add(item);
                imageUrls.add(item.url);
            }
        }
        if (imageUrls.isEmpty()) {
            closePhoto(false, false);
            return false;
        }
        currentIndex = Math.max(0, Math.min(index, imageUrls.size() - 1));
        resetImages();
        bottomLayout.setVisibility(View.GONE);
        bottomLayout.setTag(0);
        bottomLayout.setTranslationY(0);
        groupedPhotosListView.reset();
        groupedPhotosListView.setAnimateBackground(!ApplicationLoader.isNetworkOnline());
        backgroundDrawable.setAlpha(0);
        windowView.setClipChildren(false);
        navigationBar.setVisibility(View.VISIBLE);
        try {
            WindowManager manager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            if (manager == null) {
                closePhoto(false, false);
                return false;
            }
            if (windowView.getParent() != null) {
                try {
                    manager.removeViewImmediate(windowView);
                } catch (Exception ignore) {
                }
            }
            windowView.setFocusable(false);
            containerView.setFocusable(false);
            manager.addView(windowView, windowLayoutParams);
            onShowView();
        } catch (Exception e) {
            setParentAllowDrawContent(true);
            closePhoto(false, false);
            return false;
        }
        isVisible = true;
        isVisibleOrAnimating = true;
        closingPhoto = false;
        rebuildGroupedPhotoLocations();
        setImageIndex(currentIndex, true, false);
        return true;
    }

    public void closePhoto(boolean animated, boolean fromEditMode) {
        if (!isVisible && !isVisibleOrAnimating) {
            setParentAllowDrawContent(true);
            resetParentScaleView();
            clippingImageProgress = 0f;
            if (currentPlaceObject != null && currentPlaceObject.imageReceiver != null) {
                currentPlaceObject.imageReceiver.setVisible(true, true);
            }
            currentPlaceObject = null;
            showAfterAnimation = null;
            hideAfterAnimation = null;
            releaseCurrentThumb();
            if (placeProvider != null) {
                placeProvider.onClose();
                placeProvider = null;
            }
            if (containerView != null) {
                resetImages();
            }
            pageBlocksAdapter = null;
            imageUrls.clear();
            photoItems.clear();
            return;
        }
        if (closingPhoto) {
            return;
        }
        closingPhoto = true;
        isVisibleOrAnimating = false;
        if (placeProvider != null) {
            placeProvider.onReleasePlayerBeforeClose(currentIndex);
        }
        if (animated && containerView != null && closePhoto()) {
            return;
        }
        onPhotoClosed(null, !animated);
    }

    private void onPhotoClosed(PlaceProviderObject object) {
        onPhotoClosed(object, false);
    }

    private void onPhotoClosed(PlaceProviderObject object, boolean immediate) {
        isVisible = false;
        isVisibleOrAnimating = false;
        closingPhoto = false;
        openAnimationInProgress = false;
        animationInProgress = 0;
        transitionAnimationStartTime = 0;
        resetParentScaleView();
        if (currentPlaceObject != null && currentPlaceObject != object && currentPlaceObject.imageReceiver != null) {
            currentPlaceObject.imageReceiver.setVisible(true, true);
        }
        currentPlaceObject = null;
        showAfterAnimation = null;
        hideAfterAnimation = null;
        releaseCurrentThumb();
        if (object != null && object.imageReceiver != null) {
            object.imageReceiver.setVisible(true, true);
        }
        FrameLayoutDrawer container = containerView;
        if (container != null) {
            actionBar.setEnabled(true);
            for (int i = 0; i < photoProgressViews.length; i++) {
                if (photoProgressViews[i] != null) {
                    photoProgressViews[i].setBackgroundState(PROGRESS_NONE, false, true);
                }
            }
            groupedPhotosListView.clear();
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            recycleVelocityTracker();
        }
        FrameLayout window = windowView;
        Activity activity = parentActivity;
        if (container != null) {
            Runnable removeViewRunnable = () -> {
                try {
                    if (window != null && window.getParent() != null && activity != null) {
                        WindowManager manager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                        if (manager != null) {
                            manager.removeViewImmediate(window);
                            clearImages();
                            release();
                            onHideView();
                        } else {
                            release();
                            onHideView();
                        }
                    } else {
                        release();
                        onHideView();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    release();
                    onHideView();
                }
            };
            if (immediate) {
                removeViewRunnable.run();
            } else {
                container.post(removeViewRunnable);
            }
        } else {
            clippingImageProgress = 0f;
        }
        if (placeProvider != null) {
            placeProvider.willHidePhotoViewer();
        }
        if (placeProvider != null) {
            placeProvider.onClose();
            placeProvider = null;
        }
        setParentAllowDrawContent(true);
        pageBlocksAdapter = null;
        imageUrls.clear();
        photoItems.clear();
    }

    public void destroyPhotoViewer() {
        if (windowView != null) {
            if (containerView != null) {
                release();
            }
            if (windowView.getParent() != null && parentActivity != null) {
                try {
                    WindowManager manager = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    if (manager != null) {
                        unregisterBackCallback();
                        manager.removeViewImmediate(windowView);
                        onHideView();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (containerView != null) {
            NotificationCenter notificationCenter = NotificationCenter.getInstance(0);
            notificationCenter.removeObserver(progressObserver, NotificationCenter.fileLoadProgressChanged);
            notificationCenter.removeObserver(progressObserver, NotificationCenter.fileLoaded);
            notificationCenter.removeObserver(progressObserver, NotificationCenter.fileLoadFailed);
        }
        windowView = null;
        containerView = null;
        animatingImageView = null;
        navigationBar = null;
        windowLayoutParams = null;
        isVisible = false;
        isVisibleOrAnimating = false;
        closingPhoto = false;
        setParentAllowDrawContent(true);
        clippingImageProgress = 0f;
        if (currentPlaceObject != null && currentPlaceObject.imageReceiver != null) {
            currentPlaceObject.imageReceiver.setVisible(true, true);
        }
        currentPlaceObject = null;
        showAfterAnimation = null;
        hideAfterAnimation = null;
        releaseCurrentThumb();
        if (placeProvider != null) {
            placeProvider.onClose();
            placeProvider = null;
        }
        pageBlocksAdapter = null;
        imageUrls.clear();
        photoItems.clear();
        parentFragment = null;
        parentActivity = null;
    }

    public void onResume() {
        if (containerView != null) {
            containerView.invalidate();
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (windowView != null) {
            windowView.requestLayout();
        }
        if (containerView != null) {
            containerView.requestLayout();
        }
    }

    public VideoPlayer getVideoPlayer() {
        return null;
    }

    public TextureView getVideoTextureView() {
        return null;
    }

    public SurfaceView getVideoSurfaceView() {
        return null;
    }

    private UrlPhotoItem photoItemForPageBlock(PageBlocksAdapter adapter, int index) {
        String url = urlForPageBlock(adapter, index);
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        int width = 0;
        int height = 0;
        TLRPC.PageBlock block = adapter.get(index);
        if (block != null) {
            width = block.w > 0 ? block.w : block.cachedWidth;
            height = block.h > 0 ? block.h : block.cachedHeight;
        }
        TLObject media = adapter.getMedia(index);
        TLRPC.PhotoSize fullSize = null;
        if (width <= 0 || height <= 0) {
            fullSize = adapter.getFileLocation(media, new int[1]);
            if (fullSize != null && fullSize.w > 0 && fullSize.h > 0) {
                width = fullSize.w;
                height = fullSize.h;
            }
        }
        if (fullSize == null) {
            fullSize = adapter.getFileLocation(media, new int[1]);
        }
        ImageLocation imageLocation = imageLocationForMedia(media, fullSize);
        ImageLocation thumbLocation = thumbLocationForMedia(media, fullSize);
        String progressKey = progressKeyForMedia(media, fullSize, url);
        Object parentObject = adapter.getParentObject();
        return new UrlPhotoItem(url, null, width, height, adapter.getCaption(index), imageLocation, thumbLocation, parentObject, progressKey, block);
    }

    private String urlForPageBlock(PageBlocksAdapter adapter, int index) {
        TLRPC.PageBlock block = adapter.get(index);
        if (block != null && !TextUtils.isEmpty(block.url)) {
            return block.url;
        }
        File file = adapter.getFile(index);
        if (file != null && file.exists()) {
            return file.getAbsolutePath();
        }
        CharSequence caption = adapter.getCaption(index);
        if (caption != null && !TextUtils.isEmpty(caption.toString()) && caption.toString().startsWith("http")) {
            return caption.toString();
        }
        return null;
    }

    private static ImageLocation imageLocationForMedia(TLObject media, TLRPC.PhotoSize fullSize) {
        if (!canUsePhotoSizeLocation(fullSize)) {
            return null;
        }
        if (media instanceof TLRPC.Photo) {
            return ImageLocation.getForPhoto(fullSize, (TLRPC.Photo) media);
        } else if (media instanceof TLRPC.Document) {
            return ImageLocation.getForDocument(fullSize, (TLRPC.Document) media);
        }
        return null;
    }

    private static ImageLocation thumbLocationForMedia(TLObject media, TLRPC.PhotoSize fullSize) {
        if (media instanceof TLRPC.Photo) {
            TLRPC.Photo photo = (TLRPC.Photo) media;
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 90, false, fullSize, false);
            if (!canUsePhotoSizeLocation(thumbSize)) {
                return null;
            }
            return ImageLocation.getForPhoto(thumbSize, photo);
        } else if (media instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) media;
            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90, false, fullSize, false);
            if (!canUsePhotoSizeLocation(thumbSize)) {
                return null;
            }
            return ImageLocation.getForDocument(thumbSize, document);
        }
        return null;
    }

    private static boolean canUsePhotoSizeLocation(TLRPC.PhotoSize photoSize) {
        return photoSize instanceof TLRPC.TL_photoStrippedSize || photoSize instanceof TLRPC.TL_photoPathSize || photoSize != null && photoSize.location != null;
    }

    private static String progressKeyForMedia(TLObject media, TLRPC.PhotoSize fullSize, String fallbackUrl) {
        String fileName = null;
        if (media instanceof TLRPC.Document) {
            fileName = FileLoader.getAttachFileName(media);
        } else if (fullSize != null) {
            fileName = FileLoader.getAttachFileName(fullSize);
        }
        return TextUtils.isEmpty(fileName) ? fallbackUrl : fileName;
    }

    public static class UrlPhotoItem {
        public final String url;
        public final String thumbUrl;
        public final int width;
        public final int height;
        public final CharSequence caption;
        public final CharSequence title;
        public final CharSequence subtitle;
        public final ImageLocation imageLocation;
        public final ImageLocation thumbLocation;
        public final Object parentObject;
        public final String progressKey;
        public final TLRPC.PageBlock pageBlock;

        public UrlPhotoItem(String url, String thumbUrl, int width, int height) {
            this(url, thumbUrl, width, height, null);
        }

        public UrlPhotoItem(String url, String thumbUrl, int width, int height, CharSequence caption) {
            this(url, thumbUrl, width, height, caption, null, null);
        }

        public UrlPhotoItem(String url, String thumbUrl, int width, int height, CharSequence caption, CharSequence title, CharSequence subtitle) {
            this(url, thumbUrl, width, height, caption, title, subtitle, null, null, null, null, null);
        }

        public UrlPhotoItem(String url, String thumbUrl, int width, int height, CharSequence caption, ImageLocation imageLocation, ImageLocation thumbLocation, Object parentObject, String progressKey) {
            this(url, thumbUrl, width, height, caption, null, null, imageLocation, thumbLocation, parentObject, progressKey, null);
        }

        public UrlPhotoItem(String url, String thumbUrl, int width, int height, CharSequence caption, ImageLocation imageLocation, ImageLocation thumbLocation, Object parentObject, String progressKey, TLRPC.PageBlock pageBlock) {
            this(url, thumbUrl, width, height, caption, null, null, imageLocation, thumbLocation, parentObject, progressKey, pageBlock);
        }

        public UrlPhotoItem(String url, String thumbUrl, int width, int height, CharSequence caption, CharSequence title, CharSequence subtitle, ImageLocation imageLocation, ImageLocation thumbLocation, Object parentObject, String progressKey, TLRPC.PageBlock pageBlock) {
            this.url = url;
            this.thumbUrl = thumbUrl;
            this.width = width;
            this.height = height;
            this.caption = caption;
            this.title = title;
            this.subtitle = subtitle;
            this.imageLocation = imageLocation != null ? imageLocation : imageLocationForPath(url);
            this.thumbLocation = thumbLocation != null ? thumbLocation : imageLocationForPath(thumbUrl);
            this.parentObject = parentObject;
            this.progressKey = TextUtils.isEmpty(progressKey) ? defaultProgressKey(url) : progressKey;
            this.pageBlock = pageBlock;
        }
    }

    public interface PageBlocksAdapter {
        int getItemsCount();
        TLRPC.PageBlock get(int index);
        List<TLRPC.PageBlock> getAll();
        boolean isVideo(int index);
        boolean isHardwarePlayer(int index);
        TLObject getMedia(int index);
        File getFile(int index);
        String getFileName(int index);
        CharSequence getCaption(int index);
        TLRPC.PhotoSize getFileLocation(TLObject media, int[] size);
        void updateSlideshowCell(TLRPC.PageBlock currentPageBlock);
        Object getParentObject();
    }

    public static class EmptyPhotoViewerProvider {
        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            return null;
        }
        public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            return null;
        }
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {}
        public void onPhotoChanged(int photoIndex) {}
        public void willHidePhotoViewer() {}
        public void onOpen() {}
        public void onClose() {}
        public void onPreOpen() {}
        public void onPreClose() {}
        public void onReleasePlayerBeforeClose(int photoIndex) {}
        public boolean canScrollAway() {
            return true;
        }
    }

    public static class PlaceProviderObject {
        public int viewX;
        public int viewY;
        public int viewWidth;
        public int viewHeight;
        public View parentView;
        public ImageReceiver imageReceiver;
        public Object thumb;
        public int[] radius;
        public int clipTopAddition;
        public int clipBottomAddition;
        public float scale = 1.0f;
        public boolean fadeIn;
        public ClippingImageView animatingImageView;
        public int animatingImageViewYOffset;
    }

    private class BackgroundDrawable extends ColorDrawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF visibleRect = new RectF();

        private Runnable drawRunnable;
        private boolean allowDrawContent;
        private boolean textureViewSkipRender;

        BackgroundDrawable(int color) {
            super(color);
            paint.setColor(color);
        }

        @Keep
        @Override
        public void setAlpha(int alpha) {
            allowDrawContent = !isVisible || alpha != 255;
            setParentAllowDrawContent(allowDrawContent);
            super.setAlpha(alpha);
            paint.setAlpha(alpha);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (textureViewSkipRender) {
                return;
            }

            if (animationInProgress != 0 && !AndroidUtilities.isTablet() && currentPlaceObject != null && currentPlaceObject.animatingImageView != null) {
                currentPlaceObject.animatingImageView.getClippedVisibleRect(visibleRect);
                if (!visibleRect.isEmpty()) {
                    visibleRect.inset(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f));
                    android.graphics.Rect boundsRect = getBounds();
                    float width = boundsRect.right;
                    float height = boundsRect.bottom;
                    for (int i = 0; i < 4; i++) {
                        switch (i) {
                            case 0:
                                rect.set(0, visibleRect.top, visibleRect.left, visibleRect.bottom);
                                break;
                            case 1:
                                rect.set(0, 0, width, visibleRect.top);
                                break;
                            case 2:
                                rect.set(visibleRect.right, visibleRect.top, width, visibleRect.bottom);
                                break;
                            case 3:
                                rect.set(0, visibleRect.bottom, width, height);
                                break;
                        }
                        canvas.drawRect(rect, paint);
                    }
                }
            } else {
                canvas.drawRect(getBounds(), paint);
            }
            if (getAlpha() != 0) {
                if (drawRunnable != null) {
                    AndroidUtilities.runOnUIThread(drawRunnable);
                    drawRunnable = null;
                }
            }
        }
    }

    private void onDraw(Canvas canvas) {
        if (containerView == null) {
            return;
        }
        FrameLayoutDrawer containerView = this.containerView;
        updateImageBounds();
        float currentScale = getCurrentScale();
        float currentTranslationX = getCurrentTranslationX();
        float currentTranslationY = getCurrentTranslationY();
        View scaleView = getParentScaleView();
        if (scaleView != null) {
            float scale;
            if (!isVisible || closingPhoto && animationInProgress == 0) {
                scale = 1f;
            } else {
                float progress = Math.abs(currentTranslationY) / Math.max(1f, getContainerViewHeight() / 2f);
                if (clippingImageProgress != 0) {
                    progress = progress + (1f - progress) * clippingImageProgress;
                }
                scale = 1f + (1f - Utilities.clamp(progress, 1f, 0f)) * ZOOM_SCALE;
            }
            if (scaleView.getScaleX() != scale || scaleView.getScaleY() != scale) {
                scaleView.setPivotX(scaleView.getWidth() / 2f);
                scaleView.setPivotY(scaleView.getHeight() / 2f);
                scaleView.setScaleX(scale);
                scaleView.setScaleY(scale);
            }
        }
        if (animationInProgress == 1 && animatingImageView != null && animatingImageView.getVisibility() == View.VISIBLE) {
            float alpha = 1f;
            canvas.save();
            canvas.translate(
                animatingImageView.getCenterX() - canvas.getWidth() / 2f,
                animatingImageView.getCenterY() - canvas.getHeight() / 2f
            );
            float t = animatingImageView.getAnimationProgress();
            alpha = t;
            canvas.scale(alpha, alpha, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
            drawProgress(canvas, 0, 1, 0, alpha);
            canvas.restore();
            canvas.save();
            canvas.translate(animatingImageView.getX(), animatingImageView.getY());
            canvas.scale(animatingImageView.getScaleX(), animatingImageView.getScaleY());
            animatingImageView.draw(canvas);
            canvas.restore();
            return;
        }
        if (animationInProgress == 3 || !isVisible && animationInProgress != 2) {
            return;
        }
        int containerWidth = getContainerViewWidth();
        int containerHeight = getContainerViewHeight();

        sideImage = null;
        changingPage = false;
        if (currentScale >= MIN_SCALE && !zoomAnimation && !zooming) {
            if (currentTranslationX > maxX + AndroidUtilities.dp(5f)) {
                sideImage = leftImage;
            } else if (currentTranslationX < minX - AndroidUtilities.dp(5f)) {
                sideImage = rightImage;
            } else {
                groupedPhotosListView.setMoveProgress(0f);
            }
            changingPage = sideImage != null;
        }

        if (sideImage == rightImage) {
            float rightTranslateX = currentTranslationX;
            float rightScaleDiff = 0;
            float rightAlpha = 1;
            if (!zoomAnimation && rightTranslateX < minX) {
                rightAlpha = Math.min(1f, (minX - rightTranslateX) / containerWidth);
                rightScaleDiff = (1f - rightAlpha) * 0.3f;
                rightTranslateX = -containerWidth - AndroidUtilities.dp(30f) / 2f;
            }

            if (rightImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(containerWidth / 2f, containerHeight / 2f);
                canvas.translate(containerWidth + AndroidUtilities.dp(30f) / 2f + rightTranslateX, 0);
                canvas.scale(1f - rightScaleDiff, 1f - rightScaleDiff);
                int bitmapWidth = rightImage.getBitmapWidth();
                int bitmapHeight = rightImage.getBitmapHeight();
                float scaleX = containerWidth / (float) bitmapWidth;
                float scaleY = containerHeight / (float) bitmapHeight;
                float bmScale = Math.min(scaleX, scaleY);
                int width = (int) (bitmapWidth * bmScale);
                int height = (int) (bitmapHeight * bmScale);

                rightImage.setAlpha(rightAlpha);
                rightImage.setImageCoords(-width / 2, -height / 2, width, height);
                rightImage.draw(canvas);

                canvas.restore();
            }
            groupedPhotosListView.setMoveProgress(-rightAlpha);

            canvas.save();
            canvas.translate(rightTranslateX, currentTranslationY / currentScale);
            canvas.translate((containerWidth * (scale + 1f) + AndroidUtilities.dp(30f)) / 2f, -currentTranslationY / currentScale);
            photoProgressViews[PROGRESS_RIGHT_INDEX].setScale(1f - rightScaleDiff);
            photoProgressViews[PROGRESS_RIGHT_INDEX].setAlpha(rightAlpha);
            photoProgressViews[PROGRESS_RIGHT_INDEX].onDraw(canvas);
            canvas.restore();
        }

        float translateX = currentTranslationX;
        float scaleDiff = 0;
        float alpha = 1;
        if (!zoomAnimation && translateX > maxX) {
            alpha = Math.min(1f, (translateX - maxX) / containerWidth);
            scaleDiff = alpha * 0.3f;
            alpha = 1f - alpha;
            translateX = maxX;
        }

        float centerAlpha = openAnimationInProgress ? containerView.getAlpha() : 1f;
        if (animatingImageView.getVisibility() == View.VISIBLE && clippingImageProgress != 0) {
            centerAlpha = 0f;
        }
        if (sideImage == leftImage) {
            centerAlpha *= alpha;
        }

        canvas.save();
        canvas.translate(containerWidth / 2f, containerHeight / 2f);
        canvas.translate(translateX, currentTranslationY);
        canvas.scale(currentScale - scaleDiff, currentScale - scaleDiff);
        drawCenterImageInternal(canvas, 0f, centerAlpha);
        canvas.restore();

        drawPressedDrawables(canvas);
        drawProgress(canvas, translateX, currentScale, currentTranslationY, centerAlpha);

        if (sideImage == leftImage) {
            if (leftImage.hasBitmapImage()) {
                canvas.save();
                canvas.translate(containerWidth / 2f, containerHeight / 2f);
                canvas.translate(-(containerWidth * (scale + 1f) + AndroidUtilities.dp(30f)) / 2f + currentTranslationX, 0);
                int bitmapWidth = leftImage.getBitmapWidth();
                int bitmapHeight = leftImage.getBitmapHeight();
                float scaleX = containerWidth / (float) bitmapWidth;
                float scaleY = containerHeight / (float) bitmapHeight;
                float bmScale = Math.min(scaleX, scaleY);
                int width = (int) (bitmapWidth * bmScale);
                int height = (int) (bitmapHeight * bmScale);

                leftImage.setAlpha(1f);
                leftImage.setImageCoords(-width / 2, -height / 2, width, height);
                leftImage.draw(canvas);

                canvas.restore();
            }
            groupedPhotosListView.setMoveProgress(1f - alpha);

            canvas.save();
            canvas.translate(currentTranslationX, currentTranslationY / currentScale);
            canvas.translate(-(containerWidth * (scale + 1f) + AndroidUtilities.dp(30f)) / 2f, -currentTranslationY / currentScale);
            photoProgressViews[PROGRESS_LEFT_INDEX].setScale(1f);
            photoProgressViews[PROGRESS_LEFT_INDEX].setAlpha(1f);
            photoProgressViews[PROGRESS_LEFT_INDEX].onDraw(canvas);
            canvas.restore();
        }
    }

    private boolean isStatusBarVisible() {
        return !inBubbleMode;
    }

    private View getParentScaleView() {
        if (parentFragment == null) {
            return null;
        }
        View scaleView = parentFragment.getPhotoViewerScaleView();
        if (scaleView != null && scaleView.getParent() instanceof View) {
            return (View) scaleView.getParent();
        }
        return parentFragment.getFragmentView();
    }

    private void resetParentScaleView() {
        View view = getParentScaleView();
        if (view != null) {
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
    }

    private void setParentAllowDrawContent(boolean value) {
        if (parentActivity instanceof LaunchActivity && ((LaunchActivity) parentActivity).drawerLayoutContainer != null) {
            ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(value);
        }
    }

    private void onShowView() {
        attachedToWindow = true;
        registerBackCallback();
    }

    private void onHideView() {
        attachedToWindow = false;
        unregisterBackCallback();
        clippingImageProgress = 1f;
        resetParentScaleView();
        setParentAllowDrawContent(true);
    }

    private void updateContainerFlags(boolean actionBarVisible) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!actionBarVisible) {
            flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (containerView != null && (containerView.getPaddingLeft() > 0 || containerView.getPaddingRight() > 0)) {
                flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
        }
        if (containerView != null) {
            containerView.setSystemUiVisibility(flags);
        }
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || windowView == null || onBackInvokedCallback != null) {
            return;
        }
        OnBackInvokedDispatcher dispatcher = windowView.findOnBackInvokedDispatcher();
        if (dispatcher == null) {
            return;
        }
        onBackInvokedCallback = () -> closePhoto(true, false);
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBackInvokedCallback);
    }

    private void unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || windowView == null || onBackInvokedCallback == null) {
            return;
        }
        OnBackInvokedDispatcher dispatcher = windowView.findOnBackInvokedDispatcher();
        if (dispatcher != null) {
            dispatcher.unregisterOnBackInvokedCallback(onBackInvokedCallback);
        }
        onBackInvokedCallback = null;
    }

    private void releaseCurrentThumb() {
        if (currentThumb != null) {
            currentThumb.release();
            currentThumb = null;
        }
    }

    private boolean onTouchEvent(MotionEvent ev) {
        if (containerView == null) {
            return false;
        }
        FrameLayoutDrawer containerView = this.containerView;
        if (closing) {
            return true;
        }
        if (animationInProgress != 0 || animationStartTime != 0) {
            return false;
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        if (ev.getPointerCount() == 1 && gestureDetector.onTouchEvent(ev)) {
            if (doubleTap) {
                doubleTap = false;
                moving = false;
                zooming = false;
                if (imageMoveAnimation == null) {
                    checkMinMax(false);
                }
                recycleVelocityTracker();
                return true;
            }
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            discardTap = false;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            if (!draggingDown && !changingPage && ev.getPointerCount() == 2) {
                cancelMoveZoomAnimation();
                switchImageAfterAnimation = 0;
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                pinchStartScale = scale;
                pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                pinchStartX = translationX;
                pinchStartY = translationY;
                zooming = true;
                moving = false;
                draggingDown = false;
                canDragDown = false;
                hidePressedDrawables();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            } else if (ev.getPointerCount() == 1) {
                moveStartX = ev.getX();
                dragY = moveStartY = ev.getY();
                draggingDown = false;
                canDragDown = true;
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (ev.getPointerCount() == 2 && !draggingDown && zooming) {
                discardTap = true;
                scale = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - getContainerViewWidth() / 2f) - ((pinchCenterX - getContainerViewWidth() / 2f) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getContainerViewHeight() / 2f) - ((pinchCenterY - getContainerViewHeight() / 2f) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                updateImageBounds();
                containerView.invalidate();
            } else if (ev.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - moveStartX);
                float dy = Math.abs(ev.getY() - dragY);
                if (dx > touchSlop || dy > touchSlop) {
                    discardTap = true;
                    hidePressedDrawables();
                }
                if (placeProvider != null && placeProvider.canScrollAway() && canDragDown && !draggingDown && scale == MIN_SCALE && dy >= AndroidUtilities.dp(30f) && dy / 2f > dx) {
                    draggingDown = true;
                    hidePressedDrawables();
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible) {
                        toggleActionBar(false, true);
                    }
                    return true;
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    updateImageBounds();
                    containerView.invalidate();
                } else if (!invalidCoords) {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || scale == MIN_SCALE && Math.abs(moveDy) + AndroidUtilities.dp(12f) < Math.abs(moveDx) || scale != MIN_SCALE) {
                        if (!moving) {
                            moveDx = 0f;
                            moveDy = 0f;
                            moving = true;
                            canDragDown = false;
                            hidePressedDrawables();
                        }
                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX && !rightImage.hasImageSet() || translationX > maxX && !leftImage.hasImageSet()) {
                            moveDx /= 3.0f;
                        }
                        if (maxY == 0f && minY == 0f) {
                            if (translationY - moveDy < minY) {
                                translationY = minY;
                                moveDy = 0f;
                            } else if (translationY - moveDy > maxY) {
                                translationY = maxY;
                                moveDy = 0f;
                            }
                        } else if (translationY < minY || translationY > maxY) {
                            moveDy /= 3.0f;
                        }
                        translationX -= moveDx;
                        if (scale != MIN_SCALE) {
                            translationY -= moveDy;
                        }
                        float width = Math.max(1f, getContainerViewWidth());
                        float progress = 0f;
                        if (translationX < minX) {
                            progress = -Utilities.clamp((minX - translationX) / width, 1f, 0f);
                        } else if (translationX > maxX) {
                            progress = Utilities.clamp((translationX - maxX) / width, 1f, 0f);
                        }
                        groupedPhotosListView.setMoveProgress(progress);
                        updateImageBounds();
                        containerView.invalidate();
                    }
                } else {
                    invalidCoords = false;
                    moveStartX = ev.getX();
                    moveStartY = ev.getY();
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL || ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            hidePressedDrawables();
            if (zooming) {
                invalidCoords = true;
                if (scale < MIN_SCALE) {
                    updateMinMax(MIN_SCALE);
                    animateTo(MIN_SCALE, 0f, 0f, true);
                } else if (scale > MAX_SCALE) {
                    float atx = (pinchCenterX - getContainerViewWidth() / 2f) - ((pinchCenterX - getContainerViewWidth() / 2f) - pinchStartX) * (MAX_SCALE / pinchStartScale);
                    float aty = (pinchCenterY - getContainerViewHeight() / 2f) - ((pinchCenterY - getContainerViewHeight() / 2f) - pinchStartY) * (MAX_SCALE / pinchStartScale);
                    updateMinMax(MAX_SCALE);
                    if (atx < minX) {
                        atx = minX;
                    } else if (atx > maxX) {
                        atx = maxX;
                    }
                    if (aty < minY) {
                        aty = minY;
                    } else if (aty > maxY) {
                        aty = maxY;
                    }
                    animateTo(MAX_SCALE, atx, aty, true);
                } else {
                    checkMinMax(true);
                }
                zooming = false;
                moving = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - ev.getY()) > getContainerViewHeight() / 6.0f) {
                    closePhoto(true, false);
                } else {
                    animateTo(MIN_SCALE, 0f, 0f, false);
                }
                draggingDown = false;
            } else if (moving) {
                float moveToX = translationX;
                float moveToY = translationY;
                updateMinMax(scale);
                moving = false;
                canDragDown = true;
                float velocity = 0f;
                if (velocityTracker != null && scale == MIN_SCALE) {
                    velocityTracker.computeCurrentVelocity(1000);
                    velocity = velocityTracker.getXVelocity();
                }
                if ((translationX < minX - getContainerViewWidth() / 3f || velocity < -AndroidUtilities.dp(650f)) && rightImage.hasImageSet()) {
                    goToNext();
                    return true;
                }
                if ((translationX > maxX + getContainerViewWidth() / 3f || velocity > AndroidUtilities.dp(650f)) && leftImage.hasImageSet()) {
                    goToPrev();
                    return true;
                }
                if (translationX < minX) {
                    moveToX = minX;
                } else if (translationX > maxX) {
                    moveToX = maxX;
                }
                if (translationY < minY) {
                    moveToY = minY;
                } else if (translationY > maxY) {
                    moveToY = maxY;
                }
                animateTo(scale, moveToX, moveToY, false);
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                recycleVelocityTracker();
            }
        }
        return false;
    }

    private boolean isPointInsideVisibleChrome(float y) {
        if (!isActionBarVisible || chromeAlpha <= 0f) {
            return false;
        }
        int topChromeHeight = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(43f);
        if (y <= topChromeHeight) {
            return true;
        }
        int bottomChromeHeight = getBottomControlsHeight();
        return bottomChromeHeight > 0 && y >= getContainerViewHeight() - bottomChromeHeight - AndroidUtilities.dp(8f);
    }

    private void drawCenterImageInternal(Canvas canvas, float currentMirror, float alpha) {
        boolean restore = false;
        if (currentMirror > 0) {
            canvas.save();
            restore = true;
            canvas.scale(1 - currentMirror * 2, 1f);
            canvas.skew(0, 4 * currentMirror * (1f - currentMirror) * .25f);
        }
        centerImage.setAlpha(alpha);
        int width = centerImage.getBitmapWidth();
        int height = centerImage.getBitmapHeight();
        float bmScale = Math.min(getContainerViewWidth() / (float) width, getContainerViewHeight() / (float) height);
        int drawWidth = (int) (width * bmScale);
        int drawHeight = (int) (height * bmScale);
        centerImage.setImageCoords(-drawWidth / 2, -drawHeight / 2, drawWidth, drawHeight);
        centerImage.draw(canvas);
        if (restore) {
            canvas.restore();
        }
        centerImage.setAlpha(1f);
    }

    private void drawProgress(Canvas canvas, float translateX, float currentScale, float currentTranslationY, float alpha) {
        if (centerImageRect.isEmpty()) {
            return;
        }
        PhotoProgressView progressView = photoProgressViews[PROGRESS_CENTER_INDEX];
        progressView.setScale(currentScale);
        progressView.setAlpha(alpha);
        canvas.save();
        canvas.translate(translateX, currentTranslationY);
        progressView.onDraw(canvas);
        canvas.restore();
    }

    private void drawSideImage(Canvas canvas, ImageReceiver receiver, int progressIndex, float currentTranslationX, float currentTranslationY, float currentScale, boolean rightSide, float reveal) {
        RectF rect = receiver == rightImage ? rightImageRect : leftImageRect;
        if (rect.isEmpty()) {
            return;
        }
        float alpha = rightSide ? reveal : 1f;
        float scaleDiff = rightSide ? (1f - alpha) * 0.3f : 0f;
        int containerWidth = getContainerViewWidth();
        int containerHeight = getContainerViewHeight();
        canvas.save();
        canvas.translate(containerWidth / 2f, containerHeight / 2f);
        if (rightSide) {
            canvas.translate(containerWidth + AndroidUtilities.dp(30f) / 2f + currentTranslationX, 0);
        } else {
            canvas.translate(-(containerWidth * (currentScale + 1f) + AndroidUtilities.dp(30f)) / 2f + currentTranslationX, 0);
        }
        canvas.scale(1f - scaleDiff, 1f - scaleDiff);
        receiver.setAlpha(alpha);
        int bitmapWidth = receiver.getBitmapWidth();
        int bitmapHeight = receiver.getBitmapHeight();
        if (bitmapWidth > 0 && bitmapHeight > 0) {
            float scaleX = containerWidth / (float) bitmapWidth;
            float scaleY = containerHeight / (float) bitmapHeight;
            float scale = Math.min(scaleX, scaleY);
            int width = (int) (bitmapWidth * scale);
            int height = (int) (bitmapHeight * scale);
            receiver.setImageCoords(-width / 2, -height / 2, width, height);
        }
        receiver.draw(canvas);
        receiver.setAlpha(1f);
        canvas.restore();

        PhotoProgressView progressView = photoProgressViews[progressIndex];
        progressView.setScale((rightSide ? 1f - scaleDiff : 1f) * currentScale);
        progressView.setAlpha(alpha);
        canvas.save();
        canvas.translate(currentTranslationX, currentTranslationY / currentScale);
        if (rightSide) {
            canvas.translate((containerWidth * (currentScale + 1) + AndroidUtilities.dp(30f)) / 2f, -currentTranslationY / currentScale);
        } else {
            canvas.translate(-(containerWidth * (currentScale + 1f) + AndroidUtilities.dp(30f)) / 2f, -currentTranslationY / currentScale);
        }
        progressView.onDraw(canvas);
        canvas.restore();
    }

    private void hidePressedDrawables() {
        drawPressedDrawable[0] = drawPressedDrawable[1] = false;
        containerView.invalidate();
    }

    private void drawPressedDrawables(Canvas canvas) {
        long now = System.currentTimeMillis();
        long dt = lastDrawTime == 0 ? 16 : Math.min(18, now - lastDrawTime);
        lastDrawTime = now;
        int sideWidth = getContainerViewWidth() / 5;
        for (int a = 0; a < pressedDrawable.length; a++) {
            if (drawPressedDrawable[a] || pressedDrawableAlpha[a] != 0f) {
                pressedDrawable[a].setAlpha((int) (pressedDrawableAlpha[a] * 255));
                if (a == 0) {
                    pressedDrawable[a].setBounds(0, 0, sideWidth, getContainerViewHeight());
                } else {
                    pressedDrawable[a].setBounds(getContainerViewWidth() - sideWidth, 0, getContainerViewWidth(), getContainerViewHeight());
                }
                pressedDrawable[a].draw(canvas);
            }
            if (drawPressedDrawable[a]) {
                if (pressedDrawableAlpha[a] < 1.0f) {
                    pressedDrawableAlpha[a] += dt / 180.0f;
                    if (pressedDrawableAlpha[a] > 1.0f) {
                        pressedDrawableAlpha[a] = 1.0f;
                    }
                    containerView.invalidate();
                }
            } else if (pressedDrawableAlpha[a] > 0.0f) {
                pressedDrawableAlpha[a] -= dt / 180.0f;
                if (pressedDrawableAlpha[a] < 0.0f) {
                    pressedDrawableAlpha[a] = 0.0f;
                }
                containerView.invalidate();
            }
        }
    }

    private void rebuildGroupedPhotoLocations() {
        groupedPhotoLocations.clear();
        groupedPageBlocks.clear();
        for (int i = 0; i < imageUrls.size(); i++) {
            UrlPhotoItem item = i < photoItems.size() ? photoItems.get(i) : null;
            groupedPhotoLocations.add(groupedPhotoLocationForItem(item, imageUrls.get(i)));
            groupedPageBlocks.add(item == null ? null : item.pageBlock);
        }
    }

    private boolean shouldUseGroupedPageBlocks() {
        UrlPhotoItem item = currentPhotoItem();
        return imageUrls.size() > 1 && item != null && item.pageBlock != null && item.pageBlock.groupId != 0;
    }

    private ImageLocation groupedPhotoLocationForItem(UrlPhotoItem item, String fallbackUrl) {
        if (item != null) {
            if (item.thumbLocation != null) {
                return item.thumbLocation;
            } else if (item.imageLocation != null) {
                return item.imageLocation;
            }
        }
        return imageLocationForPath(fallbackUrl);
    }

    private UrlPhotoItem photoItemAt(int index) {
        return index >= 0 && index < photoItems.size() ? photoItems.get(index) : null;
    }

    private UrlPhotoItem currentPhotoItem() {
        return photoItemAt(currentIndex);
    }

    private CharSequence currentCaption() {
        UrlPhotoItem item = currentPhotoItem();
        return item == null ? null : item.caption;
    }

    private String currentUrl() {
        return currentIndex >= 0 && currentIndex < imageUrls.size() ? imageUrls.get(currentIndex) : null;
    }

    private String currentProgressKey() {
        return progressKeyForIndex(currentIndex);
    }

    private String progressKeyForIndex(int index) {
        if (index < 0 || index >= imageUrls.size()) {
            return null;
        }
        return getFileName(index);
    }

    private String getFileName(int index) {
        if (index < 0 || index >= imageUrls.size()) {
            return null;
        }
        UrlPhotoItem item = photoItemAt(index);
        return item == null || TextUtils.isEmpty(item.progressKey) ? defaultProgressKey(imageUrls.get(index)) : item.progressKey;
    }

    private void updateCurrentFileNames() {
        currentFileNames[PROGRESS_CENTER_INDEX] = getFileName(currentIndex);
        currentFileNames[PROGRESS_RIGHT_INDEX] = getFileName(currentIndex + 1);
        currentFileNames[PROGRESS_LEFT_INDEX] = getFileName(currentIndex - 1);
    }

    private boolean fileExistsForIndex(int index) {
        if (index < 0 || index >= imageUrls.size()) {
            return false;
        }
        UrlPhotoItem item = photoItemAt(index);
        File cacheFile = null;
        if (item != null && item.imageLocation != null) {
            if (item.imageLocation.photoSize != null) {
                cacheFile = FileLoader.getInstance(0).getPathToAttach(item.imageLocation.photoSize, true);
            } else if (item.imageLocation.document != null) {
                cacheFile = FileLoader.getInstance(0).getPathToAttach(item.imageLocation.document, true);
            } else if (item.imageLocation.location != null) {
                cacheFile = FileLoader.getInstance(0).getPathToAttach(item.imageLocation.location, true);
            } else if (item.imageLocation.path != null) {
                cacheFile = cacheFileForUrl(item.imageLocation.path, getFileName(index));
            }
        }
        if (cacheFile == null) {
            cacheFile = cacheFileForUrl(imageUrls.get(index), getFileName(index));
        }
        return cacheFile != null && cacheFile.exists();
    }

    private int photoIndexForProgressIndex(int progressIndex) {
        if (progressIndex == PROGRESS_RIGHT_INDEX) {
            return currentIndex + 1;
        } else if (progressIndex == PROGRESS_LEFT_INDEX) {
            return currentIndex - 1;
        }
        return currentIndex;
    }

    private int progressIndexForReceiver(ImageReceiver receiver) {
        if (receiver == centerImage) {
            return PROGRESS_CENTER_INDEX;
        } else if (receiver == rightImage) {
            return PROGRESS_RIGHT_INDEX;
        } else if (receiver == leftImage) {
            return PROGRESS_LEFT_INDEX;
        }
        return -1;
    }

    private int progressIndexForKey(String progressKey) {
        if (TextUtils.isEmpty(progressKey)) {
            return -1;
        }
        for (int i = 0; i < photoProgressViews.length; i++) {
            if (progressKey.equals(currentFileNames[i])) {
                return i;
            }
        }
        return -1;
    }

    private void shareCurrentUrl() {
        // Try to share the actual image file, fall back to URL
        File cacheFile = cacheFileForIndex(currentIndex);
        if (cacheFile != null && cacheFile.exists()) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/jpeg");
            if (Build.VERSION.SDK_INT >= 24) {
                try {
                    intent.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                            parentActivity,
                            ApplicationLoader.getApplicationId() + ".provider",
                            cacheFile));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(cacheFile));
                }
            } else {
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(cacheFile));
            }
            try {
                parentActivity.startActivity(Intent.createChooser(intent, parentActivity.getString(R.string.ShareFile)));
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            // File not cached yet — share URL as fallback
            String url = currentUrl();
            if (TextUtils.isEmpty(url)) {
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            try {
                parentActivity.startActivity(Intent.createChooser(intent, parentActivity.getString(R.string.ShareFile)));
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void copyCurrentUrl() {
        String url = currentUrl();
        if (TextUtils.isEmpty(url)) {
            return;
        }
        AndroidUtilities.addToClipboard(url);
    }

    private void openCurrentUrlExternally() {
        String url = currentUrl();
        if (TextUtils.isEmpty(url)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            parentActivity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private File cacheFileForIndex(int index) {
        if (index < 0 || index >= imageUrls.size()) {
            return null;
        }
        UrlPhotoItem item = photoItemAt(index);
        if (item != null && item.imageLocation != null) {
            if (item.imageLocation.photoSize != null) {
                return FileLoader.getInstance(0).getPathToAttach(item.imageLocation.photoSize, true);
            } else if (item.imageLocation.document != null) {
                return FileLoader.getInstance(0).getPathToAttach(item.imageLocation.document, true);
            } else if (item.imageLocation.location != null) {
                return FileLoader.getInstance(0).getPathToAttach(item.imageLocation.location, true);
            } else if (item.imageLocation.path != null) {
                return cacheFileForUrl(item.imageLocation.path, getFileName(index));
            }
        }
        return cacheFileForUrl(imageUrls.get(index), getFileName(index));
    }

    private void saveCurrentPhoto() {
        if (closing) {
            return;
        }
        // If file is already cached, save directly
        File cacheFile = cacheFileForIndex(currentIndex);
        if (cacheFile != null && cacheFile.exists()) {
            pendingSaveAfterDownload = false;
            MediaController.getInstance().saveFile(cacheFile.getAbsolutePath(), parentActivity, 0, null, null, uri -> {
                if (uri != null && parentActivity != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            Toast.makeText(parentActivity, parentActivity.getString(R.string.SaveToGallery), Toast.LENGTH_SHORT).show();
                        } catch (Exception ignore) {}
                    });
                }
            });
            return;
        }
        // File not yet downloaded — trigger download, save after it completes
        String url = currentUrl();
        if (!TextUtils.isEmpty(url) && isHttpUrl(url)) {
            pendingSaveAfterDownload = true;
            ImageLoader.getInstance().loadHttpFile(url, "jpg", 0);
        }
    }

    private void cancelCurrentLoading() {
        String url = currentUrl();
        if (!TextUtils.isEmpty(url) && isHttpUrl(url)) {
            ImageLoader.getInstance().cancelLoadHttpFile(url);
        }
        String progressKey = currentProgressKey();
        if (!TextUtils.isEmpty(progressKey)) {
            FileLoader.getInstance(0).cancelLoadFile(progressKey);
        }
        updateProgressVisibility(PROGRESS_CENTER_INDEX);
    }

    private void updateCaptionView() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) captionScrollView.getLayoutParams();
        int bottomMargin = shouldShowBottomLayout() ? AndroidUtilities.dp(48f) : 0;
        if (shouldShowGroupedPhotos()) {
            bottomMargin += AndroidUtilities.dp(68f);
        }
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin;
            captionScrollView.setLayoutParams(params);
        }
        CharSequence caption = currentCaption();
        CharSequence nextCaption = TextUtils.isEmpty(caption) ? "" : caption;
        boolean animated = didSetCaption;
        if (!TextUtils.equals(((TextView) captionTextViewSwitcher.getCurrentView()).getText(), nextCaption)) {
            captionTextViewSwitcher.setText(nextCaption, animated);
            captionScrollView.scrollTo(0, 0);
            captionScrollView.reset();
            captionScrollView.updateTopMargin();
        }
        didSetCaption = true;
        boolean show = !TextUtils.isEmpty(nextCaption);
        captionVisible = show;
        captionTextViewSwitcher.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        captionScrollView.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (show && getContainerViewWidth() > 0) {
            captionScrollView.measure(
                    View.MeasureSpec.makeMeasureSpec(getContainerViewWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(getContainerViewHeight(), View.MeasureSpec.AT_MOST)
            );
        }
    }

    private void setImageIndex(int index, boolean init, boolean animateCaption) {
        if (imageUrls.isEmpty()) {
            return;
        }
        if (index < 0 || index >= imageUrls.size()) {
            updateImageBounds();
            containerView.invalidate();
            return;
        }
        int previousIndex = currentIndex;
        if (index != currentIndex) {
            releaseCurrentThumb();
            if (placeProvider != null) {
                placeProvider.willSwitchFromPhoto(null, null, currentIndex);
            }
            if (currentPlaceObject != null && currentPlaceObject.imageReceiver != null) {
                if (animationInProgress == 0) {
                    currentPlaceObject.imageReceiver.setVisible(true, true);
                } else {
                    showAfterAnimation = currentPlaceObject;
                }
            }
            currentIndex = index;
            if (placeProvider != null) {
                placeProvider.onPhotoChanged(currentIndex);
            }
        }
        currentPlaceObject = placeProvider == null ? null : placeProvider.getPlaceForPhoto(null, null, currentIndex, init, false);
        if (init) {
            ImageReceiver.BitmapHolder openingThumb = currentPlaceObject != null && currentPlaceObject.thumb instanceof ImageReceiver.BitmapHolder
                    ? (ImageReceiver.BitmapHolder) currentPlaceObject.thumb : null;
            if (currentThumb != openingThumb) {
                releaseCurrentThumb();
                currentThumb = openingThumb;
            }
        }
        if (!init && currentPlaceObject != null && currentPlaceObject.imageReceiver != null) {
            if (animationInProgress == 0) {
                currentPlaceObject.imageReceiver.setVisible(false, true);
            } else {
                hideAfterAnimation = currentPlaceObject;
            }
        }
        switchImageAfterAnimation = 0;
        cancelMoveZoomAnimation();
        scale = MIN_SCALE;
        translationX = 0f;
        translationY = 0f;
        updateCurrentFileNames();
        setImages(previousIndex, init);
        groupedPhotosListView.fillList();
        updateSlideshowCellForCurrentPhoto();
        groupedPhotosListView.setMoveProgress(0f);
        updateBottomControlsLayout();
        countView.setCount(currentIndex + 1, imageUrls.size(), !init);
        countView.updateShow(imageUrls.size() > 1, !init);
        updateCaptionView();
        centerImageLoaded = centerImage.getBitmap() != null;
        photoProgressLoaded[PROGRESS_CENTER_INDEX] = centerImageLoaded;
        updateProgressVisibility();
        UrlPhotoItem currentItem = currentIndex >= 0 && currentIndex < photoItems.size() ? photoItems.get(currentIndex) : null;
        CharSequence title = currentItem != null && !TextUtils.isEmpty(currentItem.title) ? currentItem.title : parentActivity.getString(R.string.AttachPhoto);
        CharSequence subtitle = currentItem != null ? currentItem.subtitle : null;
        boolean useShadows = TextUtils.isEmpty(subtitle) && imageUrls.size() <= 1;
        if (fancyShadows != useShadows) {
            fancyShadows = useShadows;
            actionBar.setBackgroundColor(fancyShadows ? Color.TRANSPARENT : Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
            actionBarContainer.setTextShadows(fancyShadows);
            navigationBar.setBackgroundColor(fancyShadows ? 0 : 0x7f000000);
        }
        if (init) {
            onPhotoShow();
        } else {
            pendingOpenAnimation = false;
            containerView.setAlpha(1f);
            updateActionBarAlpha(isActionBarVisible ? 1f : 0f);
            updateImageBounds();
        }
        if (init) {
            actionBarContainer.setTitle(title);
            actionBarContainer.setSubtitle(subtitle, false);
        } else {
            actionBarContainer.setTitleAnimated(title, false, animateCaption);
            actionBarContainer.setSubtitle(subtitle, animateCaption);
        }
        containerView.invalidate();
    }

    private void setImages(int previousIndex, boolean init) {
        if (init || previousIndex < 0 || Math.abs(currentIndex - previousIndex) != 1) {
            setIndexToImage(centerImage, currentIndex);
            setIndexToImage(leftImage, currentIndex - 1);
            setIndexToImage(rightImage, currentIndex + 1);
            return;
        }
        checkProgress(PROGRESS_CENTER_INDEX, true, false);
        if (previousIndex > currentIndex) {
            ImageReceiver temp = rightImage;
            rightImage = centerImage;
            centerImage = leftImage;
            leftImage = temp;
            swapProgressSlots(PROGRESS_CENTER_INDEX, PROGRESS_LEFT_INDEX);
            setIndexToImage(leftImage, currentIndex - 1);
            checkProgress(PROGRESS_RIGHT_INDEX, true, false);
            checkProgress(PROGRESS_LEFT_INDEX, true, false);
        } else {
            ImageReceiver temp = leftImage;
            leftImage = centerImage;
            centerImage = rightImage;
            rightImage = temp;
            swapProgressSlots(PROGRESS_CENTER_INDEX, PROGRESS_RIGHT_INDEX);
            setIndexToImage(rightImage, currentIndex + 1);
            checkProgress(PROGRESS_RIGHT_INDEX, true, false);
            checkProgress(PROGRESS_LEFT_INDEX, true, false);
        }
        centerImage.setAlpha(1f);
        leftImage.setAlpha(1f);
        rightImage.setAlpha(1f);
        centerImageLoaded = centerImage.getBitmap() != null;
        photoProgressLoaded[PROGRESS_CENTER_INDEX] = centerImageLoaded;
    }

    private void swapProgressSlots(int first, int second) {
        PhotoProgressView progressView = photoProgressViews[first];
        photoProgressViews[first] = photoProgressViews[second];
        photoProgressViews[second] = progressView;
        boolean loaded = photoProgressLoaded[first];
        photoProgressLoaded[first] = photoProgressLoaded[second];
        photoProgressLoaded[second] = loaded;
    }

    private void setIndexToImage(ImageReceiver receiver, int index) {
        if (index < 0 || index >= imageUrls.size()) {
            receiver.clearImage();
            return;
        }
        UrlPhotoItem item = photoItemAt(index);
        String url = imageUrls.get(index);
        ImageLocation imageLocation = item != null && item.imageLocation != null ? item.imageLocation : imageLocationForPath(url);
        if (imageLocation == null) {
            receiver.clearImage();
            return;
        }
        ImageLocation thumbLocation = item != null ? item.thumbLocation : null;
        Object parentObject = item != null && item.parentObject != null ? item.parentObject : "photoviewer_urls";
        int progressIndex = progressIndexForReceiver(receiver);
        if (progressIndex >= 0) {
            photoProgressLoaded[progressIndex] = false;
            if (receiver == centerImage) {
                centerImageLoaded = false;
            }
            updateProgressVisibility(progressIndex);
        }
        Drawable thumbDrawable = null;
        if (receiver == centerImage) {
            if (currentThumb == null && placeProvider != null) {
                currentThumb = placeProvider.getThumbForPhoto(null, null, index);
            }
            if (currentThumb != null && !currentThumb.isRecycled()) {
                if (currentThumb.drawable != null) {
                    thumbDrawable = currentThumb.drawable;
                } else if (currentThumb.bitmap != null) {
                    thumbDrawable = new BitmapDrawable(containerView.getResources(), currentThumb.bitmap);
                }
            }
        }
        receiver.setImage(imageLocation, null, thumbLocation, null, thumbDrawable, 0, null, parentObject, 1);
    }

    private void updateProgressVisibility() {
        for (int i = 0; i < photoProgressViews.length; i++) {
            checkProgress(i, false, true);
        }
    }

    private void updateProgressVisibility(int progressIndex) {
        checkProgress(progressIndex, false, true);
    }

    private void checkProgress(int a, boolean scroll, boolean animated) {
        if (a < 0 || a >= photoProgressViews.length) {
            return;
        }
        PhotoProgressView progressView = photoProgressViews[a];
        int photoIndex = currentIndex;
        if (a == PROGRESS_RIGHT_INDEX) {
            photoIndex += 1;
        } else if (a == PROGRESS_LEFT_INDEX) {
            photoIndex -= 1;
        }
        String progressKey = currentFileNames[a];
        boolean fileExists = fileExistsForIndex(photoIndex);
        if (fileExists) {
            photoProgressLoaded[a] = true;
            if (a == PROGRESS_CENTER_INDEX) {
                centerImageLoaded = true;
            }
        }
        boolean show = !TextUtils.isEmpty(progressKey) && !photoProgressLoaded[a] && !fileExists && !closing;
        Float progress = show ? ImageLoader.getInstance().getFileProgress(progressKey) : null;
        if (show && progress != null && progress > 0f) {
            progressView.setBackgroundState(PROGRESS_CANCEL, animated, true);
        } else if (show) {
            progressView.setBackgroundState(PROGRESS_EMPTY, animated, true);
        } else {
            progressView.setBackgroundState(PROGRESS_NONE, animated, true);
        }
        progressView.setProgress(show && progress != null ? progress : show ? 0f : 1f, show && progress != null);
    }

    private void didReceiveFileLoadNotification(int id, int account, Object... args) {
        if (args == null || args.length == 0) {
            return;
        }
        String progressKey = args[0] instanceof String ? (String) args[0] : null;
        int progressIndex = progressIndexForKey(progressKey);
        if (progressIndex < 0) {
            return;
        }
        PhotoProgressView progressView = photoProgressViews[progressIndex];
        if (id == NotificationCenter.fileLoadProgressChanged) {
            Float progress = progressFromNotification(args, progressKey);
            progressView.setBackgroundState(photoProgressLoaded[progressIndex] || closing ? PROGRESS_NONE : PROGRESS_EMPTY, true, true);
            progressView.setProgress(progress == null ? 0f : progress, true);
        } else if (id == NotificationCenter.fileLoaded) {
            progressView.setProgress(1f, true);
            photoProgressLoaded[progressIndex] = true;
            if (progressIndex == PROGRESS_CENTER_INDEX) {
                centerImageLoaded = true;
            }
            updateProgressVisibility(progressIndex);
            // Auto-save after download if pending
            if (pendingSaveAfterDownload && progressIndex == PROGRESS_CENTER_INDEX) {
                pendingSaveAfterDownload = false;
                File cacheFile = cacheFileForIndex(currentIndex);
                if (cacheFile != null && cacheFile.exists()) {
                    MediaController.getInstance().saveFile(cacheFile.getAbsolutePath(), parentActivity, 0, null, null, uri -> {
                        if (uri != null && parentActivity != null) {
                            AndroidUtilities.runOnUIThread(() -> {
                                try {
                                    Toast.makeText(parentActivity, parentActivity.getString(R.string.SaveToGallery), Toast.LENGTH_SHORT).show();
                                } catch (Exception ignore) {}
                            });
                        }
                    });
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            updateProgressVisibility(progressIndex);
        }
        containerView.invalidate();
    }
    private Float progressFromNotification(Object[] args, String progressKey) {
        if (args.length >= 3 && args[1] instanceof Number && args[2] instanceof Number) {
            long loaded = ((Number) args[1]).longValue();
            long total = ((Number) args[2]).longValue();
            if (total > 0) {
                return Math.min(1f, loaded / (float) total);
            }
        }
        return ImageLoader.getInstance().getFileProgress(progressKey);
    }

    private boolean hasLeftImage() {
        return currentIndex > 0;
    }

    private boolean hasRightImage() {
        return currentIndex < imageUrls.size() - 1;
    }

    private void updateImageBounds() {
        updateTargetImageRect();
        float currentScale;
        float currentTranslationX;
        float currentTranslationY;
        if (imageMoveAnimation != null) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            currentScale = scale + (animateToScale - scale) * animationValue;
            currentTranslationX = translationX + (animateToX - translationX) * animationValue;
            currentTranslationY = translationY + (animateToY - translationY) * animationValue;
            updateMinMax(currentScale);
            containerView.invalidate();
        } else {
            if (animationStartTime != 0) {
                translationX = animateToX;
                translationY = animateToY;
                scale = animateToScale;
                animationStartTime = 0;
                updateMinMax(scale);
                zoomAnimation = false;
            }
            if (!scroller.isFinished()) {
                if (scroller.computeScrollOffset()) {
                    if (scroller.getStartX() < maxX && scroller.getStartX() > minX) {
                        translationX = scroller.getCurrX();
                    }
                    if (scroller.getStartY() < maxY && scroller.getStartY() > minY) {
                        translationY = scroller.getCurrY();
                    }
                    containerView.invalidate();
                }
            }
            if (switchImageAfterAnimation != 0) {
                if (switchImageAfterAnimation == 1) {
                    AndroidUtilities.runOnUIThread(() -> switchToNextIndex(1, false));
                } else if (switchImageAfterAnimation == 2) {
                    AndroidUtilities.runOnUIThread(() -> switchToNextIndex(-1, false));
                }
                switchImageAfterAnimation = 0;
            }
            currentScale = scale;
            currentTranslationX = translationX;
            currentTranslationY = translationY;
        }
        updateWindowBackgroundAlpha();
        float cx = targetImageRect.centerX();
        float cy = targetImageRect.centerY();
        float halfWidth = targetImageRect.width() * currentScale / 2f;
        float halfHeight = targetImageRect.height() * currentScale / 2f;
        centerImageRect.set(cx - halfWidth + currentTranslationX, cy - halfHeight + currentTranslationY, cx + halfWidth + currentTranslationX, cy + halfHeight + currentTranslationY);
        centerImage.setImageCoords(centerImageRect);
        float pageDistance = sideImagePageDistance(currentScale);
        updateSideImageBounds(leftImage, leftImageRect, currentIndex - 1, currentTranslationX - pageDistance);
        updateSideImageBounds(rightImage, rightImageRect, currentIndex + 1, currentTranslationX + pageDistance);
    }

    private float sideImagePageDistance(float currentScale) {
        return (getContainerViewWidth() * (currentScale + 1f) + AndroidUtilities.dp(30f)) / 2f;
    }

    private float getCurrentScale() {
        return imageMoveAnimation == null ? scale : scale + (animateToScale - scale) * animationValue;
    }

    private float getCurrentTranslationX() {
        return imageMoveAnimation == null ? translationX : translationX + (animateToX - translationX) * animationValue;
    }

    private float getCurrentTranslationY() {
        return imageMoveAnimation == null ? translationY : translationY + (animateToY - translationY) * animationValue;
    }

    private void updateBottomControlsLayout() {
        boolean showGroupedPhotos = shouldShowGroupedPhotos();
        boolean showBottomLayout = shouldShowBottomLayout();
        FrameLayout.LayoutParams groupedParams = (FrameLayout.LayoutParams) groupedPhotosListView.getLayoutParams();
        int groupedBottomMargin = showBottomLayout ? AndroidUtilities.dp(48f) : 0;
        if (groupedParams.bottomMargin != groupedBottomMargin) {
            groupedParams.bottomMargin = groupedBottomMargin;
            groupedPhotosListView.setLayoutParams(groupedParams);
        }
        groupedPhotosListView.setEnabled(showGroupedPhotos);
        groupedPhotosListView.setVisibility(showGroupedPhotos ? View.VISIBLE : View.INVISIBLE);
        bottomLayout.setTag(showBottomLayout ? 1 : null);
        bottomLayout.setVisibility(showBottomLayout ? View.VISIBLE : View.GONE);
        updateCaptionView();
    }

    private boolean shouldShowBottomLayout() {
        return bottomLayout.getChildCount() > 0;
    }

    private boolean shouldShowGroupedPhotos() {
        if (imageUrls.size() <= 1) {
            return false;
        }
        int width = getContainerViewWidth();
        int height = getContainerViewHeight();
        return AndroidUtilities.isTablet() || width <= 0 || height <= 0 || height >= width;
    }

    private int getBottomControlsHeight() {
        int bottom = shouldShowBottomLayout() ? AndroidUtilities.dp(48f) : 0;
        if (shouldShowGroupedPhotos()) {
            bottom += AndroidUtilities.dp(68f);
        }
        if (captionVisible) {
            bottom += captionScrollView.getMeasuredHeight();
        }
        return bottom;
    }

    private void updateWindowBackgroundAlpha() {
        if (animationInProgress != 0) {
            return;
        }
        int backgroundAlpha = 255;
        float currentScale = getCurrentScale();
        float currentTranslationY = getCurrentTranslationY();
        if (currentScale <= MIN_SCALE + 0.0001f && currentTranslationY != 0f && !zoomAnimation) {
            float maxValue = Math.max(1f, getContainerViewWidth() / 4.0f);
            float progress = Math.min(Math.abs(currentTranslationY), maxValue) / maxValue;
            backgroundAlpha = (int) Math.max(127, 255 * (1.0f - progress));
        }
        backgroundDrawable.setAlpha(backgroundAlpha);
    }

    private void updateSideImageBounds(ImageReceiver receiver, RectF rect, int index, float offsetX) {
        if (index < 0 || index >= imageUrls.size()) {
            rect.setEmpty();
            receiver.setImageCoords(rect);
            return;
        }
        updateTargetImageRectForIndex(rect, index, receiver);
        rect.offset(offsetX, 0f);
        receiver.setImageCoords(rect);
    }

    private void updateTargetImageRect() {
        updateTargetImageRectForIndex(targetImageRect, currentIndex, centerImage);
        updateMinMax(scale);
        updateOpenClippingTargetValues();
    }

    private void updateTargetImageRectForIndex(RectF rect, int index, ImageReceiver receiver) {
        int containerWidth = getContainerViewWidth();
        int containerHeight = getContainerViewHeight();
        if (containerWidth <= 0 || containerHeight <= 0) {
            rect.setEmpty();
            return;
        }
        float availableLeft = 0f;
        float availableTop = 0f;
        float availableRight = containerWidth;
        float availableBottom = containerHeight;
        float availableWidth = Math.max(1f, availableRight - availableLeft);
        float availableHeight = Math.max(1f, availableBottom - availableTop);
        float aspect = imageAspectRatioForIndex(index, receiver);
        float width = availableWidth;
        float height = width / aspect;
        if (height > availableHeight) {
            height = availableHeight;
            width = height * aspect;
        }
        float left = availableLeft + (availableWidth - width) / 2f;
        float top = availableTop + (availableHeight - height) / 2f;
        rect.set(left, top, left + width, top + height);
    }

    private float imageAspectRatioForIndex(int index, ImageReceiver receiver) {
        UrlPhotoItem item = photoItemAt(index);
        if (item != null && item.width > 0 && item.height > 0) {
            return Math.max(0.1f, Math.min(10f, item.width / (float) item.height));
        }
        android.graphics.Bitmap bitmap = receiver == null ? null : receiver.getBitmap();
        if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            return Math.max(0.1f, Math.min(10f, bitmap.getWidth() / (float) bitmap.getHeight()));
        }
        PlaceProviderObject place = placeProvider == null ? null : placeProvider.getPlaceForPhoto(null, null, index, false, false);
        if (place != null && place.viewWidth > 0 && place.viewHeight > 0) {
            return Math.max(0.1f, Math.min(10f, place.viewWidth / (float) place.viewHeight));
        }
        return Math.max(0.1f, getContainerViewWidth() / (float) Math.max(1, getContainerViewHeight()));
    }

    private void updateOpenClippingTargetValues() {
        if (!openAnimationInProgress || animatingImageView.getVisibility() != View.VISIBLE || targetImageRect.isEmpty()) {
            return;
        }
        ViewGroup.LayoutParams params = animatingImageView.getLayoutParams();
        if (params == null || params.width <= 0 || params.height <= 0) {
            return;
        }
        fillClippingAnimationValues(1, null, null, params.width, params.height, false);
    }

    private int getContainerViewWidth() {
        return containerView.getWidth();
    }

    private int getContainerViewHeight() {
        return containerView.getHeight();
    }

    private int getLeftInset() {
        return insets.left;
    }

    private int getRightInset() {
        return insets.right;
    }

    private void onPhotoShow() {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        PlaceProviderObject place = currentPlaceObject;
        if (place == null || place.imageReceiver == null) {
            place = placeProvider == null ? null : placeProvider.getPlaceForPhoto(null, null, currentIndex, true, false);
        }
        if (place != null) {
            currentPlaceObject = place;
            if (currentThumb == null && place.thumb instanceof ImageReceiver.BitmapHolder) {
                currentThumb = (ImageReceiver.BitmapHolder) place.thumb;
            }
        }
        animationInProgress = 1;
        openAnimationInProgress = true;
        transitionAnimationStartTime = System.currentTimeMillis();
        backgroundDrawable.setAlpha(0);
        containerView.setAlpha(0f);
        navigationBar.setAlpha(0f);
        boolean clippingOpen = prepareClippingImage(place, true);
        if (!clippingOpen && (getContainerViewWidth() == 0 || getContainerViewHeight() == 0)) {
            pendingOpenAnimation = true;
            return;
        }
        pendingOpenAnimation = false;
        if (!clippingOpen) {
            releasePlaceThumb(place);
        }
        final PlaceProviderObject openingPlace = place;
        final ClippingImageView[] animatingImageViews = clippingOpen ? getAnimatingImageViews(place) : new ClippingImageView[]{animatingImageView};
        boolean fadeContainerOpen = !clippingOpen;
        boolean fadeChromeOpen = !clippingOpen;
        clippingImageProgress = clippingOpen ? 1f : 0f;
        centerImage.setAlpha(clippingOpen ? 0f : 1f);
        containerView.setAlpha(clippingOpen || fadeContainerOpen ? 0f : 1f);
        float targetChromeAlpha = isActionBarVisible ? 1f : 0f;
        updateActionBarAlpha(fadeChromeOpen ? 0f : targetChromeAlpha, fadeContainerOpen);
        ValueAnimator progressAnimator = clippingOpen
                ? ObjectAnimator.ofFloat(animatingImageViews[0], AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0f, 1f)
                : ValueAnimator.ofFloat(0f, 1f);
        progressAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            clippingImageProgress = 1f - progress;
            if (!clippingOpen) {
                centerImage.setAlpha(1f);
                if (fadeContainerOpen) {
                    containerView.setAlpha(progress);
                }
            }
            updateActionBarAlpha(fadeChromeOpen ? targetChromeAlpha * progress : targetChromeAlpha, fadeContainerOpen);
            updateImageBounds();
            containerView.invalidate();
        });
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(progressAnimator);
        if (clippingOpen) {
            for (int i = 1; i < animatingImageViews.length; i++) {
                animators.add(ObjectAnimator.ofFloat(animatingImageViews[i], AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0f, 1f));
            }
        }
        if (clippingOpen) {
            animators.add(ObjectAnimator.ofFloat(containerView, View.ALPHA, 0f, 1f));
        }
        animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0, 255));
        animators.add(ObjectAnimator.ofFloat(navigationBar, View.ALPHA, 0f, isActionBarVisible ? 1f : 0f));
        openAnimator = new AnimatorSet();
        openAnimator.playTogether(animators);
        openAnimator.setDuration(200);
        EmptyPhotoViewerProvider provider = placeProvider;
        animationEndRunnable = () -> {
            animationEndRunnable = null;
            openAnimator = null;
            animationInProgress = 0;
            openAnimationInProgress = false;
            transitionAnimationStartTime = 0;
            clippingImageProgress = 0f;
            containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            clearClippingImage();
            if (clippingOpen && openingPlace != null && openingPlace == currentPlaceObject && openingPlace.imageReceiver != null) {
                openingPlace.imageReceiver.setVisible(false, true);
            }
            if (showAfterAnimation != null && showAfterAnimation.imageReceiver != null) {
                showAfterAnimation.imageReceiver.setVisible(true, true);
            }
            if (hideAfterAnimation != null && hideAfterAnimation.imageReceiver != null) {
                hideAfterAnimation.imageReceiver.setVisible(false, true);
            }
            showAfterAnimation = null;
            hideAfterAnimation = null;
            containerView.setAlpha(1f);
            centerImage.setAlpha(1f);
            updateImageBounds();
            containerView.invalidate();
            if (provider != null) {
                provider.onOpen();
            }
        };
        openAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (openAnimator == animation) {
                    AndroidUtilities.runOnUIThread(() -> {
                        transitionNotificationLocker.unlock();
                        if (animationEndRunnable != null) {
                            animationEndRunnable.run();
                            animationEndRunnable = null;
                        }
                    });
                }
            }
        });
        containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        transitionAnimationStartTime = System.currentTimeMillis();
        AnimatorSet animatorToStart = openAnimator;
        if (clippingOpen && windowView != null) {
            windowView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (windowView != null) {
                        windowView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    if (openAnimator != animatorToStart || animationInProgress != 1) {
                        return true;
                    }
                    updateTargetImageRect();
                    updateOpenClippingTargetValues();
                    if (animatingImageViews.length > 1) {
                        animatingImageViews[1].setAlpha(1.0f);
                        animatingImageViews[1].setAdditionalTranslationX(-getLeftInset());
                    }
                    animationValues[0][2] += getLeftInset();
                    for (ClippingImageView view : animatingImageViews) {
                        view.setAnimationProgress(0f);
                    }
                    backgroundDrawable.setAlpha(0);
                    containerView.setAlpha(0);
                    navigationBar.setAlpha(0);
                    backgroundDrawable.drawRunnable = () -> {
                        if (openingPlace == currentPlaceObject && openingPlace.imageReceiver != null) {
                            openingPlace.imageReceiver.setVisible(false, true);
                        }
                    };
                    startOpenAnimation(provider, animatorToStart);
                    return true;
                }
            });
            windowView.invalidate();
            return;
        }
        startOpenAnimation(provider, animatorToStart);
    }

    private void startOpenAnimation(EmptyPhotoViewerProvider provider, AnimatorSet animator) {
        if (provider != null) {
            provider.onPreOpen();
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (openAnimator != animator) {
                return;
            }
            transitionNotificationLocker.lock();
            animator.start();
        });
    }

    private boolean closePhoto() {
        if (closing) {
            return true;
        }
        if (getContainerViewWidth() == 0 || getContainerViewHeight() == 0) {
            return false;
        }
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator = null;
        }
        if (closeAnimator != null) {
            closeAnimator.cancel();
        }
        cancelMoveZoomAnimation();
        switchImageAfterAnimation = 0;
        float wasScaleX = 1f;
        float wasScaleY = 1f;
        View scaledParentView = getParentScaleView();
        if (scaledParentView != null) {
            wasScaleX = scaledParentView.getScaleX();
            wasScaleY = scaledParentView.getScaleY();
            scaledParentView.setScaleX(1f);
            scaledParentView.setScaleY(1f);
        }
        PlaceProviderObject place;
        boolean clippingClose;
        try {
            place = placeProvider == null ? null : placeProvider.getPlaceForPhoto(null, null, currentIndex, true, true);
            clippingClose = prepareClippingImage(place, false);
            closeToPlace = clippingClose;
            if (!clippingClose) {
                releasePlaceThumb(place);
            }
        } finally {
            if (scaledParentView != null) {
                scaledParentView.setScaleX(wasScaleX);
                scaledParentView.setScaleY(wasScaleY);
            }
        }
        closing = true;
        if (clippingClose && place != null && place.imageReceiver != null) {
            place.imageReceiver.setVisible(false, true);
        }
        final ClippingImageView[] animatingImageViews = clippingClose ? getAnimatingImageViews(place) : new ClippingImageView[]{animatingImageView};
        animationInProgress = clippingClose ? 3 : 2;
        transitionAnimationStartTime = System.currentTimeMillis();
        clippingImageProgress = 0f;
        if (clippingClose) {
            setParentAllowDrawContent(true);
        }
        centerImage.setAlpha(clippingClose ? 0f : 1f);
        if (!closeToPlace) {
            containerView.setPivotX(containerView.getWidth() / 2f);
            containerView.setPivotY(containerView.getHeight() / 2f);
            containerView.setScaleX(1f);
            containerView.setScaleY(1f);
            containerView.setAlpha(1f);
        }
        actionBar.setEnabled(false);
        ValueAnimator progressAnimator = clippingClose
                ? ObjectAnimator.ofFloat(animatingImageViews[0], AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0f, 1f)
                : ValueAnimator.ofFloat(0f, 1f);
        progressAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            clippingImageProgress = progress;
            if (clippingClose) {
                centerImage.setAlpha(0f);
            } else if (!closeToPlace) {
                float scale = lerp(1f, 0.9f, progress);
                containerView.setScaleX(scale);
                containerView.setScaleY(scale);
                containerView.setAlpha(1f - progress);
            } else {
                centerImage.setAlpha(0f);
            }
            updateActionBarAlpha((isActionBarVisible ? 1f : 0f) * (1f - progress));
            updateImageBounds();
            containerView.invalidate();
        });
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(progressAnimator);
        if (clippingClose) {
            for (int i = 1; i < animatingImageViews.length; i++) {
                animators.add(ObjectAnimator.ofFloat(animatingImageViews[i], AnimationProperties.CLIPPING_IMAGE_VIEW_PROGRESS, 0f, 1f));
            }
        }
        animators.add(ObjectAnimator.ofInt(backgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0));
        animators.add(ObjectAnimator.ofFloat(navigationBar, View.ALPHA, 0f));
        closeAnimator = new AnimatorSet();
        closeAnimator.playTogether(animators);
        closeAnimator.setDuration(200);
        EmptyPhotoViewerProvider provider = placeProvider;
        if (provider != null) {
            provider.onPreClose();
        }
        animationEndRunnable = () -> {
            animationEndRunnable = null;
            closing = false;
            closeAnimator = null;
            animationInProgress = 0;
            transitionAnimationStartTime = 0;
            containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            resetParentScaleView();
            clearClippingImage(place);
            this.onPhotoClosed(place);
        };
        closeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (closeAnimator == animation && animationEndRunnable != null) {
                    clippingImageProgress = 1f;
                    animationEndRunnable.run();
                }
            }
        });
        containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        closeAnimator.start();
        return true;
    }

    private boolean prepareClippingImage(PlaceProviderObject place, boolean opening) {
        if (opening && (getContainerViewWidth() == 0 || getContainerViewHeight() == 0)) {
            targetImageRect.setEmpty();
        } else {
            updateTargetImageRect();
        }
        ImageReceiver.BitmapHolder bitmapHolder = bitmapHolderForAnimation(place);
        if (place == null || bitmapHolder == null || bitmapHolder.isRecycled() || (!opening && targetImageRect.isEmpty())) {
            if (bitmapHolder != null) {
                bitmapHolder.release();
            }
            return false;
        }
        RectF drawRegion = place != null && place.imageReceiver != null ? place.imageReceiver.getDrawRegion() : null;
        boolean hasSourceReceiver = drawRegion != null && !drawRegion.isEmpty();
        int width = Math.max(1, Math.round(hasSourceReceiver ? drawRegion.width() : place.viewWidth));
        int height = Math.max(1, Math.round(hasSourceReceiver ? drawRegion.height() : place.viewHeight));
        if (width <= 0 || height <= 0) {
            bitmapHolder.release();
            return false;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) animatingImageView.getLayoutParams();
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            animatingImageView.setLayoutParams(params);
        }
        int orientation = place != null && place.imageReceiver != null ? place.imageReceiver.getOrientation() : bitmapHolder.orientation;
        int animatedOrientation = place != null && place.imageReceiver != null ? place.imageReceiver.getAnimatedOrientation() : 0;
        if (animatedOrientation != 0) {
            orientation = animatedOrientation;
        }
        int invert = place != null && place.imageReceiver != null ? place.imageReceiver.getInvert() : 0;
        ClippingImageView[] animatingImageViews = getAnimatingImageViews(place);
        for (int i = 0; i < animatingImageViews.length; i++) {
            ClippingImageView view = animatingImageViews[i];
            view.setPivotX(0f);
            view.setPivotY(0f);
            view.setAdditionalTranslationX(0f);
            view.setAdditionalTranslationY(i == 0 ? 0f : place.animatingImageViewYOffset);
            view.setOrientation(orientation, invert);
            view.setImageBitmap(bitmapHolder);
            view.setAnimationValues(animationValues, opening, place.fadeIn);
            view.setVisibility(View.VISIBLE);
            view.setAlpha(1f);
            if (place.radius != null) {
                view.setRadius(place.radius);
            }
        }
        if (opening) {
            fillClippingAnimationValues(0, place, drawRegion, width, height, true);
            if (targetImageRect.isEmpty()) {
                System.arraycopy(animationValues[0], 0, animationValues[1], 0, animationValues[0].length);
            } else {
                fillClippingAnimationValues(1, null, null, width, height, false);
            }
        } else {
            fillClippingAnimationValues(0, null, null, width, height, false);
            fillClippingAnimationValues(1, place, drawRegion, width, height, true);
        }
        for (ClippingImageView view : animatingImageViews) {
            view.setAnimationProgress(0f);
        }
        return true;
    }

    private ImageReceiver.BitmapHolder bitmapHolderForAnimation(PlaceProviderObject place) {
        if (currentThumb != null && !currentThumb.isRecycled()) {
            return currentThumb;
        }
        releaseCurrentThumb();
        if (place != null && place.thumb instanceof ImageReceiver.BitmapHolder) {
            currentThumb = (ImageReceiver.BitmapHolder) place.thumb;
            return currentThumb;
        }
        ImageReceiver.BitmapHolder placeProviderThumb = placeProvider == null ? null : placeProvider.getThumbForPhoto(null, null, currentIndex);
        if (placeProviderThumb != null && !placeProviderThumb.isRecycled()) {
            currentThumb = placeProviderThumb;
            return currentThumb;
        }
        if (placeProviderThumb != null) {
            placeProviderThumb.release();
        }
        currentThumb = place != null && place.imageReceiver != null ? place.imageReceiver.getBitmapSafe() : null;
        if (currentThumb == null) {
            currentThumb = centerImage.getBitmapSafe();
        }
        return currentThumb;
    }

    private void releasePlaceThumb(PlaceProviderObject place) {
        if (place != null && place.thumb instanceof ImageReceiver.BitmapHolder) {
            if (place.thumb != currentThumb) {
                ((ImageReceiver.BitmapHolder) place.thumb).release();
            }
            place.thumb = null;
        }
    }

    private void fillClippingAnimationValues(int index, PlaceProviderObject place, RectF drawRegion, int width, int height, boolean source) {
        if (source && place != null) {
            float left = drawRegion != null && !drawRegion.isEmpty() ? drawRegion.left : 0f;
            float top = drawRegion != null && !drawRegion.isEmpty() ? drawRegion.top : 0f;
            float sourceScale = place.scale;
            animationValues[index][0] = sourceScale;
            animationValues[index][1] = sourceScale;
            animationValues[index][2] = place.viewX + left * sourceScale;
            animationValues[index][3] = place.viewY + top * sourceScale;
            int clipHorizontal = 0;
            int clipVertical = 0;
            if (drawRegion != null && place.imageReceiver != null) {
                clipHorizontal = (int) Math.abs(drawRegion.left - place.imageReceiver.getImageX());
                clipVertical = (int) Math.abs(drawRegion.top - place.imageReceiver.getImageY());
                if (place.imageReceiver.isAspectFit()) {
                    clipHorizontal = 0;
                }
            }
            int clipTop = clipVertical;
            int clipBottom = clipVertical;
            if (place.parentView != null) {
                int[] coords = new int[2];
                place.parentView.getLocationInWindow(coords);
                clipTop = (int) (coords[1] - (place.viewY + top) + place.clipTopAddition);
                clipBottom = (int) (place.viewY + top + height - (coords[1] + place.parentView.getHeight()) + place.clipBottomAddition);
                clipTop = Math.max(Math.max(clipTop, 0), clipVertical);
                clipBottom = Math.max(Math.max(clipBottom, 0), clipVertical);
            }
            animationValues[index][4] = clipHorizontal * sourceScale;
            animationValues[index][5] = clipTop * sourceScale;
            animationValues[index][6] = clipBottom * sourceScale;
            for (int i = 0; i < 4; i++) {
                animationValues[index][7 + i] = place.radius != null ? place.radius[i] : 0;
            }
            animationValues[index][11] = clipVertical * sourceScale;
            animationValues[index][12] = clipHorizontal * sourceScale;
        } else {
            float targetScale = Math.min(targetImageRect.width() / width, targetImageRect.height() / height) * scale;
            animationValues[index][0] = targetScale;
            animationValues[index][1] = targetScale;
            animationValues[index][2] = targetImageRect.centerX() - width * targetScale / 2f + translationX;
            animationValues[index][3] = targetImageRect.centerY() - height * targetScale / 2f + translationY;
            animationValues[index][4] = 0;
            animationValues[index][5] = 0;
            animationValues[index][6] = 0;
            for (int i = 0; i < 4; i++) {
                animationValues[index][7 + i] = 0;
            }
            animationValues[index][11] = 0;
            animationValues[index][12] = 0;
        }
    }

    private void clearClippingImage() {
        clearClippingImage(currentPlaceObject);
    }

    private void clearClippingImage(PlaceProviderObject object) {
        ClippingImageView[] animatingImageViews = getAnimatingImageViews(object);
        for (ClippingImageView view : animatingImageViews) {
            view.setVisibility(View.GONE);
            view.setImageBitmap(null);
        }
    }

    private ClippingImageView[] getAnimatingImageViews(PlaceProviderObject object) {
        final boolean hasSecondAnimatingImageView = !AndroidUtilities.isTablet() && object != null && object.animatingImageView != null;
        final ClippingImageView[] animatingImageViews = new ClippingImageView[1 + (hasSecondAnimatingImageView ? 1 : 0)];
        animatingImageViews[0] = animatingImageView;
        if (hasSecondAnimatingImageView) {
            animatingImageViews[1] = object.animatingImageView;
            object.animatingImageView.setAdditionalTranslationY(object.animatingImageViewYOffset);
        }
        return animatingImageViews;
    }

    private void updateActionBarAlpha(float alpha) {
        updateActionBarAlpha(alpha, true);
    }

    private void updateActionBarAlpha(float alpha, boolean translate) {
        if (actionBarAnimator != null) {
            actionBarAnimator.cancel();
            actionBarAnimator = null;
            actionBarAnimating = false;
        }
        chromeAlpha = Utilities.clamp(alpha, 1f, 0f);
        float offsetY = translate ? AndroidUtilities.dp(24f) * (1f - chromeAlpha) : 0f;
        actionBar.setAlpha(chromeAlpha);
        actionBar.setTranslationY(-offsetY);
        countView.setAlpha(chromeAlpha);
        countView.setTranslationY(-offsetY);
        navigationBar.setAlpha(chromeAlpha);
        bottomLayout.setAlpha(chromeAlpha);
        bottomLayout.setTranslationY(offsetY);
        captionScrollView.setAlpha(chromeAlpha);
        captionScrollView.setTranslationY(offsetY);
        updateProgressVisibility();
        groupedPhotosListView.setAlpha(chromeAlpha);
        groupedPhotosListView.setTranslationY(offsetY);
        updateImageBounds();
        containerView.invalidate();
    }

    private static class ActionBarToggleParams {
        public boolean enableStatusBarAnimation;
        public boolean enableTranslationAnimation;
        public int animationDuration;
        public Interpolator animationInterpolator;

        public static final ActionBarToggleParams DEFAULT = new ActionBarToggleParams();

        public ActionBarToggleParams() {
            enableStatusBarAnimation = true;
            enableTranslationAnimation = true;
            animationDuration = 200;
            animationInterpolator = CubicBezierInterpolator.DEFAULT;
        }

        public ActionBarToggleParams enableStatusBarAnimation(boolean val) {
            enableStatusBarAnimation = val;
            return this;
        }

        public ActionBarToggleParams enableTranslationAnimation(boolean val) {
            enableTranslationAnimation = val;
            return this;
        }

        public ActionBarToggleParams animationDuration(int val) {
            animationDuration = val;
            return this;
        }

        public ActionBarToggleParams animationInterpolator(Interpolator val) {
            animationInterpolator = val;
            return this;
        }
    }

    private void toggleActionBar(boolean show, boolean animated) {
        toggleActionBar(show, animated, ActionBarToggleParams.DEFAULT);
    }

    private void toggleActionBar(boolean show, boolean animated, @NonNull final ActionBarToggleParams params) {
        if (isActionBarVisible == show && !actionBarAnimating) {
            return;
        }
        if (actionBarAnimator != null) {
            actionBarAnimator.cancel();
        }
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
            if (bottomLayout.getTag() != null) {
                bottomLayout.setVisibility(View.VISIBLE);
            }
            if (captionTextViewSwitcher.getTag() != null || captionVisible) {
                captionTextViewSwitcher.setVisibility(View.VISIBLE);
                captionScrollView.setVisibility(View.VISIBLE);
            }
            if (groupedPhotosListView != null && shouldShowGroupedPhotos()) {
                groupedPhotosListView.setVisibility(View.VISIBLE);
            }
        }
        isActionBarVisible = show;
        if (params.enableStatusBarAnimation) {
            updateContainerFlags(show);
        }
        float target = show ? 1f : 0f;
        if (!animated) {
            actionBarAnimating = false;
            updateActionBarAlpha(target, params.enableTranslationAnimation);
            if (!show) {
                actionBar.setVisibility(View.INVISIBLE);
                if (bottomLayout.getTag() != null) {
                    bottomLayout.setVisibility(View.INVISIBLE);
                }
                if (captionTextViewSwitcher.getTag() != null || captionVisible) {
                    captionTextViewSwitcher.setVisibility(View.INVISIBLE);
                    captionScrollView.setVisibility(View.INVISIBLE);
                }
            }
            return;
        }
        actionBarAnimating = true;
        final float offsetY = AndroidUtilities.dpf2(24);
        ArrayList<Animator> animators = new ArrayList<>();
        ValueAnimator alphaAnimator = ValueAnimator.ofFloat(chromeAlpha, target);
        alphaAnimator.addUpdateListener(animation -> {
            chromeAlpha = (float) animation.getAnimatedValue();
            updateProgressVisibility();
            updateImageBounds();
            containerView.invalidate();
        });
        animators.add(alphaAnimator);
        animators.add(ObjectAnimator.ofFloat(actionBar, View.ALPHA, target));
        if (params.enableTranslationAnimation) {
            animators.add(ObjectAnimator.ofFloat(actionBar, View.TRANSLATION_Y, show ? 0f : -offsetY));
        } else {
            actionBar.setTranslationY(0f);
        }
        if (countView != null) {
            animators.add(ObjectAnimator.ofFloat(countView, View.ALPHA, target));
            if (params.enableTranslationAnimation) {
                animators.add(ObjectAnimator.ofFloat(countView, View.TRANSLATION_Y, show ? 0f : -offsetY));
            } else {
                countView.setTranslationY(0f);
            }
        }
        if (navigationBar != null) {
            animators.add(ObjectAnimator.ofFloat(navigationBar, View.ALPHA, target));
        }
        if (bottomLayout != null) {
            animators.add(ObjectAnimator.ofFloat(bottomLayout, View.ALPHA, target));
            if (params.enableTranslationAnimation) {
                animators.add(ObjectAnimator.ofFloat(bottomLayout, View.TRANSLATION_Y, show ? 0f : offsetY));
            } else {
                bottomLayout.setTranslationY(0f);
            }
        }
        if (groupedPhotosListView != null) {
            animators.add(ObjectAnimator.ofFloat(groupedPhotosListView, View.ALPHA, target));
            if (params.enableTranslationAnimation) {
                animators.add(ObjectAnimator.ofFloat(groupedPhotosListView, View.TRANSLATION_Y, show ? 0f : offsetY));
            } else {
                groupedPhotosListView.setTranslationY(0f);
            }
        }
        if (captionScrollView != null) {
            animators.add(ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, target));
            if (params.enableTranslationAnimation) {
                animators.add(ObjectAnimator.ofFloat(captionScrollView, View.TRANSLATION_Y, show ? 0f : offsetY));
            } else {
                captionScrollView.setTranslationY(0f);
            }
        }
        actionBarAnimator = new AnimatorSet();
        actionBarAnimator.playTogether(animators);
        actionBarAnimator.setDuration(params.animationDuration);
        actionBarAnimator.setInterpolator(params.animationInterpolator);
        actionBarAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (actionBarAnimator == animation) {
                    if (!show) {
                        actionBar.setVisibility(View.INVISIBLE);
                        if (bottomLayout.getTag() != null) {
                            bottomLayout.setVisibility(View.INVISIBLE);
                        }
                        if (captionTextViewSwitcher.getTag() != null || captionVisible) {
                            captionTextViewSwitcher.setVisibility(View.INVISIBLE);
                            captionScrollView.setVisibility(View.INVISIBLE);
                        }
                    }
                    actionBarAnimator = null;
                    actionBarAnimating = false;
                    updateImageBounds();
                }
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                if (actionBarAnimator == animation) {
                    actionBarAnimator = null;
                    actionBarAnimating = false;
                }
            }
        });
        actionBarAnimator.start();
    }

    private float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private void updateMinMax(float forScale) {
        float imageWidth = centerImage == null ? 0f : centerImage.getImageWidth();
        float imageHeight = centerImage == null ? 0f : centerImage.getImageHeight();
        if ((imageWidth <= 0f || imageHeight <= 0f) && !targetImageRect.isEmpty()) {
            imageWidth = targetImageRect.width();
            imageHeight = targetImageRect.height();
        }
        if (imageWidth <= 0f || imageHeight <= 0f) {
            minX = maxX = minY = maxY = 0f;
            return;
        }
        int maxW = (int) (imageWidth * forScale - getContainerViewWidth()) / 2;
        int maxH = (int) (imageHeight * forScale - getContainerViewHeight()) / 2;
        if (maxW > 0) {
            minX = -maxW;
            maxX = maxW;
        } else {
            minX = maxX = 0f;
        }
        if (maxH > 0) {
            minY = -maxH;
            maxY = maxH;
        } else {
            minY = maxY = 0f;
        }
    }

    private void checkMinMax(boolean zoom) {
        float moveToX = translationX;
        float moveToY = translationY;
        updateMinMax(scale);
        if (translationX < minX) {
            moveToX = minX;
        } else if (translationX > maxX) {
            moveToX = maxX;
        }
        if (translationY < minY) {
            moveToY = minY;
        } else if (translationY > maxY) {
            moveToY = maxY;
        }
        animateTo(scale, moveToX, moveToY, zoom);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        animateTo(newScale, newTx, newTy, isZoom, 250);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom, int duration) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            return;
        }
        zoomAnimation = isZoom;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        animationStartTime = System.currentTimeMillis();
        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(ObjectAnimator.ofFloat(this, AnimationProperties.PHOTO_VIEWER_ANIMATION_VALUE, 0f, 1f));
        imageMoveAnimation.setInterpolator(interpolator);
        imageMoveAnimation.setDuration(duration);
        imageMoveAnimation.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                imageMoveAnimation = null;
                containerView.invalidate();
            }
        });
        imageMoveAnimation.start();
    }

    private void goToNext() {
        float extra = 0;
        if (scale != MIN_SCALE) {
            float imageWidth = centerImage != null && centerImage.getImageWidth() > 0 ? centerImage.getImageWidth() : targetImageRect.width();
            extra = (getContainerViewWidth() - imageWidth) / 2f * scale;
        }
        switchImageAfterAnimation = 1;
        animateTo(scale, minX - getContainerViewWidth() - extra - AndroidUtilities.dp(30f) / 2f, translationY, false);
    }

    private void goToPrev() {
        float extra = 0;
        if (scale != MIN_SCALE) {
            float imageWidth = centerImage != null && centerImage.getImageWidth() > 0 ? centerImage.getImageWidth() : targetImageRect.width();
            extra = (getContainerViewWidth() - imageWidth) / 2f * scale;
        }
        switchImageAfterAnimation = 2;
        animateTo(scale, maxX + getContainerViewWidth() + extra + AndroidUtilities.dp(30f) / 2f, translationY, false);
    }

    private void switchToNextIndex(int add, boolean init) {
        if (groupedPhotosListView != null) {
            groupedPhotosListView.setAnimateBackground(true);
        }
        setImageIndex(currentIndex + add, init, true);
    }

    private void updateSlideshowCellForCurrentPhoto() {
        if (pageBlocksAdapter == null) {
            return;
        }
        UrlPhotoItem item = currentPhotoItem();
        if (item != null && item.pageBlock != null) {
            pageBlocksAdapter.updateSlideshowCell(item.pageBlock);
        }
    }

    private void cancelMoveZoomAnimation() {
        if (imageMoveAnimation == null) {
            return;
        }
        float ts = scale + (animateToScale - scale) * animationValue;
        float tx = translationX + (animateToX - translationX) * animationValue;
        float ty = translationY + (animateToY - translationY) * animationValue;
        imageMoveAnimation.cancel();
        scale = ts;
        translationX = tx;
        translationY = ty;
        animationStartTime = 0;
        zoomAnimation = false;
        updateMinMax(scale);
        containerView.invalidate();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void clearImages() {
        if (animatingImageView != null) {
            animatingImageView.setImageBitmap(null);
        }
        centerImage.setImageBitmap((Drawable) null);
        leftImage.setImageBitmap((Drawable) null);
        rightImage.setImageBitmap((Drawable) null);
    }

    private void resetImages() {
        centerImage = imageReceivers[1];
        leftImage = imageReceivers[0];
        rightImage = imageReceivers[2];
        currentFileNames[PROGRESS_CENTER_INDEX] = null;
        currentFileNames[PROGRESS_RIGHT_INDEX] = null;
        currentFileNames[PROGRESS_LEFT_INDEX] = null;
        photoProgressViews[PROGRESS_CENTER_INDEX] = photoProgressViewsRaw[PROGRESS_CENTER_INDEX];
        photoProgressViews[PROGRESS_RIGHT_INDEX] = photoProgressViewsRaw[PROGRESS_RIGHT_INDEX];
        photoProgressViews[PROGRESS_LEFT_INDEX] = photoProgressViewsRaw[PROGRESS_LEFT_INDEX];
        photoProgressLoaded[PROGRESS_CENTER_INDEX] = false;
        photoProgressLoaded[PROGRESS_RIGHT_INDEX] = false;
        photoProgressLoaded[PROGRESS_LEFT_INDEX] = false;
        centerImageLoaded = false;
        pendingSaveAfterDownload = false;
        switchImageAfterAnimation = 0;
        scale = MIN_SCALE;
        translationX = 0f;
        translationY = 0f;
        animateToScale = MIN_SCALE;
        animateToX = 0f;
        animateToY = 0f;
        animationValue = 0f;
        animationStartTime = 0;
        zoomAnimation = false;
    }

    private void release() {
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator = null;
        }
        if (closeAnimator != null) {
            closeAnimator.cancel();
            closeAnimator = null;
        }
        if (actionBarAnimator != null) {
            actionBarAnimator.cancel();
            actionBarAnimator = null;
        }
        cancelMoveZoomAnimation();
        switchImageAfterAnimation = 0;
        if (pendingSaveAfterDownload) {
            pendingSaveAfterDownload = false;
            String url = currentUrl();
            if (!TextUtils.isEmpty(url) && isHttpUrl(url)) {
                ImageLoader.getInstance().cancelLoadHttpFile(url);
            }
        }
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
        containerView.setAlpha(1f);
        containerView.setScaleX(1f);
        containerView.setScaleY(1f);
        centerImage.setAlpha(1f);
        closing = false;
        closeToPlace = false;
        resetParentScaleView();
        clearClippingImage();
        recycleVelocityTracker();
        for (ImageReceiver receiver : imageReceivers) {
            receiver.onDetachedFromWindow();
        }
    }

    private class FrameLayoutDrawer extends SizeNotifierFrameLayoutPhoto {
        private Paint paint = new Paint();
        private boolean ignoreLayout;
        private boolean captionAbove;

        public FrameLayoutDrawer(Context context, Activity activity) {
            super(context, activity, false);
            setWillNotDraw(false);
            paint.setColor(0x33000000);
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            if (getLayoutParams().height > 0) {
                heightSize = getLayoutParams().height;
            }

            setMeasuredDimension(widthSize, heightSize);

            if (!isCurrentVideo) {
                ignoreLayout = true;
                if (needCaptionLayout) {
                    final int maxLines = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 5 : 10;
                    captionTextViewSwitcher.getCurrentView().setMaxLines(maxLines);
                    captionTextViewSwitcher.getNextView().setMaxLines(maxLines);
                } else {
                    captionTextViewSwitcher.getCurrentView().setMaxLines(Integer.MAX_VALUE);
                    captionTextViewSwitcher.getNextView().setMaxLines(Integer.MAX_VALUE);
                }
                ignoreLayout = false;
            }

            final int bottomLayoutHeight = bottomLayout.getVisibility() != GONE ? dp(48) : 0;
            final int groupedPhotosHeight;
            if (groupedPhotosListView != null && groupedPhotosListView.getVisibility() != GONE) {
                MarginLayoutParams lp = (MarginLayoutParams) groupedPhotosListView.getLayoutParams();
                lp.bottomMargin = bottomLayoutHeight;
                measureChildWithMargins(groupedPhotosListView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                groupedPhotosHeight = groupedPhotosListView.getMeasuredHeight();

                ignoreLayout = true;
                if (!AndroidUtilities.isTablet() && heightSize < widthSize) {
                    if (groupedPhotosListView.getVisibility() != INVISIBLE) {
                        groupedPhotosListView.setVisibility(INVISIBLE);
                    }
                } else {
                    if (groupedPhotosListView.getVisibility() != VISIBLE) {
                        groupedPhotosListView.setVisibility(VISIBLE);
                    }
                }
                ignoreLayout = false;
            } else {
                groupedPhotosHeight = 0;
            }

            if (videoPlayerControlFrameLayout != null) {
                videoPlayerControlFrameLayout.parentWidth = widthSize;
                videoPlayerControlFrameLayout.parentHeight = heightSize;
            }

            widthSize -= (getPaddingRight() + getPaddingLeft());
            heightSize -= getPaddingBottom();

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == groupedPhotosListView) {
                    continue;
                }
                if (child == captionScrollView) {
                    int bottomMargin = bottomLayoutHeight;
                    if (dontChangeCaptionPosition) {
                        if (captionAbove) {
                            bottomMargin += groupedPhotosHeight;
                        }
                    } else if (groupedPhotosListView.hasPhotos() && (AndroidUtilities.isTablet() || heightSize > widthSize)) {
                        bottomMargin += groupedPhotosHeight;
                        captionAbove = true;
                    } else {
                        captionAbove = false;
                    }
                    final int topMargin = (isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                    final int height = heightSize - topMargin - bottomMargin;
                    ((MarginLayoutParams) captionScrollView.getLayoutParams()).bottomMargin = bottomMargin;
                    child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int _l, int t, int _r, int _b) {
            final int count = getChildCount();
            int keyboardHeight = measureKeyboardHeight();
            keyboardSize = keyboardHeight;
//            int paddingBottom = keyboardHeight <= dp(20) && !AndroidUtilities.isInMultiwindow ? captionEdit.editText.getEmojiPadding() : 0;
            int paddingBottom = 0;

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                int l = _l + getPaddingLeft();
                int r = _r - getPaddingRight();
                int b = _b - getPaddingBottom();
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = Gravity.TOP | Gravity.LEFT;
                }

                final int horizontalGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (horizontalGravity) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = (r - l - width) - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.CENTER_VERTICAL:
                        childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = ((b - paddingBottom) - t) - height - (lp.bottomMargin);
                        break;
                    default:
                        childTop = lp.topMargin;
                        break;
                }
                child.layout(childLeft + l, childTop, childLeft + width + l, childTop + height);
            }

            notifyHeightChanged();
            updateExclusionRects();
        }

        private ArrayList<Rect> exclusionRects;

        public void updateExclusionRects() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (exclusionRects == null) {
                    exclusionRects = new ArrayList<>();
                }
                exclusionRects.clear();
                if (currentEditMode == EDIT_MODE_CROP || switchingToMode == EDIT_MODE_CROP) {
                    int h = getMeasuredHeight(), w = getMeasuredWidth();
//                    exclusionRects.add(new Rect(0, 0, AndroidUtilities.dp(50), h));
//                    exclusionRects.add(new Rect(0, 0, w, AndroidUtilities.dp(100)));
//                    exclusionRects.add(new Rect(w - AndroidUtilities.dp(50), 0, w, h));
                    exclusionRects.add(new Rect(0, (h - dp(200)) / 2, dp(100), (h + dp(200)) / 2));
                    exclusionRects.add(new Rect(w - dp(100), (h - dp(200)) / 2, w, (h + dp(200)) / 2));
                }
                setSystemGestureExclusionRects(exclusionRects);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            PhotoViewer.this.onDraw(canvas);

            if (isStatusBarVisible() && AndroidUtilities.statusBarHeight != 0 && actionBar != null) {
                paint.setAlpha((int) (255 * actionBar.getAlpha() * 0.498f));
                if (getPaddingRight() > 0) {
                    canvas.drawRect(getMeasuredWidth() - getPaddingRight(), 0, getMeasuredWidth(), getMeasuredHeight(), paint);
                }
                if (getPaddingLeft() > 0) {
                    canvas.drawRect(0, 0, getPaddingLeft(), getMeasuredHeight(), paint);
                }
                if (getPaddingBottom() > 0) {
                    float offset = dpf2(24) * (1f - actionBar.getAlpha());
                    canvas.drawRect(0, getMeasuredHeight() - getPaddingBottom() + offset, getMeasuredWidth(), getMeasuredHeight() + offset, paint);
                }
            }
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
        }

        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean result = this.drawChildInternal(canvas, child, drawingTime);
            return result;
        }

        protected boolean drawChildInternal(Canvas canvas, View child, long drawingTime) {
            try {
                return super.drawChild(canvas, child, drawingTime);
            } catch (Throwable ignore) {
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

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (ImageReceiver receiver : imageReceivers) {
                receiver.onAttachedToWindow();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            for (ImageReceiver receiver : imageReceivers) {
                receiver.onDetachedFromWindow();
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            updateBottomControlsLayout();
            if (pendingOpenAnimation) {
                onPhotoShow();
            } else {
                updateImageBounds();
            }
        }

        @Override
        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                if (scroller.getStartX() < maxX && scroller.getStartX() > minX) {
                    translationX = scroller.getCurrX();
                }
                if (scroller.getStartY() < maxY && scroller.getStartY() > minY) {
                    translationY = scroller.getCurrY();
                }
                updateImageBounds();
                invalidate();
            }
        }


    }


    private static final int EDIT_MODE_CROP = 1;
    private int currentEditMode;
    private int switchingToMode;
    private VideoPlayerControlFrameLayout videoPlayerControlFrameLayout;

    private static class VideoPlayerControlFrameLayout {
        int parentWidth;
        int parentHeight;
    }

    private static void ensureProgressDrawables(Context context) {
        if (progressDrawables != null) {
            return;
        }
        progressDrawables = new Drawable[]{
                ContextCompat.getDrawable(context, R.drawable.circle_big),
                ContextCompat.getDrawable(context, R.drawable.cancel_big),
                ContextCompat.getDrawable(context, R.drawable.load_big)
        };
    }

    private class PhotoProgressView {
        private long lastUpdateTime;
        private float radOffset;
        private float currentProgress;
        private float animationProgressStart;
        private long currentProgressTime;
        private float animatedProgressValue;
        private final RectF progressRect = new RectF();
        private int backgroundState = PROGRESS_NONE;
        private final View parent;
        private int size = AndroidUtilities.dp(64f);
        private int previousBackgroundState = -2;
        private float animatedAlphaValue = 1.0f;
        private final float[] animAlphas = new float[3];
        private final float[] alphas = new float[3];
        private float scale = 1.0f;
        private boolean visible;
        private final Paint progressPaint;
        private final DecelerateInterpolator progressInterpolator = new DecelerateInterpolator(1.5f);
        private final CombinedDrawable playDrawable;
        private final PlayPauseDrawable playPauseDrawable;

        PhotoProgressView(View parentView) {
            parent = parentView;
            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(AndroidUtilities.dp(3f));
            progressPaint.setColor(Color.WHITE);
            resetAlphas();

            playPauseDrawable = new PlayPauseDrawable(28);
            playPauseDrawable.setDuration(200);

            Drawable circleDrawable = ContextCompat.getDrawable(parent.getContext(), R.drawable.circle_big);
            playDrawable = new CombinedDrawable(circleDrawable == null ? null : circleDrawable.mutate(), playPauseDrawable);
        }

        private void updateAnimation(boolean withProgressAnimation) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt > 18) {
                dt = 18;
            }
            lastUpdateTime = newTime;
            boolean postInvalidate = false;

            if (withProgressAnimation) {
                if (animatedProgressValue != 1 || currentProgress != 1) {
                    radOffset += 360 * dt / 3000.0f;
                    float progressDiff = currentProgress - animationProgressStart;
                    if (Math.abs(progressDiff) > 0) {
                        currentProgressTime += dt;
                        if (currentProgressTime >= 300) {
                            animatedProgressValue = currentProgress;
                            animationProgressStart = currentProgress;
                            currentProgressTime = 0;
                        } else {
                            animatedProgressValue = animationProgressStart + progressDiff * progressInterpolator.getInterpolation(currentProgressTime / 300.0f);
                        }
                    }
                    postInvalidate = true;
                }
                if (animatedAlphaValue > 0 && previousBackgroundState != -2) {
                    animatedAlphaValue -= dt / 200.0f;
                    if (animatedAlphaValue <= 0) {
                        animatedAlphaValue = 0.0f;
                        previousBackgroundState = -2;
                    }
                    postInvalidate = true;
                }
            }

            for (int i = 0; i < alphas.length; i++) {
                if (alphas[i] > animAlphas[i]) {
                    animAlphas[i] = Math.min(1f, animAlphas[i] + dt / 200f);
                    postInvalidate = true;
                } else if (alphas[i] < animAlphas[i]) {
                    animAlphas[i] = Math.max(0f, animAlphas[i] - dt / 200f);
                    postInvalidate = true;
                }
            }

            if (postInvalidate) {
                parent.postInvalidateOnAnimation();
            }
        }

        void setProgress(float value, boolean animated) {
            if (!animated) {
                animatedProgressValue = value;
                animationProgressStart = value;
            } else {
                animationProgressStart = animatedProgressValue;
            }
            currentProgress = value;
            currentProgressTime = 0;
            parent.invalidate();
        }

        void setBackgroundState(int state, boolean animated, boolean animateIcon) {
            if (backgroundState == state) {
                return;
            }
            boolean animatePlayPause = animateIcon && (backgroundState == PROGRESS_PLAY || backgroundState == PROGRESS_PAUSE);
            if (state == PROGRESS_PLAY) {
                playPauseDrawable.setPause(false, animatePlayPause);
            } else if (state == PROGRESS_PAUSE) {
                playPauseDrawable.setPause(true, animatePlayPause);
            }
            playPauseDrawable.setParent(parent);
            playPauseDrawable.invalidateSelf();
            lastUpdateTime = System.currentTimeMillis();
            if (animated && backgroundState != state) {
                previousBackgroundState = backgroundState;
                animatedAlphaValue = 1.0f;
            } else {
                previousBackgroundState = -2;
            }
            backgroundState = state;
            onBackgroundStateUpdated(backgroundState);
            parent.invalidate();
        }

        protected void onBackgroundStateUpdated(int state) {
        }

        void setAlpha(float value) {
            setIndexedAlpha(0, value, false);
        }

        void setScale(float value) {
            scale = value;
        }

        void setIndexedAlpha(int index, float alpha, boolean animated) {
            if (alphas[index] != alpha) {
                alphas[index] = alpha;
                if (!animated) {
                    animAlphas[index] = alpha;
                }
                checkVisibility();
                parent.invalidate();
            }
        }

        void resetAlphas() {
            for (int i = 0; i < alphas.length; i++) {
                alphas[i] = animAlphas[i] = 1.0f;
            }
            checkVisibility();
        }

        private float calculateAlpha() {
            float alpha = 1.0f;
            for (int i = 0; i < animAlphas.length; i++) {
                if (i == 2) {
                    alpha *= AndroidUtilities.accelerateInterpolator.getInterpolation(animAlphas[i]);
                } else {
                    alpha *= animAlphas[i];
                }
            }
            return alpha;
        }

        private void checkVisibility() {
            boolean newVisible = true;
            for (float alpha : alphas) {
                if (alpha != 1.0f) {
                    newVisible = false;
                    break;
                }
            }
            if (visible != newVisible) {
                visible = newVisible;
                onVisibilityChanged(visible);
            }
        }

        protected void onVisibilityChanged(boolean visible) {
        }

        public boolean isVisible() {
            return visible;
        }

        int getX() {
            return (parent.getWidth() - (int) (size * scale)) / 2;
        }

        int getY() {
            return (parent.getHeight() - (int) (size * scale)) / 2;
        }

        void onDraw(Canvas canvas) {
            int sizeScaled = (int) (size * scale);
            int x = getX();
            int y = getY();
            float alpha = calculateAlpha();
            drawBackground(canvas, previousBackgroundState, x, y, sizeScaled, animatedAlphaValue * alpha);
            drawBackground(canvas, backgroundState, x, y, sizeScaled, previousBackgroundState != -2 ? (1.0f - animatedAlphaValue) * alpha : alpha);
            if (backgroundState == PROGRESS_EMPTY || backgroundState == PROGRESS_CANCEL || previousBackgroundState == PROGRESS_EMPTY || previousBackgroundState == PROGRESS_CANCEL) {
                int diff = AndroidUtilities.dp(4f);
                progressPaint.setAlpha((int) (255 * (previousBackgroundState != -2 ? animatedAlphaValue * alpha : alpha)));
                progressRect.set(x + diff, y + diff, x + sizeScaled - diff, y + sizeScaled - diff);
                canvas.drawArc(progressRect, -90 + radOffset, Math.max(4, 360 * animatedProgressValue), false, progressPaint);
                updateAnimation(true);
            } else {
                updateAnimation(false);
            }
        }

        private void drawBackground(Canvas canvas, int state, int x, int y, int sizeScaled, float alpha) {
            if (state < 0 || alpha <= 0) {
                return;
            }
            Drawable drawable = state < progressDrawables.length ? progressDrawables[state] : playDrawable;
            if (drawable == null) {
                return;
            }
            drawable.setAlpha((int) (255 * alpha));
            drawable.setBounds(x, y, x + sizeScaled, y + sizeScaled);
            drawable.draw(canvas);
        }
    }

    private static class PhotoViewerActionBarContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
        private final FrameLayout container;
        private final FrameLayout titleLayout;
        private final SimpleTextView[] titleTextView = new SimpleTextView[2];
        private final AnimatedTextView subtitleTextView;
        private AnimatorSet titleAnimator;
        private AnimatorSet subtitleAnimator;
        private ValueAnimator rightPaddingAnimator;
        private boolean hasSubtitle;
        private float rightPadding;
        private int lastHeight;

        PhotoViewerActionBarContainer(Context context) {
            super(context);
            container = new FrameLayout(context);
            container.setPadding(AndroidUtilities.dp((AndroidUtilities.isTablet() ? 80 : 72) - 16), 0, 0, 0);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            titleLayout = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setPivotY(getMeasuredHeight());
                }
            };
            titleLayout.setPivotX(AndroidUtilities.dp(16f));
            titleLayout.setPadding(AndroidUtilities.dp(16f), 0, 0, 0);
            titleLayout.setClipToPadding(false);
            container.addView(titleLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            for (int i = 0; i < titleTextView.length; i++) {
                titleTextView[i] = new SimpleTextView(context);
                titleTextView[i].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                titleTextView[i].setTextColor(Color.WHITE);
                titleTextView[i].setTextSize(20);
                titleTextView[i].setTypeface(AndroidUtilities.bold());
                titleTextView[i].setDrawablePadding(AndroidUtilities.dp(4f));
                titleTextView[i].setScrollNonFitText(true);
                titleLayout.addView(titleTextView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
            }

            subtitleTextView = new AnimatedTextView(context, true, false, false);
            subtitleTextView.setAnimationProperties(.4f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            subtitleTextView.setTextSize(AndroidUtilities.dp(14f));
            subtitleTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            subtitleTextView.setTextColor(Color.WHITE);
            subtitleTextView.setEllipsizeByGradient(true);
            subtitleTextView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            subtitleTextView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            subtitleTextView.setAlpha(0f);
            container.addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT | Gravity.TOP, 16, 0, 0, 0));
        }

        void setTextShadows(boolean applyShadows) {
            titleTextView[0].getPaint().setShadowLayer(AndroidUtilities.dpf2(0.66f), 0, 1, applyShadows ? 0x72000000 : 0);
            titleTextView[1].getPaint().setShadowLayer(AndroidUtilities.dpf2(0.66f), 0, 1, applyShadows ? 0x72000000 : 0);
            subtitleTextView.getDrawable().setShadowLayer(AndroidUtilities.dpf2(0.66f), 0, 1, applyShadows ? 0x72000000 : 0);
        }

        void setTitle(CharSequence title) {
            titleTextView[1].setAlpha(0f);
            titleTextView[1].setVisibility(View.GONE);
            if (!areStringsEqual(titleTextView[0].getText(), title)) {
                titleTextView[0].resetScrolling();
            }
            titleTextView[0].setText(title);
            titleTextView[0].setAlpha(1f);
            titleTextView[0].setTranslationX(0f);
            titleTextView[0].setTranslationY(0f);
        }

        CharSequence getTitle() {
            return titleTextView[0].getText();
        }

        void setTitleAnimated(CharSequence title, boolean axis, boolean direction) {
            if (areStringsEqual(titleTextView[0].getText(), title)) {
                return;
            }
            if (titleAnimator != null) {
                titleAnimator.cancel();
                titleAnimator = null;
            }
            titleTextView[1].copyScrolling(titleTextView[0]);
            titleTextView[1].setText(titleTextView[0].getText());
            titleTextView[1].setRightPadding((int) rightPadding);
            titleTextView[0].resetScrolling();
            titleTextView[0].setText(title);

            float amplitude = AndroidUtilities.dp(8f) * (direction ? 1 : -1);
            titleTextView[1].setTranslationX(0f);
            titleTextView[1].setTranslationY(0f);
            if (axis) {
                titleTextView[0].setTranslationX(0f);
                titleTextView[0].setTranslationY(-amplitude);
            } else {
                titleTextView[0].setTranslationX(-amplitude);
                titleTextView[0].setTranslationY(0f);
            }
            titleTextView[0].setAlpha(0f);
            titleTextView[1].setAlpha(1f);
            titleTextView[0].setVisibility(View.VISIBLE);
            titleTextView[1].setVisibility(View.VISIBLE);

            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(titleTextView[1], View.ALPHA, 0f));
            animators.add(ObjectAnimator.ofFloat(titleTextView[0], View.ALPHA, 1f));
            animators.add(ObjectAnimator.ofFloat(titleTextView[1], axis ? View.TRANSLATION_Y : View.TRANSLATION_X, amplitude));
            animators.add(ObjectAnimator.ofFloat(titleTextView[0], axis ? View.TRANSLATION_Y : View.TRANSLATION_X, 0f));
            titleAnimator = new AnimatorSet();
            titleAnimator.playTogether(animators);
            titleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (titleAnimator == animation) {
                        titleTextView[1].setVisibility(View.GONE);
                        titleAnimator = null;
                    }
                }
            });
            titleAnimator.setDuration(320);
            titleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            titleAnimator.start();
        }

        public void setSubtitle(CharSequence subtitle) {
            setSubtitle(subtitle, true);
        }

        void setSubtitle(CharSequence subtitle, boolean animated) {
            boolean haveSubtitle = !TextUtils.isEmpty(subtitle);
            if (haveSubtitle != hasSubtitle) {
                hasSubtitle = haveSubtitle;
                if (subtitleAnimator != null) {
                    subtitleAnimator.cancel();
                }
                boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
                int subtitleTranslation = AndroidUtilities.dp((haveSubtitle ? 30 : 33) - (isLandscape ? (AndroidUtilities.isTablet() ? -4 : 6) : 0));
                if (animated) {
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(subtitleTextView, View.ALPHA, haveSubtitle ? 1f : 0f));
                    animators.add(ObjectAnimator.ofFloat(subtitleTextView, View.TRANSLATION_Y, subtitleTranslation));
                    animators.add(ObjectAnimator.ofFloat(titleLayout, View.TRANSLATION_Y, haveSubtitle ? AndroidUtilities.dp(-12f) : 0f));
                    animators.add(ObjectAnimator.ofFloat(titleLayout, View.SCALE_X, haveSubtitle ? .87f : 1f));
                    animators.add(ObjectAnimator.ofFloat(titleLayout, View.SCALE_Y, haveSubtitle ? .87f : 1f));
                    subtitleAnimator = new AnimatorSet();
                    subtitleAnimator.playTogether(animators);
                    subtitleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    subtitleAnimator.start();
                } else {
                    subtitleTextView.setAlpha(haveSubtitle ? 1f : 0f);
                    subtitleTextView.setTranslationY(subtitleTranslation);
                    titleLayout.setTranslationY(haveSubtitle ? AndroidUtilities.dp(-12f) : 0f);
                    titleLayout.setScaleX(haveSubtitle ? .87f : 1f);
                    titleLayout.setScaleY(haveSubtitle ? .87f : 1f);
                }
            }
            subtitleTextView.setText(subtitle, animated);
            subtitleTextView.setContentDescription(TextUtils.isEmpty(subtitle) ? null : subtitle);
        }

        public void updateRightPadding(int rightPadding) {
            updateRightPadding(rightPadding, true);
        }

        void updateRightPadding(float rightPadding, boolean animated) {
            if (rightPaddingAnimator != null) {
                rightPaddingAnimator.cancel();
                rightPaddingAnimator = null;
            }
            if (animated) {
                rightPaddingAnimator = ValueAnimator.ofFloat(this.rightPadding, rightPadding);
                rightPaddingAnimator.addUpdateListener(animation -> applyRightPadding((float) animation.getAnimatedValue()));
                rightPaddingAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        updateRightPadding(rightPadding, false);
                    }
                });
                rightPaddingAnimator.setDuration(320);
                rightPaddingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                rightPaddingAnimator.start();
            } else {
                applyRightPadding(rightPadding);
            }
        }

        private void applyRightPadding(float value) {
            rightPadding = value;
            titleTextView[0].setRightPadding((int) value);
            titleTextView[1].setRightPadding((int) value);
            subtitleTextView.setRightPadding(value);
        }

        public void updateOrientation() {
            hasSubtitle = !hasSubtitle;
            setSubtitle(subtitleTextView.getText(), false);
        }

        private boolean areStringsEqual(CharSequence first, CharSequence second) {
            if (first == null && second == null) {
                return true;
            }
            if ((first == null) != (second == null)) {
                return false;
            }
            return TextUtils.equals(first.toString(), second.toString());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int statusBarHeight = AndroidUtilities.statusBarHeight;
            if (lastHeight != AndroidUtilities.displaySize.y) {
                lastHeight = AndroidUtilities.displaySize.y;
                updateOrientation();
            }
            container.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height - statusBarHeight, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int statusBarHeight = AndroidUtilities.statusBarHeight;
            container.layout(0, statusBarHeight, right - left, bottom - top);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.emojiLoaded) {
                titleTextView[0].invalidate();
                titleTextView[1].invalidate();
                subtitleTextView.invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        }
    }

    private static class PhotoCountView extends View {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedTextView.AnimatedTextDrawable left;
        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedTextView.AnimatedTextDrawable right;
        private StaticLayout center;
        private float centerWidth;
        private float centerTop;
        private boolean shown;
        private final AnimatedFloat showT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean nextNotAnimate;
        private int marginTop;
        private String lng;

        PhotoCountView(Context context) {
            super(context);
            backgroundPaint.setColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
            left = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
            left.setAnimationProperties(.3f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            left.setTextColor(Color.WHITE);
            left.setTextSize(AndroidUtilities.dp(14f));
            left.setTypeface(AndroidUtilities.bold());
            left.setCallback(this);
            left.setText("0", false);
            left.setOverrideFullWidth(AndroidUtilities.displaySize.x);

            paint.setColor(Color.WHITE);
            paint.setTextSize(AndroidUtilities.dp(14f));
            paint.setTypeface(AndroidUtilities.bold());
            setCenterText();

            right = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
            right.setAnimationProperties(.3f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            right.setTextColor(Color.WHITE);
            right.setTextSize(AndroidUtilities.dp(14f));
            right.setTypeface(AndroidUtilities.bold());
            right.setCallback(this);
            right.setText("0", false);
            right.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            setWillNotDraw(false);
        }

        private void setCenterText() {
            center = new StaticLayout(getOf(), paint, AndroidUtilities.dp(200f), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            if (center.getLineCount() >= 1) {
                centerWidth = center.getLineWidth(0);
                centerTop = center.getLineDescent(0);
            } else {
                centerWidth = 0f;
                centerTop = 0f;
            }
        }

        private String getOf() {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            lng = info == null ? null : info.shortName;
            String text = getContext().getString(R.string.Of);
            text = text.replace("%1$d", "");
            text = text.replace("%2$d", "");
            return text;
        }

        void setCount(int current, int total, boolean animated) {
            current = Math.max(0, current);
            total = Math.max(current, total);
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            String currentLanguage = info == null ? null : info.shortName;
            if (!TextUtils.equals(lng, currentLanguage)) {
                setCenterText();
            }
            boolean shouldAnimate = animated && !nextNotAnimate;
            left.setText(String.format("%d", LocaleController.isRTL ? total : current), shouldAnimate && !LocaleController.isRTL);
            right.setText(String.format("%d", LocaleController.isRTL ? current : total), shouldAnimate && !LocaleController.isRTL);
            nextNotAnimate = !animated;
            invalidate();
        }

        void updateShow(boolean show, boolean animated) {
            if (shown != show) {
                shown = show;
                if (!show) {
                    nextNotAnimate = true;
                }
                if (!animated) {
                    showT.set(show ? 1f : 0f, true);
                }
                invalidate();
            }
        }

        public boolean isShown() {
            return shown;
        }

        @Override
        protected boolean verifyDrawable(Drawable who) {
            return who == left || who == right || super.verifyDrawable(who);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            marginTop = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
            left.setOverrideFullWidth(width);
            right.setOverrideFullWidth(width);
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(marginTop + AndroidUtilities.dp(43f), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float show = showT.set(shown ? 1f : 0f);
            if (show <= 0f) {
                return;
            }
            float width = left.getCurrentWidth() + centerWidth + right.getCurrentWidth() + AndroidUtilities.dp(18f);
            float marginTop = this.marginTop - AndroidUtilities.dp(8f) * (1f - show);
            AndroidUtilities.rectTmp.set(
                    (getWidth() - width) / 2f,
                    marginTop + AndroidUtilities.dpf2(10f),
                    (getWidth() + width) / 2f,
                    marginTop + AndroidUtilities.dpf2(33f)
            );

            int wasAlpha = backgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (wasAlpha * show));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dpf2(12f), AndroidUtilities.dpf2(12f), backgroundPaint);
            backgroundPaint.setAlpha(wasAlpha);

            canvas.save();
            canvas.translate((getWidth() - width) / 2f + AndroidUtilities.dp(9f), marginTop + AndroidUtilities.dp(9.5f));
            left.setBounds(0, 0, (int) left.getCurrentWidth(), AndroidUtilities.dp(23f));
            int alpha = (int) (0xFF * show);
            left.setAlpha(alpha);
            left.draw(canvas);

            canvas.translate(left.getCurrentWidth(), 0f);
            canvas.save();
            canvas.translate(-(center.getWidth() - centerWidth) / 2f, (AndroidUtilities.dp(23f) - center.getHeight() + centerTop / 2f) / 2f);
            paint.setAlpha(alpha);
            center.draw(canvas);
            canvas.restore();

            canvas.translate(centerWidth, 0f);
            right.setBounds(0, 0, (int) right.getCurrentWidth(), AndroidUtilities.dp(23f));
            right.setAlpha(alpha);
            right.draw(canvas);
            canvas.restore();
        }
    }


    public static class CaptionTextViewSwitcher extends TextViewSwitcher {
        private boolean inScrollView = false;
        private float alpha = 1.0f;
        private NestedScrollView scrollView;
        private FrameLayout container;

        public CaptionTextViewSwitcher(Context context) {
            super(context);
        }

        public void setScrollView(NestedScrollView scrollView) {
            this.scrollView = scrollView;
        }

        public void setContainer(FrameLayout container) {
            this.container = container;
        }

        @Override
        public void setVisibility(int visibility) {
            setVisibility(visibility, true);
        }

        public void setVisibility(int visibility, boolean withScrollView) {
            super.setVisibility(visibility);
            if (inScrollView && withScrollView && scrollView != null) {
                scrollView.setVisibility(visibility);
            }
        }

        @Override
        public void setAlpha(float alpha) {
            this.alpha = alpha;
            if (inScrollView && scrollView != null) {
                scrollView.setAlpha(alpha);
            } else {
                super.setAlpha(alpha);
            }
        }

        @Override
        public float getAlpha() {
            return inScrollView ? alpha : super.getAlpha();
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            if (inScrollView && scrollView != null) {
                scrollView.invalidate();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (container != null && getParent() == container && scrollView != null) {
                inScrollView = true;
                scrollView.setVisibility(getVisibility());
                scrollView.setAlpha(alpha);
                super.setAlpha(1f);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (inScrollView) {
                inScrollView = false;
                if (scrollView != null) {
                    scrollView.setVisibility(View.GONE);
                }
                super.setAlpha(alpha);
            }
        }
    }

    private static class CaptionTextView extends TextView {
        CaptionTextView(Context context) {
            super(context);
            setTextColor(0xffffffff);
            setLinkTextColor(0xff79c4fc);
            setHighlightColor(0x33ffffff);
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
            setGravity(Gravity.CENTER_VERTICAL | LayoutHelper.getAbsoluteGravityStart());
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        }
    }

    public static class CaptionScrollView extends NestedScrollView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SpringAnimation springAnimation;
        private final CaptionTextViewSwitcher captionTextViewSwitcher;
        private final FrameLayout captionContainer;
        private boolean nestedScrollStarted;
        private float overScrollY;
        private float velocitySign;
        private float velocityY;
        private Method abortAnimatedScrollMethod;
        private OverScroller scroller;
        private boolean isLandscape;
        private int textHash;
        private int prevHeight;
        public float backgroundAlpha = 1f;
        public boolean dontChangeTopMargin;
        private int pendingTopMargin = -1;

        public CaptionScrollView(@NonNull Context context, CaptionTextViewSwitcher switcher, FrameLayout container) {
            super(context);
            this.captionTextViewSwitcher = switcher;
            this.captionContainer = container;
            setClipChildren(false);
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            paint.setColor(Color.BLACK);
            setFadingEdgeLength(AndroidUtilities.dp(12f));
            setVerticalFadingEdgeEnabled(true);
            setWillNotDraw(false);

            springAnimation = new SpringAnimation(captionTextViewSwitcher, DynamicAnimation.TRANSLATION_Y, 0);
            springAnimation.getSpring().setStiffness(100f);
            springAnimation.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
            springAnimation.addUpdateListener((animation, value, velocity) -> {
                overScrollY = value;
                velocityY = velocity;
                onScrollUpdate();
            });
            springAnimation.addEndListener((animation, canceled, value, velocity) -> onScrollEnd());
            springAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);

            try {
                abortAnimatedScrollMethod = NestedScrollView.class.getDeclaredMethod("abortAnimatedScroll");
                abortAnimatedScrollMethod.setAccessible(true);
            } catch (Exception e) {
                abortAnimatedScrollMethod = null;
                FileLog.e(e);
            }

            try {
                Field scrollerField = NestedScrollView.class.getDeclaredField("mScroller");
                scrollerField.setAccessible(true);
                scroller = (OverScroller) scrollerField.get(this);
            } catch (Exception e) {
                scroller = null;
                FileLog.e(e);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            final int width = getWidth();
            final int height = getHeight();
            final int scrollY = getScrollY();
            final int saveCount = canvas.save();
            canvas.clipRect(0, scrollY, width, height + scrollY);
            paint.setAlpha((int) (backgroundAlpha * 127));
            canvas.drawRect(0, captionContainer.getTop() + captionTextViewSwitcher.getTranslationY(), width, height + scrollY, paint);
            super.draw(canvas);
            canvas.restoreToCount(saveCount);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < captionContainer.getTop() - getScrollY() + captionTextViewSwitcher.getTranslationY()) {
                return false;
            }
            return super.onTouchEvent(ev);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            updateTopMargin(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void applyPendingTopMargin() {
            dontChangeTopMargin = false;
            if (pendingTopMargin >= 0) {
                ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin = pendingTopMargin;
                pendingTopMargin = -1;
                requestLayout();
            }
        }

        public int getPendingMarginTopDiff() {
            if (pendingTopMargin >= 0) {
                return pendingTopMargin - ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin;
            }
            return 0;
        }

        public void updateTopMargin() {
            updateTopMargin(getWidth(), getHeight());
        }

        private void updateTopMargin(int width, int height) {
            int marginTop = calculateNewContainerMarginTop(width, height);
            if (marginTop >= 0) {
                if (dontChangeTopMargin) {
                    pendingTopMargin = marginTop;
                } else {
                    ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin = marginTop;
                    pendingTopMargin = -1;
                }
            }
        }

        public int calculateNewContainerMarginTop(int width, int height) {
            if (width == 0 || height == 0) {
                return -1;
            }

            TextView textView = captionTextViewSwitcher.getCurrentView();
            CharSequence text = textView.getText();
            int textHash = text.hashCode();
            boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
            if (this.textHash == textHash && this.isLandscape == isLandscape && this.prevHeight == height) {
                return -1;
            }
            this.textHash = textHash;
            this.isLandscape = isLandscape;
            this.prevHeight = height;

            textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
            Layout layout = textView.getLayout();
            if (layout == null) {
                return -1;
            }
            int lineCount = layout.getLineCount();
            if (isLandscape && lineCount <= 2 || !isLandscape && lineCount <= 5) {
                return height - textView.getMeasuredHeight() - captionTextViewSwitcher.getPaddingBottom();
            }

            int i = Math.min(isLandscape ? 2 : 5, lineCount);
            cycle:
            while (i > 1) {
                for (int j = layout.getLineStart(i - 1); j < layout.getLineEnd(i - 1); j++) {
                    if (!Character.isWhitespace(text.charAt(j))) {
                        break cycle;
                    }
                }
                i--;
            }

            int lineHeight = textView.getPaint().getFontMetricsInt(null);
            return height - lineHeight * i - AndroidUtilities.dp(8f);
        }

        public void reset() {
            scrollTo(0, 0);
        }

        public void stopScrolling() {
            if (abortAnimatedScrollMethod != null) {
                try {
                    abortAnimatedScrollMethod.invoke(this);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        @Override
        public void fling(int velocityY) {
            super.fling(velocityY);
            velocitySign = Math.signum(velocityY);
            this.velocityY = 0f;
        }

        @Override
        public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) {
            consumed[1] = 0;

            if (nestedScrollStarted && (overScrollY > 0 && dy > 0 || overScrollY < 0 && dy < 0)) {
                float delta = overScrollY - dy;
                if (overScrollY > 0) {
                    if (delta < 0) {
                        overScrollY = 0;
                        consumed[1] += dy + delta;
                    } else {
                        overScrollY = delta;
                        consumed[1] += dy;
                    }
                } else {
                    if (delta > 0) {
                        overScrollY = 0;
                        consumed[1] += dy + delta;
                    } else {
                        overScrollY = delta;
                        consumed[1] += dy;
                    }
                }
                onScrollUpdate();
                captionTextViewSwitcher.setTranslationY(overScrollY);
                return true;
            }

            return false;
        }

        @Override
        public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
            if (dyUnconsumed != 0) {
                int topMargin = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
                int dy = Math.round(dyUnconsumed * (1f - Math.abs((-overScrollY / Math.max(1f, captionContainer.getTop() - topMargin)))));

                if (dy != 0) {
                    if (!nestedScrollStarted) {
                        if (!springAnimation.isRunning()) {
                            int consumedY;
                            float velocity = scroller != null ? scroller.getCurrVelocity() : Float.NaN;
                            if (!Float.isNaN(velocity)) {
                                float clampedVelocity = Math.min(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 3000 : 5000, velocity);
                                consumedY = (int) (dy * clampedVelocity / velocity);
                                velocity = clampedVelocity * -velocitySign;
                            } else {
                                consumedY = dy;
                                velocity = 0;
                            }
                            if (consumedY != 0) {
                                overScrollY -= consumedY;
                                captionTextViewSwitcher.setTranslationY(overScrollY);
                            }
                            startSpringAnimationIfNotRunning(velocity);
                        }
                    } else {
                        overScrollY -= dy;
                        captionTextViewSwitcher.setTranslationY(overScrollY);
                    }
                }
                onScrollUpdate();
            }
        }

        private void startSpringAnimationIfNotRunning(float velocityY) {
            if (!springAnimation.isRunning()) {
                springAnimation.setStartVelocity(velocityY);
                springAnimation.start();
            }
        }

        @Override
        public boolean startNestedScroll(int axes, int type) {
            if (type == ViewCompat.TYPE_TOUCH) {
                springAnimation.cancel();
                nestedScrollStarted = true;
                overScrollY = captionTextViewSwitcher.getTranslationY();
                onScrollStart();
            }
            return true;
        }

        @Override
        public void computeScroll() {
            super.computeScroll();
            if (!nestedScrollStarted && overScrollY != 0 && scroller != null && scroller.isFinished()) {
                startSpringAnimationIfNotRunning(0);
            }
            onScrollUpdate();
        }

        @Override
        public void stopNestedScroll(int type) {
            if (nestedScrollStarted && type == ViewCompat.TYPE_TOUCH) {
                nestedScrollStarted = false;
                if (overScrollY != 0 && scroller != null && scroller.isFinished()) {
                    startSpringAnimationIfNotRunning(velocityY);
                }
                onScrollEnd();
            }
        }

        protected void onScrollStart() {
        }

        protected void onScrollUpdate() {
        }

        protected void onScrollEnd() {
        }

        @Override
        protected float getTopFadingEdgeStrength() {
            return 1f;
        }

        @Override
        protected float getBottomFadingEdgeStrength() {
            return 1f;
        }
    }

}

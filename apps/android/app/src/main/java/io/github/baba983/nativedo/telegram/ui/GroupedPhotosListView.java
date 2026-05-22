// UPSTREAM-SCOPE: org/telegram/ui/Components/GroupedPhotosListView.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING: package + imports adapted to NativeDo paths.
// ADAPTED-BACKEND: String (URL) replaces ImageLocation; PhotoEntry replaces
//   MessageObject/TLRPC.PageBlock; ImageReceiver.setImage(url) replaces
//   upstream setImage(ImageLocation, ...). photoSize is null so thumbnails
//   default to square aspect ratio — a proper ThumbPhotoSize port is deferred.
//
// Derived from Telegram for Android's GroupedPhotosListView.java.
// Copyright (C) Telegram FZ-LLC / NativeDo contributors
// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import io.github.baba983.nativedo.telegram.messenger.AndroidUtilities;
import io.github.baba983.nativedo.telegram.messenger.ImageLoader;
import io.github.baba983.nativedo.telegram.messenger.ImageReceiver;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;

public class GroupedPhotosListView extends View implements GestureDetector.OnGestureListener {

    private Paint backgroundPaint = new Paint();
    private ArrayList<ImageReceiver> unusedReceivers = new ArrayList<>();
    private ArrayList<ImageReceiver> imagesToDraw = new ArrayList<>();
    public ArrayList<String> currentPhotos = new ArrayList<>();
    private ArrayList<PhotoViewer.PhotoEntry> currentObjects = new ArrayList<>();
    private int currentImage;
    private int itemWidth;
    private int itemHeight;
    private int itemY;
    private int itemSpacing;
    private int drawDx;
    private float moveLineProgress;
    private float currentItemProgress = 1.0f;
    private float nextItemProgress = 0.0f;
    private int nextImage;
    private long lastUpdateTime;
    private boolean moving;
    private boolean animateAllLine;
    private int animateToDX;
    private int animateToDXStart;
    private int animateToItem = -1;
    private boolean animateToItemFast;
    private android.widget.Scroller scroll;
    private GestureDetector gestureDetector;
    private boolean scrolling;
    private boolean stopedScrolling;
    private boolean ignoreChanges;
    private boolean animationsEnabled = true;
    private int nextPhotoScrolling = -1;
    private boolean hasPhotos;
    private boolean animateBackground = true;

    private GroupedPhotosListViewDelegate delegate;

    private ValueAnimator showAnimator;
    private ValueAnimator hideAnimator;
    private float drawAlpha = 1f;

    public interface GroupedPhotosListViewDelegate {
        int getCurrentIndex();
        default int getCurrentAccount() { return 0; }
        void setCurrentIndex(int index);
        ArrayList<PhotoViewer.PhotoEntry> getPhotos();
        default void onShowAnimationStart() {}
        default void onStopScrolling() {}
    }

    public GroupedPhotosListView(Context context) {
        this(context, AndroidUtilities.dp(3));
    }

    public GroupedPhotosListView(Context context, int paddingTop) {
        super(context);
        gestureDetector = new GestureDetector(context, this);
        scroll = new Scroller(context);
        itemWidth = AndroidUtilities.dp(42);
        itemHeight = AndroidUtilities.dp(56);
        itemSpacing = AndroidUtilities.dp(1);
        itemY = paddingTop;
        backgroundPaint.setColor(0x7f000000);
    }

    public void clear() {
        currentPhotos.clear();
        currentObjects.clear();
        // Also drop the recycled-receiver pool so a singleton-reused PhotoViewer does
        // not draw stale bitmaps from the previous session. Receivers are cheap to
        // recreate (just bitmap/shader holders) but their cached `bitmap` ref can be
        // recycled across opens, leaving blank rectangles. Matches the intent of
        // upstream PhotoViewer which destroys the strip's receivers on dismiss via
        // the WindowManager detach + onDetachedFromWindow.
        for (ImageReceiver receiver : imagesToDraw) {
            receiver.setImageBitmap(null);
            receiver.setThumbBitmap(null);
        }
        for (ImageReceiver receiver : unusedReceivers) {
            receiver.setImageBitmap(null);
            receiver.setThumbBitmap(null);
        }
        imagesToDraw.clear();
        unusedReceivers.clear();
        currentImage = 0;
        nextImage = 0;
        animateToItem = -1;
        moving = false;
        drawDx = 0;
    }

    // ── fillList ────────────────────────────────────────────────────────
    // Structure and control flow match upstream GroupedPhotosListView.java:102-349.
    // ADAPTED-BACKEND: "photos" is a flat URL list (no grouping by MessageObject
    // groupId or PageBlock.groupId), so the group-scoping loop and changed-detection
    // by groupId are replaced by a direct count comparison.

    public void fillList() {
        if (ignoreChanges) {
            ignoreChanges = false;
            return;
        }

        ArrayList<PhotoViewer.PhotoEntry> photos = delegate != null ? delegate.getPhotos() : null;
        int currentIndex = delegate != null ? delegate.getCurrentIndex() : 0;
        int currentAccount = delegate != null ? delegate.getCurrentAccount() : 0;

        hasPhotos = false;
        boolean changed = false;
        int newCount = 0;
        PhotoViewer.PhotoEntry currentObject = null;
        if (photos != null && !photos.isEmpty()) {
            if (currentIndex >= photos.size()) {
                currentIndex = photos.size() - 1;
            }
            currentObject = photos.get(currentIndex);
            newCount = photos.size();
            hasPhotos = true;
        }
        if (currentObject == null) {
            return;
        }
        if (animationsEnabled) {
            if (!hasPhotos) {
                if (showAnimator != null) {
                    showAnimator.cancel();
                    showAnimator = null;
                }
                if (drawAlpha > 0f && currentPhotos.size() > 1) {
                    if (hideAnimator == null) {
                        hideAnimator = ValueAnimator.ofFloat(drawAlpha, 0f);
                        hideAnimator.setDuration((long) (200 * drawAlpha));
                        hideAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (hideAnimator == animation) {
                                    hideAnimator = null;
                                    fillList();
                                }
                            }
                        });
                        hideAnimator.addUpdateListener(a -> {
                            drawAlpha = (float) a.getAnimatedValue();
                            invalidate();
                        });
                        hideAnimator.start();
                    }
                    return;
                }
            } else {
                if (hideAnimator != null) {
                    final Animator a = hideAnimator;
                    hideAnimator = null;
                    a.cancel();
                }
                if (drawAlpha < 1f && showAnimator == null) {
                    showAnimator = ValueAnimator.ofFloat(drawAlpha, 1f);
                    showAnimator.setDuration((long) (200 * (1f - drawAlpha)));
                    showAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (delegate != null) {
                                delegate.onShowAnimationStart();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (showAnimator == animation) {
                                showAnimator = null;
                            }
                        }
                    });
                    showAnimator.addUpdateListener(a -> {
                        drawAlpha = (float) a.getAnimatedValue();
                        invalidate();
                    });
                }
            }
        }
        if (!changed) {
            if (newCount != currentPhotos.size() || !currentObjects.contains(currentObject)) {
                changed = true;
            } else {
                int newImageIndex = currentObjects.indexOf(currentObject);
                if (currentImage != newImageIndex && newImageIndex != -1) {
                    boolean animate = animateAllLine;
                    if (!animate && !moving && (newImageIndex == currentImage - 1 || newImageIndex == currentImage + 1)) {
                        animate = true;
                        animateToItemFast = true;
                    }
                    if (animate) {
                        nextImage = animateToItem = newImageIndex;
                        animateToDX = (currentImage - newImageIndex) * (itemWidth + itemSpacing);
                        moving = true;
                        animateAllLine = false;
                        lastUpdateTime = System.currentTimeMillis();
                        invalidate();
                    } else {
                        fillImages(true, (currentImage - newImageIndex) * (itemWidth + itemSpacing));
                        currentImage = newImageIndex;
                        moving = false;
                    }
                    drawDx = 0;
                }
            }
        }
        if (changed) {
            int oldCount = currentPhotos.size();
            animateAllLine = false;
            currentPhotos.clear();
            currentObjects.clear();
            for (PhotoViewer.PhotoEntry e : photos) {
                currentObjects.add(e);
                currentPhotos.add(e.url);
            }
            currentImage = currentIndex;
            animateToItem = -1;
            animateToItemFast = false;
            if (currentPhotos.size() == 1) {
                currentPhotos.clear();
                currentObjects.clear();
            }
            if (currentPhotos.size() != oldCount) {
                requestLayout();
            }
            fillImages(false, 0);
        }
    }

    public int getCount() {
        return currentPhotos.size();
    }

    public int getIndex() {
        return currentImage;
    }

    public void setMoveProgress(float progress) {
        if (scrolling || animateToItem >= 0) {
            return;
        }
        if (progress > 0) {
            nextImage = currentImage - 1;
        } else {
            nextImage = currentImage + 1;
        }
        if (nextImage >= 0 && nextImage < currentPhotos.size()) {
            currentItemProgress = 1.0f - Math.abs(progress);
        } else {
            currentItemProgress = 1.0f;
        }
        nextItemProgress = 1.0f - currentItemProgress;
        moving = progress != 0;
        invalidate();
        if (currentPhotos.isEmpty() || progress < 0 && currentImage == currentPhotos.size() - 1 || progress > 0 && currentImage == 0) {
            return;
        }
        drawDx = (int) (progress * (itemWidth + itemSpacing));
        fillImages(true, drawDx);
    }

    // ── fillImages / getFreeReceiver ────────────────────────────────────
    // Match upstream GroupedPhotosListView.java:383-485.
    // ADAPTED-BACKEND: receiver.setImage(url) replaces upstream
    //   setImage(null, null, location, "80_80", 0, null, parent, 1).

    private ImageReceiver getFreeReceiver() {
        ImageReceiver receiver;
        if (unusedReceivers.isEmpty()) {
            receiver = new ImageReceiver(this);
            receiver.setAllowLoadingOnAttachedOnly(false);
        } else {
            receiver = unusedReceivers.get(0);
            unusedReceivers.remove(0);
        }
        imagesToDraw.add(receiver);
        receiver.setCurrentAccount(delegate != null ? delegate.getCurrentAccount() : 0);
        return receiver;
    }

    private void fillImages(boolean move, int dx) {
        if (!move && !imagesToDraw.isEmpty()) {
            unusedReceivers.addAll(imagesToDraw);
            imagesToDraw.clear();
            moving = false;
            moveLineProgress = 1.0f;
            currentItemProgress = 1.0f;
            nextItemProgress = 0.0f;
        }
        invalidate();
        if (getMeasuredWidth() == 0 || currentPhotos.isEmpty()) {
            return;
        }
        int width = getMeasuredWidth();
        int startX = getMeasuredWidth() / 2 - itemWidth / 2;

        int addRightIndex;
        int addLeftIndex;
        if (move) {
            addRightIndex = Integer.MIN_VALUE;
            addLeftIndex = Integer.MAX_VALUE;
            int count = imagesToDraw.size();
            for (int a = 0; a < count; a++) {
                ImageReceiver receiver = imagesToDraw.get(a);
                int num = receiver.getParam();
                int x = startX + (num - currentImage) * (itemWidth + itemSpacing) + dx;
                if (x > width || x + itemWidth < 0) {
                    unusedReceivers.add(receiver);
                    imagesToDraw.remove(a);
                    count--;
                    a--;
                }
                addLeftIndex = Math.min(addLeftIndex, num - 1);
                addRightIndex = Math.max(addRightIndex, num + 1);
            }
        } else {
            addRightIndex = currentImage;
            addLeftIndex = currentImage - 1;
        }

        if (addRightIndex != Integer.MIN_VALUE) {
            int count = currentPhotos.size();
            for (int a = addRightIndex; a < count; a++) {
                int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx;
                if (x < width) {
                    String fullUrl = currentPhotos.get(a);
                    String thumbRequestUrl = thumbUrlFor(a, fullUrl);
                    ImageReceiver receiver = getFreeReceiver();
                    receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                    bindStripReceiver(receiver, thumbRequestUrl);
                    receiver.setParam(a);
                } else {
                    break;
                }
            }
        }
        if (addLeftIndex != Integer.MAX_VALUE) {
            for (int a = addLeftIndex; a >= 0; a--) {
                int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx + itemWidth;
                if (x > 0) {
                    String fullUrl = currentPhotos.get(a);
                    String thumbRequestUrl = thumbUrlFor(a, fullUrl);
                    ImageReceiver receiver = getFreeReceiver();
                    receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                    bindStripReceiver(receiver, thumbRequestUrl);
                    receiver.setParam(a);
                } else {
                    break;
                }
            }
        }
        if (showAnimator != null && !showAnimator.isStarted()) {
            showAnimator.start();
        }
    }

    // Picks the URL to request for one strip cell. Prefers PhotoEntry.thumbUrl
    // (typically a Discourse optimized srcset entry, e.g. the ~230px-wide variant
    // that post bodies already render) so strip receivers aren't pulling the
    // full-resolution original for a 55x42dp cell. Falls back to the full URL
    // when no thumb was supplied.
    @NonNull
    private String thumbUrlFor(int index, @NonNull String fullUrl) {
        if (index >= 0 && index < currentObjects.size()) {
            PhotoViewer.PhotoEntry entry = currentObjects.get(index);
            if (entry != null && entry.thumbUrl != null && !entry.thumbUrl.isEmpty()) {
                return entry.thumbUrl;
            }
        }
        return fullUrl;
    }

    private void bindStripReceiver(@NonNull ImageReceiver receiver, @NonNull String url) {
        receiver.setScaleType(ImageReceiver.ScaleType.CENTER_CROP);
        // Prefer the thumbnail (100px square-ish) for the strip cell so
        // CENTER_CROP fills the 42x56dp cell the way Telegram's "80_80"
        // server thumbnail does. Fall back to the full bitmap only when
        // no thumbnail is available.
        Bitmap thumb = ImageLoader.getThumbnail(url);
        if (thumb != null) {
            receiver.setImageBitmap(thumb);
            return;
        }
        Bitmap cached = ImageLoader.get(url);
        if (cached != null) {
            receiver.setImageBitmap(cached);
            return;
        }
        receiver.setImage(url);
    }

    // ── GestureDetector.OnGestureListener ───────────────────────────────
    // Match upstream GroupedPhotosListView.java:487-655.

    @Override
    public boolean onDown(MotionEvent e) {
        if (!scroll.isFinished()) {
            scroll.abortAnimation();
        }
        animateToItem = -1;
        animateToItemFast = false;
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        int currentIndex = delegate != null ? delegate.getCurrentIndex() : 0;
        stopScrolling();
        int count = imagesToDraw.size();
        for (int a = 0; a < count; a++) {
            ImageReceiver receiver = imagesToDraw.get(a);
            if (receiver.isInsideImage(e.getX(), e.getY())) {
                int num = receiver.getParam();
                if (num < 0 || num >= currentObjects.size()) {
                    return true;
                }
                if (currentIndex == num) {
                    return true;
                }
                moveLineProgress = 1.0f;
                animateAllLine = true;
                delegate.setCurrentIndex(num);
                break;
            }
        }
        return false;
    }

    private void updateAfterScroll() {
        int indexChange = 0;
        int dx = drawDx;
        if (Math.abs(dx) > itemWidth / 2 + itemSpacing) {
            if (dx > 0) {
                dx -= itemWidth / 2 + itemSpacing;
                indexChange++;
            } else {
                dx += itemWidth / 2 + itemSpacing;
                indexChange--;
            }
            indexChange += dx / (itemWidth + itemSpacing * 2);
        }
        nextPhotoScrolling = currentImage - indexChange;

        int currentIndex = delegate != null ? delegate.getCurrentIndex() : 0;
        if (currentIndex != nextPhotoScrolling && nextPhotoScrolling >= 0 && nextPhotoScrolling < currentPhotos.size()) {
            ignoreChanges = true;
            delegate.setCurrentIndex(nextPhotoScrolling);
        }
        if (!scrolling) {
            scrolling = true;
            stopedScrolling = false;
        }
        fillImages(true, drawDx);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        drawDx -= distanceX;
        int min = getMinScrollX();
        int max = getMaxScrollX();
        if (drawDx < min) {
            drawDx = min;
        } else if (drawDx > max) {
            drawDx = max;
        }
        updateAfterScroll();
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        scroll.abortAnimation();
        if (currentPhotos.size() >= 10) {
            scroll.fling(drawDx, 0, Math.round(velocityX), 0, getMinScrollX(), getMaxScrollX(), 0, 0);
        }
        return false;
    }

    private void stopScrolling() {
        scrolling = false;
        if (!scroll.isFinished()) {
            scroll.abortAnimation();
        }
        if (nextPhotoScrolling >= 0 && nextPhotoScrolling < currentObjects.size()) {
            stopedScrolling = true;
            animateToItemFast = false;
            nextImage = animateToItem = nextPhotoScrolling;
            animateToDX = (currentImage - nextPhotoScrolling) * (itemWidth + itemSpacing);
            animateToDXStart = drawDx;
            moveLineProgress = 1.0f;
            nextPhotoScrolling = -1;
            if (delegate != null) {
                delegate.onStopScrolling();
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentPhotos.isEmpty() || getAlpha() != 1.0f) {
            return false;
        }
        boolean result = gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
        if (scrolling && event.getAction() == MotionEvent.ACTION_UP && scroll.isFinished()) {
            stopScrolling();
        }
        return result;
    }

    private int getMinScrollX() {
        return -(currentPhotos.size() - currentImage - 1) * (itemWidth + itemSpacing * 2);
    }

    private int getMaxScrollX() {
        return currentImage * (itemWidth + itemSpacing * 2);
    }

    // ── Layout / Drawing ────────────────────────────────────────────────
    // Match upstream GroupedPhotosListView.java:665-826.

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        fillImages(false, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!hasPhotos && imagesToDraw.isEmpty()) {
            return;
        }
        float bgAlpha = drawAlpha;
        if (!animateBackground) {
            bgAlpha = hasPhotos ? 1f : 0f;
        }
        backgroundPaint.setAlpha((int) (0x7F * bgAlpha));
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        if (imagesToDraw.isEmpty()) {
            return;
        }
        int count = imagesToDraw.size();

        int moveX = drawDx;

        int maxItemWidth = (int) (itemWidth * 2.0f);
        int padding = AndroidUtilities.dp(8);

        // Match Telegram: current selected cell width is computed from the
        // image's aspect ratio (photoSize.w * itemHeight / photoSize.h).
        // Falls back to itemHeight (square) when no dimensions are available.
        int trueWidth;
        int currentPaddings;
        PhotoViewer.PhotoEntry currentEntry = getEntryAt(currentImage);
        if (currentEntry != null && currentEntry.widthPx > 0 && currentEntry.heightPx > 0) {
            trueWidth = Math.max(itemWidth, (int) (currentEntry.widthPx * (itemHeight / (float) currentEntry.heightPx)));
        } else {
            trueWidth = itemHeight;
        }
        trueWidth = Math.min(maxItemWidth, trueWidth);
        currentPaddings = (int) (padding * 2 * currentItemProgress);
        trueWidth = itemWidth + (int) ((trueWidth - itemWidth) * currentItemProgress) + currentPaddings;

        int nextTrueWidth;
        int nextPaddings;
        PhotoViewer.PhotoEntry nextEntry = getEntryAt(nextImage);
        if (nextEntry != null && nextEntry.widthPx > 0 && nextEntry.heightPx > 0) {
            nextTrueWidth = Math.max(itemWidth, (int) (nextEntry.widthPx * (itemHeight / (float) nextEntry.heightPx)));
        } else {
            nextTrueWidth = itemWidth;
        }
        nextTrueWidth = Math.min(maxItemWidth, nextTrueWidth);
        nextPaddings = (int) (padding * 2 * nextItemProgress);
        moveX += (nextTrueWidth + nextPaddings - itemWidth) / 2 * nextItemProgress * (nextImage > currentImage ? -1 : 1);
        nextTrueWidth = itemWidth + (int) ((nextTrueWidth - itemWidth) * nextItemProgress) + nextPaddings;

        int startX = (getMeasuredWidth() - trueWidth) / 2;
        for (int a = 0; a < count; a++) {
            ImageReceiver receiver = imagesToDraw.get(a);
            int num = receiver.getParam();
            if (num == currentImage) {
                receiver.setImageX(startX + moveX + currentPaddings / 2);
                receiver.setImageWidth(trueWidth - currentPaddings);
            } else {
                if (nextImage < currentImage) {
                    if (num < currentImage) {
                        if (num <= nextImage) {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage + 1) * (itemWidth + itemSpacing) - (nextTrueWidth + itemSpacing) + moveX);
                        } else {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                        }
                    } else {
                        receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                    }
                } else {
                    if (num < currentImage) {
                        receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                    } else {
                        if (num <= nextImage) {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                        } else {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 2) * (itemWidth + itemSpacing) + (nextTrueWidth + itemSpacing) + moveX);
                        }
                    }
                }
                if (num == nextImage) {
                    receiver.setImageWidth(nextTrueWidth - nextPaddings);
                    receiver.setImageX((int) (receiver.getImageX() + nextPaddings / 2));
                } else {
                    receiver.setImageWidth(itemWidth);
                }
            }
            receiver.setAlpha(drawAlpha);
            receiver.setRoundRadius(AndroidUtilities.dp(2));
            receiver.draw(canvas);
        }

        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        if (animateToItem >= 0) {
            if (moveLineProgress > 0.0f) {
                moveLineProgress -= dt / (animateToItemFast ? 100.0f : 200.0f);
                if (animateToItem == currentImage) {
                    if (currentItemProgress < 1.0f) {
                        currentItemProgress += dt / 200.0f;
                        if (currentItemProgress > 1.0f) {
                            currentItemProgress = 1.0f;
                        }
                    }
                    drawDx = animateToDXStart + (int) Math.ceil(currentItemProgress * (animateToDX - animateToDXStart));
                } else {
                    nextItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f - moveLineProgress);
                    if (stopedScrolling) {
                        if (currentItemProgress > 0.0f) {
                            currentItemProgress -= dt / 200.0f;
                            if (currentItemProgress < 0.0f) {
                                currentItemProgress = 0.0f;
                            }
                        }
                        drawDx = animateToDXStart + (int) Math.ceil(nextItemProgress * (animateToDX - animateToDXStart));
                    } else {
                        currentItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(moveLineProgress);
                        drawDx = (int) Math.ceil(nextItemProgress * animateToDX);
                    }
                }
                if (moveLineProgress <= 0) {
                    currentImage = animateToItem;
                    moveLineProgress = 1.0f;
                    currentItemProgress = 1.0f;
                    nextItemProgress = 0.0f;
                    moving = false;
                    stopedScrolling = false;
                    drawDx = 0;
                    animateToItem = -1;
                    animateToItemFast = false;
                }
            }
            fillImages(true, drawDx);
            invalidate();
        }
        if (scrolling && currentItemProgress > 0.0f) {
            currentItemProgress -= dt / 200.0f;
            if (currentItemProgress < 0.0f) {
                currentItemProgress = 0.0f;
            }
            invalidate();
        }
        if (!scroll.isFinished()) {
            if (scroll.computeScrollOffset()) {
                drawDx = scroll.getCurrX();
                updateAfterScroll();
                invalidate();
            }
            if (scroll.isFinished()) {
                stopScrolling();
            }
        }
    }

    // ── Public helpers ──────────────────────────────────────────────────
    // Match upstream GroupedPhotosListView.java:828-867.

    @Nullable
    private PhotoViewer.PhotoEntry getEntryAt(int index) {
        if (index < 0 || index >= currentObjects.size()) {
            return null;
        }
        return currentObjects.get(index);
    }

    public void setDelegate(GroupedPhotosListViewDelegate groupedPhotosListViewDelegate) {
        delegate = groupedPhotosListViewDelegate;
    }

    public boolean hasPhotos() {
        return hasPhotos && hideAnimator == null && (drawAlpha > 0f || !animateBackground || (showAnimator != null && showAnimator.isStarted()));
    }

    public boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    public void setAnimationsEnabled(boolean animationsEnabled) {
        if (this.animationsEnabled != animationsEnabled) {
            this.animationsEnabled = animationsEnabled;
            if (!animationsEnabled) {
                if (showAnimator != null) {
                    showAnimator.cancel();
                    showAnimator = null;
                }
                if (hideAnimator != null) {
                    hideAnimator.cancel();
                    hideAnimator = null;
                }
                drawAlpha = 0f;
                invalidate();
            }
        }
    }

    public void setAnimateBackground(boolean animateBackground) {
        this.animateBackground = animateBackground;
    }

    public void reset() {
        hasPhotos = false;
        if (animationsEnabled) {
            drawAlpha = 0f;
        }
    }
}

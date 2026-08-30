/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.PinchToZoomHelper;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class ProfileGalleryView extends FrameLayout {

    public interface Callback {
        void onDown(boolean left);

        void onRelease();

        void onPhotosLoaded();

        void onVideoSet();
    }

    private final View parentListView;
    private final AvatarImageView imageView;
    private final int[] galleryLocation = new int[2];
    private final int[] parentLocation = new int[2];
    private ProfileGalleryBlurView blurView;
    private String imageUrl;
    private String imageFilter;
    private boolean imageAllowAnimated;
    private final List<ViewPager.OnPageChangeListener> pageChangeListeners = new ArrayList<>();
    private PinchToZoomHelper pinchToZoomHelper;
    private boolean parentTouchActive;

    public ProfileGalleryView(@NonNull Context context, @NonNull View parentListView) {
        super(context);
        this.parentListView = parentListView;

        imageView = new AvatarImageView(context);
        addView(imageView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        setVisibility(GONE);
    }

    protected void setCustomAvatarProgress(float progress) {

    }

    public void setActionsHeight(int actionsHeight) {
        setPadding(0, 0, 0, Math.max(0, actionsHeight));
    }

    public void setBlurView(@Nullable ProfileGalleryBlurView view) {
        if (blurView == view) {
            return;
        }
        if (blurView != null) {
            blurView.setView(null);
        }
        blurView = view;
        if (view != null) {
            view.setView(this);
            setActionsHeight(view.actionSize);
            view.setVisibility(getVisibility());
            view.setAlpha(getAlpha());
        }
    }

    // Single-item pager surface used by ProfileGalleryBlurView.

    public void addOnPageChangeListener(@NonNull ViewPager.OnPageChangeListener listener) {
        if (!pageChangeListeners.contains(listener)) {
            pageChangeListeners.add(listener);
        }
    }

    public void removeOnPageChangeListener(@NonNull ViewPager.OnPageChangeListener listener) {
        pageChangeListeners.remove(listener);
    }

    public int getCurrentItem() {
        return 0;
    }

    public boolean isZooming() {
        return pinchToZoomHelper != null && pinchToZoomHelper.isInOverlayModeFor(this);
    }

    @Nullable
    public View getItemViewAt(int index) {
        if (index == 0) {
            return imageView;
        }
        return null;
    }

    public int getRealPosition() {
        return 0;
    }

    public int getRealCount() {
        return 1;
    }

    @Nullable
    public ImageLocation getImageLocation(int position) {
        if (position != 0 || imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        return ImageLocation.getForPath(imageUrl);
    }

    public boolean isCurrentItemVideo() {
        return false;
    }

    public float getCurrentItemProgress() {
        return 1f;
    }

    public boolean isLoadingCurrentVideo() {
        return false;
    }

    public void setImage(
            @Nullable String url,
            @Nullable String filter,
            @Nullable Drawable placeholder,
            boolean allowAnimated
    ) {
        imageUrl = url;
        imageFilter = filter;
        imageAllowAnimated = allowAnimated;
        ImageReceiver receiver = imageView.getImageReceiver();
        receiver.setAllowDecodeSingleFrame(!allowAnimated);
        if (url == null || url.isEmpty()) {
            receiver.setImageBitmap((Bitmap) null);
        } else {
            imageView.setImage(url, filter, placeholder);
        }
    }

    public void prepareTransitionFrom(@NonNull ProfileActivity.AvatarImageView avatarImageView) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        ImageReceiver source = avatarImageView.getImageReceiver();
        Drawable thumb = source != null ? source.getDrawable() : null;
        imageView.getImageReceiver().setAllowDecodeSingleFrame(!imageAllowAnimated);
        imageView.setImage(imageUrl, imageFilter, thumb);
    }

    // Keep the URL request alive while adopting the collapsed avatar as its thumbnail.
    public void showAvatarContent(@NonNull ProfileActivity.AvatarImageView avatarImageView) {
        ImageReceiver source = avatarImageView.getImageReceiver();
        if (source == null || source.getDrawable() == null) {
            return;
        }
        imageView.getImageReceiver().setAllowDecodeSingleFrame(!imageAllowAnimated);
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageView.getImageReceiver().setImageBitmap(source.getDrawable());
        } else {
            imageView.setImage(imageUrl, imageFilter, source.getDrawable());
        }
    }

    public void updateSize(int width, int height) {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            setLayoutParams(new FrameLayout.LayoutParams(width, height));
            return;
        }
        if (params.width == width && params.height == height) {
            return;
        }
        params.width = width;
        params.height = height;
        setLayoutParams(params);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (blurView != null) {
            blurView.setVisibility(visibility);
        }
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (blurView != null) {
            blurView.setAlpha(alpha);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (blurView != null) {
            blurView.setTranslationY(getHeight() - blurView.getMeasuredHeight());
            blurView.notifyUpdateSize();
        }
    }

    public boolean hasImages() {
        return imageView.getImageReceiver().hasNotThumb();
    }

    @NonNull
    public BackupImageView getCurrentItemView() {
        return imageView;
    }

    @NonNull
    public ImageReceiver getImageReceiver() {
        return imageView.getImageReceiver();
    }

    @Nullable
    public ProfileGalleryBlurView getBlurDrawer() {
        return blurView;
    }

    public void onDestroy() {
        pageChangeListeners.clear();
        pinchToZoomHelper = null;
        parentTouchActive = false;
        if (blurView != null) {
            ProfileGalleryBlurView view = blurView;
            blurView = null;
            view.destroy();
        }
    }

    private void dispatchToParentList(@NonNull MotionEvent event) {
        MotionEvent copy = MotionEvent.obtain(event);
        getLocationOnScreen(galleryLocation);
        parentListView.getLocationOnScreen(parentLocation);
        copy.offsetLocation(
                galleryLocation[0] - parentLocation[0],
                galleryLocation[1] - parentLocation[1]
        );
        parentListView.onTouchEvent(copy);
        copy.recycle();
    }

    private void cancelParentTouch(@NonNull MotionEvent source) {
        if (!parentTouchActive) {
            return;
        }
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        dispatchToParentList(cancel);
        cancel.recycle();
        parentTouchActive = false;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        // The image child is display-only. ProfileGalleryView must own the full
        // stream so it can hand a vertical drag to the outer list and atomically
        // cancel that stream when PinchToZoomHelper takes over.
        return true;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            parentTouchActive = true;
        }

        boolean zoomHandled = pinchToZoomHelper != null && pinchToZoomHelper.checkPinchToZoom(
                event,
                this,
                imageView.getImageReceiver(),
                null,
                null,
                null
        );
        if (zoomHandled || pinchToZoomHelper != null && pinchToZoomHelper.isInOverlayModeFor(this)) {
            // Upstream cancels whichever consumer loses the gesture. Without this
            // cancel, RecyclerView keeps its velocity tracker/flinger alive behind
            // the zoom overlay and scrolls as the two fingers move.
            cancelParentTouch(event);
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dispatchToParentList(event);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    parentTouchActive = false;
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    public void setPinchToZoomHelper(@Nullable PinchToZoomHelper pinchToZoomHelper) {
        this.pinchToZoomHelper = pinchToZoomHelper;
    }

    private class AvatarImageView extends BackupImageView implements SizeNotifierFrameLayout.IViewWithInvalidateCallback {

        Runnable invalidateCallback;

        AvatarImageView(Context context) {
            super(context);
            getImageReceiver().setAllowDecodeSingleFrame(true);
        }

        @Override
        public void listenInvalidate(Runnable callback) {
            invalidateCallback = callback;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }

        @Override
        public void invalidate(int l, int t, int r, int b) {
            super.invalidate(l, t, r, b);
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

import org.telegram.messenger.ImageReceiver;

/**
 * NativeDo stub of org.telegram.ui.Components.AnimatedFileDrawable (1340 lines upstream).
 *
 * <p>The full upstream implementation decodes GIF/animated WebP/MP4 frames via
 * ffmpeg JNI. NativeDo doesn't run the native layer yet; GIF support will go
 * through Coil instead. We expose the API surface that ImageReceiver and
 * friends touch so the receiver pipeline compiles. All animation methods are
 * no-ops; queries return safe defaults.
 *
 * <p>Extends {@link BitmapDrawable} to match upstream so {@code (BitmapDrawable)} casts
 * from ImageReceiver resolve.
 */
public class AnimatedFileDrawable extends BitmapDrawable {

    public AnimatedFileDrawable() {
        super((Bitmap) null);
    }

    public AnimatedFileDrawable(java.io.File path, boolean createDecoder, long streamFileSize, int priority,
                                org.telegram.tgnet.TLRPC.Document document,
                                org.telegram.messenger.ImageLocation location,
                                Object parentObject, long seekTo, int currentAccount, boolean preview,
                                int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions,
                                int cacheType, boolean notLoop) {
        super((Bitmap) null);
    }

    public AnimatedFileDrawable(java.io.File path, boolean createDecoder, long streamFileSize, int priority,
                                org.telegram.tgnet.TLRPC.Document document,
                                org.telegram.messenger.ImageLocation location,
                                Object parentObject, long seekTo, int currentAccount, boolean preview,
                                org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions) {
        super((Bitmap) null);
    }

    public boolean skipFrameUpdate;
    public boolean isWebmSticker;
    public int repeatCount;

    public void start() {
    }

    public void stop() {
    }

    public boolean isRunning() {
        return false;
    }

    public void recycle() {
    }

    public void recycle(boolean force) {
    }

    public boolean hasBitmap() {
        return false;
    }

    public boolean isHeavyDrawable() {
        return false;
    }

    public Bitmap getAnimatedBitmap() {
        return null;
    }

    public void addParent(ImageReceiver receiver) {
    }

    public void removeParent(ImageReceiver receiver) {
    }

    public void addParentView(ImageReceiver receiver) {
    }

    public void removeParentView(ImageReceiver receiver) {
    }

    public void setParentView(View view) {
    }

    public void setRoundRadius(int[] radii) {
    }

    public void setStartEndTime(long start, long end) {
    }

    public void setAutoRepeat(int autoRepeat) {
    }

    public void setAutoRepeatCount(int count) {
    }

    public void setAutoRepeatTimeout(long timeout) {
    }

    public void setUseSharedQueue(boolean use) {
    }

    public int getOrientation() {
        return 0;
    }

    public void checkRepeat() {
    }

    public void setAllowDecodeSingleFrame(boolean allow) {
    }

    public void setAllowVibration(boolean allow) {
    }

    public void setIsWebmSticker(boolean value) {
        this.isWebmSticker = value;
    }

    public android.graphics.Bitmap getFrameAtTime(int ms, boolean precise) {
        return null;
    }

    public long getLastFrameTimestamp() {
        return 0L;
    }

    public void seekTo(long ms, boolean removeLoading, boolean force) {
    }

    public void restart(boolean force) {
    }

    public void setAllowDrawFramesWhileCacheGenerating(boolean allow) {
    }

    public void setActualDrawRect(float x, float y, float w, float h) {
    }

    public void setLimitFps(boolean value) {
    }

    public AnimatedFileDrawable makeCopy() {
        return new AnimatedFileDrawable();
    }

    public int getRenderingWidth() {
        return 0;
    }

    public int getRenderingHeight() {
        return 0;
    }

    public boolean isRecycled() {
        return false;
    }

    public android.util.Pair<Integer, Integer> getRenderingSize() {
        return null;
    }

    public java.util.ArrayList<ImageReceiver> getParents() {
        return new java.util.ArrayList<>();
    }

    public void drawInBackground(android.graphics.Canvas canvas, float x, float y, float w, float h, int alpha, android.graphics.ColorFilter colorFilter, int threadIndex) {
    }

    public void drawInternal(android.graphics.Canvas canvas, boolean drawInBackground, long currentTime, int threadIndex) {
    }

    @Override
    public void draw(android.graphics.Canvas canvas) {
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

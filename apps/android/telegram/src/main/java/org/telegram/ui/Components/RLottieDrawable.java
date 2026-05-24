/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

import org.telegram.messenger.ImageReceiver;

/**
 * NativeDo stub of org.telegram.ui.Components.RLottieDrawable (1473 lines upstream).
 *
 * <p>Extends {@link BitmapDrawable} to match upstream so {@code (BitmapDrawable)} casts
 * from ImageReceiver resolve.
 */
public class RLottieDrawable extends BitmapDrawable {

    public RLottieDrawable() {
        super((Bitmap) null);
    }

    public RLottieDrawable(int diceEmoji, int w, int h) {
        super((Bitmap) null);
    }

    public RLottieDrawable(String diceEmoji, int w, int h) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, int data, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, String fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, String fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, int fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, String json, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, String fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, String json, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, int fitzModifier) {
        super((Bitmap) null);
    }

    public void setLimitFps(boolean value) {
    }

    public void prepareForGenerateCache() {
    }

    public void releaseForGenerateCache() {
    }

    public void setGeneratingFrame(int frame) {
    }

    public int getFramesCount() {
        return 0;
    }

    public int getNextFrame(Bitmap bitmap) {
        return 0;
    }

    public boolean skipFrameUpdate;

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

    public void setAllowDrawFramesWhileCacheGenerating(boolean allow) {
    }

    public void addParentView(ImageReceiver receiver) {
    }

    public void removeParentView(ImageReceiver receiver) {
    }

    public void addParentView(View view) {
    }

    public void removeParentView(View view) {
    }

    public void setAutoRepeat(int autoRepeat) {
    }

    public void setAutoRepeatCount(int count) {
    }

    public void setAutoRepeatTimeout(long timeout) {
    }

    public void setAllowDecodeSingleFrame(boolean allow) {
    }

    public void setAllowVibration(boolean allow) {
    }

    public void setStartEndTime(long start, long end) {
    }

    public void setUseSharedQueue(boolean use) {
    }

    public void checkRepeat() {
    }

    public void drawInBackground(android.graphics.Canvas canvas, float x, float y, float w, float h, int alpha, android.graphics.ColorFilter colorFilter, int threadIndex) {
    }

    public void drawInternal(android.graphics.Canvas canvas, android.graphics.RectF rect, boolean drawInBackground, long currentTime, int threadIndex) {
    }

    public void restart(boolean force) {
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

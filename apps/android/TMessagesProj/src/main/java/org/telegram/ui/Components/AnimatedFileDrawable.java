/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.utils.BitmapsCache;

import java.io.File;
import java.util.ArrayList;

public class AnimatedFileDrawable extends BitmapDrawable implements Animatable, BitmapsCache.Cacheable {

    private static final int DEFAULT_DURATION_MS = 1000;
    private static final int FRAME_DELAY_MS = 33;

    private final File path;
    private final Object movieLock = new Object();
    private final ArrayList<ImageReceiver> parents = new ArrayList<>();
    private final ArrayList<View> secondParentViews = new ArrayList<>();
    private final RectF drawRect = new RectF();
    private final RectF roundRect = new RectF();
    private final Path roundPath = new Path();
    private final float[] radii = new float[8];
    private final int[] roundRadius = new int[4];

    private Movie movie;
    private Bitmap fallbackBitmap;
    private Bitmap currentFrameBitmap;
    private View parentView;
    private long startTimeMs;
    private int currentFrameTimeMs;
    private int durationMs = DEFAULT_DURATION_MS;
    private int intrinsicWidth;
    private int intrinsicHeight;
    private int renderingWidth;
    private int renderingHeight;
    private boolean isRunning;
    private boolean isRecycled;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean invalidateScheduled;
    private boolean useSharedQueue;
    private boolean limitFps;
    private boolean loop = true;
    private long startTime;
    private long endTime;
    private int autoRepeat = 1;
    private int autoRepeatCount = -1;
    private long autoRepeatTimeout;
    private long lastLoopIndex;
    private BitmapsCache bitmapsCache;

    public boolean skipFrameUpdate;
    public boolean isWebmSticker;
    public int repeatCount;

    private final Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            invalidateScheduled = false;
            if (!isRecycled && (isRunning || !decodeSingleFrame && !singleFrameDecoded)) {
                invalidateInternal();
            }
        }
    };

    public AnimatedFileDrawable() {
        super((Bitmap) null);
        path = null;
        initPaint();
    }

    public AnimatedFileDrawable(File path, boolean createDecoder, long streamFileSize, int priority,
                                org.telegram.tgnet.TLRPC.Document document,
                                org.telegram.messenger.ImageLocation location,
                                Object parentObject, long seekTo, int currentAccount, boolean preview,
                                int w, int h, BitmapsCache.CacheOptions cacheOptions,
                                int cacheType, boolean loop) {
        super((Bitmap) null);
        this.path = path;
        this.loop = loop;
        initPaint();
        loadMovie(path, w, h, cacheOptions);
        if (seekTo > 0) {
            seekTo(seekTo, false, true);
        }
    }

    public AnimatedFileDrawable(File path, boolean createDecoder, long streamFileSize, int priority,
                                org.telegram.tgnet.TLRPC.Document document,
                                org.telegram.messenger.ImageLocation location,
                                Object parentObject, long seekTo, int currentAccount, boolean preview,
                                BitmapsCache.CacheOptions cacheOptions) {
        this(path, createDecoder, streamFileSize, priority, document, location, parentObject, seekTo,
            currentAccount, preview, 0, 0, cacheOptions, document != null ? 1 : 0, true);
    }

    private void initPaint() {
        Paint paint = getPaint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
    }

    private void loadMovie(File file, int w, int h, BitmapsCache.CacheOptions cacheOptions) {
        if (file == null) {
            return;
        }
        try {
            movie = Movie.decodeFile(file.getAbsolutePath());
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (movie != null) {
            intrinsicWidth = Math.max(1, movie.width());
            intrinsicHeight = Math.max(1, movie.height());
            durationMs = movie.duration() > 0 ? movie.duration() : DEFAULT_DURATION_MS;
        } else {
            try {
                fallbackBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (fallbackBitmap != null) {
                intrinsicWidth = Math.max(1, fallbackBitmap.getWidth());
                intrinsicHeight = Math.max(1, fallbackBitmap.getHeight());
            }
        }
        renderingWidth = w > 0 ? w : intrinsicWidth;
        renderingHeight = h > 0 ? h : intrinsicHeight;
        if (cacheOptions != null && intrinsicWidth > 0 && intrinsicHeight > 0) {
            bitmapsCache = new BitmapsCache(file, this, cacheOptions,
                renderingWidth > 0 ? renderingWidth : intrinsicWidth,
                renderingHeight > 0 ? renderingHeight : intrinsicHeight,
                !limitFps);
        }
    }

    @Override
    public void start() {
        if (isRecycled || !hasBitmap()) {
            return;
        }
        if (!isRunning) {
            isRunning = true;
            startTimeMs = SystemClock.uptimeMillis() - currentFrameTimeMs;
            singleFrameDecoded = false;
        }
        scheduleNextFrame();
    }

    @Override
    public void stop() {
        isRunning = false;
        cancelScheduledInvalidate();
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public void recycle() {
        recycle(false);
    }

    public void recycle(boolean force) {
        if (isRecycled) {
            return;
        }
        isRecycled = true;
        stop();
        parents.clear();
        secondParentViews.clear();
        movie = null;
        if (fallbackBitmap != null && !fallbackBitmap.isRecycled()) {
            fallbackBitmap.recycle();
        }
        fallbackBitmap = null;
        if (currentFrameBitmap != null && !currentFrameBitmap.isRecycled()) {
            currentFrameBitmap.recycle();
        }
        currentFrameBitmap = null;
        if (bitmapsCache != null) {
            bitmapsCache.recycle();
            bitmapsCache = null;
        }
    }

    public boolean hasBitmap() {
        return !isRecycled && (movie != null || fallbackBitmap != null);
    }

    public boolean isHeavyDrawable() {
        return false;
    }

    public Bitmap getAnimatedBitmap() {
        if (!hasBitmap()) {
            return null;
        }
        int width = Math.max(1, intrinsicWidth);
        int height = Math.max(1, intrinsicHeight);
        if (currentFrameBitmap == null || currentFrameBitmap.isRecycled()
            || currentFrameBitmap.getWidth() != width || currentFrameBitmap.getHeight() != height) {
            currentFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } else {
            currentFrameBitmap.eraseColor(0);
        }
        Canvas canvas = new Canvas(currentFrameBitmap);
        drawFrame(canvas, new RectF(0, 0, width, height), 255, null, getCurrentFrameTime(), false, false);
        return currentFrameBitmap;
    }

    public void addParent(ImageReceiver receiver) {
        if (receiver != null && !parents.contains(receiver)) {
            parents.add(receiver);
        }
        if (isRunning) {
            scheduleNextFrame();
        }
    }

    public void removeParent(ImageReceiver receiver) {
        parents.remove(receiver);
        if (parents.isEmpty()) {
            repeatCount = 0;
            lastLoopIndex = 0;
        }
    }

    public void addParentView(ImageReceiver receiver) {
        addParent(receiver);
    }

    public void removeParentView(ImageReceiver receiver) {
        removeParent(receiver);
    }

    public void setParentView(View view) {
        if (parentView == null) {
            parentView = view;
        }
    }

    public void setRoundRadius(int[] radii) {
        if (radii == null) {
            for (int i = 0; i < roundRadius.length; i++) {
                roundRadius[i] = 0;
            }
            return;
        }
        for (int i = 0; i < roundRadius.length; i++) {
            roundRadius[i] = i < radii.length ? radii[i] : 0;
        }
    }

    public void setStartEndTime(long start, long end) {
        startTime = start;
        endTime = end;
    }

    public void setAutoRepeat(int autoRepeat) {
        this.autoRepeat = autoRepeat;
    }

    public void setAutoRepeatCount(int count) {
        autoRepeatCount = count;
    }

    public void setAutoRepeatTimeout(long timeout) {
        autoRepeatTimeout = timeout;
    }

    public void setUseSharedQueue(boolean use) {
        useSharedQueue = use;
    }

    public int getOrientation() {
        return 0;
    }

    public void checkRepeat() {
        if (autoRepeatCount >= 0 && repeatCount >= autoRepeatCount) {
            stop();
        } else {
            start();
        }
    }

    public void setAllowDecodeSingleFrame(boolean allow) {
        decodeSingleFrame = allow;
        if (!allow && hasBitmap()) {
            start();
        }
    }

    public void setAllowVibration(boolean allow) {
    }

    public void setIsWebmSticker(boolean value) {
        isWebmSticker = value;
    }

    public Bitmap getFrameAtTime(int ms, boolean precise) {
        if (!hasBitmap()) {
            return null;
        }
        int width = Math.max(1, intrinsicWidth);
        int height = Math.max(1, intrinsicHeight);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawFrame(canvas, new RectF(0, 0, width, height), 255, null, normalizeFrameTime(ms), false, false);
        return bitmap;
    }

    public long getLastFrameTimestamp() {
        return currentFrameTimeMs;
    }

    public void seekTo(long ms, boolean removeLoading, boolean force) {
        currentFrameTimeMs = normalizeFrameTime((int) ms);
        startTimeMs = SystemClock.uptimeMillis() - currentFrameTimeMs;
        singleFrameDecoded = false;
        invalidateInternal();
    }

    public void restart(boolean force) {
        currentFrameTimeMs = 0;
        startTimeMs = SystemClock.uptimeMillis();
        repeatCount = 0;
        lastLoopIndex = 0;
        singleFrameDecoded = false;
        start();
    }

    public void setAllowDrawFramesWhileCacheGenerating(boolean allow) {
    }

    public void setActualDrawRect(float x, float y, float w, float h) {
        drawRect.set(x, y, x + w, y + h);
    }

    public void setLimitFps(boolean value) {
        limitFps = value;
    }

    public AnimatedFileDrawable makeCopy() {
        AnimatedFileDrawable copy = new AnimatedFileDrawable(path, true, 0, 0, null, null,
            null, currentFrameTimeMs, 0, false, renderingWidth, renderingHeight, null, 0, loop);
        copy.setRoundRadius(roundRadius);
        copy.setIsWebmSticker(isWebmSticker);
        copy.setAllowDecodeSingleFrame(decodeSingleFrame);
        copy.setLimitFps(limitFps);
        copy.setUseSharedQueue(useSharedQueue);
        return copy;
    }

    public int getRenderingWidth() {
        return renderingWidth > 0 ? renderingWidth : intrinsicWidth;
    }

    public int getRenderingHeight() {
        return renderingHeight > 0 ? renderingHeight : intrinsicHeight;
    }

    public boolean isRecycled() {
        return isRecycled;
    }

    public android.util.Pair<Integer, Integer> getRenderingSize() {
        if (getRenderingWidth() <= 0 || getRenderingHeight() <= 0) {
            return null;
        }
        return new android.util.Pair<>(getRenderingWidth(), getRenderingHeight());
    }

    public ArrayList<ImageReceiver> getParents() {
        return parents;
    }

    public void drawInBackground(Canvas canvas, float x, float y, float w, float h, int alpha, ColorFilter colorFilter, int threadIndex) {
        if (canvas == null) {
            return;
        }
        drawFrame(canvas, new RectF(x, y, x + w, y + h), alpha, colorFilter, getCurrentFrameTime(), true, false);
    }

    public void drawInternal(Canvas canvas, boolean drawInBackground, long currentTime, int threadIndex) {
        if (canvas == null) {
            return;
        }
        Rect bounds = getBounds();
        RectF rect;
        if (!bounds.isEmpty()) {
            rect = new RectF(bounds);
        } else if (!drawRect.isEmpty()) {
            rect = new RectF(drawRect);
        } else {
            rect = new RectF(0, 0, Math.max(1, intrinsicWidth), Math.max(1, intrinsicHeight));
        }
        int frameTime = currentTime > 0 ? frameTimeForClock(currentTime) : getCurrentFrameTime();
        drawFrame(canvas, rect, getPaint().getAlpha(), getPaint().getColorFilter(), frameTime, drawInBackground, true);
        if (!drawInBackground) {
            afterFrameDrawn();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        drawInternal(canvas, false, SystemClock.uptimeMillis(), 0);
    }

    @Override
    public void setAlpha(int alpha) {
        getPaint().setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        getPaint().setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth > 0 ? intrinsicWidth : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight > 0 ? intrinsicHeight : -1;
    }

    @Override
    public int getNextFrame(Bitmap bitmap) {
        return drawNextCacheFrame(bitmap);
    }

    @Override
    public Bitmap getFirstFrame(Bitmap bitmap) {
        int width = Math.max(1, bitmap != null ? bitmap.getWidth() : intrinsicWidth);
        int height = Math.max(1, bitmap != null ? bitmap.getHeight() : intrinsicHeight);
        Bitmap result = bitmap != null ? bitmap : Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.eraseColor(0);
        drawFrame(new Canvas(result), new RectF(0, 0, width, height), 255, null, 0, false, false);
        return result;
    }

    @Override
    public void prepareForGenerateCache() {
        currentFrameTimeMs = 0;
    }

    @Override
    public void releaseForGenerateCache() {
    }

    private int drawNextCacheFrame(Bitmap bitmap) {
        if (bitmap == null || !hasBitmap()) {
            return BitmapsCache.FRAME_RESULT_NO_FRAME;
        }
        bitmap.eraseColor(0);
        int frameTime = currentFrameTimeMs;
        drawFrame(new Canvas(bitmap), new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), 255, null, frameTime, false, false);
        currentFrameTimeMs = normalizeFrameTime(currentFrameTimeMs + FRAME_DELAY_MS);
        return BitmapsCache.FRAME_RESULT_OK;
    }

    private void drawFrame(Canvas canvas, RectF rect, int alpha, ColorFilter colorFilter, int frameTime, boolean background, boolean schedule) {
        if (!hasBitmap() || rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        Paint paint = getPaint();
        int oldAlpha = paint.getAlpha();
        ColorFilter oldColorFilter = paint.getColorFilter();
        paint.setAlpha(alpha);
        if (colorFilter != null) {
            paint.setColorFilter(colorFilter);
        }
        canvas.save();
        applyRoundClip(canvas, rect);
        if (fallbackBitmap != null) {
            canvas.drawBitmap(fallbackBitmap, null, rect, paint);
        } else {
            synchronized (movieLock) {
                movie.setTime(normalizeFrameTime(frameTime));
                canvas.translate(rect.left, rect.top);
                canvas.scale(rect.width() / intrinsicWidth, rect.height() / intrinsicHeight);
                movie.draw(canvas, 0, 0, paint);
            }
        }
        canvas.restore();
        paint.setAlpha(oldAlpha);
        paint.setColorFilter(oldColorFilter);
        if (schedule && !background) {
            scheduleNextFrame();
        }
    }

    private void applyRoundClip(Canvas canvas, RectF rect) {
        boolean hasRadius = false;
        for (int i = 0; i < roundRadius.length; i++) {
            if (roundRadius[i] > 0) {
                hasRadius = true;
                break;
            }
        }
        if (!hasRadius) {
            return;
        }
        roundRect.set(rect);
        radii[0] = radii[1] = roundRadius[0];
        radii[2] = radii[3] = roundRadius[1];
        radii[4] = radii[5] = roundRadius[2];
        radii[6] = radii[7] = roundRadius[3];
        roundPath.reset();
        roundPath.addRoundRect(roundRect, radii, Path.Direction.CW);
        canvas.clipPath(roundPath);
    }

    private void afterFrameDrawn() {
        if (decodeSingleFrame) {
            singleFrameDecoded = true;
            return;
        }
        if (autoRepeatCount >= 0 && repeatCount >= autoRepeatCount) {
            stop();
            return;
        }
        scheduleNextFrame();
    }

    private int getCurrentFrameTime() {
        return frameTimeForClock(SystemClock.uptimeMillis());
    }

    private int frameTimeForClock(long now) {
        if (durationMs <= 0) {
            return 0;
        }
        if (startTimeMs == 0) {
            startTimeMs = now - currentFrameTimeMs;
        }
        if (!isRunning && currentFrameTimeMs > 0) {
            return currentFrameTimeMs;
        }
        long elapsed = Math.max(0, now - startTimeMs);
        if (startTime > 0 && endTime > startTime) {
            long window = Math.max(1, endTime - startTime);
            elapsed = startTime + elapsed % window;
        }
        long loopIndex = elapsed / durationMs;
        if (loopIndex > lastLoopIndex) {
            repeatCount += loopIndex - lastLoopIndex;
            lastLoopIndex = loopIndex;
        }
        if (!loop && loopIndex > 0) {
            currentFrameTimeMs = durationMs - 1;
            stop();
        } else {
            currentFrameTimeMs = (int) (elapsed % durationMs);
        }
        return currentFrameTimeMs;
    }

    private int normalizeFrameTime(int ms) {
        if (durationMs <= 0) {
            return 0;
        }
        int value = ms % durationMs;
        return value < 0 ? value + durationMs : value;
    }

    private void scheduleNextFrame() {
        if (isRecycled || skipFrameUpdate || invalidateScheduled || !hasBitmap()) {
            return;
        }
        if (decodeSingleFrame && singleFrameDecoded) {
            return;
        }
        if (!isRunning && decodeSingleFrame) {
            return;
        }
        invalidateScheduled = true;
        AndroidUtilities.runOnUIThread(invalidateRunnable, limitFps ? FRAME_DELAY_MS : 16);
    }

    private void cancelScheduledInvalidate() {
        if (invalidateScheduled) {
            AndroidUtilities.cancelRunOnUIThread(invalidateRunnable);
            invalidateScheduled = false;
        }
    }

    private void invalidateInternal() {
        for (int i = 0; i < parents.size(); i++) {
            parents.get(i).invalidate();
        }
        for (int i = 0; i < secondParentViews.size(); i++) {
            secondParentViews.get(i).invalidate();
        }
        if (parentView != null) {
            parentView.invalidate();
        }
    }
}

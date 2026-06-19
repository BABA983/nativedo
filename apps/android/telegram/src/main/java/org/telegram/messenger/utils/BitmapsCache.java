package org.telegram.messenger.utils;

import android.graphics.Bitmap;
import android.util.LruCache;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BitmapsCache {

    public static final int FRAME_RESULT_OK = 0;
    public static final int FRAME_RESULT_NO_FRAME = -1;
    public static final int COMPRESS_QUALITY_DEFAULT = 60;

    private static final int MAX_MEMORY_CACHE_BYTES = 8 * 1024 * 1024;
    private static final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(MAX_MEMORY_CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
            if (oldValue != null && oldValue != newValue && !oldValue.isRecycled()) {
                oldValue.recycle();
            }
        }
    };

    private final Cacheable source;
    private final String keyPrefix;
    private final int width;
    private final int height;
    private final boolean firstFrameOnly;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public final AtomicInteger framesProcessed = new AtomicInteger(0);
    public volatile boolean checked;

    public BitmapsCache(File sourceFile, Cacheable source, CacheOptions options, int w, int h, boolean noLimit) {
        this.source = source;
        width = Math.max(1, w);
        height = Math.max(1, h);
        firstFrameOnly = options != null && options.firstFrame;
        keyPrefix = sourceFile == null ? "memory" : sourceFile.getAbsolutePath() + "@" + width + "x" + height;
        checked = true;
    }

    public static class CacheOptions {
        public int compressQuality = COMPRESS_QUALITY_DEFAULT;
        public boolean fallback;
        public boolean firstFrame;
    }

    public static class Metadata {
        public int frame;
    }

    public interface Cacheable {
        void prepareForGenerateCache();
        int getNextFrame(Bitmap bitmap);
        void releaseForGenerateCache();
        Bitmap getFirstFrame(Bitmap bitmap);
    }

    public static void put(String key, Bitmap bitmap) {
        if (key == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        Bitmap cached = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        memoryCache.put(key, cached);
    }

    public static Bitmap get(String key) {
        Bitmap bitmap = memoryCache.get(key);
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        return bitmap;
    }

    public static void incrementTaskCounter() {
    }

    public static void decrementTaskCounter() {
    }

    public boolean cacheExist() {
        return memoryCache.get(frameKey(0)) != null;
    }

    public boolean needGenCache() {
        return !cacheExist();
    }

    public void createCache() {
        if (source == null || cancelled.get()) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        source.prepareForGenerateCache();
        try {
            if (firstFrameOnly) {
                Bitmap firstFrame = source.getFirstFrame(bitmap);
                if (firstFrame != null && !firstFrame.isRecycled()) {
                    put(frameKey(0), firstFrame);
                    framesProcessed.set(1);
                }
            } else {
                int frame = 0;
                while (!cancelled.get() && frame < 60) {
                    bitmap.eraseColor(0);
                    int result = source.getNextFrame(bitmap);
                    if (result != FRAME_RESULT_OK) {
                        break;
                    }
                    put(frameKey(frame), bitmap);
                    framesProcessed.incrementAndGet();
                    frame++;
                }
            }
        } finally {
            source.releaseForGenerateCache();
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    public int getFrame(Bitmap target, Metadata metadata) {
        int frame = metadata == null ? 0 : metadata.frame;
        Bitmap cached = get(frameKey(frame));
        if (cached == null || target == null || target.isRecycled()) {
            return FRAME_RESULT_NO_FRAME;
        }
        target.eraseColor(0);
        android.graphics.Canvas canvas = new android.graphics.Canvas(target);
        canvas.drawBitmap(cached, null, new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), null);
        if (metadata != null) {
            metadata.frame = frame + 1;
        }
        return FRAME_RESULT_OK;
    }

    public int getFrame(int frame, Bitmap target) {
        Bitmap cached = get(frameKey(frame));
        if (cached == null || target == null || target.isRecycled()) {
            return FRAME_RESULT_NO_FRAME;
        }
        target.eraseColor(0);
        android.graphics.Canvas canvas = new android.graphics.Canvas(target);
        canvas.drawBitmap(cached, null, new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), null);
        return FRAME_RESULT_OK;
    }

    public int getFrameCount() {
        return Math.max(1, framesProcessed.get());
    }

    public void cancelCreate() {
        cancelled.set(true);
    }

    public void recycle() {
        cancelled.set(true);
    }

    private String frameKey(int frame) {
        return keyPrefix + "#" + frame;
    }
}

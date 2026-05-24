package org.telegram.messenger.utils;

import android.graphics.Bitmap;

/**
 * Stub for Telegram bitmap cache utility.
 */
public class BitmapsCache {

    public static final int COMPRESS_QUALITY_DEFAULT = 60;

    public static class CacheOptions {
        public int compressQuality = COMPRESS_QUALITY_DEFAULT;
        public boolean fallback;
        public boolean firstFrame;
    }

    public interface Cacheable {
        void prepareForGenerateCache();
        int getNextFrame(Bitmap bitmap);
        void releaseForGenerateCache();
        Bitmap getFirstFrame(Bitmap bitmap);
    }

    public static void put(String key, Bitmap bitmap) {
    }

    public static Bitmap get(String key) {
        return null;
    }
}

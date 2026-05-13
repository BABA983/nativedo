// PORT-MODE: Native Feature
// HOST-WIRING:
//   - Shared URL bitmap cache/loader used by NativeDo topic/browser cells and
//     the Telegram-derived PhotoViewer URL backend.
// ADAPTED-BACKEND:
//   - Replaces Telegram FileLoader/ImageLoader with NativeDo authenticated OkHttp
//     plus app-cache files while keeping a single memory/thumbnail cache surface.
package io.github.baba983.nativedo.core;

import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.NativeDoApp;
import io.github.baba983.nativedo.ui.browser.BrowserImageDecoder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ImageLoader {
    private static final ExecutorService LOAD_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final OkHttpClient FALLBACK_CLIENT = new OkHttpClient();
    private static final LruCache<String, Bitmap> MEMORY_CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final LruCache<String, Bitmap> THUMBNAIL_CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    @Nullable
    public static Bitmap get(@Nullable String url) {
        if (url == null) {
            return null;
        }
        Bitmap cached = MEMORY_CACHE.get(url);
        if (cached != null) {
            return cached;
        }
        android.content.Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null) {
            Bitmap fromCoil = CoilBridge.bitmapFromMemoryCache(ctx, url);
            if (fromCoil != null) {
                put(url, fromCoil);
                return fromCoil;
            }
        }
        return null;
    }

    public static void put(@Nullable String url, @Nullable Bitmap bitmap) {
        if (url == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        // Convert HARDWARE bitmaps to ARGB_8888 before caching. Hardware bitmaps
        // are backed by GPU memory and can be recycled when the GL context changes
        // (e.g. activity pause/resume), leaving the cache full of recycled refs.
        Bitmap safe = softwareBitmap(bitmap);
        MEMORY_CACHE.put(url, safe != null ? safe : bitmap);
    }

    /** Returns an ARGB_8888 copy of a HARDWARE bitmap, or null if it can't be copied. */
    @Nullable
    public static Bitmap softwareBitmap(@Nullable Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        if (android.os.Build.VERSION.SDK_INT < 26 || source.getConfig() != Bitmap.Config.HARDWARE) {
            return source;
        }
        try {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    public static Bitmap getThumbnail(@Nullable String url) {
        if (url == null) {
            return null;
        }
        Bitmap cached = THUMBNAIL_CACHE.get(url);
        if (cached != null) {
            return cached;
        }
        Bitmap full = get(url);
        if (full != null) {
            Bitmap thumb = createSquareThumbnail(full);
            if (thumb != null) {
                THUMBNAIL_CACHE.put(url, thumb);
                return thumb;
            }
        }
        return null;
    }

    public static void putThumbnail(@Nullable String url, @Nullable Bitmap bitmap) {
        if (url == null || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        THUMBNAIL_CACHE.put(url, bitmap);
    }

    @Nullable
    public static Bitmap load(@NonNull String url) {
        Bitmap cached = get(url);
        if (cached != null) {
            return cached;
        }
        Bitmap loaded = loadBitmapFromCacheOrNetwork(url);
        if (loaded != null) {
            put(url, loaded);
        }
        return loaded;
    }

    /**
     * Asynchronously loads the image at {@code url} and delivers the result to
     * {@code callback} on the main thread. If the bitmap is already cached the
     * callback fires immediately.
     *
     * R3 cache unification: we route through Coil's SingletonImageLoader (already
     * wired with cookie/UA/Referer OkHttp via AppContainer.newImageLoader) so the
     * post body's Coil.load(), the GroupedPhotosListView strip's ImageReceiver,
     * and PhotoViewer's own bitmapFromCoilMemoryCache all hit a single decoded
     * bitmap. Hits return synchronously via the main thread; misses enqueue an
     * ImageRequest and mirror the result into our MEMORY_CACHE so legacy
     * ImageReceiver.setImage (sync get() path) also stays warm.
     */
    public static void loadAsync(@NonNull String url, @NonNull Callback callback) {
        Bitmap cached = get(url);
        if (cached != null) {
            MAIN_HANDLER.post(() -> callback.onLoaded(cached));
            return;
        }
        android.content.Context appContext = ApplicationLoader.applicationContext;
        if (appContext != null) {
            Bitmap coilCached = CoilBridge.bitmapFromMemoryCache(appContext, url);
            if (coilCached != null) {
                put(url, coilCached);
                MAIN_HANDLER.post(() -> callback.onLoaded(coilCached));
                return;
            }
            CoilBridge.loadAsync(appContext, url, bitmap -> {
                if (bitmap != null) {
                    put(url, bitmap);
                }
                callback.onLoaded(bitmap);
            });
            return;
        }
        LOAD_EXECUTOR.execute(() -> {
            Bitmap loaded = loadBitmapFromCacheOrNetwork(url);
            if (loaded != null) {
                put(url, loaded);
            }
            MAIN_HANDLER.post(() -> callback.onLoaded(loaded));
        });
    }

    @Nullable
    public static Bitmap createThumbnail(@Nullable Bitmap source) {
        if (source == null) {
            return null;
        }
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= 0 || srcH <= 0) {
            return null;
        }
        float scale = Math.min(100f / srcW, 100f / srcH);
        if (scale >= 1f) {
            return source;
        }
        int w = Math.round(srcW * scale);
        int h = Math.round(srcH * scale);
        try {
            return Bitmap.createScaledBitmap(source, w, h, true);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Creates a center-cropped square thumbnail (80x80) similar to Telegram's
     * "80_80" server thumbnail. Used by the GroupedPhotosListView strip so
     * CENTER_CROP fills the cell regardless of the source aspect ratio.
     */
    @Nullable
    public static Bitmap createSquareThumbnail(@Nullable Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= 0 || srcH <= 0) {
            return null;
        }
        int size = 80;
        int x = (srcW - Math.min(srcW, srcH)) / 2;
        int y = (srcH - Math.min(srcW, srcH)) / 2;
        int cropSize = Math.min(srcW, srcH);
        try {
            Bitmap cropped = Bitmap.createBitmap(source, x, y, cropSize, cropSize);
            if (cropped == source) {
                // Source was already square — scale it down.
                return Bitmap.createScaledBitmap(source, size, size, true);
            }
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, size, size, true);
            if (scaled != cropped) {
                cropped.recycle();
            }
            return scaled;
        } catch (Exception ignored) {
            return null;
        }
    }

    public interface Callback {
        void onLoaded(@Nullable Bitmap bitmap);
    }

    @Nullable
    private static Bitmap loadBitmapFromCacheOrNetwork(@NonNull String url) {
        Bitmap loaded = null;
        Response response = null;
        InputStream stream = null;
        try {
            File cacheFile = cacheFileFor(url);
            File typeFile = contentTypeFileFor(cacheFile);
            if (cacheFile.exists()) {
                stream = new FileInputStream(cacheFile);
                loaded = BrowserImageDecoder.decode(stream, readContentType(typeFile));
            }
            if (loaded == null) {
                closeQuietly(stream);
                stream = null;
                Request request = new Request.Builder().url(url).get().build();
                response = getImageOkHttpClient().newCall(request).execute();
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    return null;
                }
                String contentType = body.contentType() == null ? response.header("Content-Type") : body.contentType().toString();
                stream = body.byteStream();
                writeToFile(stream, cacheFile);
                writeContentType(typeFile, contentType);
                closeQuietly(stream);
                stream = new FileInputStream(cacheFile);
                loaded = BrowserImageDecoder.decode(stream, contentType);
            }
        } catch (Exception ignored) {
            loaded = null;
        } finally {
            closeQuietly(stream);
            if (response != null) {
                response.close();
            }
        }
        return loaded;
    }

    @NonNull
    private static OkHttpClient getImageOkHttpClient() {
        android.content.Context appContext = ApplicationLoader.applicationContext;
        if (appContext instanceof NativeDoApp) {
            return ((NativeDoApp) appContext).getAppContainer().getImageOkHttpClient();
        }
        return FALLBACK_CLIENT;
    }

    @NonNull
    private static File cacheFileFor(@NonNull String url) throws Exception {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "photo-viewer");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            name.append(String.format(Locale.US, "%02x", b));
        }
        return new File(dir, name.toString());
    }

    @NonNull
    private static File contentTypeFileFor(@NonNull File cacheFile) {
        return new File(cacheFile.getParentFile(), cacheFile.getName() + ".type");
    }

    @Nullable
    private static String readContentType(@NonNull File typeFile) {
        if (!typeFile.exists()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(typeFile)) {
            byte[] data = new byte[(int) typeFile.length()];
            int read = in.read(data);
            return read <= 0 ? null : new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeContentType(@NonNull File typeFile, @Nullable String contentType) {
        if (contentType == null) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(typeFile)) {
            out.write(contentType.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static void writeToFile(@NonNull InputStream stream, @NonNull File file) throws Exception {
        try (OutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void closeQuietly(@Nullable InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (Exception ignored) {
        }
    }
}

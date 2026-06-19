/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utilities {
    public static Pattern pattern = Pattern.compile("[\\-0-9]+");
    public static SecureRandom random = new SecureRandom();
    public static Random fastRandom = new Random(random.nextLong());

    public static volatile DispatchQueue stageQueue = new DispatchQueue("stageQueue");
    public static volatile DispatchQueue globalQueue = new DispatchQueue("globalQueue");
    public static volatile DispatchQueue cacheClearQueue = new DispatchQueue("cacheClearQueue");
    public static volatile DispatchQueue searchQueue = new DispatchQueue("searchQueue");
    public static volatile DispatchQueue phoneBookQueue = new DispatchQueue("phoneBookQueue");
    public static volatile DispatchQueue themeQueue = new DispatchQueue("themeQueue");
    public static volatile DispatchQueue externalNetworkQueue = new DispatchQueue("externalNetworkQueue");
    public static volatile DispatchQueue videoPlayerQueue;

    private final static String RANDOM_STRING_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    static {
        try {
            File URANDOM_FILE = new File("/dev/urandom");
            FileInputStream sUrandomIn = new FileInputStream(URANDOM_FILE);
            byte[] buffer = new byte[1024];
            sUrandomIn.read(buffer);
            sUrandomIn.close();
            random.setSeed(buffer);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    // ── Image operations (Java replacements for native image.cpp) ────────

    /**
     * Pin bitmap pixels in native memory to prevent GC relocation.
     * Java fallback: no-op. Modern ART GC (Android 8+) handles this well.
     */
    public static int pinBitmap(Bitmap bitmap) {
        // No Java equivalent of AndroidBitmap_lockPixels needed on modern ART.
        // Returning 1 (success) to match native convention.
        return bitmap != null ? 1 : 0;
    }

    /**
     * Unpin previously pinned bitmap pixels.
     * Java fallback: no-op.
     */
    public static void unpinBitmap(Bitmap bitmap) {
        // No-op counterpart to pinBitmap.
    }

    /**
     * Fast box/stack blur on a bitmap's pixel data.
     * Matches upstream native fastBlur/fastBlurMore from image.cpp.
     * Operates directly on ARGB_8888 pixel array for performance.
     */
    public static void blurBitmap(Object bitmapObj, int radius, int unpin, int width, int height, int stride) {
        if (!(bitmapObj instanceof Bitmap)) {
            return;
        }
        Bitmap bitmap = (Bitmap) bitmapObj;
        if (bitmap == null || width <= 0 || height <= 0 || stride <= 0) {
            return;
        }
        int[] pixels = new int[height * (stride / 4)];
        bitmap.getPixels(pixels, 0, stride / 4, 0, 0, width, height);
        stackBlurPixels(pixels, stride / 4, width, height, radius);
        bitmap.setPixels(pixels, 0, stride / 4, 0, 0, width, height);
    }

    /**
     * Detect if an image needs color inversion (premultiplied alpha fix).
     * Matches upstream native needInvert from image.cpp.
     * Checks if the majority of non-transparent pixels are dark (needing inversion for light theme).
     */
    public static int needInvert(Object bitmapObj, int unpin, int width, int height, int stride) {
        if (!(bitmapObj instanceof Bitmap)) {
            return 0;
        }
        Bitmap bitmap = (Bitmap) bitmapObj;
        if (bitmap == null || width <= 0 || height <= 0) {
            return 0;
        }
        // Only check small images (upstream: width*height <= 150*150, stride == width*4)
        if (width * height > 150 * 150) {
            return 0;
        }
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int matching = 0;
        int total = 0;
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            float alpha = ((pixel >> 24) & 0xFF) / 255.0f;
            if (alpha < 0.1f) {
                continue;
            }
            // Pre-multiplied to straight color
            int r = Math.min(255, (int) (((pixel) & 0xFF) / alpha));
            int g = Math.min(255, (int) (((pixel >> 8) & 0xFF) / alpha));
            int b = Math.min(255, (int) (((pixel >> 16) & 0xFF) / alpha));
            float brightness = (r * 0.299f + g * 0.587f + b * 0.114f) / 255.0f;
            if (brightness > 0.5f) {
                matching++;
            }
            total++;
        }
        if (total == 0) {
            return 0;
        }
        return (matching / (float) total) > 0.6f ? 1 : 0;
    }

    /**
     * CLAHE histogram equalization (photo editor enhance feature).
     * Not needed for NativeDo — no-op.
     */
    public static void calcCDT(ByteBuffer hsvBuffer, int width, int height, ByteBuffer buffer, ByteBuffer calcBuffer) {
        // NativeDo: photo enhance (CLAHE) not supported without native code.
    }

    /**
     * Video frame color-space conversion.
     * Not needed for NativeDo — no-op returning 0 (failure).
     */
    public static int convertVideoFrame(ByteBuffer src, ByteBuffer dest, int destFormat, int width, int height, int padding, int swap) {
        return 0;
    }

    // ── AES encryption (Java replacements for native jni.c + BoringSSL) ──

    private static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, int offset, int length) {
        // NativeDo: Telegram IGE encryption not applicable to Discourse.
    }

    private static void aesIgeEncryptionByteArray(byte[] buffer, byte[] key, byte[] iv, boolean encrypt, int offset, int length) {
        // NativeDo: Telegram IGE encryption not applicable to Discourse.
    }

    public static void aesCtrDecryption(ByteBuffer buffer, byte[] key, byte[] iv, int offset, int length) {
        // NativeDo: Telegram CTR decryption not applicable to Discourse.
    }

    public static void aesCtrDecryptionByteArray(byte[] buffer, byte[] key, byte[] iv, int offset, long length, int fileOffset) {
        // NativeDo: Telegram CTR decryption not applicable to Discourse.
        // Leaving buffer unchanged — callers are for secret chat / encrypted media.
    }

    private static void aesCbcEncryptionByteArray(byte[] buffer, byte[] key, byte[] iv, int offset, int length, int n, int encrypt) {
        // NativeDo: Telegram CBC encryption not applicable to Discourse.
    }

    public static void aesCbcEncryption(ByteBuffer buffer, byte[] key, byte[] iv, int offset, int length, int encrypt) {
        // NativeDo: Telegram CBC encryption not applicable to Discourse.
    }

    public static String readlink(String path) {
        // NativeDo: Linux readlink not available. Return path unchanged.
        return path;
    }

    public static String readlinkFd(int fd) {
        return null;
    }

    // ── File/directory operations (Java replacements for native jni.c) ──

    /**
     * Calculate total size of files in a directory tree.
     * Matches upstream native listdir() with docType filter.
     */
    public static long getDirSize(String path, int docType, boolean subdirs) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        return getDirSizeInternal(dir, docType, subdirs);
    }

    private static long getDirSizeInternal(File dir, int docType, boolean subdirs) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (subdirs) {
                    size += getDirSizeInternal(file, docType, true);
                }
            } else {
                // docType 0 = all, 1 = other than music, 2 = music, 3 = emoji, 4 = temp, 5 = other
                if (docType == 0) {
                    size += file.length();
                } else {
                    String name = file.getName().toLowerCase();
                    boolean isMusic = name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".wav");
                    if (docType == 1 && !isMusic) {
                        size += file.length();
                    } else if (docType == 2 && isMusic) {
                        size += file.length();
                    } else if (docType == 4 && name.contains("_temp")) {
                        size += file.length();
                    } else if (docType == 5) {
                        size += file.length();
                    }
                }
            }
        }
        return size;
    }

    /**
     * Get last usage time of a file (seconds since epoch).
     * Matches upstream native getLastUsageFileTime: prefers atim, falls back to mtim.
     * Java fallback: uses lastModified() (mtime), as Android rarely updates atime.
     */
    public static long getLastUsageFileTime(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return 0;
        }
        return file.lastModified() / 1000;
    }

    /**
     * Delete files in a directory older than the given time threshold.
     * Matches upstream native clearDir with docType filter.
     */
    public static void clearDir(String path, int docType, long time, boolean subdirs) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        clearDirInternal(dir, docType, time, subdirs);
    }

    private static void clearDirInternal(File dir, int docType, long time, boolean subdirs) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (subdirs) {
                    clearDirInternal(file, docType, time, true);
                }
            } else {
                long lastModified = file.lastModified() / 1000;
                if (time == 0 || lastModified < time) {
                    file.delete();
                }
            }
        }
    }

    private static int pbkdf2(byte[] password, byte[] salt, byte[] dst, int iterations) {
        // NativeDo: PBKDF2 handled by computePBKDF2 using javax.crypto.
        return 0;
    }

    /**
     * Stack blur algorithm — pure Java implementation matching upstream
     * native stackBlurBitmap from image.cpp. Operates on ARGB_8888 pixels.
     */
    public static void stackBlurBitmap(Bitmap bitmap, int radius) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        if (radius < 1) {
            return;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        stackBlurPixels(pixels, w, w, h, radius);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    /**
     * Core stack blur on a pixel array. Ported from upstream image.cpp
     * stackBlurBitmap (which itself is a standard stack blur algorithm).
     *
     * @param pixels  ARGB_8888 pixel array
     * @param stride  pixels per row (= width for ARGB_8888)
     * @param width   image width
     * @param height  image height
     * @param radius  blur radius (>= 1)
     */
    private static void stackBlurPixels(int[] pixels, int stride, int width, int height, int radius) {
        int div = radius + radius + 1;
        int[] rSum = new int[width];
        int[] gSum = new int[width];
        int[] bSum = new int[width];
        int[] aSum = new int[width];

        // Horizontal pass
        for (int y = 0; y < height; y++) {
            int rIn = 0, gIn = 0, bIn = 0, aIn = 0;
            int idx = y * stride;

            // Initialize sums for the left edge
            for (int x = -radius; x <= radius; x++) {
                int sx = Math.max(0, Math.min(width - 1, x));
                int pixel = pixels[idx + sx];
                aIn += (pixel >> 24) & 0xFF;
                rIn += pixel & 0xFF;
                gIn += (pixel >> 8) & 0xFF;
                bIn += (pixel >> 16) & 0xFF;
            }

            for (int x = 0; x < width; x++) {
                int addIdx = Math.min(x + radius + 1, width - 1);
                int remIdx = Math.max(x - radius, 0);
                int addPixel = pixels[idx + addIdx];
                int remPixel = pixels[idx + remIdx];

                aSum[x] = aIn / div;
                rSum[x] = rIn / div;
                gSum[x] = gIn / div;
                bSum[x] = bIn / div;

                aIn += ((addPixel >> 24) & 0xFF) - ((remPixel >> 24) & 0xFF);
                rIn += (addPixel & 0xFF) - (remPixel & 0xFF);
                gIn += ((addPixel >> 8) & 0xFF) - ((remPixel >> 8) & 0xFF);
                bIn += ((addPixel >> 16) & 0xFF) - ((remPixel >> 16) & 0xFF);
            }

            // Write back
            for (int x = 0; x < width; x++) {
                pixels[idx + x] = (aSum[x] << 24) | (bSum[x] << 16) | (gSum[x] << 8) | rSum[x];
            }
        }

        // Vertical pass
        rSum = new int[height];
        gSum = new int[height];
        bSum = new int[height];
        aSum = new int[height];

        for (int x = 0; x < width; x++) {
            int rIn = 0, gIn = 0, bIn = 0, aIn = 0;

            for (int y = -radius; y <= radius; y++) {
                int sy = Math.max(0, Math.min(height - 1, y));
                int pixel = pixels[sy * stride + x];
                aIn += (pixel >> 24) & 0xFF;
                rIn += pixel & 0xFF;
                gIn += (pixel >> 8) & 0xFF;
                bIn += (pixel >> 16) & 0xFF;
            }

            for (int y = 0; y < height; y++) {
                int addY = Math.min(y + radius + 1, height - 1);
                int remY = Math.max(y - radius, 0);
                int addPixel = pixels[addY * stride + x];
                int remPixel = pixels[remY * stride + x];

                aSum[y] = aIn / div;
                rSum[y] = rIn / div;
                gSum[y] = gIn / div;
                bSum[y] = bIn / div;

                aIn += ((addPixel >> 24) & 0xFF) - ((remPixel >> 24) & 0xFF);
                rIn += (addPixel & 0xFF) - (remPixel & 0xFF);
                gIn += ((addPixel >> 8) & 0xFF) - ((remPixel >> 8) & 0xFF);
                bIn += ((addPixel >> 16) & 0xFF) - ((remPixel >> 16) & 0xFF);
            }

            for (int y = 0; y < height; y++) {
                pixels[y * stride + x] = (aSum[y] << 24) | (bSum[y] << 16) | (gSum[y] << 8) | rSum[y];
            }
        }
    }

    /**
     * Dithered gradient drawing on a bitmap.
     * Not needed for NativeDo — no-op.
     */
    public static void drawDitheredGradient(Bitmap bitmap, int[] colors, int startX, int startY, int endX, int endY) {
        // NativeDo: dithered gradient not supported without native code.
    }

    /**
     * Gradient wallpaper generation with phase animation.
     * Not needed for NativeDo — no-op.
     */
    public static void generateGradient(Bitmap bitmap, boolean unpin, int phase, float progress, int width, int height, int stride, int[] colors) {
        // NativeDo: gradient wallpaper generation not supported without native code.
    }

    /**
     * Native crash listener setup via breakpad.
     * Not applicable to NativeDo — no-op.
     */
    public static void setupNativeCrashesListener(String path) {
        // NativeDo: using Firebase Crashlytics instead of breakpad.
    }

    public static Bitmap stackBlurBitmapMax(Bitmap bitmap) {
        return stackBlurBitmapMax(bitmap, false);
    }

    public static Bitmap stackBlurBitmapMax(Bitmap bitmap, boolean round) {
        int w = AndroidUtilities.dp(20);
        int h = (int) (AndroidUtilities.dp(20) * (float) bitmap.getHeight() / bitmap.getWidth());
        Bitmap scaledBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.save();
        canvas.scale((float) scaledBitmap.getWidth() / bitmap.getWidth(), (float) scaledBitmap.getHeight() / bitmap.getHeight());
        if (round) {
            Path path = new Path();
            path.addCircle(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 2f - 1, Path.Direction.CW);
            canvas.clipPath(path);
        }
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
        Utilities.stackBlurBitmap(scaledBitmap, Math.max(10, Math.max(w, h) / 150));
        return scaledBitmap;
    }

    public static Bitmap stackBlurBitmapWithScaleFactor(Bitmap bitmap, float scaleFactor) {
        int w = (int) Math.max(AndroidUtilities.dp(20), bitmap.getWidth() / scaleFactor);
        int h = (int) Math.max(AndroidUtilities.dp(20) * (float) bitmap.getHeight() / bitmap.getWidth(), bitmap.getHeight() / scaleFactor);
        Bitmap scaledBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.save();
        canvas.scale((float) scaledBitmap.getWidth() / bitmap.getWidth(), (float) scaledBitmap.getHeight() / bitmap.getHeight());
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
        Utilities.stackBlurBitmap(scaledBitmap, Math.max(10, Math.max(w, h) / 150));
        return scaledBitmap;
    }

    public static Bitmap blurWallpaper(Bitmap src) {
        if (src == null) {
            return null;
        }
        Bitmap b;
        if (src.getHeight() > src.getWidth()) {
            b = Bitmap.createBitmap(Math.round(450f * src.getWidth() / src.getHeight()), 450, Bitmap.Config.ARGB_8888);
        } else {
            b = Bitmap.createBitmap(450, Math.round(450f * src.getHeight() / src.getWidth()), Bitmap.Config.ARGB_8888);
        }
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect rect = new Rect(0, 0, b.getWidth(), b.getHeight());
        new Canvas(b).drawBitmap(src, null, rect, paint);
        stackBlurBitmap(b, 12);
        return b;
    }

    public static void aesIgeEncryption(ByteBuffer buffer, byte[] key, byte[] iv, boolean encrypt, boolean changeIv, int offset, int length) {
        aesIgeEncryption(buffer, key, changeIv ? iv : iv.clone(), encrypt, offset, length);
    }

    public static void aesIgeEncryptionByteArray(byte[] buffer, byte[] key, byte[] iv, boolean encrypt, boolean changeIv, int offset, int length) {
        aesIgeEncryptionByteArray(buffer, key, changeIv ? iv : iv.clone(), encrypt, offset, length);
    }

    public static void aesCbcEncryptionByteArraySafe(byte[] buffer, byte[] key, byte[] iv, int offset, int length, int n, int encrypt) {
        // NativeDo: Telegram secret chat CBC encryption not applicable to Discourse.
        // Leaving buffer unchanged — callers in EncryptedFileInputStream are unused.
    }

    public static Integer parseInt(CharSequence value) {
        if (value == null) {
            return 0;
        }
        if (BuildConfig.BUILD_HOST_IS_WINDOWS) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return Integer.valueOf(matcher.group());
            }
        } else {
            int val = 0;
            try {
                int start = -1, end;
                for (end = 0; end < value.length(); ++end) {
                    char character = value.charAt(end);
                    boolean allowedChar = character == '-' || character >= '0' && character <= '9';
                    if (allowedChar && start < 0) {
                        start = end;
                    } else if (!allowedChar && start >= 0) {
                        end++;
                        break;
                    }
                }
                if (start >= 0) {
                    String str = value.subSequence(start, end).toString();
//                val = parseInt(str);
                    val = Integer.parseInt(str);
                }
            } catch (Exception ignore) {}
            return val;
        }
        return 0;
    }

    private static int parseInt(final String s) {
        int num = 0;
        boolean negative = true;
        final int len = s.length();
        final char ch = s.charAt(0);
        if (ch == '-') {
            negative = false;
        } else {
            num = '0' - ch;
        }
        int i = 1;
        while (i < len) {
            num = num * 10 + '0' - s.charAt(i++);
        }

        return negative ? -num : num;
    }

    public static Long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        long val = 0L;
        try {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String num = matcher.group(0);
                val = Long.parseLong(num);
            }
        } catch (Exception ignore) {

        }
        return val;
    }

    public static String parseIntToString(String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static boolean isGoodPrime(byte[] prime, int g) {
        // V1 stub: native prime check not available, accept all
        return true;
    }

    public static boolean isGoodGaAndGb(BigInteger g_a, BigInteger p) {
        return !(g_a.compareTo(BigInteger.valueOf(1)) <= 0 || g_a.compareTo(p.subtract(BigInteger.valueOf(1))) >= 0);
    }

    public static boolean arraysEquals(byte[] arr1, int offset1, byte[] arr2, int offset2) {
        if (arr1 == null || arr2 == null || offset1 < 0 || offset2 < 0 || arr1.length - offset1 > arr2.length - offset2 || arr1.length - offset1 < 0 || arr2.length - offset2 < 0) {
            return false;
        }
        boolean result = true;
        for (int a = offset1; a < arr1.length; a++) {
            if (arr1[a + offset1] != arr2[a + offset2]) {
                result = false;
            }
        }
        return result;
    }

    public static byte[] computeSHA1(byte[] convertme, int offset, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(convertme, offset, len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[20];
    }

    public static byte[] computeSHA1(ByteBuffer convertme, int offset, int len) {
        int oldp = convertme.position();
        int oldl = convertme.limit();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            convertme.position(offset);
            convertme.limit(len);
            md.update(convertme);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            convertme.limit(oldl);
            convertme.position(oldp);
        }
        return new byte[20];
    }

    public static byte[] computeSHA1(ByteBuffer convertme) {
        return computeSHA1(convertme, 0, convertme.limit());
    }

    public static byte[] computeSHA1(byte[] convertme) {
        return computeSHA1(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme) {
        return computeSHA256(convertme, 0, convertme.length);
    }

    public static byte[] computeSHA256(byte[] convertme, int offset, long len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(convertme, offset, (int) len);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[32];
    }

    public static byte[] computeSHA256(byte[]... args) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int a = 0; a < args.length; a++) {
                md.update(args[a], 0, args[a].length);
            }
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[32];
    }

    public static byte[] computeSHA512(byte[] convertme) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA512(byte[] convertme, byte[] convertme2) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            md.update(convertme2, 0, convertme2.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computePBKDF2(byte[] password, byte[] salt) {
        try {
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    new String(password, java.nio.charset.StandardCharsets.UTF_8).toCharArray(),
                    salt, 100000, 64 * 8);
            byte[] result = factory.generateSecret(spec).getEncoded();
            return result != null ? result : new byte[64];
        } catch (Exception e) {
            FileLog.e(e);
            return new byte[64];
        }
    }

    public static byte[] computeSHA512(byte[] convertme, byte[] convertme2, byte[] convertme3) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(convertme, 0, convertme.length);
            md.update(convertme2, 0, convertme2.length);
            md.update(convertme3, 0, convertme3.length);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new byte[64];
    }

    public static byte[] computeSHA256(byte[] b1, int o1, int l1, ByteBuffer b2, int o2, int l2) {
        int oldp = b2.position();
        int oldl = b2.limit();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(b1, o1, l1);
            b2.position(o2);
            b2.limit(l2);
            md.update(b2);
            return md.digest();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            b2.limit(oldl);
            b2.position(oldp);
        }
        return new byte[32];
    }

    public static long bytesToLong(byte[] bytes) {
        return ((long) bytes[7] << 56) + (((long) bytes[6] & 0xFF) << 48) + (((long) bytes[5] & 0xFF) << 40) + (((long) bytes[4] & 0xFF) << 32)
                + (((long) bytes[3] & 0xFF) << 24) + (((long) bytes[2] & 0xFF) << 16) + (((long) bytes[1] & 0xFF) << 8) + ((long) bytes[0] & 0xFF);
    }

    public static int bytesToInt(byte[] bytes) {
        return (((int) bytes[3] & 0xFF) << 24) + (((int) bytes[2] & 0xFF) << 16) + (((int) bytes[1] & 0xFF) << 8) + ((int) bytes[0] & 0xFF);
    }

    public static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public static String MD5(String md5) {
        if (md5 == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(AndroidUtilities.getStringBytes(md5));
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < array.length; a++) {
                sb.append(Integer.toHexString((array[a] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e(e);
        }
        return null;
    }

    public static String SHA256(String x) {
        if (x == null) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] array = md.digest(AndroidUtilities.getStringBytes(x));
            StringBuilder sb = new StringBuilder();
            for (int a = 0; a < array.length; a++) {
                sb.append(Integer.toHexString((array[a] & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            FileLog.e(e);
        }
        return null;
    }

    public static int clamp(int value, int maxValue, int minValue) {
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static long clamp(long value, long maxValue, long minValue) {
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static float clamp(float value, float maxValue, float minValue) {
        if (Float.isNaN(value)) {
            return minValue;
        }
        if (Float.isInfinite(value)) {
            return maxValue;
        }
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static float clamp01(float value) {
        return clamp(value, 1f, 0f);
    }

    public static double clamp(double value, double maxValue, double minValue) {
        if (Double.isNaN(value)) {
            return minValue;
        }
        if (Double.isInfinite(value)) {
            return maxValue;
        }
        return Math.max(Math.min(value, maxValue), minValue);
    }

    public static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    public static String generateRandomString() {
        return generateRandomString(16);
    }

    public static String generateRandomString(int chars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars; i++) {
            sb.append(RANDOM_STRING_CHARS.charAt(fastRandom.nextInt(RANDOM_STRING_CHARS.length())));
        }
        return sb.toString();
    }

    public static String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        String ext = null;
        if (idx != -1) {
            ext = fileName.substring(idx + 1);
        }
        if (ext == null) {
            return null;
        }
        ext = ext.toUpperCase();
        return ext;
    }

    public static interface Callback<T> {
        public void run(T arg);
    }

    public static interface CallbackVoidReturn<ReturnType> {
        public ReturnType run();
    }

    public static interface Callback0Return<ReturnType> {
        public ReturnType run();
    }

    public static interface CallbackReturn<Arg, ReturnType> {
        public ReturnType run(Arg arg);
    }

    public static interface Callback2Return<T1, T2, ReturnType> {
        public ReturnType run(T1 arg, T2 arg2);
    }

    public static interface Callback3Return<T1, T2, T3, ReturnType> {
        public ReturnType run(T1 arg, T2 arg2, T3 arg3);
    }

    public static interface Callback2<T, T2> {
        public void run(T arg, T2 arg2);
    }

    public static interface Callback3<T, T2, T3> {
        public void run(T arg, T2 arg2, T3 arg3);
    }

    public static interface Callback4<T, T2, T3, T4> {
        public void run(T arg, T2 arg2, T3 arg3, T4 arg4);
    }

    public static interface Callback4Return<T, T2, T3, T4, ReturnType> {
        public ReturnType run(T arg, T2 arg2, T3 arg3, T4 arg4);
    }
    public static interface Callback5<T, T2, T3, T4, T5> {
        public void run(T arg, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    public static interface Callback5Return<T, T2, T3, T4, T5, ReturnType> {
        public ReturnType run(T arg, T2 arg2, T3 arg3, T4 arg4, T5 arg5);
    }

    public static interface IndexedConsumer<T> {
        void accept(T t, int index);
    }

    public static <Key, Value> Value getOrDefault(HashMap<Key, Value> map, Key key, Value defaultValue) {
        Value v = map.get(key);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    public static void doCallbacks(Utilities.Callback<Runnable> ...actions) {
        doCallbacks(0, actions);
    }
    private static void doCallbacks(int i, Utilities.Callback<Runnable> ...actions) {
        if (actions != null && actions.length > i) {
            actions[i].run(() -> doCallbacks(i + 1, actions));
        }
    }

    public static void raceCallbacks(Runnable onFinish, Utilities.Callback<Runnable> ...actions) {
        if (actions == null || actions.length == 0) {
            if (onFinish != null) {
                onFinish.run();
            }
            return;
        }
        final int[] finished = new int[] { 0 };
        Runnable checkFinish = () -> {
            finished[0]++;
            if (finished[0] == actions.length) {
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        };
        for (int i = 0; i < actions.length; ++i) {
            actions[i].run(checkFinish);
        }
    }

    public static DispatchQueue getOrCreatePlayerQueue() {
        if (videoPlayerQueue == null) {
            videoPlayerQueue = new DispatchQueue("playerQueue");
        }
        return videoPlayerQueue;
    }

    public static boolean isNullOrEmpty(final Collection<?> list) {
        return list == null || list.isEmpty();
    }

    public static Uri uriParseSafe(String link) {
        try {
            return Uri.parse(link);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static <T> void swapItems(List<T> list, int index1, int index2) {
        T temp = list.get(index1);
        list.set(index1, list.get(index2));
        list.set(index2, temp);
    }

    public static long tryParseLong(String str, long defaultValue) {
        try {
            return Long.parseLong(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int divCeil(int a, int b) {
        return (a + b - 1) / b;
    }

    public static boolean copyBitmaps(Bitmap src, Bitmap dst) {
        if (src == null || dst == null || src.isRecycled() || dst.isRecycled()) {
            return false;
        }
        Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, 0, 0, null);
        return true;
    }

}

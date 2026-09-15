/*
 * This file is part of NativeDo.
 *
 * Small CSS-keyframes SVG drawable for Discourse avatar flairs.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import androidx.core.graphics.PathParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.utils.BitmapsCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class AnimatedSvgDrawable extends AnimatedFileDrawable {

    private static final int DEFAULT_DURATION_MS = 6000;
    private static final int FRAME_DELAY_MS = 16;

    private final File sourceFile;
    private final ArrayList<Layer> layers;
    private final HashMap<String, Keyframes> keyframes;
    private final RectF viewBox;
    private final RectF drawRect = new RectF();
    private final RectF roundRect = new RectF();
    private final Path roundPath = new Path();
    private final float[] radii = new float[8];
    private final int[] roundRadius = new int[4];
    private final ArrayList<ImageReceiver> parents = new ArrayList<>();
    private final ArrayList<View> secondParentViews = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    private View parentView;
    private long startTimeMs;
    private boolean running;
    private boolean recycled;
    private boolean invalidateScheduled;
    private boolean decodeSingleFrame;
    private boolean singleFrameDecoded;
    private boolean limitFps;
    private int renderingWidth;
    private int renderingHeight;
    private Bitmap currentFrameBitmap;
    private int durationMs;

    private final Runnable invalidateRunnable = new Runnable() {
        @Override
        public void run() {
            invalidateScheduled = false;
            if (!recycled && (running || !decodeSingleFrame && !singleFrameDecoded)) {
                invalidateInternal();
            }
        }
    };

    private AnimatedSvgDrawable(File sourceFile, ArrayList<Layer> layers, HashMap<String, Keyframes> keyframes,
                                RectF viewBox, int renderingWidth, int renderingHeight, int durationMs) {
        super();
        this.sourceFile = sourceFile;
        this.layers = layers;
        this.keyframes = keyframes;
        this.viewBox = viewBox;
        this.renderingWidth = renderingWidth > 0 ? renderingWidth : Math.max(1, Math.round(viewBox.width()));
        this.renderingHeight = renderingHeight > 0 ? renderingHeight : Math.max(1, Math.round(viewBox.height()));
        this.durationMs = durationMs > 0 ? durationMs : DEFAULT_DURATION_MS;
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            glowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dpf2(1.2f), BlurMaskFilter.Blur.NORMAL));
        }
    }

    public static AnimatedSvgDrawable create(File file, int width, int height) {
        if (file == null || !file.exists() || file.length() <= 0 || file.length() > 512 * 1024) {
            return null;
        }
        String svgText;
        try {
            svgText = readFile(file);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
        String lower = svgText.toLowerCase(Locale.US);
        if (!lower.contains("@keyframes") || !lower.contains("animation")) {
            return null;
        }
        try {
            return parse(file, svgText, width, height);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static AnimatedSvgDrawable parse(File file, String svgText, int width, int height) {
        Document document = Jsoup.parse(svgText, "", Parser.xmlParser());
        String css = collectStyleText(document);
        HashMap<String, Keyframes> keyframes = parseKeyframes(css);
        HashMap<String, Map<String, String>> classStyles = parseClassStyles(css);
        RectF viewBox = parseViewBox(document);
        ArrayList<Layer> layers = new ArrayList<>();
        int maxDuration = 0;
        for (Element pathElement : document.select("path[d]")) {
            Layer layer = parseLayer(pathElement, classStyles, keyframes);
            if (layer != null) {
                layers.add(layer);
                if (layer.animation != null) {
                    maxDuration = Math.max(maxDuration, layer.animation.durationMs);
                }
            }
        }
        if (layers.isEmpty() || keyframes.isEmpty()) {
            return null;
        }
        if (maxDuration <= 0) {
            maxDuration = DEFAULT_DURATION_MS;
        }
        return new AnimatedSvgDrawable(file, layers, keyframes, viewBox, width, height, maxDuration);
    }

    private static Layer parseLayer(Element pathElement, HashMap<String, Map<String, String>> classStyles,
                                    HashMap<String, Keyframes> keyframes) {
        HashMap<String, String> styles = new HashMap<>();
        String classAttr = pathElement.attr("class");
        if (!classAttr.isEmpty()) {
            String[] classes = classAttr.split("\\s+");
            for (String className : classes) {
                Map<String, String> classStyle = classStyles.get(className.trim());
                if (classStyle != null) {
                    styles.putAll(classStyle);
                }
            }
        }
        styles.putAll(parseDeclarations(pathElement.attr("style")));
        for (org.jsoup.nodes.Attribute attr : pathElement.attributes()) {
            String key = attr.getKey();
            if (!"class".equals(key) && !"style".equals(key) && !"d".equals(key)) {
                styles.put(key, attr.getValue());
            }
        }

        Path path = PathParser.createPathFromPathData(pathElement.attr("d"));
        if (path == null) {
            return null;
        }
        String transform = pathElement.attr("transform");
        if (!transform.isEmpty()) {
            Matrix matrix = SvgHelper.parseTransform(transform);
            if (matrix != null) {
                path.transform(matrix);
            }
        }
        Layer layer = new Layer();
        layer.path = path;
        layer.totalLength = pathLength(path);
        layer.fillColor = parseColor(styles.get("fill"), Color.TRANSPARENT);
        layer.strokeColor = parseColor(styles.get("stroke"), Color.TRANSPARENT);
        layer.strokeWidth = parseFloat(styles.get("stroke-width"), 1f);
        layer.strokeCap = parseCap(styles.get("stroke-linecap"));
        layer.strokeJoin = parseJoin(styles.get("stroke-linejoin"));
        layer.fillOpacity = parseFloat(styles.get("fill-opacity"), parseFloat(styles.get("opacity"), 1f));
        layer.strokeOpacity = parseFloat(styles.get("stroke-opacity"), parseFloat(styles.get("opacity"), 1f));
        layer.opacity = parseFloat(styles.get("opacity"), 1f);
        layer.dashArray = parseFloat(styles.get("stroke-dasharray"), 0f);
        layer.dashOffset = parseFloat(styles.get("stroke-dashoffset"), 0f);
        layer.scale = parseScale(styles.get("transform"), 1f);
        layer.hasGlow = styles.get("filter") != null && styles.get("filter").contains("url(");
        layer.animation = parseAnimation(styles.get("animation"), keyframes);
        if (layer.animation == null) {
            layer.animation = parseAnimation(styles.get("animation-name"), keyframes);
        }
        return layer.animation == null ? null : layer;
    }

    @Override
    public void start() {
        if (recycled || layers.isEmpty()) {
            return;
        }
        if (!running) {
            running = true;
            startTimeMs = SystemClock.uptimeMillis();
            singleFrameDecoded = false;
        }
        scheduleNextFrame();
    }

    @Override
    public void stop() {
        running = false;
        cancelScheduledInvalidate();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void recycle(boolean force) {
        if (recycled) {
            return;
        }
        recycled = true;
        stop();
        parents.clear();
        secondParentViews.clear();
        if (currentFrameBitmap != null && !currentFrameBitmap.isRecycled()) {
            currentFrameBitmap.recycle();
        }
        currentFrameBitmap = null;
    }

    @Override
    public boolean hasBitmap() {
        return !recycled && !layers.isEmpty();
    }

    @Override
    public Bitmap getAnimatedBitmap() {
        if (!hasBitmap()) {
            return null;
        }
        int width = Math.max(1, getRenderingWidth());
        int height = Math.max(1, getRenderingHeight());
        if (currentFrameBitmap == null || currentFrameBitmap.isRecycled()
                || currentFrameBitmap.getWidth() != width || currentFrameBitmap.getHeight() != height) {
            currentFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } else {
            currentFrameBitmap.eraseColor(0);
        }
        drawFrame(new Canvas(currentFrameBitmap), new RectF(0, 0, width, height), 255, null, frameTime(), false);
        return currentFrameBitmap;
    }

    @Override
    public void addParent(ImageReceiver receiver) {
        if (receiver != null && !parents.contains(receiver)) {
            parents.add(receiver);
        }
        if (running) {
            scheduleNextFrame();
        }
    }

    @Override
    public void removeParent(ImageReceiver receiver) {
        parents.remove(receiver);
        if (parents.isEmpty()) {
            repeatCount = 0;
        }
    }

    @Override
    public void addParentView(ImageReceiver receiver) {
        addParent(receiver);
    }

    @Override
    public void removeParentView(ImageReceiver receiver) {
        removeParent(receiver);
    }

    @Override
    public void setParentView(View view) {
        if (parentView == null) {
            parentView = view;
        }
    }

    @Override
    public void setRoundRadius(int[] radii) {
        for (int i = 0; i < roundRadius.length; i++) {
            roundRadius[i] = radii != null && i < radii.length ? radii[i] : 0;
        }
    }

    @Override
    public void checkRepeat() {
        start();
    }

    @Override
    public void setAllowDecodeSingleFrame(boolean allow) {
        decodeSingleFrame = allow;
        if (!allow && hasBitmap()) {
            singleFrameDecoded = false;
            start();
        }
    }

    @Override
    public Bitmap getFrameAtTime(int ms, boolean precise) {
        int width = Math.max(1, getRenderingWidth());
        int height = Math.max(1, getRenderingHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawFrame(new Canvas(bitmap), new RectF(0, 0, width, height), 255, null, normalizeFrameTime(ms), false);
        return bitmap;
    }

    @Override
    public long getLastFrameTimestamp() {
        return frameTime();
    }

    @Override
    public void seekTo(long ms, boolean removeLoading, boolean force) {
        startTimeMs = SystemClock.uptimeMillis() - normalizeFrameTime((int) ms);
        invalidateInternal();
    }

    @Override
    public void restart(boolean force) {
        repeatCount = 0;
        startTimeMs = SystemClock.uptimeMillis();
        singleFrameDecoded = false;
        start();
    }

    @Override
    public void setActualDrawRect(float x, float y, float w, float h) {
        drawRect.set(x, y, x + w, y + h);
    }

    @Override
    public void setLimitFps(boolean value) {
        limitFps = value;
    }

    @Override
    public AnimatedFileDrawable makeCopy() {
        AnimatedSvgDrawable copy = new AnimatedSvgDrawable(sourceFile, layers, keyframes, viewBox,
                renderingWidth, renderingHeight, durationMs);
        copy.setRoundRadius(roundRadius);
        copy.setLimitFps(limitFps);
        copy.decodeSingleFrame = decodeSingleFrame;
        return copy;
    }

    @Override
    public int getRenderingWidth() {
        return renderingWidth > 0 ? renderingWidth : Math.max(1, Math.round(viewBox.width()));
    }

    @Override
    public int getRenderingHeight() {
        return renderingHeight > 0 ? renderingHeight : Math.max(1, Math.round(viewBox.height()));
    }

    @Override
    public boolean isRecycled() {
        return recycled;
    }

    @Override
    public void drawInBackground(Canvas canvas, float x, float y, float w, float h, int alpha, ColorFilter colorFilter, int threadIndex) {
        if (canvas != null) {
            drawFrame(canvas, new RectF(x, y, x + w, y + h), alpha, colorFilter, frameTime(), true);
        }
    }

    @Override
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
            rect = new RectF(0, 0, getRenderingWidth(), getRenderingHeight());
        }
        int frameTime = currentTime > 0 ? frameTime(currentTime) : frameTime();
        drawFrame(canvas, rect, getPaint().getAlpha(), getPaint().getColorFilter(), frameTime, drawInBackground);
        if (!drawInBackground) {
            if (decodeSingleFrame) {
                singleFrameDecoded = true;
            } else {
                scheduleNextFrame();
            }
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
        return Math.max(1, Math.round(viewBox.width()));
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.max(1, Math.round(viewBox.height()));
    }

    @Override
    public int getNextFrame(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return BitmapsCache.FRAME_RESULT_NO_FRAME;
        }
        bitmap.eraseColor(0);
        drawFrame(new Canvas(bitmap), new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), 255, null, frameTime(), false);
        return BitmapsCache.FRAME_RESULT_OK;
    }

    @Override
    public Bitmap getFirstFrame(Bitmap bitmap) {
        int width = Math.max(1, bitmap != null ? bitmap.getWidth() : getRenderingWidth());
        int height = Math.max(1, bitmap != null ? bitmap.getHeight() : getRenderingHeight());
        Bitmap result = bitmap != null ? bitmap : Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.eraseColor(0);
        drawFrame(new Canvas(result), new RectF(0, 0, width, height), 255, null, 0, false);
        return result;
    }

    private void drawFrame(Canvas canvas, RectF rect, int alpha, ColorFilter colorFilter, int frameTime, boolean background) {
        if (rect.width() <= 0 || rect.height() <= 0 || layers.isEmpty()) {
            return;
        }
        canvas.save();
        applyRoundClip(canvas, rect);
        canvas.translate(rect.left, rect.top);
        canvas.scale(rect.width() / viewBox.width(), rect.height() / viewBox.height());
        canvas.translate(-viewBox.left, -viewBox.top);

        float centerX = viewBox.centerX();
        float centerY = viewBox.centerY();
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            FrameValues values = layer.valuesAt(keyframes, frameTime);
            if (values.scale != 1f) {
                canvas.save();
                canvas.scale(values.scale, values.scale, centerX, centerY);
                drawLayer(canvas, layer, values, alpha, colorFilter);
                canvas.restore();
            } else {
                drawLayer(canvas, layer, values, alpha, colorFilter);
            }
        }
        canvas.restore();
    }

    private void drawLayer(Canvas canvas, Layer layer, FrameValues values, int alpha, ColorFilter colorFilter) {
        if (layer.fillColor != Color.TRANSPARENT && values.fillOpacity > 0f) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(layer.fillColor);
            paint.setAlpha(frameAlpha(alpha, values.opacity * values.fillOpacity));
            paint.setColorFilter(colorFilter);
            canvas.drawPath(layer.path, paint);
        }

        if (layer.strokeColor != Color.TRANSPARENT && layer.strokeWidth > 0 && values.strokeOpacity > 0f) {
            Path strokePath = layer.visibleStrokePath(values.dashOffset);
            if (strokePath.isEmpty()) {
                return;
            }
            if (layer.hasGlow) {
                glowPaint.setColor(layer.strokeColor);
                glowPaint.setStrokeWidth(layer.strokeWidth * 3.5f);
                glowPaint.setAlpha(frameAlpha(alpha, values.opacity * values.strokeOpacity * 0.45f));
                glowPaint.setColorFilter(colorFilter);
                canvas.drawPath(strokePath, glowPaint);
            }
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(layer.strokeWidth);
            paint.setStrokeCap(layer.strokeCap);
            paint.setStrokeJoin(layer.strokeJoin);
            paint.setColor(layer.strokeColor);
            paint.setAlpha(frameAlpha(alpha, values.opacity * values.strokeOpacity));
            paint.setColorFilter(colorFilter);
            canvas.drawPath(strokePath, paint);
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

    private void scheduleNextFrame() {
        if (recycled || invalidateScheduled || (decodeSingleFrame && singleFrameDecoded)) {
            return;
        }
        invalidateScheduled = true;
        AndroidUtilities.runOnUIThread(invalidateRunnable, limitFps ? 33 : FRAME_DELAY_MS);
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

    private int frameTime() {
        return frameTime(SystemClock.uptimeMillis());
    }

    private int frameTime(long now) {
        if (startTimeMs == 0) {
            startTimeMs = now;
        }
        return normalizeFrameTime((int) Math.max(0, now - startTimeMs));
    }

    private int normalizeFrameTime(int ms) {
        if (durationMs <= 0) {
            return 0;
        }
        int value = ms % durationMs;
        return value < 0 ? value + durationMs : value;
    }

    private static int frameAlpha(int baseAlpha, float value) {
        return Math.max(0, Math.min(255, Math.round(baseAlpha * value)));
    }

    private static String readFile(File file) throws Exception {
        try (InputStream input = openMaybeGzip(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(file.length(), 64 * 1024))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static InputStream openMaybeGzip(File file) throws Exception {
        PushbackInputStream input = new PushbackInputStream(new FileInputStream(file), 2);
        int first = input.read();
        int second = input.read();
        if (second != -1) {
            input.unread(second);
        }
        if (first != -1) {
            input.unread(first);
        }
        if (first == 0x1f && second == 0x8b) {
            return new GZIPInputStream(input);
        }
        return input;
    }

    private static String collectStyleText(Document document) {
        StringBuilder builder = new StringBuilder();
        for (Element style : document.select("style")) {
            builder.append(style.data()).append('\n');
            builder.append(style.html()).append('\n');
        }
        return builder.toString();
    }

    private static RectF parseViewBox(Document document) {
        Element svg = document.selectFirst("svg[viewBox]");
        if (svg != null) {
            String[] parts = svg.attr("viewBox").trim().split("\\s+");
            if (parts.length >= 4) {
                float x = parseFloat(parts[0], 0f);
                float y = parseFloat(parts[1], 0f);
                float w = parseFloat(parts[2], 48f);
                float h = parseFloat(parts[3], 48f);
                if (w > 0 && h > 0) {
                    return new RectF(x, y, x + w, y + h);
                }
            }
        }
        return new RectF(0, 0, 48, 48);
    }

    private static HashMap<String, Map<String, String>> parseClassStyles(String css) {
        HashMap<String, Map<String, String>> result = new HashMap<>();
        int index = 0;
        while (index < css.length()) {
            int dot = css.indexOf('.', index);
            if (dot < 0) {
                break;
            }
            int brace = css.indexOf('{', dot);
            if (brace < 0) {
                break;
            }
            String selector = css.substring(dot + 1, brace).trim();
            if (!selector.matches("[A-Za-z0-9_-]+")) {
                index = brace + 1;
                continue;
            }
            int end = findMatchingBrace(css, brace);
            if (end < 0) {
                break;
            }
            result.put(selector, parseDeclarations(css.substring(brace + 1, end)));
            index = end + 1;
        }
        return result;
    }

    private static HashMap<String, Keyframes> parseKeyframes(String css) {
        HashMap<String, Keyframes> result = new HashMap<>();
        int index = 0;
        while (index < css.length()) {
            int at = css.indexOf("@keyframes", index);
            if (at < 0) {
                break;
            }
            int nameStart = at + "@keyframes".length();
            int brace = css.indexOf('{', nameStart);
            if (brace < 0) {
                break;
            }
            String name = css.substring(nameStart, brace).trim();
            int end = findMatchingBrace(css, brace);
            if (end < 0) {
                break;
            }
            Keyframes keyframes = parseKeyframeBlock(name, css.substring(brace + 1, end));
            if (keyframes != null) {
                result.put(name, keyframes);
            }
            index = end + 1;
        }
        return result;
    }

    private static Keyframes parseKeyframeBlock(String name, String block) {
        Keyframes keyframes = new Keyframes(name);
        int index = 0;
        while (index < block.length()) {
            int brace = block.indexOf('{', index);
            if (brace < 0) {
                break;
            }
            String selector = block.substring(index, brace).trim();
            int end = findMatchingBrace(block, brace);
            if (end < 0) {
                break;
            }
            Map<String, String> declarations = parseDeclarations(block.substring(brace + 1, end));
            String[] selectors = selector.split(",");
            for (String item : selectors) {
                Float percent = parsePercent(item.trim());
                if (percent != null) {
                    keyframes.frames.add(new Keyframe(percent, declarations));
                }
            }
            index = end + 1;
        }
        Collections.sort(keyframes.frames, (a, b) -> Float.compare(a.percent, b.percent));
        return keyframes.frames.isEmpty() ? null : keyframes;
    }

    private static int findMatchingBrace(String text, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Map<String, String> parseDeclarations(String block) {
        if (block == null || block.isEmpty()) {
            return Collections.emptyMap();
        }
        HashMap<String, String> result = new HashMap<>();
        String[] declarations = block.split(";");
        for (String declaration : declarations) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = declaration.substring(0, colon).trim();
            String value = declaration.substring(colon + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static AnimationSpec parseAnimation(String raw, HashMap<String, Keyframes> keyframes) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        String name = null;
        int duration = 0;
        for (String part : parts) {
            if (part.endsWith("ms")) {
                duration = Math.round(parseFloat(part.substring(0, part.length() - 2), 0f));
            } else if (part.endsWith("s")) {
                duration = Math.round(parseFloat(part.substring(0, part.length() - 1), 0f) * 1000f);
            } else if (keyframes.containsKey(part)) {
                name = part;
            }
        }
        if (name == null && keyframes.containsKey(raw.trim())) {
            name = raw.trim();
        }
        if (name == null) {
            return null;
        }
        if (duration <= 0) {
            duration = DEFAULT_DURATION_MS;
        }
        return new AnimationSpec(name, duration);
    }

    private static Float parsePercent(String raw) {
        String value = raw.endsWith("%") ? raw.substring(0, raw.length() - 1) : raw;
        try {
            return Float.parseFloat(value);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.isEmpty() || "none".equals(value)) {
            return fallback;
        }
        int space = value.indexOf(' ');
        if (space > 0) {
            value = value.substring(0, space);
        }
        value = value.replace("px", "");
        try {
            return Float.parseFloat(value);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private static float parseScale(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        int start = raw.indexOf("scale(");
        if (start < 0) {
            return fallback;
        }
        start += "scale(".length();
        int end = raw.indexOf(')', start);
        if (end < 0) {
            return fallback;
        }
        String value = raw.substring(start, end).split("[,\\s]+")[0];
        return parseFloat(value, fallback);
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.isEmpty() || "none".equalsIgnoreCase(value) || "transparent".equalsIgnoreCase(value)) {
            return Color.TRANSPARENT;
        }
        if (value.startsWith("#")) {
            try {
                if (value.length() == 4) {
                    int r = Integer.parseInt(value.substring(1, 2), 16);
                    int g = Integer.parseInt(value.substring(2, 3), 16);
                    int b = Integer.parseInt(value.substring(3, 4), 16);
                    return Color.rgb(r * 17, g * 17, b * 17);
                }
                return Color.parseColor(value);
            } catch (Throwable ignore) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Paint.Cap parseCap(String raw) {
        if ("square".equals(raw)) {
            return Paint.Cap.SQUARE;
        } else if ("butt".equals(raw)) {
            return Paint.Cap.BUTT;
        }
        return Paint.Cap.ROUND;
    }

    private static Paint.Join parseJoin(String raw) {
        if ("miter".equals(raw)) {
            return Paint.Join.MITER;
        } else if ("bevel".equals(raw)) {
            return Paint.Join.BEVEL;
        }
        return Paint.Join.ROUND;
    }

    private static float pathLength(Path path) {
        float length = 0f;
        PathMeasure measure = new PathMeasure(path, false);
        do {
            length += measure.getLength();
        } while (measure.nextContour());
        return length;
    }

    private static Path segmentPath(Path source, float totalLength, float visibleLength) {
        Path out = new Path();
        if (visibleLength <= 0f || totalLength <= 0f) {
            return out;
        }
        if (visibleLength >= totalLength - 0.5f) {
            out.set(source);
            return out;
        }
        PathMeasure measure = new PathMeasure(source, false);
        float remaining = visibleLength;
        do {
            float length = measure.getLength();
            if (remaining <= 0f) {
                break;
            }
            float end = Math.min(length, remaining);
            measure.getSegment(0, end, out, true);
            remaining -= end;
        } while (measure.nextContour());
        return out;
    }

    private static class Layer {
        Path path;
        float totalLength;
        int fillColor;
        int strokeColor;
        float strokeWidth;
        Paint.Cap strokeCap;
        Paint.Join strokeJoin;
        float opacity;
        float fillOpacity;
        float strokeOpacity;
        float dashArray;
        float dashOffset;
        float scale;
        boolean hasGlow;
        AnimationSpec animation;

        FrameValues valuesAt(HashMap<String, Keyframes> keyframes, int frameTimeMs) {
            FrameValues values = new FrameValues();
            values.opacity = opacity;
            values.fillOpacity = fillOpacity;
            values.strokeOpacity = strokeOpacity;
            values.dashOffset = dashOffset;
            values.scale = scale;
            Keyframes frames = animation == null ? null : keyframes.get(animation.name);
            if (frames == null) {
                return values;
            }
            float percent = (frameTimeMs % animation.durationMs) / (float) animation.durationMs * 100f;
            values.opacity = frames.floatValue("opacity", percent, values.opacity);
            values.fillOpacity = frames.floatValue("fill-opacity", percent, values.fillOpacity);
            values.strokeOpacity = frames.floatValue("stroke-opacity", percent, values.strokeOpacity);
            values.dashOffset = frames.floatValue("stroke-dashoffset", percent, values.dashOffset);
            values.scale = frames.scaleValue(percent, values.scale);
            return values;
        }

        Path visibleStrokePath(float currentDashOffset) {
            if (dashArray <= 0f) {
                return path;
            }
            float visibleFraction = Math.max(0f, Math.min(1f, (dashArray - currentDashOffset) / dashArray));
            return segmentPath(path, totalLength, totalLength * visibleFraction);
        }
    }

    private static class FrameValues {
        float opacity;
        float fillOpacity;
        float strokeOpacity;
        float dashOffset;
        float scale;
    }

    private static class AnimationSpec {
        final String name;
        final int durationMs;

        AnimationSpec(String name, int durationMs) {
            this.name = name;
            this.durationMs = durationMs;
        }
    }

    private static class Keyframes {
        final String name;
        final ArrayList<Keyframe> frames = new ArrayList<>();

        Keyframes(String name) {
            this.name = name;
        }

        float floatValue(String property, float percent, float fallback) {
            KeyframePair pair = pairFor(property, percent);
            if (pair == null) {
                return fallback;
            }
            float start = parseFloat(pair.start.declarations.get(property), fallback);
            float end = parseFloat(pair.end.declarations.get(property), start);
            return lerp(start, end, pair.progress);
        }

        float scaleValue(float percent, float fallback) {
            KeyframePair pair = pairFor("transform", percent);
            if (pair == null) {
                return fallback;
            }
            float start = parseScale(pair.start.declarations.get("transform"), fallback);
            float end = parseScale(pair.end.declarations.get("transform"), start);
            return lerp(start, end, pair.progress);
        }

        private KeyframePair pairFor(String property, float percent) {
            Keyframe previous = null;
            Keyframe next = null;
            for (int i = 0; i < frames.size(); i++) {
                Keyframe frame = frames.get(i);
                if (!frame.declarations.containsKey(property)) {
                    continue;
                }
                if (frame.percent <= percent) {
                    previous = frame;
                }
                if (frame.percent >= percent) {
                    next = frame;
                    break;
                }
            }
            if (previous == null) {
                previous = next;
            }
            if (next == null) {
                next = previous;
            }
            if (previous == null || next == null) {
                return null;
            }
            float span = next.percent - previous.percent;
            float progress = span <= 0f ? 0f : (percent - previous.percent) / span;
            return new KeyframePair(previous, next, easeInOut(progress));
        }
    }

    private static class Keyframe {
        final float percent;
        final Map<String, String> declarations;

        Keyframe(float percent, Map<String, String> declarations) {
            this.percent = percent;
            this.declarations = declarations;
        }
    }

    private static class KeyframePair {
        final Keyframe start;
        final Keyframe end;
        final float progress;

        KeyframePair(Keyframe start, Keyframe end, float progress) {
            this.start = start;
            this.end = end;
            this.progress = progress;
        }
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float easeInOut(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }
}

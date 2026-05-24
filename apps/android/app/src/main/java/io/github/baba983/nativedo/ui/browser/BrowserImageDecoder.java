package io.github.baba983.nativedo.ui.browser;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;

import java.io.InputStream;
import java.util.Locale;

public final class BrowserImageDecoder {

    private static final int DEFAULT_SVG_SIZE = 512;

    private BrowserImageDecoder() {
    }

    @Nullable
    public static Bitmap decode(@NonNull InputStream stream, @Nullable String contentType) {
        return decode(stream, contentType, DEFAULT_SVG_SIZE, DEFAULT_SVG_SIZE);
    }

    @Nullable
    public static Bitmap decode(@NonNull InputStream stream, @Nullable String contentType, int fallbackWidth, int fallbackHeight) {
        if (isSvg(contentType)) {
            return decodeSvg(stream, fallbackWidth, fallbackHeight);
        }
        return BitmapFactory.decodeStream(stream);
    }

    @Nullable
    private static Bitmap decodeSvg(@NonNull InputStream stream, int fallbackWidth, int fallbackHeight) {
        try {
            SVG svg = SVG.getFromInputStream(stream);
            int width = Math.round(svg.getDocumentWidth());
            int height = Math.round(svg.getDocumentHeight());
            if (width <= 0 || height <= 0) {
                RectF viewBox = svg.getDocumentViewBox();
                if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                    width = Math.round(viewBox.width());
                    height = Math.round(viewBox.height());
                }
            }
            if (width <= 0 || height <= 0) {
                width = Math.max(1, fallbackWidth);
                height = Math.max(1, fallbackHeight);
            }
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            svg.renderToCanvas(canvas);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isSvg(@Nullable String contentType) {
        if (TextUtils.isEmpty(contentType)) {
            return false;
        }
        return contentType.toLowerCase(Locale.US).contains("svg");
    }
}

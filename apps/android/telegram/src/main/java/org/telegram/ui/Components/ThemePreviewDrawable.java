/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.drawable.BitmapDrawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.DocumentObject;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

/**
 * Host theme preview used by ImageLoader for downloaded theme documents.
 * Telegram's theme-file database and preview resource pack are not part of the
 * NativeDo surface, so this keeps the same preview dimensions and main visual
 * layers using the local theme palette and optional wallpaper bitmap.
 */
public class ThemePreviewDrawable extends BitmapDrawable {

    private final DocumentObject.ThemeDocument themeDocument;

    public ThemePreviewDrawable(File pattern, DocumentObject.ThemeDocument document) {
        super(createPreview(pattern, document));
        themeDocument = document;
    }

    public DocumentObject.ThemeDocument getThemeDocument() {
        return themeDocument;
    }

    private static Bitmap createPreview(File pattern, DocumentObject.ThemeDocument document) {
        final int width = 560;
        final int height = 678;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int wallpaperColor = Theme.getColor(Theme.key_chat_wallpaper);
        int actionBarColor = Theme.getColor(Theme.key_actionBarDefault);
        int actionBarIconColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
        int incomingColor = Theme.getColor(Theme.key_chat_inBubble);
        int outgoingColor = Theme.getColor(Theme.key_chat_outBubble);
        int panelColor = Theme.getColor(Theme.key_chat_messagePanelBackground);
        if (document != null && document.accentColor != 0) {
            actionBarColor = document.accentColor;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new LinearGradient(0, 120, width, height - 120,
                ColorUtils.blendARGB(wallpaperColor, Color.WHITE, 0.08f),
                ColorUtils.blendARGB(wallpaperColor, Color.BLACK, 0.08f),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        if (pattern != null && pattern.isFile()) {
            Bitmap patternBitmap = BitmapFactory.decodeFile(pattern.getAbsolutePath());
            if (patternBitmap != null && !patternBitmap.isRecycled()) {
                paint.setAlpha(38);
                RectF dst = new RectF(0, 120, width, height - 120);
                canvas.drawBitmap(patternBitmap, null, dst, paint);
                paint.setAlpha(255);
            }
        }

        paint.setColor(actionBarColor);
        canvas.drawRect(0, 0, width, 120, paint);
        paint.setColor(panelColor);
        canvas.drawRect(0, height - 120, width, height, paint);

        paint.setColor(actionBarIconColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(30, 60, 52, 60, paint);
        canvas.drawLine(30, 60, 42, 48, paint);
        canvas.drawLine(30, 60, 42, 72, paint);
        paint.setStyle(Paint.Style.FILL);

        RectF bubble = new RectF(161, 216, width - 20, 308);
        paint.setColor(outgoingColor);
        canvas.drawRoundRect(bubble, 18, 18, paint);
        bubble.set(20, 323, 399, 415);
        paint.setColor(incomingColor);
        canvas.drawRoundRect(bubble, 18, 18, paint);
        bubble.set(161, 430, width - 20, 522);
        paint.setColor(outgoingColor);
        canvas.drawRoundRect(bubble, 18, 18, paint);

        paint.setColor(ColorUtils.setAlphaComponent(actionBarIconColor, 180));
        canvas.drawCircle(34, height - 60, 12, paint);
        canvas.drawCircle(width - 34, height - 60, 12, paint);
        return bitmap;
    }
}

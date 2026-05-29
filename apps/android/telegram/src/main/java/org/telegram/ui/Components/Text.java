package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;

public class Text {
    private final String text;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public Text(CharSequence text, float size, Typeface typeface) {
        this.text = text == null ? "" : text.toString();
        paint.setTextSize(AndroidUtilities.dp(size));
        paint.setTypeface(typeface);
    }

    public float getCurrentWidth() {
        return paint.measureText(text);
    }

    public void draw(Canvas canvas, float x, float y, int color, float alpha) {
        paint.setColor(color);
        paint.setAlpha((int) (255 * alpha));
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2f, paint);
    }
}

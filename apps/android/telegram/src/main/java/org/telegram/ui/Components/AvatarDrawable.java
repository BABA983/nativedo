/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

public class AvatarDrawable extends Drawable {

    private TextPaint namePaint;
    private boolean hasGradient;
    private int color, color2;
    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private float scaleSize = 1f;
    private StringBuilder stringBuilder = new StringBuilder(5);
    private int roundRadius = -1;

    private int gradientBottom;
    private int gradientColor1, gradientColor2;
    private LinearGradient gradient;

    private boolean drawAvatarBackground = true;

    public static final int AVATAR_TYPE_NORMAL = 0;

    private int alpha = 255;
    private boolean invalidateTextLayout;

    public AvatarDrawable() {
        super();
        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTypeface(AndroidUtilities.bold());
        namePaint.setTextSize(dp(18));
    }

    public void setDrawAvatarBackground(boolean drawAvatarBackground) {
        this.drawAvatarBackground = drawAvatarBackground;
    }

    public static int getColorIndex(long id) {
        return (int) Math.abs(id % Theme.keys_avatar_background.length);
    }

    public static int getColorForId(long id) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)]);
    }

    public void setScaleSize(float value) {
        scaleSize = value;
    }

    public void setColor(int value) {
        hasGradient = false;
        color = color2 = value;
    }

    public void setColor(int value, int value2) {
        hasGradient = true;
        color = value;
        color2 = value2;
    }

    public void setTextSize(int size) {
        namePaint.setTextSize(size);
    }

    public int getColor() {
        return color;
    }

    public int getColor2() {
        return color2;
    }

    private static String takeFirstCharacter(String text) {
        return text.substring(0, text.offsetByCodePoints(0, Math.min(text.codePointCount(0, text.length()), 1)));
    }

    public void setInfo(long id) {
        invalidateTextLayout = true;
        hasGradient = true;
        int idx = getColorIndex(id);
        color = Theme.getColor(Theme.keys_avatar_background[idx]);
        color2 = Theme.getColor(Theme.keys_avatar_background2[idx]);
        getAvatarSymbols("", "", stringBuilder);
    }

    /**
     * Discourse-flavored setInfo: id seeds the palette color, name produces the
     * monogram. Mirrors upstream {@code setInfo(long, String firstName, String lastName)}
     * with lastName fixed to null.
     */
    public void setInfo(long id, String name) {
        invalidateTextLayout = true;
        hasGradient = true;
        int idx = getColorIndex(id);
        color = Theme.getColor(Theme.keys_avatar_background[idx]);
        color2 = Theme.getColor(Theme.keys_avatar_background2[idx]);
        getAvatarSymbols(name, null, stringBuilder);
    }

    public void setInfo(long id, String firstName, String lastName) {
        setInfo(id, firstName);
    }

    public void setText(String text) {
        invalidateTextLayout = true;
        getAvatarSymbols(text, null, stringBuilder);
    }

    public static void getAvatarSymbols(String firstName, String lastName, StringBuilder result) {
        result.setLength(0);
        if (firstName != null && firstName.length() > 0) {
            result.append(takeFirstCharacter(firstName));
        }
        if (lastName != null && lastName.length() > 0) {
            String lastNameLastWord = lastName;
            int index;
            if ((index = lastNameLastWord.lastIndexOf(' ')) >= 0) {
                lastNameLastWord = lastNameLastWord.substring(index + 1);
            }
            result.append("‌");
            result.append(takeFirstCharacter(lastNameLastWord));
        } else if (firstName != null && firstName.length() > 0) {
            for (int a = firstName.length() - 1; a >= 0; a--) {
                if (firstName.charAt(a) == ' ') {
                    if (a != firstName.length() - 1 && firstName.charAt(a + 1) != ' ') {
                        int idx = result.length();
                        result.append("‌");
                        result.append(takeFirstCharacter(firstName.substring(idx)));
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.width();
        namePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_avatar_text), alpha));

        Paint backgroundPaint = Theme.avatar_backgroundPaint;
        if (hasGradient) {
            int c1 = ColorUtils.setAlphaComponent(getColor(), alpha);
            int c2 = ColorUtils.setAlphaComponent(getColor2(), alpha);
            if (gradient == null || gradientBottom != bounds.height() || gradientColor1 != c1 || gradientColor2 != c2) {
                gradient = new LinearGradient(0, 0, 0, gradientBottom = bounds.height(), gradientColor1 = c1, gradientColor2 = c2, Shader.TileMode.CLAMP);
            }
            backgroundPaint.setShader(gradient);
            backgroundPaint.setAlpha(alpha);
        } else {
            backgroundPaint.setShader(null);
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(getColor(), alpha));
        }
        canvas.save();
        canvas.translate(bounds.left, bounds.top);

        if (drawAvatarBackground) {
            if (roundRadius > 0) {
                AndroidUtilities.rectTmp.set(0, 0, size, size);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, roundRadius, roundRadius, backgroundPaint);
            } else {
                canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, backgroundPaint);
            }
        }

        if (invalidateTextLayout) {
            invalidateTextLayout = false;
            if (stringBuilder.length() > 0) {
                CharSequence text = stringBuilder.toString().toUpperCase();
                if (textLayout == null || !TextUtils.equals(text, textLayout.getText())) {
                    try {
                        textLayout = new StaticLayout(text, namePaint, dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (textLayout.getLineCount() > 0) {
                            textLeft = textLayout.getLineLeft(0);
                            textWidth = textLayout.getLineWidth(0);
                            textHeight = textLayout.getLineBottom(0);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } else {
                textLayout = null;
            }
        }
        if (textLayout != null) {
            float scale = size / (float) dp(50);
            canvas.scale(scale, scale, size / 2f, size / 2f);
            canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2);

            textLayout.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }

    public void setRoundRadius(int roundRadius) {
        this.roundRadius = roundRadius;
    }

    /**
     * NativeDo addition — kept for compatibility with call sites that pass a
     * 4-corner radii array. Upstream only supports a single uniform radius
     * via {@link #setRoundRadius(int)}; we honor the first entry.
     */
    public void setRoundRadius(int[] radii) {
        if (radii != null && radii.length > 0) {
            this.roundRadius = radii[0];
        }
    }
}

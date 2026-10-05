package io.github.baba983.nativedo.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.ui.Cells.SettingsSearchCell;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The QR card, ported from {@code QrActivity}'s {@code QrView} (QrActivity.java:928-1420): the
 * white rounded card with its shadow, the QR in Telegram's own drawing style, the
 * {@code @username} line under it, and the blank circle in the QR's middle where upstream draws
 * the plane logo - here the reader's own avatar when it is on disk.
 *
 * <p>The QR itself is {@code TelegramQRCodeWriter.encode}'s own drawing loop
 * (TelegramQRCodeWriter.java:61-194): zxing's {@code Encoder} through reflection (zxing is
 * TMessagesProj's {@code implementation} dependency, so this module cannot name it), the rounded
 * modules, the writer's public {@code drawSideQuadsGradient} for the three corner patterns, and
 * its centre blank sized for the middle mark. One step is left out: the plane logo the writer
 * stamps from Telegram's own SVG ({@code readRes(R.raw.qr_logo)} through {@code SvgHelper}), which
 * a linux.do card has no use for - and which is also why the writer itself is not called: its
 * signature needs zxing's {@code WriterException} at compile time and its drawing always expects
 * that logo. What is left out besides: the animated {@code MotionBackgroundDrawable} gradient the
 * account's themes paint the QR through, the timer/export-token branch and the crossfade.
 */
public class ProfileQrView extends View {

    /** QrActivity.QrView's own constants (QrActivity.java:930-931). */
    private static final float SHADOW_SIZE = dp(2);
    private static final float RADIUS = dp(20);

    private final String link;
    private final String username;
    private Bitmap backgroundBitmap;
    private Bitmap contentBitmap;
    /** The picture drawn in the QR's centre blank; null leaves it white. */
    private Bitmap centerBitmap;
    /** The centre blank's radius in the content bitmap, from the writer's own imageSize. */
    private float centerRadius;
    private int centerX;
    private int centerY;
    private final Paint contentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public ProfileQrView(Context context, String link, String username) {
        super(context);
        this.link = link == null ? "" : link;
        this.username = username == null ? "" : username;
    }

    /** The link the QR encodes; the screen test pins it. */
    public String getLink() {
        return link;
    }

    /** The reader's own picture for the middle of the code, once it is decoded. */
    public void setCenterBitmap(Bitmap bitmap) {
        centerBitmap = bitmap;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw && h == oldh) {
            return;
        }
        if (backgroundBitmap != null) {
            backgroundBitmap.recycle();
            backgroundBitmap = null;
        }
        // QrActivity.java:1008-1019: the white rounded card with its soft shadow.
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.WHITE);
        backgroundPaint.setShadowLayer(dp(4), 0f, SHADOW_SIZE, 0x0F000000);
        backgroundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(backgroundBitmap);
        RectF rect = new RectF(SHADOW_SIZE, SHADOW_SIZE, w - SHADOW_SIZE, h - SHADOW_SIZE);
        canvas.drawRoundRect(rect, RADIUS, RADIUS, backgroundPaint);
        prepareContent(w, h);
    }

    /**
     * QrActivity.java:1223-1420, the non-timer branch: the QR with the site's own error-correction
     * hints, the blank centre, and the {@code @username} line under it.
     */
    private void prepareContent(int w, int h) {
        if (w == 0 || h == 0 || TextUtils.isEmpty(link)) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        final int qrColor = 0xff000000;
        final int backgroundColor = 0x00ffffff;
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
        textPaint.setColor(qrColor);
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
        StaticLayout staticLayout = null;
        Drawable drawable;
        int attemptsCount = 2;
        final int textMaxWidth = bitmap.getWidth() - dp(20) * 2;
        for (int i = 0; i <= attemptsCount; ++i) {
            if (i == 0) {
                drawable = ContextCompat.getDrawable(getContext(), R.drawable.qr_at_large);
                textPaint.setTextSize(dp(30));
            } else if (i == 1) {
                drawable = ContextCompat.getDrawable(getContext(), R.drawable.qr_at_medium);
                textPaint.setTextSize(dp(25));
            } else {
                drawable = ContextCompat.getDrawable(getContext(), R.drawable.qr_at_small);
                textPaint.setTextSize(dp(19));
            }
            if (drawable != null) {
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                drawable.setColorFilter(new PorterDuffColorFilter(qrColor, PorterDuff.Mode.SRC_IN));
            }

            SpannableStringBuilder string = new SpannableStringBuilder(" " + username);
            string.setSpan(new SettingsSearchCell.VerticalImageSpan(drawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            float textWidth = textPaint.measureText(string, 1, string.length())
                    + (drawable == null ? 0 : drawable.getBounds().width());
            if (i <= 1 && textWidth > textMaxWidth) {
                continue;
            }
            int linesCount = textWidth > textMaxWidth ? 2 : 1;
            int layoutWidth = textMaxWidth;
            if (linesCount > 1) {
                layoutWidth = (int) (textWidth + (drawable == null ? 0 : drawable.getBounds().width())) / 2 + dp(2);
            }
            if (layoutWidth > textMaxWidth) {
                linesCount = 3;
                layoutWidth = (int) (textWidth + (drawable == null ? 0 : drawable.getBounds().width())) / 3 + dp(4);
            }
            staticLayout = org.telegram.ui.Components.StaticLayoutEx.createStaticLayout(
                    string, textPaint, layoutWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false, null,
                    Math.min(layoutWidth + dp(10), bitmap.getWidth()), linesCount);
            break;
        }
        float lineHeight = textPaint.descent() - textPaint.ascent();
        float textHeight = lineHeight * (staticLayout == null ? 0 : staticLayout.getLineCount());

        Bitmap qrBitmap = null;
        int imageSize = 0;
        int qrBitmapSize = w - dp(30) * 2;
        for (int version = 3; version < 5; ++version) {
            QrResult result = encodeQr(qrBitmapSize, version);
            if (result != null) {
                qrBitmap = result.bitmap;
                imageSize = result.imageSize;
                break;
            }
        }
        if (qrBitmap == null) {
            return;
        }

        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(backgroundColor);

        float left = (w - qrBitmap.getWidth()) / 2f;
        float qrTop = h * 0.15f;
        if (staticLayout != null && staticLayout.getLineCount() == 3) {
            qrTop = h * 0.13f;
        }
        canvas.drawBitmap(qrBitmap, left, qrTop, contentPaint);

        // QrActivity.java:1355-1361: the middle mark, sized by the writer's own imageSize. Upstream
        // paints a solid circle here and draws the plane logo over it; this card draws the reader's
        // avatar when it has one and leaves the writer's blank white otherwise.
        centerRadius = imageSize * 0.5f;
        centerX = (int) (left + qrBitmap.getWidth() * 0.5f);
        centerY = (int) (qrTop + qrBitmap.getWidth() * 0.5f);

        if (staticLayout != null) {
            float xTranslate = (canvas.getWidth() - staticLayout.getWidth()) * 0.5f;
            float yTranslate = qrTop + qrBitmap.getHeight()
                    + (canvas.getHeight() - (qrTop + qrBitmap.getHeight()) - textHeight) * 0.5f - dp(4);
            canvas.save();
            canvas.translate(xTranslate, yTranslate);
            staticLayout.draw(canvas);
            canvas.restore();
            qrBitmap.recycle();
        }

        contentBitmap = bitmap;
        invalidate();
    }

    /** The QR bitmap and the centre blank's diameter, from the writer's own maths. */
    private static class QrResult {
        final Bitmap bitmap;
        final int imageSize;

        QrResult(Bitmap bitmap, int imageSize) {
            this.bitmap = bitmap;
            this.imageSize = imageSize;
        }
    }

    /**
     * TelegramQRCodeWriter.encode's drawing loop, with the plane logo left out (see the class
     * comment). The geometry, colours, hint set and radius factor are the writer's own:
     * error correction M, no quiet zone, a pinned QR version, {@code radiusFactor 0.75f}, a 16 px
     * padding, and the centre block sized by {@code round((size - 32) / 4.65f / multiple)}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private QrResult encodeQr(int requestedSize, int hintVersion) {
        final int qrColor = 0xff000000;
        final int backgroundColor = 0x00ffffff;
        final float radiusFactor = 0.75f;
        try {
            Class<?> hintType = Class.forName("com.google.zxing.EncodeHintType");
            Class<?> levelType = Class.forName("com.google.zxing.qrcode.decoder.ErrorCorrectionLevel");
            Class<?> encoderType = Class.forName("com.google.zxing.qrcode.encoder.Encoder");
            Map hints = new HashMap();
            hints.put(Enum.valueOf((Class<? extends Enum>) hintType, "QR_VERSION"), hintVersion);
            Object level = Enum.valueOf((Class<? extends Enum>) levelType, "M");
            Object code = encoderType.getMethod("encode", String.class, levelType, Map.class)
                    .invoke(null, link, level, hints);
            Object matrix = code.getClass().getMethod("getMatrix").invoke(code);
            Method matrixGet = matrix.getClass().getMethod("get", int.class, int.class);
            int inputWidth = (int) matrix.getClass().getMethod("getWidth").invoke(matrix);
            int inputHeight = (int) matrix.getClass().getMethod("getHeight").invoke(matrix);
            boolean[][] modules = new boolean[inputWidth][inputHeight];
            for (int x = 0; x < inputWidth; x++) {
                for (int y = 0; y < inputHeight; y++) {
                    modules[x][y] = ((Number) matrixGet.invoke(matrix, x, y)).intValue() == 1;
                }
            }

            int sideQuadSize = 0;
            for (int x = 0; x < inputWidth; x++) {
                if (modules[x][0]) {
                    sideQuadSize++;
                } else {
                    break;
                }
            }
            int outputWidth = Math.max(requestedSize, inputWidth);
            int outputHeight = Math.max(requestedSize, inputHeight);
            int multiple = Math.min(outputWidth / inputWidth, outputHeight / inputHeight);
            int padding = 16;
            int size = multiple * inputWidth + padding * 2;

            int imageBloks = Math.round((size - 32) / 4.65f / multiple);
            if (imageBloks % 2 != inputWidth % 2) {
                imageBloks++;
            }
            int imageBlockX = (inputWidth - imageBloks) / 2;
            int imageSize = imageBloks * multiple - 24;

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(backgroundColor);
            Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blackPaint.setColor(qrColor);
            GradientDrawable rect = new GradientDrawable();
            rect.setShape(GradientDrawable.RECTANGLE);
            float[] radii = new float[8];
            rect.setCornerRadii(radii);

            TelegramQRCodeWriter.drawSideQuadsGradient(canvas, blackPaint, rect, sideQuadSize, multiple,
                    padding, size, radiusFactor, radii, backgroundColor, qrColor);

            boolean isTransparentBackground = Color.alpha(backgroundColor) == 0;
            float r = multiple / 2.0f * radiusFactor;
            for (int y = 0, outputY = padding; y < inputHeight; y++, outputY += multiple) {
                for (int x = 0, outputX = padding; x < inputWidth; x++, outputX += multiple) {
                    if (has(modules, sideQuadSize, imageBlockX, imageBloks, x, y)) {
                        Arrays.fill(radii, r);
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x, y - 1)) {
                            radii[0] = radii[1] = 0;
                            radii[2] = radii[3] = 0;
                        }
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x, y + 1)) {
                            radii[6] = radii[7] = 0;
                            radii[4] = radii[5] = 0;
                        }
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x - 1, y)) {
                            radii[0] = radii[1] = 0;
                            radii[6] = radii[7] = 0;
                        }
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x + 1, y)) {
                            radii[2] = radii[3] = 0;
                            radii[4] = radii[5] = 0;
                        }
                        rect.setColor(qrColor);
                        rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                        rect.draw(canvas);
                    } else {
                        boolean has = false;
                        Arrays.fill(radii, 0);
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x - 1, y - 1)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x - 1, y)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x, y - 1)) {
                            radii[0] = radii[1] = r;
                            has = true;
                        }
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x + 1, y - 1)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x + 1, y)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x, y - 1)) {
                            radii[2] = radii[3] = r;
                            has = true;
                        }
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x - 1, y + 1)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x - 1, y)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x, y + 1)) {
                            radii[6] = radii[7] = r;
                            has = true;
                        }
                        if (has(modules, sideQuadSize, imageBlockX, imageBloks, x + 1, y + 1)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x + 1, y)
                                && has(modules, sideQuadSize, imageBlockX, imageBloks, x, y + 1)) {
                            radii[4] = radii[5] = r;
                            has = true;
                        }
                        if (has && !isTransparentBackground) {
                            canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, blackPaint);
                            rect.setColor(backgroundColor);
                            rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                            rect.draw(canvas);
                        }
                    }
                }
            }
            canvas.setBitmap(null);
            return new QrResult(bitmap, imageSize);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** TelegramQRCodeWriter.has (TelegramQRCodeWriter.java:281-295), the centre blank included. */
    private static boolean has(boolean[][] modules, int sideQuadSize, int imageBlockX, int imageBloks,
                               int x, int y) {
        if (x >= imageBlockX && x < imageBlockX + imageBloks && y >= imageBlockX && y < imageBlockX + imageBloks) {
            return false;
        }
        if ((x < sideQuadSize || x >= modules.length - sideQuadSize) && y < sideQuadSize) {
            return false;
        }
        if (x < sideQuadSize && y >= modules[0].length - sideQuadSize) {
            return false;
        }
        return x >= 0 && y >= 0 && x < modules.length && y < modules[0].length && modules[x][y];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null);
        }
        if (contentBitmap != null) {
            canvas.drawBitmap(contentBitmap, 0f, 0f, contentPaint);
        }
        if (centerBitmap != null && !centerBitmap.isRecycled() && centerRadius > 0) {
            float half = centerBitmap.getWidth() * 0.5f;
            canvas.save();
            canvas.translate(centerX - half, centerY - half);
            android.graphics.Path clip = new android.graphics.Path();
            clip.addCircle(half, half, centerRadius, android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(centerBitmap, 0f, 0f, centerPaint);
            canvas.restore();
        }
    }
}

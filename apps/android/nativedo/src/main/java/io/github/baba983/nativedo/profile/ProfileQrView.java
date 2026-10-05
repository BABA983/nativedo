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
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.ui.Cells.SettingsSearchCell;

import java.util.HashMap;
import java.util.Map;

/**
 * The QR card, ported from {@code QrActivity}'s {@code QrView} (QrActivity.java:928-1420): the
 * white rounded card with its shadow, the QR built by upstream's own
 * {@link TelegramQRCodeWriter}, the {@code @username} line under it, and the solid circle in the
 * QR's middle where upstream draws its plane logo. What is left out is Telegram-only: the animated
 * {@code MotionBackgroundDrawable} gradient the QR is tinted through (it comes from the account's
 * themes), the timer/export-token branch, the crossfade between two contents, and the logo lottie.
 * The card is drawn in the QR's own black on white in both themes, the way upstream's writer
 * paints it before a theme is applied.
 */
public class ProfileQrView extends View {

    /** QrActivity.QrView's own constants (QrActivity.java:930-931). */
    private static final float SHADOW_SIZE = dp(2);
    private static final float RADIUS = dp(20);

    private final String link;
    private final String username;
    private Bitmap backgroundBitmap;
    private Bitmap contentBitmap;
    private final Paint contentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public ProfileQrView(Context context, String link, String username) {
        super(context);
        this.link = link == null ? "" : link;
        this.username = username == null ? "" : username;
    }

    /** The link the QR encodes; the screen test pins it. */
    public String getLink() {
        return link;
    }

    /**
     * The hints upstream builds by name (QrActivity.java:1345-1352): error correction M, no quiet
     * zone, a pinned QR version so the code fills the card the way upstream's does. zxing is
     * TMessagesProj's own {@code implementation} dependency, so this module cannot name its enums
     * at compile time; they are the same constants, looked up by name at runtime.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map qrHints() {
        HashMap hints = new HashMap();
        try {
            Class<?> hintType = Class.forName("com.google.zxing.EncodeHintType");
            Class<?> level = Class.forName("com.google.zxing.qrcode.decoder.ErrorCorrectionLevel");
            hints.put(Enum.valueOf((Class<? extends Enum>) hintType, "ERROR_CORRECTION"),
                    Enum.valueOf((Class<? extends Enum>) level, "M"));
            hints.put(Enum.valueOf((Class<? extends Enum>) hintType, "MARGIN"), 0);
        } catch (Exception e) {
            // A build without zxing cannot have TelegramQRCodeWriter either; an empty hint map
            // still encodes with the writer's own defaults.
        }
        return hints;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object hintConstant(String name) {
        try {
            Class<?> hintType = Class.forName("com.google.zxing.EncodeHintType");
            return Enum.valueOf((Class<? extends Enum>) hintType, name);
        } catch (Exception e) {
            return name;
        }
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
     * hints, the circle in its middle, and the {@code @username} line under it.
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
        Map hints = qrHints();
        TelegramQRCodeWriter writer = new TelegramQRCodeWriter();
        for (int version = 3; version < 5; ++version) {
            try {
                hints.put(hintConstant("QR_VERSION"), version);
                // TelegramQRCodeWriter.encode declares zxing's WriterException, which this module
                // cannot name at compile time (zxing is TMessagesProj's own implementation
                // dependency); the call is the same one, made reflectively.
                java.lang.reflect.Method encode = TelegramQRCodeWriter.class.getMethod("encode",
                        String.class, int.class, int.class, Map.class, Bitmap.class, float.class, int.class, int.class);
                qrBitmap = (Bitmap) encode.invoke(writer, link, qrBitmapSize, qrBitmapSize, hints, null, 0.75f, backgroundColor, qrColor);
                imageSize = writer.getImageSize();
            } catch (Exception ignore) {
            }
            if (qrBitmap != null) {
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

        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(qrColor);
        float xCenter = left + qrBitmap.getWidth() * 0.5f;
        float yCenter = qrTop + qrBitmap.getWidth() * 0.5f;
        canvas.drawCircle(xCenter, yCenter, imageSize * 0.5f, circlePaint);

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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null);
        }
        if (contentBitmap != null) {
            canvas.drawBitmap(contentBitmap, 0f, 0f, contentPaint);
        }
    }
}

// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/ui/Cells/DialogCell.java (measurement / drawing primitives only)
// UPSTREAM-COMMIT: master@2026-05-13
// EXACT-SUBSYSTEMS: measurement (heightDefault, messagePaddingStart, BADGE_*),
//                   drawing (selector + circle avatar + StaticLayout name/message + count pill + pinned dot)
// HOST-WIRING:
//   - Coil avatar load via NativeDo CoilBridge (Telegram uses ImageReceiver/FileLoader pipeline).
//   - Theme.ResourcesProvider -> Theme.getColor(Theme.key_*).
// ADAPTED-BACKEND:
//   - TLRPC.Dialog / MessageObject / MessagesController -> io.github.baba983.nativedo.core.model.TopicSummary.
//   - Time formatting via java.time.Instant + DateUtils (Discourse ISO-8601 strings),
//     not LocaleController.formatDateAudio.
//   - Category color tag (Discourse) replaces Telegram archive folder color.
// ADAPTED-PRODUCT:
//   - Renders a Discourse category color bar to the left of the avatar instead of the
//     archive/folder pill.
//   - "Last poster" username drawn alongside the date instead of the dialog "fromName".
// OMITTED:
//   - Forum topic icon (forum/icon-name composing), forwards, drafts, draft hint,
//     reactions row, stories ring, secret-chat lock, scam/verified/fake icons,
//     mute/unmute icon, swipe-to-archive overlay, recyclable thumb images,
//     animated emoji name spans, message preview thumbs, mentions/reactions
//     secondary badges, online dot, drag-reorder.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Cells/DialogCell.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Cells/DialogCell.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
// TODO(image-pipeline): import org.telegram.messenger.CoilBridge;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.TopicSummary;
import org.telegram.ui.ActionBar.Theme;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Telegram-shaped Discourse topic row.
 *
 * <p>Layout primitives (cell height, avatar position, name/time/message offsets,
 * unread badge geometry) mirror {@link org.telegram.ui.Cells.DialogCell} so the
 * row feels identical to Telegram's chat list. Backend reads come from
 * {@link TopicSummary} and the Coil image cache.
 */
public class TopicCell extends View {

    // ===== Layout constants (mirror DialogCell) =====
    public int heightDefault = 70;
    public int messagePaddingStart = 72;

    // BADGE_SIZE = BADGE_TEXT_PADDING * 2 + BADGE_TEXT_MIN_WIDTH; matches DialogCell.
    private static final float BADGE_SIZE = 20.666f;
    private static final float BADGE_TEXT_PADDING = 6.333f;
    private static final float BADGE_TEXT_MIN_WIDTH = 8f;
    private static final float BADGE_GAP = 25f - BADGE_TEXT_MIN_WIDTH;
    private static final float BADGE_MARGIN = 15.666f;

    // ===== Paints =====
    private final TextPaint namePaint;
    private final TextPaint messagePaint;
    private final TextPaint timePaint;
    private final TextPaint countTextPaint;
    private final Paint avatarPlaceholderPaint;
    private final Paint avatarTextBgPaint;
    private final TextPaint avatarMonogramPaint;
    private final Paint countPaint;
    private final Paint pinnedDotPaint;
    private final Paint categoryBarPaint;

    // ===== Cached layout =====
    private TopicSummary topic;
    private @Nullable Map<Integer, CategorySummary> categories;

    private int lastWidth;
    private int avatarLeft;
    private int avatarTop;
    private final RectF avatarRect = new RectF();

    private StaticLayout nameLayout;
    private int nameLeft;
    private int nameWidth;

    private StaticLayout messageLayout;
    private int messageLeft;
    private int messageTop;
    private int messageWidth;

    private StaticLayout timeLayout;
    private int timeLeft;
    private int timeTop;
    private int timeWidth;

    private StaticLayout countLayout;
    private int countLeft;
    private int countTop;
    private int countWidth;
    private boolean drawCount;

    private boolean drawPinned;
    private int pinLeft;
    private int pinTop;

    private int categoryColor;
    private boolean drawCategoryBar;

    // Avatar
    private @Nullable Bitmap avatarBitmap;
    private @Nullable BitmapShader avatarShader;
    private @Nullable String currentAvatarUrl;
    private String monogram = "";
    private int monogramBgColor;

    // Selector
    private final Drawable selectorDrawable;

    public TopicCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()));
        namePaint.setTypeface(AndroidUtilities.bold());

        messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        messagePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics()));

        timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 13, getResources().getDisplayMetrics()));

        countTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        countTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 13, getResources().getDisplayMetrics()));
        countTextPaint.setTypeface(AndroidUtilities.bold());

        countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinnedDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        categoryBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        avatarPlaceholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avatarTextBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avatarMonogramPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        avatarMonogramPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics()));
        avatarMonogramPaint.setTypeface(AndroidUtilities.bold());

        selectorDrawable = Theme.getSelectorDrawable(false);
        selectorDrawable.setCallback(this);

        applyThemeColors();
    }

    public void setCategories(@Nullable Map<Integer, CategorySummary> categories) {
        this.categories = categories;
        if (topic != null) {
            resolveCategory();
            invalidate();
        }
    }

    /**
     * Bind a topic summary; supplies the base URL so author avatar templates can be expanded.
     */
    public void setTopic(@NonNull TopicSummary topic, @NonNull String baseUrl) {
        this.topic = topic;
        // Update painted state.
        applyThemeColors();
        resolveCategory();
        prepareMonogram();
        loadAvatar(baseUrl);
        // Request a layout build on the next measure pass.
        lastWidth = 0;
        requestLayout();
        invalidate();
    }

    public @Nullable TopicSummary getTopic() {
        return topic;
    }

    private void applyThemeColors() {
        namePaint.setColor(Theme.getColor(Theme.key_chats_name));
        messagePaint.setColor(Theme.getColor(Theme.key_chats_message));
        timePaint.setColor(Theme.getColor(Theme.key_chats_date));
        countTextPaint.setColor(Theme.getColor(Theme.key_chats_unreadCounterText));
        countPaint.setColor(Theme.getColor(Theme.key_chats_unreadCounter));
        pinnedDotPaint.setColor(Theme.getColor(Theme.key_chats_pinnedIcon));
        avatarPlaceholderPaint.setColor(Theme.getColor(Theme.key_chats_pinnedOverlay));
        avatarMonogramPaint.setColor(Theme.getColor(Theme.key_avatar_text));
    }

    private void resolveCategory() {
        if (topic == null || topic.getCategoryId() == null || categories == null) {
            drawCategoryBar = false;
            return;
        }
        CategorySummary category = categories.get(topic.getCategoryId());
        if (category == null) {
            drawCategoryBar = false;
            return;
        }
        String hex = category.getColorHex();
        if (hex == null || hex.isEmpty()) {
            drawCategoryBar = false;
            return;
        }
        try {
            String prefixed = hex.startsWith("#") ? hex : "#" + hex;
            categoryColor = Color.parseColor(prefixed);
            drawCategoryBar = true;
            categoryBarPaint.setColor(categoryColor);
        } catch (IllegalArgumentException e) {
            drawCategoryBar = false;
        }
    }

    private void prepareMonogram() {
        String name = topic != null ? topic.getAuthorUsername() : null;
        if (name == null || name.isEmpty()) {
            monogram = "?";
        } else {
            int cp = name.codePointAt(0);
            monogram = new String(Character.toChars(Character.toUpperCase(cp))).toUpperCase();
        }
        // Stable color from username hash; mirrors Telegram AvatarDrawable's color picking.
        int hash = name == null ? 0 : name.hashCode();
        float hue = ((hash & 0x00ffffff) % 360);
        monogramBgColor = Color.HSVToColor(new float[]{hue, 0.45f, 0.65f});
        avatarTextBgPaint.setColor(monogramBgColor);
    }

    private void loadAvatar(String baseUrl) {
        avatarBitmap = null;
        avatarShader = null;
        currentAvatarUrl = null;
        if (topic == null) return;
        String template = topic.getAuthorAvatarTemplate();
        if (template == null || template.isEmpty()) return;
        // Discourse avatar template: e.g. "/user_avatar/linux.do/baba/{size}/123_2.png"
        String sized = template.replace("{size}", "96");
        String url = sized.startsWith("http") ? sized : (baseUrl.replaceAll("/+$", "") + (sized.startsWith("/") ? sized : "/" + sized));
        currentAvatarUrl = url;
        // TODO(image-pipeline): wire ImageReceiver/ImageLoader (Telegram pipeline)
        // for avatar load once the receiver chain is fully online. CoilBridge was
        // removed because the long-term plan is to use telegram's own image stack.
    }

    private void attachAvatarBitmap(@Nullable Bitmap bitmap) {
        avatarBitmap = bitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            avatarShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } else {
            avatarShader = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Match DialogCell.heightDefault (with separator).
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = dp(heightDefault) + 1; // +1 for hairline separator
        setMeasuredDimension(width, height);

        if (width != lastWidth && topic != null) {
            buildLayout();
            lastWidth = width;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed && topic != null) {
            buildLayout();
            lastWidth = right - left;
        }
    }

    /**
     * Mirrors DialogCell.buildLayout() for the subset of fields TopicSummary exposes.
     * Order: avatar bounds → time bounds (right side) → unread badge (right side) →
     *        name layout (left to time/badge) → message layout (under name).
     */
    public void buildLayout() {
        if (topic == null || lastWidth <= 0 && getMeasuredWidth() <= 0) return;
        int width = getMeasuredWidth();

        // ===== Avatar =====
        avatarTop = dp(11);
        avatarLeft = dp(10);
        avatarRect.set(avatarLeft, avatarTop, avatarLeft + dp(56), avatarTop + dp(56));

        // ===== Time (right-aligned, top row) =====
        String timeText = formatTime(topic.getLastPostedAt());
        timeWidth = (int) Math.ceil(timePaint.measureText(timeText));
        timeLeft = width - dp(15) - timeWidth;
        timeTop = dp(13);
        timeLayout = new StaticLayout(timeText, timePaint, Math.max(0, timeWidth + dp(2)),
            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

        // ===== Unread badge (right-aligned, bottom row) =====
        int unread = topic.getUnreadCount();
        countTop = dp(42); // mirrors DialogCell countTop = dp(42.33f)
        if (unread > 0) {
            String countString = String.valueOf(unread);
            countWidth = Math.max(dp(BADGE_TEXT_MIN_WIDTH), (int) Math.ceil(countTextPaint.measureText(countString)));
            countLayout = new StaticLayout(countString, countTextPaint, countWidth,
                Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            countLeft = width - dp(BADGE_MARGIN) - (countWidth + dp(BADGE_TEXT_PADDING * 2));
            drawCount = true;
        } else {
            countLayout = null;
            countWidth = 0;
            drawCount = false;
        }

        // ===== Pinned dot (right-aligned, bottom row) — when no unread =====
        drawPinned = topic.isPinned() && !drawCount;
        if (drawPinned) {
            pinTop = dp(43);
            pinLeft = width - dp(14) - dp(8);
        }

        // ===== Name (top row, left-aligned, ends before time) =====
        nameLeft = dp(messagePaddingStart);
        nameWidth = width - nameLeft - dp(messagePaddingStart - 56) - timeWidth - dp(8);
        if (nameWidth < dp(20)) nameWidth = dp(20);
        CharSequence ellipsizedName = TextUtils.ellipsize(
            topic.getTitle() == null ? "" : topic.getTitle(),
            namePaint, nameWidth, TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(ellipsizedName, namePaint, nameWidth,
            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);

        // ===== Message (bottom row, left-aligned, ends before count/pin) =====
        messageLeft = dp(messagePaddingStart);
        messageTop = dp(34);
        int rightInset = drawCount
            ? (width - countLeft) + dp(4)
            : drawPinned
                ? dp(28)
                : dp(messagePaddingStart - 56);
        messageWidth = width - messageLeft - rightInset;
        if (messageWidth < dp(20)) messageWidth = dp(20);
        CharSequence preview = buildMessagePreview();
        CharSequence ellipsizedMsg = TextUtils.ellipsize(preview, messagePaint, messageWidth, TextUtils.TruncateAt.END);
        messageLayout = new StaticLayout(ellipsizedMsg, messagePaint, messageWidth,
            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }

    private CharSequence buildMessagePreview() {
        if (topic == null) return "";
        String poster = topic.getLastPosterUsername();
        // For Discourse: prefer "lastPoster · N replies" since topics rarely have an excerpt locally.
        if (poster != null && !poster.isEmpty()) {
            int replies = topic.getReplyCount();
            if (replies > 0) {
                return poster + " · " + replies;
            }
            return poster;
        }
        return topic.getAuthorUsername() == null ? "" : topic.getAuthorUsername();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (topic == null) return;
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        // Selector ripple background.
        selectorDrawable.setBounds(0, 0, width, height - 1);
        selectorDrawable.draw(canvas);

        // Hairline separator at the bottom.
        int dividerColor = Theme.getColor(Theme.key_divider);
        Paint divider = new Paint();
        divider.setColor(dividerColor);
        canvas.drawRect(dp(messagePaddingStart), height - 1, width, height, divider);

        // Category color bar (left edge, full cell height).
        if (drawCategoryBar) {
            canvas.drawRect(0, 0, dp(3), height - 1, categoryBarPaint);
        }

        // Avatar.
        if (avatarShader != null) {
            avatarPlaceholderPaint.setShader(avatarShader);
            // Scale shader to fit 56dp circle.
            float bmpW = avatarBitmap.getWidth();
            float bmpH = avatarBitmap.getHeight();
            float scale = dp(56) / Math.min(bmpW, bmpH);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(scale, scale);
            m.postTranslate(avatarLeft - (bmpW * scale - dp(56)) / 2f,
                            avatarTop - (bmpH * scale - dp(56)) / 2f);
            avatarShader.setLocalMatrix(m);
            canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), dp(28), avatarPlaceholderPaint);
            avatarPlaceholderPaint.setShader(null);
        } else {
            // Monogram fallback.
            canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), dp(28), avatarTextBgPaint);
            float tw = avatarMonogramPaint.measureText(monogram);
            Paint.FontMetrics fm = avatarMonogramPaint.getFontMetrics();
            float ty = avatarRect.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(monogram, avatarRect.centerX() - tw / 2f, ty, avatarMonogramPaint);
        }

        // Name.
        if (nameLayout != null) {
            canvas.save();
            canvas.translate(nameLeft, dp(11));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        // Time.
        if (timeLayout != null) {
            canvas.save();
            canvas.translate(timeLeft, timeTop);
            timeLayout.draw(canvas);
            canvas.restore();
        }

        // Message.
        if (messageLayout != null) {
            canvas.save();
            canvas.translate(messageLeft, messageTop);
            messageLayout.draw(canvas);
            canvas.restore();
        }

        // Unread count pill — mirrors DialogCell badge geometry.
        if (drawCount && countLayout != null) {
            float radius = dp(BADGE_SIZE) / 2f;
            RectF rect = AndroidUtilities.rectTmp;
            rect.set(countLeft, countTop, countLeft + countWidth + dp(BADGE_TEXT_PADDING * 2), countTop + dp(BADGE_SIZE));
            canvas.drawRoundRect(rect, radius, radius, countPaint);
            canvas.save();
            canvas.translate(countLeft + dp(BADGE_TEXT_PADDING), countTop + (dp(BADGE_SIZE) - countLayout.getHeight()) / 2f);
            countLayout.draw(canvas);
            canvas.restore();
        } else if (drawPinned) {
            // Small pin dot — placeholder for Theme.dialogs_pinnedDrawable (not ported).
            canvas.drawCircle(pinLeft, pinTop + dp(4), dp(3), pinnedDotPaint);
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == selectorDrawable || super.verifyDrawable(who);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        selectorDrawable.setState(getDrawableState());
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        selectorDrawable.jumpToCurrentState();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Discourse "lastPostedAt" is an ISO-8601 string. Convert to a relative
     * short form ("now", "12m", "3h", "2d", or "MMM d" / "MMM d, yyyy").
     * This stands in for upstream {@code LocaleController.formatDateAudio}.
     */
    private static String formatTime(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            Instant i = OffsetDateTime.parse(iso).toInstant();
            Instant now = Instant.now();
            long seconds = ChronoUnit.SECONDS.between(i, now);
            if (seconds < 60) return "now";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m";
            long hours = minutes / 60;
            if (hours < 24) return hours + "h";
            long days = hours / 24;
            if (days < 7) return days + "d";
            OffsetDateTime dt = i.atZone(ZoneId.systemDefault()).toOffsetDateTime();
            int year = dt.getYear();
            int nowYear = OffsetDateTime.now().getYear();
            if (year == nowYear) {
                return dt.getMonth().name().substring(0, 3) + " " + dt.getDayOfMonth();
            }
            return dt.getMonth().name().substring(0, 3) + " " + dt.getDayOfMonth() + ", " + year;
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}

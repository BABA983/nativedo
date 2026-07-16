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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import io.github.baba983.nativedo.core.ui.DiscourseUiHelpersKt;
import io.github.baba983.nativedo.core.model.CategorySummary;
import io.github.baba983.nativedo.core.model.TopicSummary;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.MovableCell;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Locale;

/**
 * Telegram-shaped Discourse topic row.
 *
 * <p>Layout mirrors {@link org.telegram.ui.Cells.DialogCell} but adapted for
 * Discourse topic semantics. Two-row structure:
 *
 * <pre>
 * Row 1: [Avatar] [Title.................................] [time]
 * Row 2:        [@lastPoster · category · #tag..........] [replies/unread]
 * </pre>
 *
 * <p>Key structural differences from DialogCell:
 * <ul>
 *   <li>Row 1 right: last activity time (not clock/checks)</li>
 *   <li>Row 2 left: last poster + category + one tag (not message preview)</li>
 *   <li>Row 2 right: reply count / unread badge</li>
 *   <li>Pinned rows get background tint; hot topics get an orange tint on the reply badge</li>
 * </ul>
 */
public class TopicCell extends View implements MovableCell, Theme.Colorable {

    public static final int MASK_UNREAD = 1;
    public static final int MASK_REPLY_COUNT = 1 << 1;
    public static final int MASK_LAST_POSTED = 1 << 2;
    public static final int MASK_PINNED = 1 << 3;
    public static final int MASK_MUTED = 1 << 4;
    public static final int MASK_FULL = 1 << 5;

    // ===== Layout constants (mirror DialogCell) =====
    public int heightDefault = 70;
    public int avatarStart = 11;
    public int messagePaddingStart = 72;

    // Badge geometry — matches DialogCell.
    private static final float BADGE_SIZE = 20.666f;
    private static final float BADGE_TEXT_PADDING = 6.333f;
    private static final float BADGE_TEXT_MIN_WIDTH = 8f;
    private static final float BADGE_MARGIN = 15.666f;

    // Activity line geometry.
    private static final float CATEGORY_DOT_SIZE = 6f;
    private static final float CATEGORY_DOT_MARGIN = 6f;

    // ===== Paints =====
    private final Paint categoryBarPaint;
    private final Paint hotBadgePaint;
    private final Paint categoryDotPaint;

    // ===== Cached layout =====
    private TopicSummary topic;
    private @Nullable Map<Integer, CategorySummary> categories;

    private int lastWidth;
    private int avatarLeft;
    private int avatarTop;
    private final RectF avatarRect = new RectF();

    // Row 1: Title + time
    private StaticLayout nameLayout;
    private int nameLeft;
    private int nameWidth;

    private StaticLayout timeLayout;
    private int timeLeft;
    private int timeTop;
    private int timeWidth;

    private StaticLayout countLayout;   // reply count or unread badge
    private int countLeft;
    private int countTop;
    private int countWidth;
    private boolean drawCount;
    private boolean countIsHot;         // hot-reply badge uses orange tint

    // Row 2: activity line + right badge
    private boolean drawCategoryBar;
    private int categoryColor;
    private boolean drawCategoryDot;
    private String categoryName;
    private StaticLayout activityLayout;
    private int activityLeft;
    private int activityTop;
    private int activityWidth;
    private int row2Top;

    // Pinned — time-left pin bubble (mirrors DialogCell upstream).
    private boolean drawPin;
    private static final int PIN_BUBBLE_HEIGHT = 17;
    private static final int PIN_BUBBLE_RADIUS = PIN_BUBBLE_HEIGHT / 2;

    // ItemAnimator state
    private boolean moving;
    private float clipProgress;
    private int topClip;
    private int bottomClip;

    // Avatar
    private final ImageReceiver avatarImage;
    private final AvatarDrawable avatarDrawable;
    private Drawable pinnedDrawable;
    private String avatarUrl;
    private String avatarFilter;
    private String searchQuery;
    private boolean showSearchExcerpt;

    public TopicCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        categoryBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        categoryDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        hotBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        avatarDrawable = new AvatarDrawable();
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(dp(26));
        avatarImage.setAllowLoadingOnAttachedOnly(true);
        avatarImage.setDelegate((imageReceiver, set, thumb, memCache) -> invalidateAvatar());
        avatarImage.setVisibleInvalidate(this::invalidateAvatar);
        updatePinnedDrawable();
    }

    public void setCategories(@Nullable Map<Integer, CategorySummary> categories) {
        this.categories = categories;
        if (topic != null) {
            resolveCategory();
            invalidate();
        }
    }

    public void setSearchPresentation(@Nullable String query, boolean showExcerpt) {
        String normalized = query == null ? null : query.trim();
        if (equals(searchQuery, normalized) && showSearchExcerpt == showExcerpt) {
            return;
        }
        searchQuery = normalized;
        showSearchExcerpt = showExcerpt;
        lastWidth = 0;
        requestLayout();
        invalidate();
    }

    public void setTopic(@NonNull TopicSummary topic, @NonNull String baseUrl) {
        TopicSummary previousTopic = this.topic;
        this.topic = topic;
        resolveCategory();
        loadAvatar(baseUrl);
        if (previousTopic == null || previousTopic.getId() != topic.getId() || !sameTextLayoutInputs(previousTopic, topic)) {
            lastWidth = 0;
            requestLayout();
        } else {
            int width = getMeasuredWidth();
            if (width > 0) {
                lastWidth = width;
                buildLayout(width);
            }
        }
        invalidate();
    }

    public void setTopic(@NonNull TopicSummary topic, @NonNull String baseUrl, int payloadMask) {
        update(topic, payloadMask, baseUrl);
    }

    public void update(@NonNull TopicSummary topic, int mask) {
        update(topic, mask, null);
    }

    private void update(@NonNull TopicSummary topic, int mask, @Nullable String baseUrl) {
        TopicSummary previousTopic = this.topic;
        if (previousTopic == null || previousTopic.getId() != topic.getId() || mask == 0) {
            if (baseUrl != null) {
                setTopic(topic, baseUrl);
            } else {
                this.topic = topic;
                lastWidth = 0;
                requestLayout();
                invalidate();
            }
            return;
        }

        this.topic = topic;
        resolveCategory();

        if (baseUrl != null && avatarChanged(previousTopic, topic)) {
            loadAvatar(baseUrl);
        }

        int width = getMeasuredWidth();
        if ((mask & MASK_FULL) != 0 || !sameTextLayoutInputs(previousTopic, topic)) {
            if (width > 0) {
                lastWidth = width;
                buildLayout(width);
            } else {
                lastWidth = 0;
                requestLayout();
            }
            invalidate();
            return;
        }

        if (width > 0) {
            lastWidth = width;
            if ((mask & (MASK_LAST_POSTED | MASK_PINNED)) != 0) {
                buildTimeLayout(width);
                buildNameLayout(width);
            }
            if ((mask & (MASK_UNREAD | MASK_REPLY_COUNT | MASK_MUTED)) != 0) {
                buildCountLayout(width);
                buildActivityLayout(width);
            }
        } else {
            lastWidth = 0;
            requestLayout();
        }
        invalidate();
    }

    public @Nullable TopicSummary getTopic() {
        return topic;
    }

    private static boolean avatarChanged(@NonNull TopicSummary oldTopic, @NonNull TopicSummary newTopic) {
        return !equals(oldTopic.getAuthorUsername(), newTopic.getAuthorUsername()) ||
            !equals(oldTopic.getAuthorAvatarTemplate(), newTopic.getAuthorAvatarTemplate()) ||
            !equals(oldTopic.getAuthorAnimatedAvatarUrl(), newTopic.getAuthorAnimatedAvatarUrl());
    }

    private static boolean sameTextLayoutInputs(@NonNull TopicSummary oldTopic, @NonNull TopicSummary newTopic) {
        return equals(oldTopic.getTitle(), newTopic.getTitle()) &&
            equals(oldTopic.getLastPosterUsername(), newTopic.getLastPosterUsername()) &&
            equals(oldTopic.getExcerpt(), newTopic.getExcerpt()) &&
            equals(oldTopic.getTargetPostNumber(), newTopic.getTargetPostNumber()) &&
            equals(oldTopic.getCategoryId(), newTopic.getCategoryId()) &&
            equals(oldTopic.getTags(), newTopic.getTags()) &&
            oldTopic.isPinned() == newTopic.isPinned() &&
            oldTopic.isHot() == newTopic.isHot();
    }

    private static boolean equals(@Nullable Object a, @Nullable Object b) {
        return a == b || (a != null && a.equals(b));
    }

    private void resolveCategory() {
        if (topic == null || topic.getCategoryId() == null || categories == null) {
            drawCategoryBar = false;
            categoryName = null;
            return;
        }
        CategorySummary category = categories.get(topic.getCategoryId());
        if (category == null) {
            drawCategoryBar = false;
            categoryName = null;
            return;
        }
        categoryName = category.getName();
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
            categoryDotPaint.setColor(categoryColor);
        } catch (IllegalArgumentException e) {
            drawCategoryBar = false;
        }
    }

    private void loadAvatar(String baseUrl) {
        if (topic == null) return;
        String name = topic.getAuthorUsername();
        long id = name == null ? 0 : name.hashCode();
        avatarDrawable.setInfo(id, name);
        avatarDrawable.setRoundRadius(dp(26));
        int avatarSize = dp(52);
        avatarImage.setImageCoords(dp(avatarStart), dp(9), avatarSize, avatarSize);

        String animatedAvatarUrl = topic.getAuthorAnimatedAvatarUrl();
        String nextAvatarUrl = DiscourseUiHelpersKt.resolveAvatarUrl(baseUrl, topic.getAuthorAvatarTemplate(), animatedAvatarUrl, 96);
        String nextAvatarFilter = DiscourseUiHelpersKt.avatarImageFilter(animatedAvatarUrl, 50);
        avatarImage.setAllowDecodeSingleFrame(!DiscourseUiHelpersKt.isAnimatedDiscourseImageUrl(nextAvatarUrl));
        if (equals(avatarUrl, nextAvatarUrl) && equals(avatarFilter, nextAvatarFilter)) {
            return;
        }
        avatarUrl = nextAvatarUrl;
        avatarFilter = nextAvatarFilter;
        if (nextAvatarUrl == null || nextAvatarUrl.isEmpty()) {
            avatarImage.setImage(null, null, avatarDrawable, null, topic, 0);
            return;
        }
        ImageLocation location = ImageLocation.getForPath(nextAvatarUrl);
        avatarImage.setImage(location, nextAvatarFilter, avatarDrawable, null, topic, 0);
    }

    private void invalidateAvatar() {
        if (avatarRect.isEmpty()) {
            invalidate();
        } else {
            invalidate((int) avatarRect.left, (int) avatarRect.top, (int) avatarRect.right, (int) avatarRect.bottom);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, dp(heightDefault));
        if (width != lastWidth && topic != null) {
            lastWidth = width;
            buildLayout(width);
        }
    }

    private void buildLayout(int width) {
        buildAvatarLayout();
        buildTimeLayout(width);
        buildCountLayout(width);
        buildNameLayout(width);
        buildActivityLayout(width);
    }

    private void buildAvatarLayout() {
        // ===== Avatar =====
        avatarTop = dp(9);
        avatarLeft = dp(avatarStart);
        avatarRect.set(avatarLeft, avatarTop, avatarLeft + dp(52), avatarTop + dp(52));
    }

    private void buildTimeLayout(int width) {
        drawPin = topic.isPinned();

        // ===== Pin bubble (left of time, mirrors DialogCell pinnedDrawable2) =====

        // ===== Row 1 right: last activity time =====
        String timeText = formatTime(topic.getLastPostedAt());
        timeWidth = (int) Math.ceil(Theme.dialogs_timePaint.measureText(timeText));
        timeTop = dp(16);
        // Pinned: time shifts right by dp(20) to make room for pin bubble — mirrors DialogCell.
        float pinOffset = drawPin ? dp(20) : 0;
        timeLeft = width - dp(15) - timeWidth - (int) pinOffset;
        timeLayout = new StaticLayout(timeText, Theme.dialogs_timePaint, Math.max(0, timeWidth + dp(2)),
            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }

    private void buildCountLayout(int width) {
        // ===== Row 2 right: reply count or unread badge =====
        int unread = topic.getUnreadCount();
        int replies = topic.getReplyCount();
        countTop = dp(35);
        if (unread > 0) {
            String countString = unread > 99 ? "99+" : String.valueOf(unread);
            countWidth = Math.max(dp(BADGE_TEXT_MIN_WIDTH), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
            countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth,
                Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            countLeft = width - dp(BADGE_MARGIN) - (countWidth + dp(BADGE_TEXT_PADDING * 2));
            drawCount = true;
            countIsHot = false;
        } else if (replies > 0) {
            // Show reply count as a subtle badge (not the bold unread pill).
            String countString = formatCount(replies);
            countWidth = Math.max(dp(BADGE_TEXT_MIN_WIDTH), (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(countString)));
            countLayout = new StaticLayout(countString, Theme.dialogs_countTextPaint, countWidth,
                Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
            countLeft = width - dp(BADGE_MARGIN) - (countWidth + dp(BADGE_TEXT_PADDING * 2));
            drawCount = true;
            countIsHot = topic.isHot();
        } else {
            countLayout = null;
            countWidth = 0;
            drawCount = false;
            countIsHot = false;
        }
    }

    private void buildNameLayout(int width) {
        // ===== Row 1: Title (left, ends before time+pin area) =====
        nameLeft = dp(messagePaddingStart + 4);
        // Pinned: time + pin bubble together occupy more right-side space.
        float pinOffset = drawPin ? dp(20) : 0;
        int timeInset = timeWidth > 0 ? timeWidth + dp(24) + (int) pinOffset : dp(16);
        nameWidth = width - nameLeft - timeInset;
        if (nameWidth < dp(20)) nameWidth = dp(20);
        CharSequence ellipsizedName = TextUtils.ellipsize(
            highlightSearchText(topic.getTitle() == null ? "" : topic.getTitle()),
            Theme.dialogs_namePaint, nameWidth, TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(ellipsizedName, Theme.dialogs_namePaint, nameWidth,
            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }

    private void buildActivityLayout(int width) {
        // ===== Row 2 vertical position =====
        row2Top = dp(39);

        // ===== Row 2 left: activity line =====
        drawCategoryDot = topic.getCategoryId() != null;
        activityLeft = dp(messagePaddingStart + 4) + (drawCategoryDot ? dp(CATEGORY_DOT_SIZE + CATEGORY_DOT_MARGIN) : 0);
        activityTop = row2Top;
        int rightInset = drawCount ? (width - countLeft) + dp(8) : dp(15);
        activityWidth = width - activityLeft - rightInset;
        if (activityWidth < dp(20)) activityWidth = dp(20);
        CharSequence activity = TextUtils.ellipsize(
            buildActivityLine(),
            Theme.dialogs_messagePaint, activityWidth, TextUtils.TruncateAt.END);
        activityLayout = new StaticLayout(activity, Theme.dialogs_messagePaint, activityWidth,
            Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }

    /** Builds the muted Telegram-style second line: "@lastPoster · category · #tag". */
    private CharSequence buildActivityLine() {
        if (showSearchExcerpt && topic.getExcerpt() != null && !topic.getExcerpt().isEmpty()) {
            String excerpt = Html.fromHtml(topic.getExcerpt(), Html.FROM_HTML_MODE_LEGACY).toString().trim();
            String poster = topic.getAuthorUsername();
            String text = poster == null || poster.isEmpty() ? excerpt : "@" + poster + " · " + excerpt;
            return highlightSearchText(text);
        }
        StringBuilder sb = new StringBuilder();
        String poster = topic.getLastPosterUsername();
        if (poster == null || poster.isEmpty()) {
            poster = topic.getAuthorUsername();
        }
        if (poster != null && !poster.isEmpty()) {
            sb.append('@').append(poster);
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(categoryName);
        }
        java.util.List<String> tags = topic.getTags();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null || tag.isEmpty()) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append('#').append(tag);
                break;
            }
        }
        if (sb.length() == 0) {
            int replies = topic.getReplyCount();
            if (replies > 0) {
                sb.append(formatCount(replies)).append(" replies");
            }
        }
        return sb.toString();
    }

    private CharSequence highlightSearchText(@NonNull String text) {
        if (searchQuery == null || searchQuery.isEmpty() || text.isEmpty()) {
            return text;
        }
        SpannableString highlighted = new SpannableString(text);
        String lowercaseText = text.toLowerCase(Locale.ROOT);
        String[] terms = searchQuery.toLowerCase(Locale.ROOT).split("\\s+");
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        for (String term : terms) {
            if (term.isEmpty()) continue;
            int start = 0;
            while (start < lowercaseText.length()) {
                int index = lowercaseText.indexOf(term, start);
                if (index < 0) break;
                highlighted.setSpan(
                    new ForegroundColorSpan(color),
                    index,
                    index + term.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = index + term.length();
            }
        }
        return highlighted;
    }

    /** Format large counts compactly: 1200 → 1.2k, 1200000 → 1.2M. */
    private static String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 10000) {
            return String.format("%.1fk", count / 1000f);
        }
        if (count < 1_000_000) {
            return (count / 1000) + "k";
        }
        return String.format("%.1fM", count / 1_000_000f);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (topic == null) return;
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        boolean clippedForAnimation = clipProgress != 0.0f && Build.VERSION.SDK_INT != 24;
        if (clippedForAnimation) {
            canvas.save();
            canvas.clipRect(0, topClip * clipProgress, width, height - (int) (bottomClip * clipProgress));
        }

        // Category color bar (left edge).
        if (drawCategoryBar) {
            canvas.drawRect(0, 0, dp(3), height - 1, categoryBarPaint);
        }

        // Avatar.
        avatarImage.setImageCoords(avatarRect);
        avatarImage.draw(canvas);

        // ===== Row 1: Title =====
        if (nameLayout != null) {
            canvas.save();
            canvas.translate(nameLeft, dp(14));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        // ===== Row 1 right: time =====
        if (timeLayout != null) {
            canvas.save();
            canvas.translate(timeLeft, timeTop);

            // Pinned: draw pin bubble to the left of time text — mirrors
            // DialogCell upstream which draws dialogs_pinnedDrawable2 + alpha-27
            // roundRect at timeLeft - dp(20).
            if (drawPin && pinnedDrawable != null) {
                // Half-transparent background bubble.
                float y = timeLayout.getHeight() / 2f - dp(PIN_BUBBLE_HEIGHT / 2f);
                float l = -dp(20);
                float r = timeLayout.getWidth() + dp(6);
                int timeAlpha = Theme.dialogs_timePaint.getAlpha();
                Theme.dialogs_timePaint.setAlpha(27);
                canvas.drawRoundRect(l, y, r, y + dp(PIN_BUBBLE_HEIGHT),
                    dp(PIN_BUBBLE_RADIUS), dp(PIN_BUBBLE_RADIUS), Theme.dialogs_timePaint);
                Theme.dialogs_timePaint.setAlpha(timeAlpha);

                // Pin icon (vendored msg_pin_mini) — mirrors upstream
                // DialogCell which draws dialogs_pinnedDrawable2.
                final Drawable pd = pinnedDrawable;
                final int dy = (int) (y + (dp(PIN_BUBBLE_HEIGHT) - pd.getIntrinsicHeight()) / 2f);
                final int dx = (int) l + dp(4);
                pd.setBounds(dx, dy,
                    dx + pd.getIntrinsicWidth(),
                    dy + pd.getIntrinsicHeight());
                pd.draw(canvas);
            }

            timeLayout.draw(canvas);
            canvas.restore();
        }

        // ===== Row 2 right: reply/unread badge =====
        if (drawCount && countLayout != null) {
            float radius = dp(BADGE_SIZE) / 2f;
            RectF rect = AndroidUtilities.rectTmp;
            rect.set(countLeft, countTop, countLeft + countWidth + dp(BADGE_TEXT_PADDING * 2), countTop + dp(BADGE_SIZE));
            if (topic.getUnreadCount() > 0) {
                // Unread: bold accent pill (or muted gray for isMuted).
                Paint counterPaint = topic.isMuted() ? Theme.dialogs_countGrayPaint : Theme.dialogs_countPaint;
                canvas.drawRoundRect(rect, radius, radius, counterPaint);
                // White text for unread pill.
                canvas.save();
                canvas.translate(countLeft + dp(BADGE_TEXT_PADDING), countTop + (dp(BADGE_SIZE) - countLayout.getHeight()) / 2f);
                countLayout.draw(canvas);
                canvas.restore();
            } else {
                // Reply count: subtle background pill. Hot topics get orange tint.
                hotBadgePaint.setColor(0x1AFF6D3F);
                Paint bgPaint = countIsHot ? hotBadgePaint : Theme.dialogs_pinnedBgPaint;
                canvas.drawRoundRect(rect, radius, radius, bgPaint);
                Paint textPaint = countLayout.getPaint();
                int origColor = textPaint.getColor();
                textPaint.setColor(countIsHot ? 0xffFF6D3F : Theme.getColor(Theme.key_chats_date));
                canvas.save();
                canvas.translate(countLeft + dp(BADGE_TEXT_PADDING), countTop + (dp(BADGE_SIZE) - countLayout.getHeight()) / 2f);
                countLayout.draw(canvas);
                textPaint.setColor(origColor);
                canvas.restore();
            }
        }

        drawRow2(canvas);

        if (clippedForAnimation) {
            canvas.restore();
        }
    }

    /** Draw Row 2: activity line (left) and reply/unread badge (right). */
    private void drawRow2(Canvas canvas) {
        if (drawCategoryDot) {
            float dotR = dp(CATEGORY_DOT_SIZE) / 2f;
            canvas.drawCircle(dp(messagePaddingStart + 4) + dotR, row2Top + dp(10), dotR, categoryDotPaint);
        }

        if (activityLayout != null) {
            canvas.save();
            canvas.translate(activityLeft, activityTop);
            activityLayout.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

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

    // MovableCell

    public void setMoving(boolean moving) { this.moving = moving; }
    public boolean isMoving() { return moving; }
    public void setClipProgress(float value) { clipProgress = value; invalidate(); }
    public float getClipProgress() { return clipProgress; }
    public void setTopClip(int value) { topClip = value; }
    public void setBottomClip(int value) { bottomClip = value; }

    /**
     * {@link Theme.Colorable} — called by the host fragment when the theme
     * changes. Shared paints (Theme.dialogs_*Paint) are already updated by
     * Theme.applyThemeInternal; this hook only needs to refresh per-cell
     * drawables whose color was sampled at construction time.
     */
    @Override
    public void updateColors() {
        updatePinnedDrawable();
        invalidate();
    }

    private void updatePinnedDrawable() {
        Drawable source = Theme.dialogs_pinnedDrawable2;
        if (source == null) {
            pinnedDrawable = null;
            return;
        }
        Drawable copy = source.getConstantState() != null
            ? source.getConstantState().newDrawable().mutate()
            : source.mutate();
        Theme.setDrawableColorByKey(copy, Theme.key_chats_pinnedIcon);
        pinnedDrawable = copy;
    }
}

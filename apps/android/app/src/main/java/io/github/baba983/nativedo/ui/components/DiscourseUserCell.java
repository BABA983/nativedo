/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's org/telegram/ui/Cells/ReactedUserHolderView.java.
 * Keeps the default user-row geometry and selector while binding URL-shaped
 * Discourse avatar and identity data instead of Telegram peers/reactions.
 */
package io.github.baba983.nativedo.ui.components;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class DiscourseUserCell extends FrameLayout implements Theme.Colorable {
    public static final int ITEM_HEIGHT_DP = 50;
    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_POLL = 1;
    private static final float AVATAR_SIZE_DP = 42f;
    private static final float AVATAR_START_DP = 10f;
    private static final float IDENTITY_START_DP = 62f;
    private static final float IDENTITY_TITLE_TOP_DP = 6f;
    private static final float IDENTITY_SUBTITLE_TOP_DP = 26f;

    private final BackupImageView avatarView;
    private final SimpleTextView titleView;
    private final SimpleTextView subtitleView;
    private final View overlaySelectorView;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();

    public DiscourseUserCell(@NonNull Context context) {
        this(context, STYLE_DEFAULT);
    }

    public DiscourseUserCell(@NonNull Context context, int style) {
        super(context);
        setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(ITEM_HEIGHT_DP)));

        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(AVATAR_SIZE_DP));
        addView(avatarView, LayoutHelper.createFrameRelatively(
                AVATAR_SIZE_DP,
                AVATAR_SIZE_DP,
                Gravity.START | Gravity.CENTER_VERTICAL,
                AVATAR_START_DP,
                0,
                0,
                0
        ));

        titleView = new SimpleTextView(context) {
            @Override
            public boolean setText(CharSequence value) {
                return super.setText(Emoji.replaceEmoji(value, getPaint().getFontMetricsInt(), false));
            }
        };
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setEllipsizeByGradient(true);
        titleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        titleView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, IDENTITY_START_DP, IDENTITY_TITLE_TOP_DP, 12, 0));

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setEllipsizeByGradient(true);
        subtitleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitleView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(30) : 0);
        addView(subtitleView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, IDENTITY_START_DP, IDENTITY_SUBTITLE_TOP_DP, 20, 0));

        overlaySelectorView = new View(context);
        overlaySelectorView.setBackground(Theme.getSelectorDrawable(false));
        addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateColors();
    }

    public void bind(
            @NonNull String displayName,
            @NonNull String username,
            @NonNull String avatarUrl,
            @NonNull String avatarFilter,
            boolean animatedAvatar,
            Object parentObject
    ) {
        String normalizedUsername = username.trim();
        while (normalizedUsername.startsWith("@")) {
            normalizedUsername = normalizedUsername.substring(1);
        }
        String name = displayName.trim().isEmpty() ? normalizedUsername : displayName.trim();
        titleView.setText(name);
        subtitleView.setText(normalizedUsername.isEmpty() ? "" : "@" + normalizedUsername);
        setContentDescription(normalizedUsername.isEmpty() ? name : name + ", @" + normalizedUsername);

        avatarDrawable.setInfo(normalizedUsername.hashCode(), name);
        avatarView.getImageReceiver().setAllowDecodeSingleFrame(!animatedAvatar);
        if (avatarUrl.isEmpty()) {
            avatarView.setImageDrawable(avatarDrawable);
        } else {
            avatarView.setImage(ImageLocation.getForPath(avatarUrl), avatarFilter, avatarDrawable, parentObject);
        }
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        overlaySelectorView.setBackground(Theme.getSelectorDrawable(false));
        invalidate();
    }
}

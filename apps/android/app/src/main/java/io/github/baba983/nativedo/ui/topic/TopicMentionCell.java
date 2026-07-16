/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's
 * TMessagesProj/src/main/java/org/telegram/ui/Cells/MentionCell.java.
 */
package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class TopicMentionCell extends LinearLayout {
    public static final int HEIGHT_DP = 36;

    private final BackupImageView imageView;
    private final TextView nameTextView;
    private final TextView usernameTextView;
    private final AvatarDrawable avatarDrawable;

    public TopicMentionCell(@NonNull Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackground(Theme.getSelectorDrawable(false));

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(18));

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(14));
        addView(imageView, LayoutHelper.createLinear(28, 28, 12, 4, 0, 0));

        nameTextView = new TextView(context) {
            @Override
            public void setText(CharSequence text, BufferType type) {
                text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), false);
                super.setText(text, type);
            }
        };
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.LEFT);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        usernameTextView = new TextView(context);
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        usernameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        usernameTextView.setSingleLine(true);
        usernameTextView.setGravity(Gravity.LEFT);
        usernameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 12, 0, 8, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(HEIGHT_DP), MeasureSpec.EXACTLY)
        );
    }

    public void bind(
            @NonNull String displayName,
            @NonNull String username,
            @NonNull String avatarUrl,
            @NonNull String avatarFilter,
            boolean animatedAvatar,
            Object parentObject
    ) {
        String name = displayName.trim().isEmpty() ? username : displayName.trim();
        nameTextView.setText(name);
        usernameTextView.setText("@" + username);
        setContentDescription(name + ", @" + username);

        avatarDrawable.setInfo(username.hashCode(), name);
        imageView.getImageReceiver().setAllowDecodeSingleFrame(!animatedAvatar);
        if (avatarUrl.isEmpty()) {
            imageView.setImageDrawable(avatarDrawable);
        } else {
            imageView.setImage(ImageLocation.getForPath(avatarUrl), avatarFilter, avatarDrawable, parentObject);
        }
    }
}

package io.github.baba983.nativedo.profile;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;

/**
 * The QR screen the profile tab's QR button opens, ported from upstream's {@code QrActivity}
 * (QrActivity.java:193-660) for a link this client owns.
 *
 * <p>Upstream's screen cannot be reused as a fragment here: it resolves the link from a Telegram
 * user or chat ({@code MessagesController.getUser/chat}), themes it from the account's own emoji
 * themes and exports contact tokens through tgnet. What is reused is its drawing - the
 * {@code QrView} is ported to {@link ProfileQrView}, which still runs upstream's own
 * {@code TelegramQRCodeWriter} - and its layout: the round avatar above the white card, the card
 * with the QR and the {@code @username} line under it, the close button on top, and the same
 * background pair upstream paints under its motion gradient (QrActivity.java:311: dark
 * {@code 0xFF121A2A}, light {@code 0xFF9BC38F}). The theme chooser, the share/scan buttons, the
 * timer token and the logo animation are Telegram-only and are left out.</p>
 */
public class ProfileQrFragment extends BaseFragment {

    private static final String ARG_USERNAME = "username";
    private static final String ARG_DISPLAY_NAME = "displayName";
    private static final String ARG_AVATAR_TEMPLATE = "avatarTemplate";
    private static final String ARG_URL = "url";

    /** QrActivity measures its card at dp(260) x dp(330) in portrait (QrActivity.java:257-262). */
    private static final int CARD_WIDTH_DP = 260;
    private static final int CARD_HEIGHT_DP = 330;

    private AppContainer container;
    private TLRPC.User avatarUser;
    private BackupImageView avatarView;
    private ProfileQrView qrView;

    /** The QR screen for one profile: [url] is the link the QR encodes. */
    public static ProfileQrFragment forProfile(String username, String displayName, String avatarTemplate, String url) {
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username == null ? "" : username);
        args.putString(ARG_DISPLAY_NAME, displayName == null ? "" : displayName);
        args.putString(ARG_AVATAR_TEMPLATE, avatarTemplate);
        args.putString(ARG_URL, url == null ? "" : url);
        return new ProfileQrFragment(args);
    }

    public ProfileQrFragment(Bundle args) {
        super(args);
    }

    private String username() {
        return getArguments() == null ? "" : getArguments().getString(ARG_USERNAME, "");
    }

    private String displayName() {
        String name = getArguments() == null ? "" : getArguments().getString(ARG_DISPLAY_NAME, "");
        return name == null || name.isEmpty() ? username() : name;
    }

    /** The link the QR encodes: the profile's own site URL. */
    public String getLink() {
        return getArguments() == null ? "" : getArguments().getString(ARG_URL, "");
    }

    @Override
    protected View createView(Context context) {
        setHasOwnBackground(true);

        // QrActivity.java:199-205: no bar surface, white items, the close button a plain back arrow.
        actionBar.setAddToContainer(false);
        actionBar.setBackground(null);
        actionBar.setItemsColor(0xffffffff, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.backButtonImageView.setColorFilter(
                new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final boolean dark = Theme.isCurrentThemeDark();
        qrView = new ProfileQrView(context, getLink(), username());
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(42));
        avatarView.setSize(AndroidUtilities.dp(84), AndroidUtilities.dp(84));

        // QrActivity's own portrait layout (QrActivity.java:255-285), without the theme chooser at
        // the bottom: the card centred, the avatar dp(48) above it.
        FrameLayout rootLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int width = getMeasuredWidth();
                int height = getMeasuredHeight();
                int cardWidth = qrView.getMeasuredWidth();
                int cardHeight = qrView.getMeasuredHeight();
                int cardLeft = (width - cardWidth) / 2;
                int cardTop = AndroidUtilities.statusBarHeight
                        + (height - AndroidUtilities.statusBarHeight - cardHeight - AndroidUtilities.dp(48)) / 2
                        + AndroidUtilities.dp(48 + 4);
                qrView.layout(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
                if (avatarView != null) {
                    int avatarLeft = (width - avatarView.getMeasuredWidth()) / 2;
                    int avatarTop = cardTop - AndroidUtilities.dp(48);
                    avatarView.layout(avatarLeft, avatarTop,
                            avatarLeft + avatarView.getMeasuredWidth(), avatarTop + avatarView.getMeasuredHeight());
                }
            }
        };
        // QrActivity.java:307-313: the colour the motion gradient is painted over.
        rootLayout.setBackgroundColor(dark ? 0xFF121A2A : 0xFF9BC38F);
        rootLayout.setContentDescription(LocaleController.getString(R.string.QrCode));

        rootLayout.addView(qrView, LayoutHelper.createFrame(CARD_WIDTH_DP, CARD_HEIGHT_DP, Gravity.LEFT | Gravity.TOP));
        rootLayout.addView(avatarView, LayoutHelper.createFrame(84, 84, Gravity.LEFT | Gravity.TOP));
        rootLayout.addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        fragmentView = rootLayout;
        loadAvatar();
        return rootLayout;
    }

    /** The avatar above the card, downloaded into the same cache the profile header draws from. */
    private void loadAvatar() {
        String template = getArguments() == null ? null : getArguments().getString(ARG_AVATAR_TEMPLATE);
        if (template == null || template.isEmpty() || getParentActivity() == null) {
            return;
        }
        try {
            container = NativeDo.container(getParentActivity());
            avatarUser = DiscourseAvatars.INSTANCE.user(username(), displayName(), template, container.getBaseUrl());
            bindAvatar();
            String url = DiscourseAvatars.INSTANCE.url(template, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || avatarUser == null || DiscourseAvatars.INSTANCE.isCached(avatarUser)) {
                return;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, avatarUser, () -> {
                bindAvatar();
                return kotlin.Unit.INSTANCE;
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void bindAvatar() {
        if (avatarView == null || avatarUser == null) {
            return;
        }
        ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(avatarUser);
        if (avatar != null) {
            avatarView.setImage(avatar, "84_84", avatarView.getImageReceiver().getDrawable(), avatarUser);
        }
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextPaint;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public final class Theme {

    // Upstream debug paint, used by DrawingInBackgroundThreadDrawable's
    // BuildVars.DEBUG_PRIVATE_VERSION error branch. Copied verbatim from upstream.
    public static Paint DEBUG_RED = new Paint(); static { DEBUG_RED.setColor(0xffff0000); }

    public interface ThemeDelegate {
        void didSetNewTheme();
    }

    /** Stub of upstream {@code Theme.getBaseThemeKey(TLRPC.ThemeSettings)} — returns "" until ThemeDocument is wired. */
    public static String getBaseThemeKey(Object themeSettings) {
        return "";
    }

    /** Stub of upstream {@code Theme.roundMessageInset} — round-bubble side inset in px. */
    public static int roundMessageInset = 0;

    /** Stub of upstream {@code Theme.checkCurrentRemoteTheme(boolean)}. */
    public static void checkCurrentRemoteTheme(boolean force) {
    }

    /** Stub of upstream {@code Theme.createInsetRoundRectDrawable}. */
    public static android.graphics.drawable.Drawable createInsetRoundRectDrawable(int inset, int rad, int color) {
        return new android.graphics.drawable.ColorDrawable(color);
    }

    public static android.graphics.drawable.Drawable createInsetRoundRectDrawable(int inset, int radTL, int radTR, int radBR, int radBL) {
        return new android.graphics.drawable.ColorDrawable(0);
    }

    public static android.graphics.drawable.Drawable createInsetRoundRectDrawable(int inset, int rad, int color, int pressedColor) {
        return new android.graphics.drawable.ColorDrawable(color);
    }

    /**
     * Compile-only shim mirroring Telegram's {@code Theme.Colorable}.
     * Used by views that need to update their colors when the theme changes.
     */
    public interface Colorable {
        void updateColors();
    }

    /**
     * Compile-only shim mirroring Telegram's {@code Theme.ResourcesProvider}.
     * Telegram lets fragments override theme color lookups by passing a
     * provider; this milestone always reads from the global {@link Theme}, so
     * the methods are here only to preserve upstream constructor signatures
     * and let L0 ports compile. See AGENTS.md Hard Rules.
     */
    public interface ResourcesProvider {
        default int getColor(int key) {
            return Theme.getColor(key);
        }

        default Integer getColorOrNull(int key) {
            return Theme.getColor(key);
        }

        default Drawable getDrawable(String drawableKey) {
            return null;
        }

        /** ADAPTED: getPaint stub for RecyclerListView compatibility. */
        default Paint getPaint(String paintKey) {
            return Theme.getThemePaint(paintKey);
        }

        default boolean isDark() {
            return Theme.isCurrentThemeDark();
        }
    }

    public static final int RIPPLE_MASK_CIRCLE_20DP = 1;
    public static final int RIPPLE_MASK_ALL = 2;
    /** ADAPTED: rounded-rect ripple mask used by ScrollSlidingTextTabStrip selector. */
    public static final int RIPPLE_MASK_ROUNDRECT_6DP = 7;


    public static int colorsCount;

    public static final int key_wallpaperFileOffset = colorsCount++;
    public static final int key_dialogBackground = colorsCount++;
    public static final int key_dialogBackgroundGray = colorsCount++;
    public static final int key_dialogTextBlack = colorsCount++;
    public static final int key_dialogTextLink = colorsCount++;
    public static final int key_dialogLinkSelection = colorsCount++;
    public static final int key_dialogTextBlue = colorsCount++;
    public static final int key_dialogTextBlue2 = colorsCount++;
    public static final int key_dialogTextBlue4 = colorsCount++;
    public static final int key_dialogTextGray = colorsCount++;
    public static final int key_dialogTextGray2 = colorsCount++;
    public static final int key_dialogTextGray3 = colorsCount++;
    public static final int key_dialogTextGray4 = colorsCount++;
    public static final int key_dialogTextHint = colorsCount++;
    public static final int key_dialogInputField = colorsCount++;
    public static final int key_dialogInputFieldActivated = colorsCount++;
    public static final int key_dialogCheckboxSquareBackground = colorsCount++;
    public static final int key_dialogCheckboxSquareCheck = colorsCount++;
    public static final int key_dialogCheckboxSquareUnchecked = colorsCount++;
    public static final int key_dialogCheckboxSquareDisabled = colorsCount++;
    public static final int key_dialogScrollGlow = colorsCount++;
    public static final int key_dialogRoundCheckBox = colorsCount++;
    public static final int key_dialogRoundCheckBoxCheck = colorsCount++;
    public static final int key_dialogRadioBackground = colorsCount++;
    public static final int key_dialogRadioBackgroundChecked = colorsCount++;
    public static final int key_dialogLineProgress = colorsCount++;
    public static final int key_dialogLineProgressBackground = colorsCount++;
    public static final int key_dialogButton = colorsCount++;
    public static final int key_dialogButtonSelector = colorsCount++;
    public static final int key_dialogIcon = colorsCount++;
    public static final int key_dialogGrayLine = colorsCount++;
    public static final int key_dialogTopBackground = colorsCount++;
    public static final int key_dialog_inlineProgressBackground = colorsCount++;
    public static final int key_dialog_inlineProgress = colorsCount++;
    public static final int key_dialogSearchBackground = colorsCount++;
    public static final int key_dialogSearchHint = colorsCount++;
    public static final int key_dialogSearchIcon = colorsCount++;
    public static final int key_dialogSearchText = colorsCount++;
    public static final int key_dialogFloatingButton = colorsCount++;
    public static final int key_dialogFloatingButtonPressed = colorsCount++;
    public static final int key_dialogFloatingIcon = colorsCount++;
    public static final int key_dialogShadowLine = colorsCount++;
    public static final int key_dialogEmptyImage = colorsCount++;
    public static final int key_dialogEmptyText = colorsCount++;
    public static final int key_dialogSwipeRemove = colorsCount++;
    public static final int key_dialogReactionMentionBackground = colorsCount++;
    public static final int key_dialogCardShadow = colorsCount++;
    public static final int key_dialogGiftsBackground = colorsCount++;
    public static final int key_dialogGiftsTabText = colorsCount++;

    public static final int key_windowBackgroundWhite = colorsCount++;
    public static final int key_windowBackgroundUnchecked = colorsCount++;
    public static final int key_windowBackgroundChecked = colorsCount++;
    public static final int key_windowBackgroundCheckText = colorsCount++;
    public static final int key_progressCircle = colorsCount++;
    public static final int key_listSelector = colorsCount++;
    public static final int key_settings_listSelector = colorsCount++;
    public static final int key_windowBackgroundWhiteInputField = colorsCount++;
    public static final int key_windowBackgroundWhiteInputFieldActivated = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayIcon = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText3 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText4 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText5 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText6 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText7 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueButton = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueIcon = colorsCount++;
    public static final int key_windowBackgroundWhiteGreenText = colorsCount++;
    public static final int key_windowBackgroundWhiteGreenText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText3 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText4 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText5 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText6 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText7 = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText8 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlackText = colorsCount++;
    public static final int key_windowBackgroundWhiteHintText = colorsCount++;
    public static final int key_windowBackgroundWhiteValueText = colorsCount++;
    public static final int key_windowBackgroundWhiteLinkText = colorsCount++;
    public static final int key_windowBackgroundWhiteLinkSelection = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueHeader = colorsCount++;
    public static final int key_switchTrack = colorsCount++;
    public static final int key_switchTrackChecked = colorsCount++;
    public static final int key_switchTrackBlue = colorsCount++;
    public static final int key_switchTrackBlueChecked = colorsCount++;
    public static final int key_switchTrackBlueThumb = colorsCount++;
    public static final int key_switchTrackBlueThumbChecked = colorsCount++;
    public static final int key_switchTrackBlueSelector = colorsCount++;
    public static final int key_switchTrackBlueSelectorChecked = colorsCount++;
    public static final int key_switch2Track = colorsCount++;
    public static final int key_switch2TrackChecked = colorsCount++;
    public static final int key_checkboxSquareBackground = colorsCount++;
    public static final int key_checkboxSquareCheck = colorsCount++;
    public static final int key_checkboxSquareUnchecked = colorsCount++;
    public static final int key_checkboxSquareDisabled = colorsCount++;
    public static final int key_windowBackgroundGray = colorsCount++;
    public static final int key_windowBackgroundGrayShadow = colorsCount++;
    public static final int key_emptyListPlaceholder = colorsCount++;
    public static final int key_divider = colorsCount++;
    public static final int key_graySection = colorsCount++;
    public static final int key_graySectionText = colorsCount++;
    public static final int key_radioBackground = colorsCount++;
    public static final int key_radioBackgroundChecked = colorsCount++;
    public static final int key_checkbox = colorsCount++;
    public static final int key_checkboxDisabled = colorsCount++;
    public static final int key_checkboxCheck = colorsCount++;
    public static final int key_fastScrollActive = colorsCount++;
    public static final int key_fastScrollInactive = colorsCount++;
    public static final int key_fastScrollText = colorsCount++;
    public static final int key_pollCreateIcons = colorsCount++;

    public static final int key_text_RedRegular = colorsCount++;
    public static final int key_text_RedBold = colorsCount++;
    public static final int key_fill_RedNormal = colorsCount++;
    public static final int key_fill_RedDark = colorsCount++;

    public static final int key_inappPlayerPerformer = colorsCount++;
    public static final int key_inappPlayerTitle = colorsCount++;
    public static final int key_inappPlayerBackground = colorsCount++;
    public static final int key_inappPlayerPlayPause = colorsCount++;
    public static final int key_inappPlayerClose = colorsCount++;

    public static final int key_returnToCallBackground = colorsCount++;
    public static final int key_returnToCallMutedBackground = colorsCount++;
    public static final int key_returnToCallText = colorsCount++;

    public static final int key_contextProgressInner1 = colorsCount++;
    public static final int key_contextProgressOuter1 = colorsCount++;
    public static final int key_contextProgressInner2 = colorsCount++;
    public static final int key_contextProgressOuter2 = colorsCount++;
    public static final int key_contextProgressInner3 = colorsCount++;
    public static final int key_contextProgressOuter3 = colorsCount++;
    public static final int key_contextProgressInner4 = colorsCount++;
    public static final int key_contextProgressOuter4 = colorsCount++;

    public static final int key_avatar_text = colorsCount++;
    public static final int key_avatar_backgroundSaved = colorsCount++;
    public static final int key_avatar_background2Saved = colorsCount++;
    public static final int key_avatar_backgroundArchived = colorsCount++;
    public static final int key_avatar_backgroundArchivedHidden = colorsCount++;
    public static final int key_avatar_backgroundRed = colorsCount++;
    public static final int key_avatar_backgroundOrange = colorsCount++;
    public static final int key_avatar_backgroundViolet = colorsCount++;
    public static final int key_avatar_backgroundGreen = colorsCount++;
    public static final int key_avatar_backgroundCyan = colorsCount++;
    public static final int key_avatar_backgroundBlue = colorsCount++;
    public static final int key_avatar_backgroundPink = colorsCount++;
    public static final int key_avatar_background2Red = colorsCount++;
    public static final int key_avatar_background2Orange = colorsCount++;
    public static final int key_avatar_background2Violet = colorsCount++;
    public static final int key_avatar_background2Green = colorsCount++;
    public static final int key_avatar_background2Cyan = colorsCount++;
    public static final int key_avatar_background2Blue = colorsCount++;
    public static final int key_avatar_background2Pink = colorsCount++;
    public static final int key_avatar_backgroundGray = colorsCount++;

    public static final int key_avatar_backgroundInProfileBlue = colorsCount++;
    public static final int key_avatar_backgroundActionBarBlue = colorsCount++;
    public static final int key_avatar_actionBarSelectorBlue = colorsCount++;
    public static final int key_avatar_actionBarIconBlue = colorsCount++;
    public static final int key_avatar_subtitleInProfileBlue = colorsCount++;

    public static final int key_avatar_nameInMessageRed = colorsCount++;
    public static final int key_avatar_nameInMessageOrange = colorsCount++;
    public static final int key_avatar_nameInMessageViolet = colorsCount++;
    public static final int key_avatar_nameInMessageGreen = colorsCount++;
    public static final int key_avatar_nameInMessageCyan = colorsCount++;
    public static final int key_avatar_nameInMessageBlue = colorsCount++;
    public static final int key_avatar_nameInMessagePink = colorsCount++;

    public static int[] keys_avatar_background = {key_avatar_backgroundRed, key_avatar_backgroundOrange, key_avatar_backgroundViolet, key_avatar_backgroundGreen, key_avatar_backgroundCyan, key_avatar_backgroundBlue, key_avatar_backgroundPink};
    public static int[] keys_avatar_background2 = {key_avatar_background2Red, key_avatar_background2Orange, key_avatar_background2Violet, key_avatar_background2Green, key_avatar_background2Cyan, key_avatar_background2Blue, key_avatar_background2Pink};
    public static int[] keys_avatar_nameInMessage = {key_avatar_nameInMessageRed, key_avatar_nameInMessageOrange, key_avatar_nameInMessageViolet, key_avatar_nameInMessageGreen, key_avatar_nameInMessageCyan, key_avatar_nameInMessageBlue, key_avatar_nameInMessagePink};

    public static final int key_actionBarDefault = colorsCount++;
    public static final int key_actionBarDefaultSelector = colorsCount++;
    public static final int key_actionBarWhiteSelector = colorsCount++;
    public static final int key_actionBarDefaultIcon = colorsCount++;
    public static final int key_actionBarActionModeDefault = colorsCount++;
    public static final int key_actionBarActionModeDefaultTop = colorsCount++;
    public static final int key_actionBarActionModeDefaultIcon = colorsCount++;
    public static final int key_actionBarActionModeDefaultSelector = colorsCount++;
    public static final int key_actionBarDefaultTitle = colorsCount++;
    public static final int key_actionBarDefaultSubtitle = colorsCount++;
    public static final int key_actionBarDefaultSearch = colorsCount++;
    public static final int key_actionBarDefaultSearchPlaceholder = colorsCount++;
    public static final int key_actionBarDefaultSubmenuItem = colorsCount++;
    public static final int key_actionBarDefaultSubmenuItemIcon = colorsCount++;
    public static final int key_actionBarDefaultSubmenuBackground = colorsCount++;
    public static final int key_actionBarDefaultSubmenuSeparator = colorsCount++;
    public static final int key_actionBarTabActiveText = colorsCount++;
    public static final int key_actionBarTabUnactiveText = colorsCount++;
    public static final int key_actionBarTabLine = colorsCount++;
    public static final int key_actionBarTabSelector = colorsCount++;
    public static final int key_actionBarDefaultArchived = colorsCount++;
    public static final int key_actionBarDefaultArchivedSelector = colorsCount++;
    public static final int key_actionBarDefaultArchivedIcon = colorsCount++;
    public static final int key_actionBarDefaultArchivedTitle = colorsCount++;
    public static final int key_actionBarDefaultArchivedSearch = colorsCount++;
    public static final int key_actionBarDefaultArchivedSearchPlaceholder = colorsCount++;

    public static final int key_actionBarBrowser = colorsCount++;

    public static final int key_chats_onlineCircle = colorsCount++;
    public static final int key_chats_unreadCounter = colorsCount++;
    public static final int key_chats_unreadCounterMuted = colorsCount++;
    public static final int key_chats_unreadCounterText = colorsCount++;
    public static final int key_chats_name = colorsCount++;
    public static final int key_chats_nameArchived = colorsCount++;
    public static final int key_chats_secretName = colorsCount++;
    public static final int key_chats_secretIcon = colorsCount++;
    public static final int key_chats_pinnedIcon = colorsCount++;
    public static final int key_chats_archiveBackground = colorsCount++;
    public static final int key_chats_archivePinBackground = colorsCount++;
    public static final int key_chats_archiveIcon = colorsCount++;
    public static final int key_chats_archiveText = colorsCount++;
    public static final int key_chats_message = colorsCount++;
    public static final int key_chats_messageArchived = colorsCount++;
    public static final int key_chats_message_threeLines = colorsCount++;
    public static final int key_chats_draft = colorsCount++;
    public static final int key_chats_nameMessage = colorsCount++;
    public static final int key_chats_nameMessageArchived = colorsCount++;
    public static final int key_chats_nameMessage_threeLines = colorsCount++;
    public static final int key_chats_nameMessageArchived_threeLines = colorsCount++;
    public static final int key_chats_attachMessage = colorsCount++;
    public static final int key_chats_actionMessage = colorsCount++;
    public static final int key_chats_date = colorsCount++;
    public static final int key_chats_date_bold = colorsCount++;
    public static final int key_chats_pinnedOverlay = colorsCount++;
    public static final int key_chats_tabletSelectedOverlay = colorsCount++;
    public static final int key_chats_sentCheck = colorsCount++;
    public static final int key_chats_sentReadCheck = colorsCount++;
    public static final int key_chats_sentClock = colorsCount++;
    public static final int key_chats_sentError = colorsCount++;
    public static final int key_chats_sentErrorIcon = colorsCount++;
    public static final int key_chats_verifiedBackground = colorsCount++;
    public static final int key_chats_verifiedCheck = colorsCount++;
    public static final int key_chats_muteIcon = colorsCount++;
    public static final int key_chats_mentionIcon = colorsCount++;
    public static final int key_chats_menuTopShadow = colorsCount++;
    public static final int key_chats_menuTopShadowCats = colorsCount++;
    public static final int key_chats_menuBackground = colorsCount++;
    public static final int key_chats_menuItemText = colorsCount++;
    public static final int key_chats_menuItemCheck = colorsCount++;
    public static final int key_chats_menuItemIcon = colorsCount++;
    public static final int key_chats_menuName = colorsCount++;
    public static final int key_chats_menuPhone = colorsCount++;
    public static final int key_chats_menuPhoneCats = colorsCount++;
    public static final int key_chats_menuTopBackgroundCats = colorsCount++;
    public static final int key_chats_menuTopBackground = colorsCount++;
    public static final int key_chats_actionIcon = colorsCount++;
    public static final int key_chats_actionBackground = colorsCount++;
    public static final int key_chats_actionPressedBackground = colorsCount++;
    public static final int key_chats_archivePullDownBackground = colorsCount++;
    public static final int key_chats_archivePullDownBackgroundActive = colorsCount++;
    public static final int key_chats_tabUnreadActiveBackground = colorsCount++;
    public static final int key_chats_tabUnreadUnactiveBackground = colorsCount++;

    public static final int key_chat_attachCheckBoxCheck = colorsCount++;
    public static final int key_chat_attachCheckBoxBackground = colorsCount++;
    public static final int key_chat_attachPhotoBackground = colorsCount++;
    public static final int key_chat_attachActiveTab = colorsCount++;
    public static final int key_chat_attachUnactiveTab = colorsCount++;
    public static final int key_chat_attachPermissionImage = colorsCount++;
    public static final int key_chat_attachPermissionMark = colorsCount++;
    public static final int key_chat_attachPermissionText = colorsCount++;
    public static final int key_chat_attachEmptyImage = colorsCount++;

    public static final int key_chat_inPollCorrectAnswer = colorsCount++;
    public static final int key_chat_outPollCorrectAnswer = colorsCount++;
    public static final int key_chat_inPollWrongAnswer = colorsCount++;
    public static final int key_chat_outPollWrongAnswer = colorsCount++;

    public static final int key_chat_attachIcon = colorsCount++;
    public static final int key_chat_attachGalleryBackground = colorsCount++;   //
    public static final int key_chat_attachAudioBackground = colorsCount++; //
    public static final int key_chat_attachContactBackground = colorsCount++;   //
    public static final int key_chat_attachContactText = colorsCount++; //
    public static final int key_chat_attachLocationBackground = colorsCount++;  //
    public static final int key_chat_attachPollBackground = colorsCount++;  //

    public static final int key_chat_status = colorsCount++;
    public static final int key_chat_inGreenCall = colorsCount++;
    public static final int key_chat_inBubble = colorsCount++;
    public static final int key_chat_inBubbleSelectedOverlay = colorsCount++;
    public static final int key_chat_inBubbleShadow = colorsCount++;

    public static final int key_actionBarActionModeReaction = colorsCount++;
    public static final int key_actionBarActionModeReactionText = colorsCount++;
    public static final int key_actionBarActionModeReactionDot = colorsCount++;

    public static final int key_buttonNeutral = colorsCount++;
    public static final int key_buttonNeutralText = colorsCount++;

    //my messages bubbles
    public static final int myMessagesBubblesStartIndex = colorsCount;
    public static final int key_chat_outBubble = colorsCount++;
    public static final int key_chat_outBubbleSelected = colorsCount++;
    public static final int key_chat_outBubbleShadow = colorsCount++;
    public static final int key_chat_outBubbleGradient1 = colorsCount++;
    public static final int key_chat_outBubbleGradient2 = colorsCount++;
    public static final int key_chat_outBubbleGradient3 = colorsCount++;
    public static final int myMessagesBubblesEndIndex = colorsCount;

    //my messages
    public static final int myMessagesStartIndex = colorsCount;
    public static final int key_chat_outGreenCall = colorsCount++;
    public static final int key_chat_outSentCheck = colorsCount++;
    public static final int key_chat_outSentCheckSelected = colorsCount++;
    public static final int key_chat_outSentCheckRead = colorsCount++;
    public static final int key_chat_outSentCheckReadSelected = colorsCount++;
    public static final int key_chat_outSentClock = colorsCount++;
    public static final int key_chat_outSentClockSelected = colorsCount++;
    public static final int key_chat_outMediaIcon = colorsCount++;
    public static final int key_chat_outMediaIconSelected = colorsCount++;
    public static final int key_chat_outViews = colorsCount++;
    public static final int key_chat_outViewsSelected = colorsCount++;
    public static final int key_chat_outMenu = colorsCount++;
    public static final int key_chat_outMenuSelected = colorsCount++;
    public static final int key_chat_outInstant = colorsCount++;
    public static final int key_chat_outInstantSelected = colorsCount++;
    public static final int key_chat_outPreviewInstantText = colorsCount++;
    public static final int key_chat_outForwardedNameText = colorsCount++;
    public static final int key_chat_outViaBotNameText = colorsCount++;
    public static final int key_chat_outReplyLine = colorsCount++;
    public static final int key_chat_outReplyLine2 = colorsCount++;
    public static final int key_chat_outReplyNameText = colorsCount++;
    public static final int key_chat_outReplyMessageText = colorsCount++;
    public static final int key_chat_outReplyMediaMessageText = colorsCount++;
    public static final int key_chat_outReplyMediaMessageSelectedText = colorsCount++;
    public static final int key_chat_outPreviewLine = colorsCount++;
    public static final int key_chat_outSiteNameText = colorsCount++;
    public static final int key_chat_outContactNameText = colorsCount++;
    public static final int key_chat_outContactPhoneText = colorsCount++;
    public static final int key_chat_outContactPhoneSelectedText = colorsCount++;
    public static final int key_chat_outAudioPerformerText = colorsCount++;
    public static final int key_chat_outAudioPerformerSelectedText = colorsCount++;
    public static final int key_chat_outTimeSelectedText = colorsCount++;
    public static final int key_chat_outAdminText = colorsCount++;
    public static final int key_chat_outAdminSelectedText = colorsCount++;
    public static final int key_chat_outAudioProgress = colorsCount++;
    public static final int key_chat_outAudioSelectedProgress = colorsCount++;
    public static final int key_chat_outTimeText = colorsCount++;
    public static final int key_chat_outAudioTitleText = colorsCount++;
    public static final int key_chat_outAudioDurationText = colorsCount++;
    public static final int key_chat_outAudioDurationSelectedText = colorsCount++;
    public static final int key_chat_outAudioSeekbar = colorsCount++;
    public static final int key_chat_outAudioCacheSeekbar = colorsCount++;
    public static final int key_chat_outAudioSeekbarSelected = colorsCount++;
    public static final int key_chat_outAudioSeekbarFill = colorsCount++;
    public static final int key_chat_outVoiceSeekbar = colorsCount++;
    public static final int key_chat_outVoiceSeekbarSelected = colorsCount++;
    public static final int key_chat_outVoiceSeekbarFill = colorsCount++;
    public static final int key_chat_outFileProgress = colorsCount++;
    public static final int key_chat_outFileProgressSelected = colorsCount++;
    public static final int key_chat_outFileNameText = colorsCount++;
    public static final int key_chat_outFileInfoText = colorsCount++;
    public static final int key_chat_outFileInfoSelectedText = colorsCount++;
    public static final int key_chat_outFileBackground = colorsCount++;
    public static final int key_chat_outFileBackgroundSelected = colorsCount++;
    public static final int key_chat_outVenueInfoText = colorsCount++;
    public static final int key_chat_outVenueInfoSelectedText = colorsCount++;
    public static final int key_chat_outLinkSelectBackground = colorsCount++;
    public static final int key_chat_outLoader = colorsCount++;
    public static final int key_chat_outLoaderSelected = colorsCount++;
    public static final int key_chat_outLocationIcon = colorsCount++;
    public static final int key_chat_outContactBackground = colorsCount++;
    public static final int key_chat_outContactIcon = colorsCount++;
    public static final int key_chat_outReactionButtonBackground = colorsCount++;
    public static final int myMessagesEndIndex = colorsCount;

    public static final int myMessages2StartIndex = colorsCount;
    public static final int key_chat_outTextSelectionHighlight = colorsCount++;
    public static final int key_chat_outTextSelectionCursor = colorsCount++;
    public static final int key_chat_outBubbleLocationPlaceholder = colorsCount++;
    public static final int key_chat_outBubbleSelectedOverlay = colorsCount++;
    public static final int key_chat_outPsaNameText = colorsCount++;
    public static final int key_chat_outBubbleGradientAnimated = colorsCount++;
    public static final int key_chat_outBubbleGradientSelectedOverlay = colorsCount++;
    public static final int myMessages2EndIndex = colorsCount;

    public static final int key_chat_inBubbleSelected = colorsCount++;
    public static final int key_chat_messageTextIn = colorsCount++;
    public static final int key_chat_messageTextOut = colorsCount++;
    public static final int key_chat_messageLinkIn = colorsCount++;
    public static final int key_chat_messageLinkOut = colorsCount++;
    public static final int key_chat_serviceText = colorsCount++;
    public static final int key_chat_serviceLink = colorsCount++;
    public static final int key_chat_serviceIcon = colorsCount++;
    public static final int key_chat_serviceBackground = colorsCount++;
    public static final int key_chat_serviceBackgroundSelected = colorsCount++;
    public static final int key_chat_serviceBackgroundSelector = colorsCount++;
    public static final int key_chat_muteIcon = colorsCount++;
    public static final int key_chat_lockIcon = colorsCount++;
    public static final int key_chat_inSentClock = colorsCount++;
    public static final int key_chat_inSentClockSelected = colorsCount++;
    public static final int key_chat_mediaSentCheck = colorsCount++;
    public static final int key_chat_mediaSentClock = colorsCount++;
    public static final int key_chat_inMediaIcon = colorsCount++;
    public static final int key_chat_inMediaIconSelected = colorsCount++;

    public static final int key_chat_mediaTimeBackground = colorsCount++;
    public static final int key_chat_inViews = colorsCount++;
    public static final int key_chat_inViewsSelected = colorsCount++;
    public static final int key_chat_mediaViews = colorsCount++;
    public static final int key_chat_inMenu = colorsCount++;
    public static final int key_chat_inMenuSelected = colorsCount++;
    public static final int key_chat_mediaMenu = colorsCount++;
    public static final int key_chat_inInstant = colorsCount++;
    public static final int key_chat_inInstantSelected = colorsCount++;
    public static final int key_chat_sentError = colorsCount++;
    public static final int key_chat_sentErrorIcon = colorsCount++;
    public static final int key_chat_selectedBackground = colorsCount++;
    public static final int key_chat_previewDurationText = colorsCount++;
    public static final int key_chat_previewGameText = colorsCount++;
    public static final int key_chat_inPreviewInstantText = colorsCount++;
    public static final int key_chat_secretTimeText = colorsCount++;
    public static final int key_chat_stickerNameText = colorsCount++;
    public static final int key_chat_botButtonText = colorsCount++;
    public static final int key_chat_inForwardedNameText = colorsCount++;
    public static final int key_chat_inPsaNameText = colorsCount++;
    public static final int key_chat_inViaBotNameText = colorsCount++;
    public static final int key_chat_stickerViaBotNameText = colorsCount++;
    public static final int key_chat_inQuote = colorsCount++;
    public static final int key_chat_outQuote = colorsCount++;
    public static final int key_chat_inReplyLine = colorsCount++;
    public static final int key_chat_stickerReplyLine = colorsCount++;
    public static final int key_chat_inReplyNameText = colorsCount++;
    public static final int key_chat_stickerReplyNameText = colorsCount++;
    public static final int key_chat_inReplyMessageText = colorsCount++;
    public static final int key_chat_inReplyMediaMessageText = colorsCount++;
    public static final int key_chat_inReplyMediaMessageSelectedText = colorsCount++;
    public static final int key_chat_stickerReplyMessageText = colorsCount++;
    public static final int key_chat_inPreviewLine = colorsCount++;
    public static final int key_chat_inSiteNameText = colorsCount++;
    public static final int key_chat_inContactNameText = colorsCount++;
    public static final int key_chat_inContactPhoneText = colorsCount++;
    public static final int key_chat_inContactPhoneSelectedText = colorsCount++;
    public static final int key_chat_mediaProgress = colorsCount++;
    public static final int key_chat_inAudioProgress = colorsCount++;
    public static final int key_chat_inAudioSelectedProgress = colorsCount++;
    public static final int key_chat_mediaTimeText = colorsCount++;
    public static final int key_chat_inAdminText = colorsCount++;
    public static final int key_chat_inAdminSelectedText = colorsCount++;
    public static final int key_chat_inTimeText = colorsCount++;
    public static final int key_chat_inTimeSelectedText = colorsCount++;
    public static final int key_chat_inAudioPerformerText = colorsCount++;
    public static final int key_chat_inAudioPerformerSelectedText = colorsCount++;
    public static final int key_chat_inAudioTitleText = colorsCount++;
    public static final int key_chat_inAudioDurationText = colorsCount++;
    public static final int key_chat_inAudioDurationSelectedText = colorsCount++;
    public static final int key_chat_inAudioSeekbar = colorsCount++;
    public static final int key_chat_inAudioCacheSeekbar = colorsCount++;
    public static final int key_chat_inAudioSeekbarSelected = colorsCount++;
    public static final int key_chat_inAudioSeekbarFill = colorsCount++;
    public static final int key_chat_inVoiceSeekbar = colorsCount++;
    public static final int key_chat_inVoiceSeekbarSelected = colorsCount++;
    public static final int key_chat_inVoiceSeekbarFill = colorsCount++;
    public static final int key_chat_inFileProgress = colorsCount++;
    public static final int key_chat_inFileProgressSelected = colorsCount++;
    public static final int key_chat_inFileNameText = colorsCount++;
    public static final int key_chat_inFileInfoText = colorsCount++;
    public static final int key_chat_inFileInfoSelectedText = colorsCount++;
    public static final int key_chat_inFileBackground = colorsCount++;
    public static final int key_chat_inFileBackgroundSelected = colorsCount++;
    public static final int key_chat_inVenueInfoText = colorsCount++;
    public static final int key_chat_inVenueInfoSelectedText = colorsCount++;
    public static final int key_chat_mediaInfoText = colorsCount++;
    public static final int key_chat_linkSelectBackground = colorsCount++;
    public static final int key_chat_textSelectBackground = colorsCount++;
    public static final int key_chat_wallpaper = colorsCount++;
    public static final int key_chat_wallpaper_gradient_to1 = colorsCount++;
    public static final int key_chat_wallpaper_gradient_to2 = colorsCount++;
    public static final int key_chat_wallpaper_gradient_to3 = colorsCount++;
    public static final int key_chat_wallpaper_gradient_rotation = colorsCount++;
    public static final int key_chat_messagePanelBackground = colorsCount++;
    public static final int key_chat_messagePanelShadow = colorsCount++;
    public static final int key_chat_messagePanelText = colorsCount++;
    public static final int key_chat_messagePanelHint = colorsCount++;
    public static final int key_chat_messagePanelCursor = colorsCount++;
    public static final int key_chat_messagePanelIcons = colorsCount++;
    public static final int key_chat_messagePanelSend = colorsCount++;
    public static final int key_chat_messagePanelVoiceLock = colorsCount++;
    public static final int key_chat_messagePanelVoiceLockBackground = colorsCount++;
    public static final int key_chat_messagePanelVoiceLockShadow = colorsCount++;
    public static final int key_chat_topPanelBackground = colorsCount++;
    public static final int key_chat_topPanelClose = colorsCount++;
    public static final int key_chat_topPanelLine = colorsCount++;
    public static final int key_chat_topPanelTitle = colorsCount++;
    public static final int key_chat_topPanelMessage = colorsCount++;
    public static final int key_chat_addContact = colorsCount++;
    public static final int key_chat_inLoader = colorsCount++;
    public static final int key_chat_inLoaderSelected = colorsCount++;
    public static final int key_chat_inLoaderPhoto = colorsCount++;
    public static final int key_chat_mediaLoaderPhoto = colorsCount++;
    public static final int key_chat_mediaLoaderPhotoSelected = colorsCount++;
    public static final int key_chat_mediaLoaderPhotoIcon = colorsCount++;
    public static final int key_chat_mediaLoaderPhotoIconSelected = colorsCount++;
    public static final int key_chat_inLocationBackground = colorsCount++;
    public static final int key_chat_inLocationIcon = colorsCount++;
    public static final int key_chat_inContactBackground = colorsCount++;
    public static final int key_chat_inContactIcon = colorsCount++;
    public static final int key_chat_replyPanelIcons = colorsCount++;
    public static final int key_chat_replyPanelClose = colorsCount++;
    public static final int key_chat_replyPanelName = colorsCount++;
    public static final int key_chat_replyPanelLine = colorsCount++;
    public static final int key_chat_searchPanelIcons = colorsCount++;
    public static final int key_chat_searchPanelText = colorsCount++;
    public static final int key_chat_secretChatStatusText = colorsCount++;
    public static final int key_chat_fieldOverlayText = colorsCount++;
    public static final int key_chat_stickersHintPanel = colorsCount++;
    public static final int key_chat_botSwitchToInlineText = colorsCount++;
    public static final int key_chat_unreadMessagesStartArrowIcon = colorsCount++;
    public static final int key_chat_unreadMessagesStartText = colorsCount++;
    public static final int key_chat_unreadMessagesStartBackground = colorsCount++;
    public static final int key_chat_inlineResultIcon = colorsCount++;
    public static final int key_chat_emojiPanelBackground = colorsCount++;
    public static final int key_chat_emojiSearchBackground = colorsCount++;
    public static final int key_chat_emojiSearchIcon = colorsCount++;
    public static final int key_chat_emojiPanelShadowLine = colorsCount++;
    public static final int key_chat_emojiPanelEmptyText = colorsCount++;
    public static final int key_chat_emojiPanelIcon = colorsCount++;
    public static final int key_chat_emojiBottomPanelIcon = colorsCount++;
    public static final int key_chat_emojiPanelIconSelected = colorsCount++;
    public static final int key_chat_emojiPanelStickerPackSelector = colorsCount++;
    public static final int key_chat_emojiPanelStickerPackSelectorLine = colorsCount++;
    public static final int key_chat_emojiPanelBackspace = colorsCount++;
    public static final int key_chat_emojiPanelTrendingTitle = colorsCount++;
    public static final int key_chat_emojiPanelStickerSetName = colorsCount++;
    public static final int key_chat_emojiPanelStickerSetNameHighlight = colorsCount++;
    public static final int key_chat_emojiPanelStickerSetNameIcon = colorsCount++;
    public static final int key_chat_emojiPanelTrendingDescription = colorsCount++;
    public static final int key_chat_botKeyboardButtonText = colorsCount++;
    public static final int key_chat_botKeyboardButtonBackground = colorsCount++;
    public static final int key_chat_botKeyboardButtonBackgroundPressed = colorsCount++;
    public static final int key_chat_emojiPanelNewTrending = colorsCount++;
    public static final int key_chat_messagePanelVoicePressed = colorsCount++;
    public static final int key_chat_messagePanelVoiceBackground = colorsCount++;
    public static final int key_chat_messagePanelVoiceDelete = colorsCount++;
    public static final int key_chat_messagePanelVoiceDuration = colorsCount++;
    public static final int key_chat_recordedVoicePlayPause = colorsCount++;
    public static final int key_chat_recordedVoiceProgress = colorsCount++;
    public static final int key_chat_recordedVoiceProgressInner = colorsCount++;
    public static final int key_chat_recordedVoiceDot = colorsCount++;
    public static final int key_chat_recordedVoiceBackground = colorsCount++;
    public static final int key_chat_recordedVoiceDarkerBackground = colorsCount++;
    public static final int key_chat_recordVoiceCancel = colorsCount++;
    public static final int key_chat_recordTime = colorsCount++;
    public static final int key_chat_messagePanelCancelInlineBot = colorsCount++;
    public static final int key_chat_gifSaveHintText = colorsCount++;
    public static final int key_chat_gifSaveHintBackground = colorsCount++;
    public static final int key_chat_goDownButton = colorsCount++;
    public static final int key_chat_goDownButtonCounter = colorsCount++;
    public static final int key_chat_goDownButtonCounterBackground = colorsCount++;
    public static final int key_chat_inTextSelectionHighlight = colorsCount++;
    public static final int key_chat_TextSelectionCursor = colorsCount++;
    public static final int key_chat_inBubbleLocationPlaceholder = colorsCount++;
    public static final int key_chat_BlurAlpha = colorsCount++;
    public static final int key_chat_BlurAlphaSlow = colorsCount++;
    public static final int key_chat_editMediaButton = colorsCount++;

    public static final int key_voipgroup_listSelector = colorsCount++;
    public static final int key_voipgroup_inviteMembersBackground = colorsCount++;
    public static final int key_voipgroup_actionBar = colorsCount++;
    public static final int key_voipgroup_actionBarItems = colorsCount++;
    public static final int key_voipgroup_actionBarItemsSelector = colorsCount++;
    public static final int key_voipgroup_actionBarUnscrolled = colorsCount++;
    public static final int key_voipgroup_listViewBackgroundUnscrolled = colorsCount++;
    public static final int key_voipgroup_lastSeenTextUnscrolled = colorsCount++;
    public static final int key_voipgroup_mutedIconUnscrolled = colorsCount++;
    public static final int key_voipgroup_nameText = colorsCount++;
    public static final int key_voipgroup_lastSeenText = colorsCount++;
    public static final int key_voipgroup_listeningText = colorsCount++;
    public static final int key_voipgroup_speakingText = colorsCount++;
    public static final int key_voipgroup_mutedIcon = colorsCount++;
    public static final int key_voipgroup_mutedByAdminIcon = colorsCount++;
    public static final int key_voipgroup_listViewBackground = colorsCount++;
    public static final int key_voipgroup_dialogBackground = colorsCount++;
    public static final int key_voipgroup_leaveCallMenu = colorsCount++;
    public static final int key_voipgroup_checkMenu = colorsCount++;
    public static final int key_voipgroup_soundButton = colorsCount++;
    public static final int key_voipgroup_soundButtonActive = colorsCount++;
    public static final int key_voipgroup_soundButtonActiveScrolled = colorsCount++;
    public static final int key_voipgroup_soundButton2 = colorsCount++;
    public static final int key_voipgroup_soundButtonActive2 = colorsCount++;
    public static final int key_voipgroup_soundButtonActive2Scrolled = colorsCount++;
    public static final int key_voipgroup_leaveButton = colorsCount++;
    public static final int key_voipgroup_leaveButtonScrolled = colorsCount++;
    public static final int key_voipgroup_muteButton = colorsCount++;
    public static final int key_voipgroup_muteButton2 = colorsCount++;
    public static final int key_voipgroup_muteButton3 = colorsCount++;
    public static final int key_voipgroup_unmuteButton = colorsCount++;
    public static final int key_voipgroup_unmuteButton2 = colorsCount++;
    public static final int key_voipgroup_disabledButton = colorsCount++;
    public static final int key_voipgroup_rtmpButton = colorsCount++;
    public static final int key_voipgroup_disabledButtonActive = colorsCount++;
    public static final int key_voipgroup_disabledButtonActiveScrolled = colorsCount++;
    public static final int key_voipgroup_connectingProgress = colorsCount++;
    public static final int key_voipgroup_scrollUp = colorsCount++;
    public static final int key_voipgroup_searchPlaceholder = colorsCount++;
    public static final int key_voipgroup_searchBackground = colorsCount++;
    public static final int key_voipgroup_searchText = colorsCount++;
    public static final int key_voipgroup_overlayGreen1 = colorsCount++;
    public static final int key_voipgroup_overlayGreen2 = colorsCount++;
    public static final int key_voipgroup_overlayBlue1 = colorsCount++;
    public static final int key_voipgroup_overlayBlue2 = colorsCount++;
    public static final int key_voipgroup_topPanelGreen1 = colorsCount++;
    public static final int key_voipgroup_topPanelGreen2 = colorsCount++;
    public static final int key_voipgroup_topPanelBlue1 = colorsCount++;
    public static final int key_voipgroup_topPanelBlue2 = colorsCount++;
    public static final int key_voipgroup_topPanelGray = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientMuted = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientMuted2 = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientUnmuted = colorsCount++;
    public static final int key_voipgroup_overlayAlertGradientUnmuted2 = colorsCount++;
    public static final int key_voipgroup_overlayAlertMutedByAdmin = colorsCount++;
    public static final int key_voipgroup_overlayAlertMutedByAdmin2 = colorsCount++;
    public static final int key_voipgroup_mutedByAdminGradient = colorsCount++;
    public static final int key_voipgroup_mutedByAdminGradient2 = colorsCount++;
    public static final int key_voipgroup_mutedByAdminGradient3 = colorsCount++;
    public static final int key_voipgroup_mutedByAdminMuteButton = colorsCount++;
    public static final int key_voipgroup_mutedByAdminMuteButtonDisabled = colorsCount++;
    public static final int key_voipgroup_windowBackgroundWhiteInputField = colorsCount++;
    public static final int key_voipgroup_windowBackgroundWhiteInputFieldActivated = colorsCount++;

    public static final int key_table_background = colorsCount++;
    public static final int key_table_border = colorsCount++;

    public static final int key_passport_authorizeBackground = colorsCount++;
    public static final int key_passport_authorizeBackgroundSelected = colorsCount++;
    public static final int key_passport_authorizeText = colorsCount++;

    public static final int key_profile_creatorIcon = colorsCount++;
    public static final int key_profile_title = colorsCount++;
    public static final int key_profile_actionIcon = colorsCount++;
    public static final int key_profile_actionBackground = colorsCount++;
    public static final int key_profile_actionPressedBackground = colorsCount++;
    public static final int key_profile_verifiedBackground = colorsCount++;
    public static final int key_profile_verifiedCheck = colorsCount++;
    public static final int key_profile_status = colorsCount++;
    public static final int key_chat_tagAdmin = colorsCount++;
    public static final int key_chat_tagCreator = colorsCount++;

    public static final int key_profile_tabText = colorsCount++;
    public static final int key_profile_tabSelectedText = colorsCount++;
    public static final int key_profile_tabSelectedLine = colorsCount++;
    public static final int key_profile_tabSelector = colorsCount++;

    public static final int key_sharedMedia_startStopLoadIcon = colorsCount++;
    public static final int key_sharedMedia_linkPlaceholder = colorsCount++;
    public static final int key_sharedMedia_linkPlaceholderText = colorsCount++;
    public static final int key_sharedMedia_photoPlaceholder = colorsCount++;

    public static final int key_featuredStickers_addedIcon = colorsCount++;
    public static final int key_featuredStickers_buttonProgress = colorsCount++;
    public static final int key_featuredStickers_addButton = colorsCount++;
    public static final int key_featuredStickers_addButton2 = colorsCount++;
    public static final int key_featuredStickers_addButtonPressed = colorsCount++;
    public static final int key_featuredStickers_removeButtonText = colorsCount++;
    public static final int key_featuredStickers_buttonText = colorsCount++;
    public static final int key_featuredStickers_unread = colorsCount++;

    public static final int key_stickers_menu = colorsCount++;
    public static final int key_stickers_menuSelector = colorsCount++;

    public static final int key_changephoneinfo_image2 = colorsCount++;

    public static final int key_groupcreate_hintText = colorsCount++;
    public static final int key_groupcreate_cursor = colorsCount++;
    public static final int key_groupcreate_sectionShadow = colorsCount++;
    public static final int key_groupcreate_sectionText = colorsCount++;
    public static final int key_groupcreate_spanText = colorsCount++;
    public static final int key_groupcreate_spanBackground = colorsCount++;
    public static final int key_groupcreate_spanDelete = colorsCount++;

    public static final int key_contacts_inviteBackground = colorsCount++;
    public static final int key_contacts_inviteText = colorsCount++;

    public static final int key_login_progressInner = colorsCount++;
    public static final int key_login_progressOuter = colorsCount++;

    public static final int key_picker_enabledButton = colorsCount++;
    public static final int key_picker_disabledButton = colorsCount++;
    public static final int key_picker_badge = colorsCount++;
    public static final int key_picker_badgeText = colorsCount++;

    public static final int key_location_sendLocationBackground = colorsCount++;
    public static final int key_location_sendLocationIcon = colorsCount++;
    public static final int key_location_sendLocationText = colorsCount++;
    public static final int key_location_sendLiveLocationBackground = colorsCount++;
    public static final int key_location_sendLiveLocationIcon = colorsCount++;
    public static final int key_location_sendLiveLocationText = colorsCount++;
    public static final int key_location_liveLocationProgress = colorsCount++;
    public static final int key_location_placeLocationBackground = colorsCount++;
    public static final int key_location_actionIcon = colorsCount++;
    public static final int key_location_actionActiveIcon = colorsCount++;
    public static final int key_location_actionBackground = colorsCount++;
    public static final int key_location_actionPressedBackground = colorsCount++;

    public static final int key_dialog_liveLocationProgress = colorsCount++;

    public static final int key_files_folderIcon = colorsCount++;
    public static final int key_files_folderIconBackground = colorsCount++;
    public static final int key_files_iconText = colorsCount++;

    public static final int key_sessions_devicesImage = colorsCount++;

    public static final int key_calls_callReceivedGreenIcon = colorsCount++;
    public static final int key_calls_callReceivedRedIcon = colorsCount++;

    public static final int key_undo_background = colorsCount++;
    public static final int key_undo_cancelColor = colorsCount++;
    public static final int key_undo_infoColor = colorsCount++;

    public static final int key_sheet_scrollUp = colorsCount++;
    public static final int key_sheet_other = colorsCount++;
    public static final int key_bot_loadingIcon = colorsCount++;
    public static final int key_gift_ribbon = colorsCount++;
    public static final int key_gift_ribbon_soldout = colorsCount++;

    public static final int key_player_actionBarSelector = colorsCount++;
    public static final int key_player_actionBarTitle = colorsCount++;
    public static final int key_player_actionBarSubtitle = colorsCount++;
    public static final int key_player_actionBarItems = colorsCount++;
    public static final int key_player_background = colorsCount++;
    public static final int key_player_time = colorsCount++;
    public static final int key_player_progressBackground = colorsCount++;
    public static final int key_player_progressCachedBackground = colorsCount++;
    public static final int key_player_progress = colorsCount++;
    public static final int key_player_button = colorsCount++;
    public static final int key_player_buttonActive = colorsCount++;

    public static final int key_statisticChartSignature = colorsCount++;
    public static final int key_statisticChartSignatureAlpha = colorsCount++;
    public static final int key_statisticChartHintLine = colorsCount++;
    public static final int key_statisticChartActiveLine = colorsCount++;
    public static final int key_statisticChartInactivePickerChart = colorsCount++;
    public static final int key_statisticChartActivePickerChart = colorsCount++;
    public static final int key_statisticChartRipple = colorsCount++;
    public static final int key_statisticChartBackZoomColor = colorsCount++;
    public static final int key_statisticChartChevronColor = colorsCount++;
    public static final int key_statisticChartLine_blue = colorsCount++;
    public static final int key_statisticChartLine_green = colorsCount++;
    public static final int key_statisticChartLine_red = colorsCount++;
    public static final int key_statisticChartLine_golden = colorsCount++;
    public static final int key_statisticChartLine_lightblue = colorsCount++;
    public static final int key_statisticChartLine_lightgreen = colorsCount++;
    public static final int key_statisticChartLine_orange = colorsCount++;
    public static final int key_statisticChartLine_indigo = colorsCount++;
    public static final int key_statisticChartLine_purple = colorsCount++;
    public static final int key_statisticChartLine_cyan = colorsCount++;
    public static final int key_statisticChartLineEmpty = colorsCount++;

    public static final int key_color_lightblue = colorsCount++;
    public static final int key_color_blue = colorsCount++;
    public static final int key_color_green = colorsCount++;
    public static final int key_color_lightgreen = colorsCount++;
    public static final int key_color_red = colorsCount++;
    public static final int key_color_orange = colorsCount++;
    public static final int key_color_yellow = colorsCount++;
    public static final int key_color_purple = colorsCount++;
    public static final int key_color_cyan = colorsCount++;
    public static final int[] keys_colors = {key_color_lightblue, key_color_blue, key_color_green, key_color_lightgreen, key_color_red, key_color_orange, key_color_yellow, key_color_purple, key_color_cyan};

    public static final int key_chat_inReactionButtonBackground = colorsCount++;
    public static final int key_chat_outReactionButtonText = colorsCount++;
    public static final int key_chat_inReactionButtonText = colorsCount++;
    public static final int key_chat_inReactionButtonTextSelected = colorsCount++;
    public static final int key_chat_outReactionButtonTextSelected = colorsCount++;
    public static final int key_chat_reactionServiceButtonBackgroundSelected = colorsCount++;
    public static final int key_chat_reactionServiceButtonTextSelected = colorsCount++;
    public static final int key_reactionStarSelector = colorsCount++;

    public static final int key_premiumGradient0 = colorsCount++;
    public static final int key_premiumGradient1 = colorsCount++;
    public static final int key_premiumGradient2 = colorsCount++;
    public static final int key_premiumGradient3 = colorsCount++;
    public static final int key_premiumGradient4 = colorsCount++;
    public static final int key_premiumGradientBackground1 = colorsCount++;
    public static final int key_premiumGradientBackground2 = colorsCount++;
    public static final int key_premiumGradientBackground3 = colorsCount++;
    public static final int key_premiumGradientBackground4 = colorsCount++;
    public static final int key_premiumGradientBackgroundOverlay = colorsCount++;
    public static final int key_premiumStartSmallStarsColor = colorsCount++;
    public static final int key_premiumStarGradient1 = colorsCount++;
    public static final int key_premiumStarGradient2 = colorsCount++;
    public static final int key_premiumCoinGradient1 = colorsCount++;
    public static final int key_premiumCoinGradient2 = colorsCount++;
    public static final int key_premiumStartSmallStarsColor2 = colorsCount++;
    public static final int key_premiumGradientBottomSheet1 = colorsCount++;
    public static final int key_premiumGradientBottomSheet2 = colorsCount++;
    public static final int key_premiumGradientBottomSheet3 = colorsCount++;
    public static final int key_topics_unreadCounter = colorsCount++;
    public static final int key_topics_unreadCounterMuted = colorsCount++;
    public static final int key_starsGradient1 = colorsCount++;
    public static final int key_starsGradient2 = colorsCount++;

    public static final int key_stories_circle1 = colorsCount++;
    public static final int key_stories_circle2 = colorsCount++;
    public static final int key_stories_circle_dialog1 = colorsCount++;
    public static final int key_stories_circle_dialog2 = colorsCount++;
    public static final int key_stories_circle_closeFriends1 = colorsCount++;
    public static final int key_stories_circle_closeFriends2 = colorsCount++;
    public static final int key_stories_circle_live1 = colorsCount++;
    public static final int key_stories_circle_live2 = colorsCount++;

    public static final int key_chat_inCodeBackground = colorsCount++;
    public static final int key_chat_outCodeBackground = colorsCount++;
    public static final int key_code_keyword = colorsCount++;
    public static final int key_code_operator = colorsCount++;
    public static final int key_code_constant = colorsCount++;
    public static final int key_code_string = colorsCount++;
    public static final int key_code_number = colorsCount++;
    public static final int key_code_comment = colorsCount++;
    public static final int key_code_function = colorsCount++;

    public static final int key_iv_background = colorsCount++;
    public static final int key_iv_backgroundGray = colorsCount++;
    public static final int key_iv_ab_progress = colorsCount++;
    public static final int key_iv_navigationBackground = colorsCount++;

    public static final int key_share_linkText = colorsCount++;
    public static final int key_share_linkBackground = colorsCount++;
    public static final int key_share_icon = colorsCount++;

    public static final int key_glass_defaultIcon = colorsCount++;
    public static final int key_glass_defaultText = colorsCount++;
    public static final int key_glass_targetMainTabs = colorsCount++;
    public static final int key_glass_targetMainTopPanel = colorsCount++;
    public static final int key_glass_tabSelected = colorsCount++;
    public static final int key_glass_tabSelectedText = colorsCount++;
    public static final int key_glass_tabUnselected = colorsCount++;

    public static final int key_botKeyboard_button_primary = colorsCount++;
    public static final int key_botKeyboard_button_danger = colorsCount++;
    public static final int key_botKeyboard_button_success = colorsCount++;

    public static final int key_telegram_color_dialogsLogo = colorsCount++;
    public static final int key_telegram_color = colorsCount++;
    public static final int key_telegram_color_text = colorsCount++;

    public static final int ACTION_BAR_WHITE_SELECTOR_COLOR = 0x40ffffff;
    public static final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final class ThemeInfo {
        public String name;
        public SparseIntArray colors = new SparseIntArray();
        private boolean dark;

        private ThemeInfo(String name, boolean dark) {
            this.name = name;
            this.dark = dark;
        }

        public boolean isDark() {
            return dark;
        }

        public boolean isLight() {
            return !dark;
        }

        public String getKey() {
            return name;
        }
    }

    private static final String THEME_CONFIG = "themeconfig";
    private static final String THEME_CONFIG_ACTIVE = "theme";
    private static final String THEME_NATIVE_DO = "NativeDo";
    private static final String THEME_DARK_BLUE = "Dark Blue";

    private static final ArrayList<ThemeDelegate> themeDelegates = new ArrayList<>();
    private static final ArrayList<ThemeInfo> themes = new ArrayList<>();
    private static final HashMap<String, ThemeInfo> themesDict = new HashMap<>();
    private static final SparseIntArray currentColors = new SparseIntArray();
    private static final int[] defaultColors = ThemeColors.createDefaultColors();
    private static final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Mirrors upstream {@code Theme.avatar_backgroundPaint} — shared paint AvatarDrawable draws into. */
    public static final Paint avatar_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Shared paint pool for cell-level views (TopicCell etc). Naming mirrors
    // upstream Theme.dialogs_*Paint so call sites stay aligned. Text size and
    // typeface are configured once in init(); colors are refreshed on every
    // applyThemeInternal() so theme switches re-tint the pool atomically.
    public static final TextPaint dialogs_namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint dialogs_messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint dialogs_timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint dialogs_countTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint dialogs_countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Muted counter background (gray pill for muted chats). */
    public static final Paint dialogs_countGrayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Returns the color used for service-message chrome. Mirrors upstream Theme.getServiceMessageColor(). */
    public static int getServiceMessageColor() {
        return getColor(key_chat_serviceBackground);
    }

    /** Xfermode-clear paint used to punch holes through composited layers. Mirrors upstream Theme.PAINT_CLEAR. */
    public static final Paint PAINT_CLEAR = new Paint(Paint.ANTI_ALIAS_FLAG); static {
        PAINT_CLEAR.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
    }
    public static final Paint dialogs_pinnedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Pinned row background overlay — mirrors upstream DialogCell key_chats_pinnedOverlay. */
    public static final Paint dialogs_pinnedBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Small pin icon drawn next to time on pinned rows — mirrors upstream dialogs_pinnedDrawable2. */
    public static Drawable dialogs_pinnedDrawable2;
    /** NativeDo extension: bottom hairline separator paint shared across list cells. */
    public static final Paint dialogs_dividerPaint = new Paint();
    public static final Paint checkboxSquare_eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint chat_docBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint chat_audioTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static Drawable chat_composeShadowDrawable;
    public static final Paint chat_composeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Drawable[] chat_locationDrawable = new Drawable[] { new ColorDrawable(Color.TRANSPARENT) };

    public static class MessageDrawable extends ColorDrawable {
        public MessageDrawable() {
            super(Color.TRANSPARENT);
        }

        // Minimal host-wiring surface for the vendored CheckBoxBase. The local
        // never sets a MessageDrawable on a CheckBox (no setBackgroundDrawable call
        // sites in :telegram), so the gradient path is dead code; these methods
        // exist only to satisfy the API. Mirrors upstream MessageDrawable API.

        public boolean hasGradient() { return false; }
        public android.graphics.Shader getGradientShader() { return null; }
        public android.graphics.Matrix getMatrix() { return null; }
        public void applyMatrixScale() { /* no-op: no gradient support */ }
        public int getTopY() { return 0; }
    }

    private static Context applicationContext;
    private static ThemeInfo currentTheme;
    private static ThemeInfo currentNightTheme;
    private static ThemeInfo currentDayTheme;
    private static ThemeInfo defaultTheme;
    private static int themeGeneration;

    static {
        ThemeInfo nativeDo = createNativeDoTheme();
        themes.add(currentDayTheme = defaultTheme = nativeDo);
        themesDict.put(nativeDo.getKey(), nativeDo);

        ThemeInfo darkBlue = createDarkBlueTheme();
        themes.add(currentNightTheme = darkBlue);
        themesDict.put(darkBlue.getKey(), darkBlue);

        applyThemeInternal(defaultTheme);
    }

    private Theme() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        applicationContext = context.getApplicationContext();
        createDialogsResources();
        // Load vendored pin icon — mirrors upstream Theme.createDialogsResources().
        dialogs_pinnedDrawable2 = context.getResources().getDrawable(R.drawable.msg_pin_mini).mutate();
        chat_composeShadowDrawable = context.getResources().getDrawable(R.drawable.compose_panel_shadow).mutate();
        SharedPreferences preferences = applicationContext.getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE);
        ThemeInfo themeInfo = themesDict.get(preferences.getString(THEME_CONFIG_ACTIVE, defaultTheme.getKey()));
        if (themeInfo == null) {
            themeInfo = defaultTheme;
        }
        if (themeInfo != currentTheme) {
            applyTheme(themeInfo, false, themeInfo == currentNightTheme);
        } else {
            // Same theme — still need to push current colors into the shared
            // paint pool now that text sizes / typefaces have been set.
            applyDialogsColors();
        }
    }

    /**
     * Configure the shared dialogs paint pool. Sizes depend on
     * {@link AndroidUtilities#density} so this must run after
     * {@code AndroidUtilities.init()}. Color is set separately in
     * {@link #applyDialogsColors()} since it follows the active theme.
     */
    private static void createDialogsResources() {
        dialogs_namePaint.setTextSize(AndroidUtilities.dp(16));
        dialogs_namePaint.setTypeface(AndroidUtilities.bold());
        dialogs_messagePaint.setTextSize(AndroidUtilities.dp(14));
        dialogs_timePaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_countTextPaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_countTextPaint.setTypeface(AndroidUtilities.bold());
    }

    /** Simplified port of upstream setDrawableColor — handles ShapeDrawable and falls through to PorterDuff. */
    public static void setDrawableColor(Drawable drawable, int color) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof android.graphics.drawable.ShapeDrawable) {
            ((android.graphics.drawable.ShapeDrawable) drawable).getPaint().setColor(color);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    /** Port of upstream setDrawableColorByKey. */
    public static void setDrawableColorByKey(Drawable drawable, int key) {
        setDrawableColor(drawable, getColor(key));
    }

    private static void applyDialogsColors() {
        dialogs_namePaint.setColor(getColor(key_chats_name));
        dialogs_messagePaint.setColor(getColor(key_chats_message));
        dialogs_timePaint.setColor(getColor(key_chats_date));
        dialogs_countTextPaint.setColor(getColor(key_chats_unreadCounterText));
        dialogs_countPaint.setColor(getColor(key_chats_unreadCounter));
        dialogs_countGrayPaint.setColor(getColor(key_chats_unreadCounterMuted));
        dialogs_pinnedPaint.setColor(getColor(key_chats_pinnedIcon));
        dialogs_pinnedBgPaint.setColor(getColor(key_chats_pinnedOverlay));
        setDrawableColorByKey(dialogs_pinnedDrawable2, key_chats_pinnedIcon);
        setDrawableColorByKey(chat_composeShadowDrawable, key_chat_messagePanelShadow);
        chat_composeBackgroundPaint.setColor(getColor(key_chat_messagePanelBackground));
        dialogs_dividerPaint.setColor(getColor(key_divider));
    }

    public static ThemeInfo getTheme(String key) {
        return themesDict.get(key);
    }

    public static ThemeInfo getCurrentTheme() {
        return currentDayTheme != null ? currentDayTheme : defaultTheme;
    }

    public static ThemeInfo getCurrentNightTheme() {
        return currentNightTheme;
    }

    public static ThemeInfo getActiveTheme() {
        return currentTheme;
    }

    public static boolean isCurrentThemeNight() {
        return currentTheme == currentNightTheme;
    }

    public static boolean isCurrentThemeDark() {
        return currentTheme != null && currentTheme.isDark();
    }

    public static int getThemeGeneration() {
        return themeGeneration;
    }

    public static void toggleTheme() {
        applyTheme(isCurrentThemeDark() ? defaultTheme : currentNightTheme);
    }

    public static void applyTheme(ThemeInfo themeInfo) {
        applyTheme(themeInfo, true, themeInfo == currentNightTheme);
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean save, boolean nightTheme) {
        if (themeInfo == null) {
            return;
        }
        if (!nightTheme) {
            currentDayTheme = themeInfo;
        }
        applyThemeInternal(themeInfo);
        if (save && applicationContext != null) {
            applicationContext
                    .getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE)
                    .edit()
                    .putString(THEME_CONFIG_ACTIVE, themeInfo.getKey())
                    .apply();
        }
        notifyThemeChanged();
    }

    public static int getColor(int key) {
        int index = currentColors.indexOfKey(key);
        if (index >= 0) {
            int color = currentColors.valueAt(index);
            if (key == key_windowBackgroundWhite || key == key_windowBackgroundGray || key == key_actionBarDefault) {
                color |= 0xff000000;
            }
            return color;
        }
        return getDefaultColor(key);
    }

    public static int getColor(int key, ResourcesProvider resourcesProvider) {
        if (resourcesProvider != null) {
            Integer color = resourcesProvider.getColorOrNull(key);
            if (color != null) {
                return color;
            }
        }
        return getColor(key);
    }

    public static int getColor(int key, ResourcesProvider resourcesProvider, boolean[] isDefault) {
        return getColor(key, resourcesProvider);
    }

    public static int getColor(int key, ResourcesProvider resourcesProvider, boolean isDefault) {
        return getColor(key, resourcesProvider);
    }

    public static int blendOver(int color, int overlay) {
        return androidx.core.graphics.ColorUtils.compositeColors(overlay, color);
    }

    /** ADAPTED: NativeDo has no animated color blending; alias to getColor. */
    public static int getNonAnimatedColor(int key) {
        return getColor(key);
    }

    public static int multAlpha(int color, float multiply) {
        if (multiply == 1f) {
            return color;
        }
        int alpha = Math.max(0, Math.min(0xFF, (int) (Color.alpha(color) * multiply)));
        return (color & 0x00ffffff) | (alpha << 24);
    }

    public static int getDefaultColor(int key) {
        if (key < 0 || key >= defaultColors.length) {
            return 0xffff0000;
        }
        int value = defaultColors[key];
        return value == 0 ? 0xffff0000 : value;
    }

    public static RippleDrawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, RIPPLE_MASK_CIRCLE_20DP);
    }

    public static RippleDrawable createSelectorDrawable(int color, int maskType) {
        Drawable maskDrawable = null;
        if (maskType == RIPPLE_MASK_ALL) {
            maskDrawable = new ColorDrawable(Color.WHITE);
        } else if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            maskDrawable = new Drawable() {
                @Override
                public void draw(Canvas canvas) {
                    Rect bounds = getBounds();
                    maskPaint.setColor(Color.WHITE);
                    canvas.drawCircle(bounds.centerX(), bounds.centerY(), AndroidUtilities.dp(20), maskPaint);
                }

                @Override
                public void setAlpha(int alpha) {
                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return PixelFormat.UNKNOWN;
                }
            };
        }
        RippleDrawable rippleDrawable = new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
        if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            rippleDrawable.setRadius(AndroidUtilities.dp(20));
        }
        return rippleDrawable;
    }

    /** ADAPTED: 3-arg overload with radius for RecyclerListView selector. */
    public static RippleDrawable createSelectorDrawable(int color, int maskType, int radius) {
        Drawable maskDrawable = null;
        if (maskType == RIPPLE_MASK_ROUNDRECT_6DP) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            g.setColor(Color.WHITE);
            g.setCornerRadius(radius > 0 ? radius : AndroidUtilities.dp(6));
            maskDrawable = g;
            return new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
        }
        if (maskType == RIPPLE_MASK_ALL) {
            maskDrawable = new ColorDrawable(Color.WHITE);
        } else if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            maskDrawable = new Drawable() {
                @Override
                public void draw(Canvas canvas) {
                    Rect bounds = getBounds();
                    maskPaint.setColor(Color.WHITE);
                    canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, maskPaint);
                }

                @Override
                public void setAlpha(int alpha) {
                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return PixelFormat.UNKNOWN;
                }
            };
        }
        RippleDrawable rippleDrawable = new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
        rippleDrawable.setRadius(radius);
        return rippleDrawable;
    }

    public static RippleDrawable getSelectorDrawable(boolean whiteBackground) {
        return createSelectorDrawable(
            getColor(whiteBackground ? key_actionBarDefaultSelector : key_listSelector),
            RIPPLE_MASK_ALL
        );
    }

    /** ADAPTED: getSelectorDrawable by color key for RecyclerListView. */
    public static Drawable getSelectorDrawable(int color, boolean whiteBackground) {
        return createSelectorDrawable(color, RIPPLE_MASK_ALL);
    }

    /** ADAPTED: createRadSelectorDrawable for RecyclerListView rounded selectors. */
    public static Drawable createRadSelectorDrawable(int color, int topRadius, int bottomRadius) {
        Drawable maskDrawable = new Drawable() {
            private final Paint maskPaintLocal = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path maskPath = new Path();
            private final RectF maskRect = new RectF();
            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                maskPaintLocal.setColor(Color.WHITE);
                maskRect.set(bounds);
                maskPath.reset();
                float[] radii = new float[]{
                    topRadius, topRadius, topRadius, topRadius,
                    bottomRadius, bottomRadius, bottomRadius, bottomRadius
                };
                maskPath.addRoundRect(maskRect, radii, Path.Direction.CW);
                canvas.drawPath(maskPath, maskPaintLocal);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.UNKNOWN; }
        };
        return new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
    }

    public static Drawable createRadSelectorDrawable(int color, int rippleColor, int topRadius, int bottomRadius) {
        return createRadSelectorDrawable(rippleColor == 0 ? color : rippleColor, topRadius, bottomRadius);
    }

    /** ADAPTED: createSimpleSelectorRoundRectDrawable for RecyclerListView. */
    public static Drawable createSimpleSelectorRoundRectDrawable(int radius, int color, int selectorColor, int backgroundColor) {
        Drawable maskDrawable = new Drawable() {
            private final Paint maskPaintLocal = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF maskRect = new RectF();
            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                maskPaintLocal.setColor(Color.WHITE);
                maskRect.set(bounds);
                canvas.drawRoundRect(maskRect, radius, radius, maskPaintLocal);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.UNKNOWN; }
        };
        return new RippleDrawable(ColorStateList.valueOf(selectorColor), null, maskDrawable);
    }

    /** ADAPTED: setSelectorDrawableColor for RecyclerListView. */
    public static void setSelectorDrawableColor(Drawable drawable, int color, boolean animated) {
        if (drawable instanceof RippleDrawable) {
            ((RippleDrawable) drawable).setColor(ColorStateList.valueOf(color));
        }
    }

    /** ADAPTED: setMaskDrawableRad for RecyclerListView. */
    public static void setMaskDrawableRad(Drawable drawable, int topRad, int bottomRad) {
        // No-op: mask radius changes are not critical for NativeDo's use case
    }

    /** ADAPTED: createRoundRectDrawable for RecyclerListView section backgrounds. */
    public static Drawable createRoundRectDrawable(int radius, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return new Drawable() {
            private final RectF rect = new RectF();
            @Override
            public void draw(Canvas canvas) {
                rect.set(getBounds());
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
        };
    }

    /** ADAPTED: lightweight shadowed rounded rect used by FragmentSearchField section background. */
    public static Drawable createRoundRectDrawableShadowed(int radius, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setShadowLayer(AndroidUtilities.dpf2(1.5f), 0, AndroidUtilities.dp(1), 0x26000000);
        return new Drawable() {
            private final RectF rect = new RectF();

            @Override
            public void draw(Canvas canvas) {
                rect.set(getBounds());
                rect.inset(AndroidUtilities.dp(1), AndroidUtilities.dp(1));
                canvas.drawRoundRect(rect, radius, radius, paint);
            }

            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
        };
    }

    /** ADAPTED: getThemeDrawable stub for RecyclerListView. */
    public static Drawable getThemeDrawable(String key) {
        return null;
    }

    /** ADAPTED: getThemePaint stub for RecyclerListView. */
    public static Paint getThemePaint(String paintKey) {
        return new Paint();
    }

    public static Drawable createCircleDrawable(int size, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return new Drawable() {
            private final RectF rect = new RectF();

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                rect.set(bounds);
                canvas.drawOval(rect, paint);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }

            @Override
            public int getIntrinsicWidth() {
                return size;
            }

            @Override
            public int getIntrinsicHeight() {
                return size;
            }
        };
    }

    public static Drawable createOutlineCircleDrawable(int size, int color, int strokeWidth) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        return new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                final Rect b = getBounds();
                canvas.drawCircle(b.centerX(), b.centerY(), size / 2.0f, paint);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }

            @Override
            public int getIntrinsicWidth() {
                return size + strokeWidth;
            }

            @Override
            public int getIntrinsicHeight() {
                return size + strokeWidth;
            }
        };
    }

    public static Drawable getThemedDrawable(Context context, int resId, int key) {
        if (context == null || resId == 0) {
            return new ColorDrawable(getColor(key));
        }
        try {
            Drawable drawable = context.getResources().getDrawable(resId).mutate();
            setDrawableColor(drawable, getColor(key));
            return drawable;
        } catch (Exception e) {
            return new ColorDrawable(getColor(key));
        }
    }

    public static void setCombinedDrawableColor(org.telegram.ui.Components.CombinedDrawable drawable, int color, boolean isIcon) {
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public static void addThemeDelegate(ThemeDelegate delegate) {
        if (delegate != null && !themeDelegates.contains(delegate)) {
            themeDelegates.add(delegate);
        }
    }

    public static void removeThemeDelegate(ThemeDelegate delegate) {
        themeDelegates.remove(delegate);
    }

    private static void applyThemeInternal(ThemeInfo themeInfo) {
        currentTheme = themeInfo;
        currentColors.clear();
        SparseIntArray colors = themeInfo.colors;
        for (int i = 0; i < colors.size(); i++) {
            currentColors.put(colors.keyAt(i), colors.valueAt(i));
        }
        themeGeneration++;
        applyDialogsColors();
    }

    private static void notifyThemeChanged() {
        ArrayList<ThemeDelegate> delegates = new ArrayList<>(themeDelegates);
        for (int i = 0; i < delegates.size(); i++) {
            delegates.get(i).didSetNewTheme();
        }
    }

    private static ThemeInfo createNativeDoTheme() {
        ThemeInfo themeInfo = new ThemeInfo(THEME_NATIVE_DO, false);
        themeInfo.colors.put(key_windowBackgroundWhite, 0xfffbfaf3);
        themeInfo.colors.put(key_windowBackgroundGray, 0xfff5f4ed);
        themeInfo.colors.put(key_windowBackgroundWhiteBlackText, 0xff20251f);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText, 0xff6b6f63);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText2, 0xff74786b);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueText, 0xff1e7755);
        themeInfo.colors.put(key_divider, 0xffdfdace);
        themeInfo.colors.put(key_actionBarDefault, 0xfff5f4ed);
        themeInfo.colors.put(key_actionBarDefaultSubtitle, 0xff6b6f63);
        themeInfo.colors.put(key_actionBarDefaultSubmenuItem, 0xff1c2119);
        themeInfo.colors.put(key_actionBarDefaultSubmenuBackground, 0xfffbfaf3);
        themeInfo.colors.put(key_actionBarDefaultSubmenuSeparator, 0xffe6e1d5);
        themeInfo.colors.put(key_glass_tabSelected, 0xff1e7755);
        themeInfo.colors.put(key_glass_tabSelectedText, 0xff1e7755);
        themeInfo.colors.put(key_glass_tabUnselected, 0xff20251f);
        themeInfo.colors.put(key_telegram_color, 0xff1e7755);
        themeInfo.colors.put(key_iv_background, 0xffffffff);
        themeInfo.colors.put(key_iv_backgroundGray, 0xfff0f0f0);
        themeInfo.colors.put(key_iv_navigationBackground, 0xffffffff);
        themeInfo.colors.put(key_iv_ab_progress, 0xff1e7755);
        themeInfo.colors.put(key_actionBarActionModeDefaultSelector, 0xffe2e2e2);
        themeInfo.colors.put(key_chat_messagePanelBackground, 0xffffffff);
        themeInfo.colors.put(key_chat_messagePanelShadow, 0xff000000);
        themeInfo.colors.put(key_graySection, 0xfff5f4ed);
        themeInfo.colors.put(key_graySectionText, 0xff6b6f63);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueHeader, 0xff1e7755);
        themeInfo.colors.put(key_windowBackgroundGrayShadow, 0xffdfdace);
        themeInfo.colors.put(key_dialogBackground, 0xffffffff);
        themeInfo.colors.put(key_fastScrollInactive, 0xffc4c4c4);
        themeInfo.colors.put(key_fastScrollActive, 0xff999999);
        themeInfo.colors.put(key_fastScrollText, 0xffffffff);
        themeInfo.colors.put(key_settings_listSelector, 0xffe8e8d8);
        themeInfo.colors.put(key_progressCircle, 0xff1e7755);

        // ScrollSlidingTextTabStrip
        themeInfo.colors.put(key_actionBarTabLine, 0xff1e7755);
        themeInfo.colors.put(key_actionBarTabActiveText, 0xff1e7755);
        themeInfo.colors.put(key_actionBarTabUnactiveText, 0xff6b6f63);
        themeInfo.colors.put(key_actionBarTabSelector, 0x14000000);

        // PullForegroundDrawable
        themeInfo.colors.put(key_chats_archivePullDownBackground, 0xffc6c9ce);
        themeInfo.colors.put(key_chats_archivePullDownBackgroundActive, 0xff66a9e0);
        themeInfo.colors.put(key_avatar_backgroundArchivedHidden, 0xffc6c9ce);

        // DialogCell / TopicCell
        themeInfo.colors.put(key_chats_name, 0xff20251f);
        themeInfo.colors.put(key_chats_nameMessage, 0xff1e7755);
        themeInfo.colors.put(key_chats_message, 0xff6b6f63);
        themeInfo.colors.put(key_chats_date, 0xff95968a);
        themeInfo.colors.put(key_chats_pinnedIcon, 0xff95968a);
        themeInfo.colors.put(key_chats_pinnedOverlay, 0xfff0eee5);
        themeInfo.colors.put(key_chats_unreadCounter, 0xff1e7755);
        themeInfo.colors.put(key_chats_unreadCounterText, 0xffffffff);
        themeInfo.colors.put(key_chats_unreadCounterMuted, 0xffbcbeb1);
        themeInfo.colors.put(key_chats_actionBackground, 0xff1e7755);
        themeInfo.colors.put(key_avatar_text, 0xffffffff);

        // Avatar palette — matches upstream Theme defaults so monogram avatars
        // look identical across NativeDo and Telegram screenshots.
        themeInfo.colors.put(key_avatar_backgroundRed, 0xffFF845E);
        themeInfo.colors.put(key_avatar_backgroundOrange, 0xffFEBB5B);
        themeInfo.colors.put(key_avatar_backgroundViolet, 0xffB694F9);
        themeInfo.colors.put(key_avatar_backgroundGreen, 0xff9AD164);
        themeInfo.colors.put(key_avatar_backgroundCyan, 0xff5BCBE3);
        themeInfo.colors.put(key_avatar_backgroundBlue, 0xff5CAFFA);
        themeInfo.colors.put(key_avatar_backgroundPink, 0xffFF8AAC);
        themeInfo.colors.put(key_avatar_background2Red, 0xffD45246);
        themeInfo.colors.put(key_avatar_background2Orange, 0xffF68136);
        themeInfo.colors.put(key_avatar_background2Violet, 0xff6C61DF);
        themeInfo.colors.put(key_avatar_background2Green, 0xff46BA43);
        themeInfo.colors.put(key_avatar_background2Cyan, 0xff359AD4);
        themeInfo.colors.put(key_avatar_background2Blue, 0xff408ACF);
        themeInfo.colors.put(key_avatar_background2Pink, 0xffD95574);

        // TableLayout
        themeInfo.colors.put(key_table_background, 0xfff5f4ed);
        themeInfo.colors.put(key_table_border, 0xffdfdace);

        return themeInfo;
    }

    private static ThemeInfo createDarkBlueTheme() {
        ThemeInfo themeInfo = new ThemeInfo(THEME_DARK_BLUE, true);
        themeInfo.colors.put(key_windowBackgroundWhite, -14866637);
        themeInfo.colors.put(key_windowBackgroundGray, -15393241);
        themeInfo.colors.put(key_windowBackgroundWhiteBlackText, -1);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText, -8549479);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText2, 1878130175);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueText, -10177041);
        themeInfo.colors.put(key_divider, -1795162112);
        themeInfo.colors.put(key_listSelector, 350681087);
        themeInfo.colors.put(key_actionBarDefault, -14406343);
        themeInfo.colors.put(key_actionBarDefaultSelector, 516942079);
        themeInfo.colors.put(key_actionBarDefaultIcon, -1);
        themeInfo.colors.put(key_actionBarDefaultTitle, -1);
        themeInfo.colors.put(key_actionBarDefaultSubtitle, 2111566591);
        themeInfo.colors.put(key_actionBarDefaultSubmenuItem, -1);
        themeInfo.colors.put(key_actionBarDefaultSubmenuItemIcon, -1);
        themeInfo.colors.put(key_actionBarDefaultSubmenuBackground, -14141368);
        themeInfo.colors.put(key_actionBarDefaultSubmenuSeparator, -14733761);
        themeInfo.colors.put(key_glass_tabSelected, -10177041);
        themeInfo.colors.put(key_glass_tabSelectedText, -10177041);
        themeInfo.colors.put(key_glass_tabUnselected, -1);
        themeInfo.colors.put(key_telegram_color, -10177041);
        themeInfo.colors.put(key_iv_background, 0xff000000);
        themeInfo.colors.put(key_iv_backgroundGray, 0xff1f1f1f);
        themeInfo.colors.put(key_iv_navigationBackground, 0xff000000);
        themeInfo.colors.put(key_iv_ab_progress, -10177041);
        themeInfo.colors.put(key_actionBarActionModeDefaultSelector, 0xff3a3a3a);
        themeInfo.colors.put(key_chat_messagePanelBackground, 0xff1c1c1e);
        themeInfo.colors.put(key_chat_messagePanelShadow, 0xff000000);
        themeInfo.colors.put(key_graySection, -15393241);
        themeInfo.colors.put(key_graySectionText, -8549479);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueHeader, -10177041);
        themeInfo.colors.put(key_windowBackgroundGrayShadow, -1795162112);
        themeInfo.colors.put(key_dialogBackground, -14141368);
        themeInfo.colors.put(key_fastScrollInactive, 0xff3a3a3a);
        themeInfo.colors.put(key_fastScrollActive, 0xff5a5a5a);
        themeInfo.colors.put(key_fastScrollText, 0xffffffff);
        themeInfo.colors.put(key_settings_listSelector, 0xff2a2a2a);
        themeInfo.colors.put(key_progressCircle, -10177041);

        // ScrollSlidingTextTabStrip
        themeInfo.colors.put(key_actionBarTabLine, -10177041);
        themeInfo.colors.put(key_actionBarTabActiveText, -10177041);
        themeInfo.colors.put(key_actionBarTabUnactiveText, -8549479);
        themeInfo.colors.put(key_actionBarTabSelector, 0x1fffffff);

        // PullForegroundDrawable
        themeInfo.colors.put(key_chats_archivePullDownBackground, 0xff424850);
        themeInfo.colors.put(key_chats_archivePullDownBackgroundActive, 0xff336699);
        themeInfo.colors.put(key_avatar_backgroundArchivedHidden, 0xff424850);

        // DialogCell / TopicCell
        themeInfo.colors.put(key_chats_name, 0xffffffff);
        themeInfo.colors.put(key_chats_nameMessage, -10177041);
        themeInfo.colors.put(key_chats_message, 0xff8a8a8a);
        themeInfo.colors.put(key_chats_date, 0xff7a7a7a);
        themeInfo.colors.put(key_chats_pinnedIcon, 0xff7a7a7a);
        themeInfo.colors.put(key_chats_pinnedOverlay, 0xff1a1f26);
        themeInfo.colors.put(key_chats_unreadCounter, -10177041);
        themeInfo.colors.put(key_chats_unreadCounterText, 0xffffffff);
        themeInfo.colors.put(key_chats_unreadCounterMuted, 0xff464a52);
        themeInfo.colors.put(key_chats_actionBackground, -10177041);
        themeInfo.colors.put(key_avatar_text, 0xffffffff);

        // Avatar palette — share the light palette since both gradient stops
        // already sit comfortably under white monogram text. Matches upstream
        // dark-blue.attheme behavior (only Red/Orange override; rest fall back).
        themeInfo.colors.put(key_avatar_backgroundRed, 0xffFF845E);
        themeInfo.colors.put(key_avatar_backgroundOrange, 0xffFEBB5B);
        themeInfo.colors.put(key_avatar_backgroundViolet, 0xffB694F9);
        themeInfo.colors.put(key_avatar_backgroundGreen, 0xff9AD164);
        themeInfo.colors.put(key_avatar_backgroundCyan, 0xff5BCBE3);
        themeInfo.colors.put(key_avatar_backgroundBlue, 0xff5CAFFA);
        themeInfo.colors.put(key_avatar_backgroundPink, 0xffFF8AAC);
        themeInfo.colors.put(key_avatar_background2Red, 0xffD45246);
        themeInfo.colors.put(key_avatar_background2Orange, 0xffF68136);
        themeInfo.colors.put(key_avatar_background2Violet, 0xff6C61DF);
        themeInfo.colors.put(key_avatar_background2Green, 0xff46BA43);
        themeInfo.colors.put(key_avatar_background2Cyan, 0xff359AD4);
        themeInfo.colors.put(key_avatar_background2Blue, 0xff408ACF);
        themeInfo.colors.put(key_avatar_background2Pink, 0xffD95574);

        // TableLayout
        themeInfo.colors.put(key_table_background, -15393241);
        themeInfo.colors.put(key_table_border, -1795162112);

        return themeInfo;
    }
}

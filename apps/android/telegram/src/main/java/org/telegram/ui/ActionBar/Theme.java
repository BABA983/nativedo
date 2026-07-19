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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.text.TextPaint;
import android.util.StateSet;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Method;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.PathAnimator;

public final class Theme {

    // Upstream debug paint, used by DrawingInBackgroundThreadDrawable's
    // BuildVars.DEBUG_PRIVATE_VERSION error branch. Copied verbatim from upstream.
    public static Paint DEBUG_RED = new Paint(); static { DEBUG_RED.setColor(0xffff0000); }
    public static Paint DEBUG_GREEN_STROKE = new Paint(); static { DEBUG_GREEN_STROKE.setColor(0xff00ff00); DEBUG_GREEN_STROKE.setStrokeWidth(2); DEBUG_GREEN_STROKE.setStyle(Paint.Style.STROKE); }
    public static Paint dialogs_onlineCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static TextPaint chat_msgTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static TextPaint chat_replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public static TextPaint chat_replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    public static TextPaint chat_audioPerformerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static TextPaint chat_instantViewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static Drawable[] chat_pollCheckDrawable = new Drawable[2];
    public static PathAnimator playPauseAnimator;
    public static Path[] chat_filePath = new Path[2];
    public static Path[] chat_updatePath = new Path[3];
    public static Drawable chat_flameIcon;
    public static Drawable chat_gifIcon;

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

    public static Drawable createInsetRoundRectDrawable(int color, float radius, int inset) {
        return createInsetRoundRectDrawable(color, radius, inset, inset);
    }

    public static Drawable createInsetRoundRectDrawable(int color, float radius, int insetW, int insetH) {
        return createInsetRoundRectDrawable(color, radius, insetW, insetH, insetW, insetH);
    }

    public static Drawable createInsetRoundRectDrawable(int color, float radius, int insetL, int insetT, int insetR, int insetB) {
        maskPaint.setColor(0xffffffff);
        Drawable maskDrawable = new Drawable() {
            private final RectF rectF = new RectF();

            @Override
            public void draw(Canvas canvas) {
                rectF.set(getBounds());
                rectF.left += insetL;
                rectF.top += insetT;
                rectF.right -= insetR;
                rectF.bottom -= insetB;
                canvas.drawRoundRect(rectF, radius, radius, maskPaint);
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
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
    }

    /**
     * Mirrors Telegram's {@code Theme.Colorable}. Views implement this so hosts
     * can re-tint after {@link #applyTheme(ThemeInfo)} / {@code didSetNewTheme}.
     */
    public interface Colorable {
        void updateColors();

        default int[] getColorKeys() {
            return null;
        }
    }

    public static final class AdaptiveRipple {
        private AdaptiveRipple() {
        }

        public static Drawable filledRect(int color, float radiusDp) {
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(color);
            background.setCornerRadius(AndroidUtilities.dp(radiusDp));
            return background;
        }
    }

    /** Upstream-shaped provider used for per-sheet and per-surface color overrides. */
    public interface ResourcesProvider {
        default int getColor(int key) {
            return Theme.getColor(key);
        }

        default Integer getColorOrNull(int key) {
            return getColor(key);
        }

        default int getColorOrDefault(int key) {
            return getColor(key);
        }

        default int getCurrentColor(int key) {
            return getColor(key);
        }

        default void setAnimatedColor(int key, int color) {
        }

        default Drawable getDrawable(String drawableKey) {
            return null;
        }

        /** ADAPTED: getPaint stub for RecyclerListView compatibility. */
        default Paint getPaint(String paintKey) {
            return Theme.getThemePaint(paintKey);
        }

        default boolean hasGradientService() {
            return false;
        }

        default boolean isDark() {
            return Theme.isCurrentThemeDark();
        }

    }

    public static final int RIPPLE_MASK_CIRCLE_20DP = 1;
    public static final int RIPPLE_MASK_ALL = 2;
    public static final int RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE = 3;
    public static final int RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER = 4;
    public static final int RIPPLE_MASK_CIRCLE_AUTO = 5;
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
    public static final int ACTION_BAR_PHOTO_VIEWER_COLOR = 0x8a000000;
    public static final int ACTION_BAR_PICKER_SELECTOR_COLOR = 0xff3d3d3d;
    public static final int ACTION_BAR_AUDIO_SELECTOR_COLOR = 0x2f000000;
    public static final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final class ThemeInfo {
        public String name;
        /** Upstream asset name under {@code assets/} (e.g. {@code bluebubbles.attheme}). */
        public String assetName;
        public SparseIntArray colors = new SparseIntArray();
        private boolean dark;

        private ThemeInfo(String name, boolean dark) {
            this.name = name;
            this.dark = dark;
        }

        private ThemeInfo(String name, String assetName, boolean dark) {
            this.name = name;
            this.assetName = assetName;
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
    /** Upstream-shaped: day theme key (e.g. "Blue"). */
    private static final String THEME_CONFIG_DAY = "theme";
    /** Upstream-shaped: night theme key (e.g. "Dark Blue"). */
    private static final String THEME_CONFIG_NIGHT = "nighttheme";
    /** Host: whether the last applied theme was the night slot. */
    private static final String THEME_CONFIG_ACTIVE_IS_NIGHT = "activeIsNight";
    private static final String THEME_BLUE = "Blue";
    private static final String THEME_DARK_BLUE = "Dark Blue";
    private static final String ASSET_BLUE = "bluebubbles.attheme";
    private static final String ASSET_DARK_BLUE = "darkblue.attheme";

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
    public static Paint checkboxSquare_checkPaint;
    public static Paint checkboxSquare_backgroundPaint;
    public static final Paint checkboxSquare_eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static void createCommonResources(Context context) {
        if (checkboxSquare_checkPaint == null) {
            checkboxSquare_checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkboxSquare_checkPaint.setStyle(Paint.Style.STROKE);
            checkboxSquare_checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
            checkboxSquare_checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkboxSquare_eraserPaint.setColor(0);
            checkboxSquare_eraserPaint.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            checkboxSquare_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
    }
    public static final Paint chat_docBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint chat_urlPaint = new Paint(); static {
        chat_urlPaint.setPathEffect(LinkPath.getRoundedEffect());
    }
    public static final Paint chat_outUrlPaint = new Paint(); static {
        chat_outUrlPaint.setPathEffect(LinkPath.getRoundedEffect());
    }
    public static final Paint chat_actionBackgroundGradientDarkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG); static {
        chat_actionBackgroundGradientDarkenPaint.setColor(0x15000000);
    }
    public static final String key_paint_chatActionBackground = "paintChatActionBackground";
    public static final String key_paint_divider = "paintDivider";
    public static final String key_paint_chatActionBackgroundDarken = "paintChatActionBackgroundDarken";
    public static final String key_paint_chatActionBackgroundSelected = "paintChatActionBackgroundSelected";
    private static final Paint chat_actionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private static final Paint chat_actionBackgroundSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
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
        // Upstream default themes (Theme.java ~L4610): Blue + Dark Blue only for
        // this host slice. Accent tables / Arctic / Day / Night omitted until a
        // theme picker is ported. Colors load from assets/*.attheme on apply.
        ThemeInfo blue = new ThemeInfo(THEME_BLUE, ASSET_BLUE, false);
        themes.add(currentDayTheme = defaultTheme = blue);
        themesDict.put(THEME_BLUE, blue);

        ThemeInfo darkBlue = new ThemeInfo(THEME_DARK_BLUE, ASSET_DARK_BLUE, true);
        themes.add(currentNightTheme = darkBlue);
        themesDict.put(THEME_DARK_BLUE, darkBlue);

        updateChatMessageTextPaints();
        // Colors stay at defaultColors[] until init() can open assets.
        currentTheme = defaultTheme;
    }

    private Theme() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        applicationContext = context.getApplicationContext();
        FileLog.d("Theme.init isDark=" + isCurrentThemeDark());
        createDialogsResources();
        try {
            createMediaActionResources(context);
            // Load vendored pin icon — mirrors upstream Theme.createDialogsResources().
            dialogs_pinnedDrawable2 = context.getResources().getDrawable(R.drawable.msg_pin_mini).mutate();
            chat_composeShadowDrawable = context.getResources().getDrawable(R.drawable.compose_panel_shadow).mutate();
            for (int a = 0; a < chat_pollCheckDrawable.length; a++) {
                chat_pollCheckDrawable[a] = context.getResources().getDrawable(R.drawable.poll_right).mutate();
            }
        } catch (Throwable e) {
            // Unit tests / incomplete host resource packs may lack optional drawables;
            // color theme pipeline must still initialize.
            FileLog.e(e);
        }
        SharedPreferences preferences = applicationContext.getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE);
        // Resolve day/night slots; unknown keys (e.g. retired "NativeDo") fall back to defaults.
        currentDayTheme = resolveThemeKey(preferences.getString(THEME_CONFIG_DAY, THEME_BLUE), defaultTheme);
        currentNightTheme = resolveThemeKey(preferences.getString(THEME_CONFIG_NIGHT, THEME_DARK_BLUE),
                themesDict.get(THEME_DARK_BLUE) != null ? themesDict.get(THEME_DARK_BLUE) : defaultTheme);
        boolean activeIsNight = preferences.getBoolean(THEME_CONFIG_ACTIVE_IS_NIGHT, false);
        ThemeInfo themeInfo = activeIsNight ? currentNightTheme : currentDayTheme;
        if (themeInfo == null) {
            themeInfo = defaultTheme;
            activeIsNight = false;
        }
        // Always apply so asset-backed colors and paint pool refresh after density init.
        applyTheme(themeInfo, false, activeIsNight);
    }

    /**
     * Maps a prefs/theme key to a registered {@link ThemeInfo}. Unknown or
     * retired keys (including the former "NativeDo" brand theme) fall back to
     * {@code fallback}.
     */
    private static ThemeInfo resolveThemeKey(String key, ThemeInfo fallback) {
        if (key == null || key.isEmpty()) {
            return fallback;
        }
        ThemeInfo info = themesDict.get(key);
        return info != null ? info : fallback;
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
        chat_audioTitlePaint.setTextSize(AndroidUtilities.dp(16));
        chat_audioPerformerPaint.setTextSize(AndroidUtilities.dp(15));
        chat_instantViewPaint.setTextSize(AndroidUtilities.dp(13));
        chat_instantViewPaint.setTypeface(AndroidUtilities.bold());
        updateChatMessageTextPaints();
    }

    /** Host-wired subset of upstream Theme chat text paint sizing. */
    public static void updateChatMessageTextPaints() {
        chat_msgTextPaint.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize));
        float smallerDp = (2 * SharedConfig.fontSize + 10) / 3f;
        chat_replyNamePaint.setTextSize(AndroidUtilities.dp(smallerDp));
        chat_replyNamePaint.setTypeface(AndroidUtilities.bold());
        chat_replyTextPaint.setTextSize(AndroidUtilities.dp(smallerDp));
    }

    /** Minimal upstream media-action icon/path bootstrap used by RadialProgress2. */
    private static void createMediaActionResources(Context context) {
        RectF rect = new RectF();
        chat_updatePath[0] = new Path();
        chat_updatePath[2] = new Path();
        float cx = AndroidUtilities.dp(12);
        float cy = AndroidUtilities.dp(12);
        rect.set(cx - AndroidUtilities.dp(5), cy - AndroidUtilities.dp(5), cx + AndroidUtilities.dp(5), cy + AndroidUtilities.dp(5));
        chat_updatePath[2].arcTo(rect, -160, -110, true);
        chat_updatePath[2].arcTo(rect, 20, -110, true);

        chat_updatePath[0].moveTo(cx, cy + AndroidUtilities.dp(8));
        chat_updatePath[0].lineTo(cx, cy + AndroidUtilities.dp(2));
        chat_updatePath[0].lineTo(cx + AndroidUtilities.dp(3), cy + AndroidUtilities.dp(5));
        chat_updatePath[0].close();
        chat_updatePath[0].moveTo(cx, cy - AndroidUtilities.dp(8));
        chat_updatePath[0].lineTo(cx, cy - AndroidUtilities.dp(2));
        chat_updatePath[0].lineTo(cx - AndroidUtilities.dp(3), cy - AndroidUtilities.dp(5));
        chat_updatePath[0].close();

        int rad = AndroidUtilities.dp(2);
        chat_filePath[0] = new Path();
        chat_filePath[0].moveTo(AndroidUtilities.dp(7), AndroidUtilities.dp(3));
        chat_filePath[0].lineTo(AndroidUtilities.dp(14), AndroidUtilities.dp(3));
        chat_filePath[0].lineTo(AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        chat_filePath[0].lineTo(AndroidUtilities.dp(21), AndroidUtilities.dp(20));
        rect.set(AndroidUtilities.dp(21) - rad * 2, AndroidUtilities.dp(19) - rad, AndroidUtilities.dp(21), AndroidUtilities.dp(19) + rad);
        chat_filePath[0].arcTo(rect, 0, 90, false);
        chat_filePath[0].lineTo(AndroidUtilities.dp(6), AndroidUtilities.dp(21));
        rect.set(AndroidUtilities.dp(5), AndroidUtilities.dp(19) - rad, AndroidUtilities.dp(5) + rad * 2, AndroidUtilities.dp(19) + rad);
        chat_filePath[0].arcTo(rect, 90, 90, false);
        chat_filePath[0].lineTo(AndroidUtilities.dp(5), AndroidUtilities.dp(4));
        rect.set(AndroidUtilities.dp(5), AndroidUtilities.dp(3), AndroidUtilities.dp(5) + rad * 2, AndroidUtilities.dp(3) + rad * 2);
        chat_filePath[0].arcTo(rect, 180, 90, false);
        chat_filePath[0].close();

        chat_filePath[1] = new Path();
        chat_filePath[1].moveTo(AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        chat_filePath[1].lineTo(AndroidUtilities.dp(19), AndroidUtilities.dp(10));
        chat_filePath[1].lineTo(AndroidUtilities.dp(14), AndroidUtilities.dp(10));
        chat_filePath[1].close();

        chat_flameIcon = context.getResources().getDrawable(R.drawable.filled_fire).mutate();
        chat_gifIcon = context.getResources().getDrawable(R.drawable.msg_round_gif_m).mutate();

        playPauseAnimator = new PathAnimator(0.293f, -26, -28, 1.0f);
        playPauseAnimator.addSvgKeyFrame("M 34.141 16.042 C 37.384 17.921 40.886 20.001 44.211 21.965 C 46.139 23.104 49.285 24.729 49.586 25.917 C 50.289 28.687 48.484 30 46.274 30 L 6 30.021 C 3.79 30.021 2.075 30.023 2 26.021 L 2.009 3.417 C 2.009 0.417 5.326 -0.58 7.068 0.417 C 10.545 2.406 25.024 10.761 34.141 16.042 Z", 166);
        playPauseAnimator.addSvgKeyFrame("M 37.843 17.769 C 41.143 19.508 44.131 21.164 47.429 23.117 C 48.542 23.775 49.623 24.561 49.761 25.993 C 50.074 28.708 48.557 30 46.347 30 L 6 30.012 C 3.79 30.012 2 28.222 2 26.012 L 2.009 4.609 C 2.009 1.626 5.276 0.664 7.074 1.541 C 10.608 3.309 28.488 12.842 37.843 17.769 Z", 200);
        playPauseAnimator.addSvgKeyFrame("M 40.644 18.756 C 43.986 20.389 49.867 23.108 49.884 25.534 C 49.897 27.154 49.88 24.441 49.894 26.059 C 49.911 28.733 48.6 30 46.39 30 L 6 30.013 C 3.79 30.013 2 28.223 2 26.013 L 2.008 5.52 C 2.008 2.55 5.237 1.614 7.079 2.401 C 10.656 4 31.106 14.097 40.644 18.756 Z", 217);
        playPauseAnimator.addSvgKeyFrame("M 43.782 19.218 C 47.117 20.675 50.075 21.538 50.041 24.796 C 50.022 26.606 50.038 24.309 50.039 26.104 C 50.038 28.736 48.663 30 46.453 30 L 6 29.986 C 3.79 29.986 2 28.196 2 25.986 L 2.008 6.491 C 2.008 3.535 5.196 2.627 7.085 3.316 C 10.708 4.731 33.992 14.944 43.782 19.218 Z", 234);
        playPauseAnimator.addSvgKeyFrame("M 47.421 16.941 C 50.544 18.191 50.783 19.91 50.769 22.706 C 50.761 24.484 50.76 23.953 50.79 26.073 C 50.814 27.835 49.334 30 47.124 30 L 5 30.01 C 2.79 30.01 1 28.22 1 26.01 L 1.001 10.823 C 1.001 8.218 3.532 6.895 5.572 7.26 C 7.493 8.01 47.421 16.941 47.421 16.941 Z", 267);
        playPauseAnimator.addSvgKeyFrame("M 47.641 17.125 C 50.641 18.207 51.09 19.935 51.078 22.653 C 51.07 24.191 51.062 21.23 51.088 23.063 C 51.109 24.886 49.587 27 47.377 27 L 5 27.009 C 2.79 27.009 1 25.219 1 23.009 L 0.983 11.459 C 0.983 8.908 3.414 7.522 5.476 7.838 C 7.138 8.486 47.641 17.125 47.641 17.125 Z", 300);
        playPauseAnimator.addSvgKeyFrame("M 48 7 C 50.21 7 52 8.79 52 11 C 52 19 52 19 52 19 C 52 21.21 50.21 23 48 23 L 4 23 C 1.79 23 0 21.21 0 19 L 0 11 C 0 8.79 1.79 7 4 7 C 48 7 48 7 48 7 Z", 383);
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
        int messageTextColor = getColor(key_chat_messageTextIn);
        chat_audioTitlePaint.setColor(messageTextColor);
        chat_audioPerformerPaint.setColor(messageTextColor);
        chat_audioPerformerPaint.linkColor = getColor(key_chat_messageLinkIn);
        chat_instantViewPaint.setColor(messageTextColor);
        int pollAccentColor = getColor(key_chat_inAudioSeekbarFill);
        chat_docBackPaint.setColor(pollAccentColor);
        chat_urlPaint.setColor(getColor(key_chat_linkSelectBackground));
        chat_outUrlPaint.setColor(getColor(key_chat_outLinkSelectBackground));
        for (int a = 0; a < chat_pollCheckDrawable.length; a++) {
            if (chat_pollCheckDrawable[a] != null) {
                setDrawableColor(chat_pollCheckDrawable[a], pollAccentColor);
            }
        }
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

    public static boolean hasGradientService() {
        return false;
    }

    public static int getThemeGeneration() {
        return themeGeneration;
    }

    public static void toggleTheme() {
        // Flip between the day and night slots (Blue ↔ Dark Blue by default).
        if (isCurrentThemeNight() || isCurrentThemeDark()) {
            applyTheme(currentDayTheme != null ? currentDayTheme : defaultTheme, true, false);
        } else {
            applyTheme(currentNightTheme != null ? currentNightTheme : themesDict.get(THEME_DARK_BLUE), true, true);
        }
    }

    public static void applyTheme(ThemeInfo themeInfo) {
        applyTheme(themeInfo, true, themeInfo != null && themeInfo.isDark());
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean save, boolean nightTheme) {
        if (themeInfo == null) {
            return;
        }
        try {
            if (nightTheme) {
                currentNightTheme = themeInfo;
            } else {
                currentDayTheme = themeInfo;
            }
            // Upstream: when assetName/path set, load into currentColorsNoAccent then refresh.
            // Host subset: load asset into themeInfo.colors then copy into currentColors.
            if (themeInfo.assetName != null) {
                SparseIntArray fromFile = getThemeFileValues(null, themeInfo.assetName, null);
                themeInfo.colors = fromFile;
            }
            if (save && applicationContext != null) {
                SharedPreferences.Editor editor = applicationContext
                        .getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE)
                        .edit();
                if (nightTheme) {
                    editor.putString(THEME_CONFIG_NIGHT, themeInfo.getKey());
                } else {
                    editor.putString(THEME_CONFIG_DAY, themeInfo.getKey());
                }
                editor.putBoolean(THEME_CONFIG_ACTIVE_IS_NIGHT, nightTheme);
                // Drop retired single-key "theme" misuse of active theme name if present.
                editor.apply();
            }
            applyThemeInternal(themeInfo);
            notifyThemeChanged();
        } catch (Exception e) {
            FileLog.e(e);
        }
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
            return resourcesProvider.getColor(key);
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

    public static Drawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, RIPPLE_MASK_CIRCLE_20DP, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType) {
        return createSelectorDrawable(color, maskType, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType, int radius) {
        Drawable maskDrawable = null;
        if ((maskType == RIPPLE_MASK_CIRCLE_20DP || maskType == RIPPLE_MASK_CIRCLE_AUTO) && Build.VERSION.SDK_INT >= 23) {
            maskDrawable = null;
        } else if (
                maskType == RIPPLE_MASK_CIRCLE_20DP ||
                maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE ||
                maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_CORNER ||
                maskType == RIPPLE_MASK_CIRCLE_AUTO ||
                maskType == 6 ||
                maskType == RIPPLE_MASK_ROUNDRECT_6DP
        ) {
            maskPaint.setColor(0xffffffff);
            maskDrawable = new Drawable() {
                RectF rect;

                @Override
                public void draw(Canvas canvas) {
                    Rect bounds = getBounds();
                    if (maskType == RIPPLE_MASK_ROUNDRECT_6DP) {
                        if (rect == null) {
                            rect = new RectF();
                        }
                        rect.set(bounds);
                        float rad = radius <= 0 ? AndroidUtilities.dp(6) : radius;
                        canvas.drawRoundRect(rect, rad, rad, maskPaint);
                    } else {
                        int rad;
                        if (maskType == RIPPLE_MASK_CIRCLE_20DP || maskType == 6) {
                            rad = radius <= 0 ? AndroidUtilities.dp(20) : radius;
                        } else if (maskType == RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE) {
                            rad = Math.max(bounds.width(), bounds.height()) / 2;
                        } else {
                            rad = (int) Math.ceil(Math.sqrt((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())));
                        }
                        canvas.drawCircle(bounds.centerX(), bounds.centerY(), rad, maskPaint);
                    }
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
        } else if (maskType == RIPPLE_MASK_ALL) {
            maskDrawable = new ColorDrawable(0xffffffff);
        }
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
        );
        RippleDrawable rippleDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
        if (Build.VERSION.SDK_INT >= 23) {
            if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
                rippleDrawable.setRadius(radius <= 0 ? AndroidUtilities.dp(20) : radius);
            } else if (maskType == RIPPLE_MASK_CIRCLE_AUTO) {
                rippleDrawable.setRadius(RippleDrawable.RADIUS_AUTO);
            }
        }
        return rippleDrawable;
    }

    public static Drawable getSelectorDrawable(boolean whiteBackground) {
        return getSelectorDrawable(getColor(key_listSelector), whiteBackground);
    }

    public static Drawable getSelectorDrawable(boolean whiteBackground, ResourcesProvider resourcesProvider) {
        final int color = getColor(key_listSelector, resourcesProvider);
        if (whiteBackground) {
            return getSelectorDrawable(color, key_windowBackgroundWhite, resourcesProvider);
        } else {
            return createSelectorDrawable(color, RIPPLE_MASK_ALL);
        }
    }

    public static Drawable getSelectorDrawable(int color, boolean whiteBackground) {
        if (whiteBackground) {
            return getSelectorDrawable(color, key_windowBackgroundWhite);
        } else {
            return createSelectorDrawable(color, RIPPLE_MASK_ALL);
        }
    }

    public static Drawable getSelectorDrawable(int color, int backgroundColor) {
        return getSelectorDrawable(color, backgroundColor, null);
    }

    public static Drawable getSelectorDrawable(int color, int backgroundColor, ResourcesProvider resourcesProvider) {
        if (backgroundColor >= 0) {
            Drawable maskDrawable = new ColorDrawable(0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new BaseCell.RippleDrawableSafe(colorStateList, new ColorDrawable(getColor(backgroundColor, resourcesProvider)), maskDrawable);
        } else {
            return createSelectorDrawable(color, RIPPLE_MASK_ALL);
        }
    }

    public static class RippleRadMaskDrawable extends Drawable {
        private final Path path = new Path();
        private final float[] radii = new float[8];
        private boolean invalidatePath = true;

        public RippleRadMaskDrawable(float top, float bottom) {
            setRadius(top, bottom);
        }

        public RippleRadMaskDrawable(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            setRadius(topLeft, topRight, bottomRight, bottomLeft);
        }

        public void setRadius(float top, float bottom) {
            radii[0] = radii[1] = radii[2] = radii[3] = AndroidUtilities.dp(top);
            radii[4] = radii[5] = radii[6] = radii[7] = AndroidUtilities.dp(bottom);
            invalidatePath = true;
            invalidateSelf();
        }

        public void setRadius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
            radii[0] = radii[1] = AndroidUtilities.dp(topLeft);
            radii[2] = radii[3] = AndroidUtilities.dp(topRight);
            radii[4] = radii[5] = AndroidUtilities.dp(bottomRight);
            radii[6] = radii[7] = AndroidUtilities.dp(bottomLeft);
            invalidatePath = true;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            invalidatePath = true;
        }

        @Override
        public void draw(Canvas canvas) {
            if (invalidatePath) {
                invalidatePath = false;
                path.reset();
                AndroidUtilities.rectTmp.set(getBounds());
                path.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            }
            canvas.drawPath(path, maskPaint);
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
    }

    public static Drawable createRadSelectorDrawable(int color, int topRad, int bottomRad) {
        maskPaint.setColor(0xffffffff);
        Drawable maskDrawable = new RippleRadMaskDrawable(topRad, bottomRad);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
    }

    public static Drawable createRadSelectorDrawable(int color, int rippleColor, int topRad, int bottomRad) {
        maskPaint.setColor(0xffffffff);
        Drawable maskDrawable = new RippleRadMaskDrawable(topRad, bottomRad);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{rippleColor}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, createRoundRectDrawable(AndroidUtilities.dp(topRad), AndroidUtilities.dp(bottomRad), color), maskDrawable);
    }

    public static Drawable createRadSelectorDrawable(int color, int topLeftRad, int topRightRad, int bottomRightRad, int bottomLeftRad) {
        maskPaint.setColor(0xffffffff);
        Drawable maskDrawable = new RippleRadMaskDrawable(topLeftRad, topRightRad, bottomRightRad, bottomLeftRad);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor) {
        return createSimpleSelectorRoundRectDrawable(rad, defaultColor, pressedColor, pressedColor);
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor, int maskColor) {
        return createSimpleSelectorRoundRectDrawable(rad, rad, rad, rad, defaultColor, pressedColor, maskColor);
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int leftTop, int rightTop, int rightBottom, int leftBottom, int defaultColor, int pressedColor, int maskColor) {
        return createSimpleSelectorRoundRectDrawable(new float[]{
                leftTop, leftTop,
                rightTop, rightTop,
                rightBottom, rightBottom,
                leftBottom, leftBottom
        }, defaultColor, pressedColor, maskColor);
    }

    private static Drawable createSimpleSelectorRoundRectDrawable(float[] rad, int defaultColor, int pressedColor, int maskColor) {
        return createSimpleSelectorRoundRectDrawable(rad, defaultColor, pressedColor, maskColor, 0);
    }

    private static Drawable createSimpleSelectorRoundRectDrawable(float[] rad, int defaultColor, int pressedColor, int maskColor, int inset) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(rad, null, null));
        defaultDrawable.setPadding(inset, inset, inset, inset);
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(new RoundRectShape(rad, null, null));
        pressedDrawable.getPaint().setColor(maskColor);
        pressedDrawable.setPadding(inset, inset, inset, inset);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{pressedColor}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, defaultDrawable, pressedDrawable);
    }

    private static Method stateListDrawableGetStateDrawableMethod;

    private static Drawable getStateDrawable(Drawable drawable, int index) {
        if (!(drawable instanceof StateListDrawable)) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return ((StateListDrawable) drawable).getStateDrawable(index);
            }
            if (stateListDrawableGetStateDrawableMethod == null) {
                stateListDrawableGetStateDrawableMethod = StateListDrawable.class.getDeclaredMethod("getStateDrawable", int.class);
                stateListDrawableGetStateDrawableMethod.setAccessible(true);
            }
            return (Drawable) stateListDrawableGetStateDrawableMethod.invoke(drawable, index);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean setSelectorDrawableColor(Drawable drawable, int color, boolean selected) {
        boolean changed = false;
        if (drawable instanceof StateListDrawable) {
            try {
                Drawable state;
                if (selected) {
                    state = getStateDrawable(drawable, 0);
                    if (state instanceof ShapeDrawable) {
                        changed = ((ShapeDrawable) state).getPaint().getColor() != color || changed;
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else if (state != null) {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                    state = getStateDrawable(drawable, 1);
                } else {
                    state = getStateDrawable(drawable, 2);
                }
                if (state instanceof ShapeDrawable) {
                    changed = ((ShapeDrawable) state).getPaint().getColor() != color || changed;
                    ((ShapeDrawable) state).getPaint().setColor(color);
                } else if (state != null) {
                    state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
            } catch (Throwable ignore) {
            }
        } else if (drawable instanceof RippleDrawable) {
            RippleDrawable rippleDrawable = (RippleDrawable) drawable;
            if (selected) {
                rippleDrawable.setColor(new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{color}
                ));
            } else if (rippleDrawable.getNumberOfLayers() > 0) {
                Drawable content = rippleDrawable.getDrawable(0);
                if (content instanceof ShapeDrawable) {
                    changed = ((ShapeDrawable) content).getPaint().getColor() != color || changed;
                    ((ShapeDrawable) content).getPaint().setColor(color);
                } else if (content != null) {
                    content.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
            }
        }
        return changed;
    }

    /** ADAPTED: setMaskDrawableRad for RecyclerListView and link-preview selectors. */
    public static void setMaskDrawableRad(Drawable drawable, int topRad, int bottomRad) {
        if (!(drawable instanceof RippleDrawable)) {
            return;
        }
        RippleDrawable rippleDrawable = (RippleDrawable) drawable;
        for (int i = 0; i < rippleDrawable.getNumberOfLayers(); i++) {
            Drawable layer = rippleDrawable.getDrawable(i);
            if (layer instanceof RippleRadMaskDrawable) {
                ((RippleRadMaskDrawable) layer).setRadius(topRad, bottomRad);
                return;
            }
        }
    }

    public static void setMaskDrawableRad(Drawable drawable, float topLeftRad, float topRightRad, float bottomRightRad, float bottomLeftRad) {
        if (!(drawable instanceof RippleDrawable)) {
            return;
        }
        RippleDrawable rippleDrawable = (RippleDrawable) drawable;
        for (int i = 0; i < rippleDrawable.getNumberOfLayers(); i++) {
            Drawable layer = rippleDrawable.getDrawable(i);
            if (layer instanceof RippleRadMaskDrawable) {
                ((RippleRadMaskDrawable) layer).setRadius(topLeftRad, topRightRad, bottomRightRad, bottomLeftRad);
                return;
            }
        }
    }

    public static ShapeDrawable createRoundRectDrawable(int rad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static ShapeDrawable createRoundRectDrawable(int topLeftRad, int topRightRad, int bottomRightRad, int bottomLeftRad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{topLeftRad, topLeftRad, topRightRad, topRightRad, bottomRightRad, bottomRightRad, bottomLeftRad, bottomLeftRad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static ShapeDrawable createRoundRectDrawable(int topRad, int bottomRad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{topRad, topRad, topRad, topRad, bottomRad, bottomRad, bottomRad, bottomRad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static InsetDrawable createRoundRectDrawableShadowed(int rad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        defaultDrawable.getPaint().setShadowLayer(AndroidUtilities.dpf2(2), 0, AndroidUtilities.dpf2(0.33f), multAlpha(0x11000000, Color.alpha(defaultColor) / 255.0f));
        return new InsetDrawable(defaultDrawable, AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
    }

    public static GradientDrawable createRoundRectGradientDrawable(int rad, int topColor, int bottomColor) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{topColor, bottomColor});
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(rad);
        return drawable;
    }

    /** ADAPTED: getThemeDrawable stub for RecyclerListView. */
    public static Drawable getThemeDrawable(String key) {
        return null;
    }

    public static Paint getThemePaint(String paintKey) {
        if (key_paint_divider.equals(paintKey)) {
            dividerPaint.setColor(getColor(key_divider));
            return dividerPaint;
        }
        if (key_paint_chatActionBackground.equals(paintKey)) {
            chat_actionBackgroundPaint.setColor(getColor(key_chat_serviceBackground));
            return chat_actionBackgroundPaint;
        }
        if (key_paint_chatActionBackgroundSelected.equals(paintKey)) {
            chat_actionBackgroundSelectedPaint.setColor(getColor(key_chat_serviceBackgroundSelected));
            return chat_actionBackgroundSelectedPaint;
        }
        if (key_paint_chatActionBackgroundDarken.equals(paintKey)) {
            return chat_actionBackgroundGradientDarkenPaint;
        }
        return new Paint();
    }

    public static Drawable createSimpleSelectorDrawable(Context context, int resource, int defaultColor, int pressedColor) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(resource).mutate();
        if (defaultColor != 0) {
            defaultDrawable.setColorFilter(new PorterDuffColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY));
        }
        Drawable pressedDrawable = resources.getDrawable(resource).mutate();
        if (pressedColor != 0) {
            pressedDrawable.setColorFilter(new PorterDuffColorFilter(pressedColor, PorterDuff.Mode.MULTIPLY));
        }
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
        return stateListDrawable;
    }

    public static ShapeDrawable createCircleDrawable(int size, int color) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.setIntrinsicWidth(size);
        defaultDrawable.setIntrinsicHeight(size);
        defaultDrawable.getPaint().setColor(color);
        return defaultDrawable;
    }

    public static ShapeDrawable createCircleDrawable(int size, int colorTop, int colorBottom) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.setIntrinsicWidth(size);
        defaultDrawable.setIntrinsicHeight(size);
        android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
            0, 0, 0, size,
            colorTop, colorBottom,
            android.graphics.Shader.TileMode.CLAMP
        );
        defaultDrawable.getPaint().setShader(gradient);
        return defaultDrawable;
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, int iconRes) {
        return createCircleDrawableWithIcon(size, iconRes, 0);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, int iconRes, int stroke) {
        Drawable drawable = iconRes != 0 && ApplicationLoader.applicationContext != null
                ? ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate()
                : null;
        return createCircleDrawableWithIcon(size, drawable, stroke);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, Drawable drawable, int stroke) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        Paint paint = defaultDrawable.getPaint();
        paint.setColor(0xffffffff);
        if (stroke == 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
        } else if (stroke == 2) {
            paint.setAlpha(0);
        }
        CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
        combinedDrawable.setCustomSize(size, size);
        return combinedDrawable;
    }

    public static Drawable createRoundRectDrawableWithIcon(int rad, int iconRes) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(0xffffffff);
        Drawable drawable = ApplicationLoader.applicationContext == null || iconRes == 0
                ? null
                : ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
        return new CombinedDrawable(defaultDrawable, drawable);
    }

    public static int getWallpaperColor(int color) {
        return color == 0 ? 0 : color | 0xff000000;
    }

    public static float getThemeIntensity(float value) {
        if (value < 0 && !getActiveTheme().isDark()) {
            return -value;
        }
        return value;
    }

    public static Drawable createSimpleSelectorCircleDrawable(int size, int defaultColor, int pressedColor) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(ovalShape);
        pressedDrawable.getPaint().setColor(0xffffffff);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{pressedColor}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, defaultDrawable, pressedDrawable);
    }

    public static Drawable createSelectorDrawableFromDrawables(Drawable normal, Drawable pressed) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressed);
        stateListDrawable.addState(StateSet.WILD_CARD, normal);
        return stateListDrawable;
    }

    public static Drawable getRoundRectSelectorDrawable(int color) {
        return getRoundRectSelectorDrawable(AndroidUtilities.dp(3), color);
    }

    public static Drawable getRoundRectSelectorDrawable(int corners, int color) {
        Drawable maskDrawable = createRoundRectDrawable(corners, 0xffffffff);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{(color & 0x00ffffff) | 0x19000000}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
    }

    public static Drawable createSelectorWithBackgroundDrawable(int backgroundColor, int color) {
        Drawable maskDrawable = new ColorDrawable(backgroundColor);
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, new ColorDrawable(backgroundColor), maskDrawable);
    }

    public static Drawable createCircleSelectorDrawable(int color, int leftInset, int rightInset) {
        maskPaint.setColor(0xffffffff);
        Drawable maskDrawable = new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                int radius = (Math.max(bounds.width(), bounds.height()) / 2) + leftInset + rightInset;
                canvas.drawCircle(bounds.centerX() - leftInset + rightInset, bounds.centerY(), radius, maskPaint);
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
        ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
        );
        return new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
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

    public static void setCombinedDrawableColor(Drawable combinedDrawable, int color, boolean isIcon) {
        if (!(combinedDrawable instanceof CombinedDrawable)) {
            return;
        }
        Drawable drawable = isIcon
                ? ((CombinedDrawable) combinedDrawable).getIcon()
                : ((CombinedDrawable) combinedDrawable).getBackground();
        if (drawable == null) {
            return;
        }
        if (drawable instanceof ColorDrawable) {
            ((ColorDrawable) drawable).setColor(color);
        } else {
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
        if (colors != null) {
            for (int i = 0; i < colors.size(); i++) {
                currentColors.put(colors.keyAt(i), colors.valueAt(i));
            }
        }
        themeGeneration++;
        applyDialogsColors();
    }

    private static void notifyThemeChanged() {
        ArrayList<ThemeDelegate> delegates = new ArrayList<>(themeDelegates);
        for (int i = 0; i < delegates.size(); i++) {
            delegates.get(i).didSetNewTheme();
        }
        // Upstream refreshThemeColors posts didSetNewTheme on the global bus so
        // fragments/layouts that are not ThemeDelegate can react.
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.didSetNewTheme, false, true);
    }

    /**
     * Upstream-shaped: copy asset into files dir when missing/stale, return the file.
     */
    public static File getAssetFile(String assetName) {
        File file = new File(ApplicationLoader.getFilesDirFixed(), assetName);
        long size;
        try {
            Context ctx = applicationContext != null
                    ? applicationContext
                    : ApplicationLoader.applicationContext;
            if (ctx == null) {
                return file;
            }
            InputStream stream = ctx.getAssets().open(assetName);
            size = stream.available();
            stream.close();
        } catch (Exception e) {
            size = 0;
            FileLog.e(e);
        }
        if (!file.exists() || size != 0 && file.length() != size) {
            try {
                Context ctx = applicationContext != null
                        ? applicationContext
                        : ApplicationLoader.applicationContext;
                if (ctx == null) {
                    return file;
                }
                try (InputStream in = ctx.getAssets().open(assetName)) {
                    AndroidUtilities.copyFile(in, file);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return file;
    }

    /**
     * Upstream-shaped {@code .attheme} parser. Loads key=value lines into a
     * SparseIntArray keyed by {@link ThemeColors#stringKeyToInt}. Wallpaper
     * blob ({@code WPS}) is recorded only as {@link #key_wallpaperFileOffset};
     * {@code WLS=} links are ignored until wallpaper pipeline is ported.
     */
    public static SparseIntArray getThemeFileValues(File file, String assetName, String[] wallpaperLink) {
        FileInputStream stream = null;
        SparseIntArray stringMap = new SparseIntArray();
        try {
            byte[] bytes = new byte[1024];
            int currentPosition = 0;
            if (assetName != null) {
                file = getAssetFile(assetName);
            }
            if (file == null || !file.exists()) {
                return stringMap;
            }
            stream = new FileInputStream(file);
            int idx;
            int read;
            boolean finished = false;
            int wallpaperFileOffset = -1;
            while ((read = stream.read(bytes)) != -1) {
                int previousPosition = currentPosition;
                int start = 0;
                for (int a = 0; a < read; a++) {
                    if (bytes[a] == '\n') {
                        int len = a - start + 1;
                        String line = new String(bytes, start, len - 1);
                        if (line.startsWith("WLS=")) {
                            if (wallpaperLink != null && wallpaperLink.length > 0) {
                                wallpaperLink[0] = line.substring(4);
                            }
                        } else if (line.startsWith("WPS")) {
                            wallpaperFileOffset = currentPosition + len;
                            finished = true;
                            break;
                        } else {
                            if ((idx = line.indexOf('=')) != -1) {
                                String key = line.substring(0, idx);
                                String param = line.substring(idx + 1);
                                int value;
                                if (param.length() > 0 && param.charAt(0) == '#') {
                                    try {
                                        value = Color.parseColor(param);
                                    } catch (Exception ignore) {
                                        Integer parsed = Utilities.parseInt(param);
                                        value = parsed != null ? parsed : 0;
                                    }
                                } else {
                                    Integer parsed = Utilities.parseInt(param);
                                    value = parsed != null ? parsed : 0;
                                }
                                int keyFromString = ThemeColors.stringKeyToInt(key);
                                if (keyFromString >= 0) {
                                    stringMap.put(keyFromString, value);
                                }
                            }
                        }
                        start += len;
                        currentPosition += len;
                    }
                }
                if (previousPosition == currentPosition) {
                    break;
                }
                stream.getChannel().position(currentPosition);
                if (finished) {
                    break;
                }
            }
            stringMap.put(key_wallpaperFileOffset, wallpaperFileOffset);
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return stringMap;
    }
}

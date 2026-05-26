/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ThemeColors.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java
 *
 * NativeDo milestone slice: keeps the default color table for the Telegram theme
 * keys currently used by ActionBar, ActionBarMenu, and the mock topic screens.
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
package org.telegram.ui.ActionBar;

import static org.telegram.ui.ActionBar.Theme.*;

public final class ThemeColors {

    public static final int TELEGRAM_COLOR = 0xff229af0;
    public static final int TELEGRAM_COLOR_TEXT = 0xff298acf;
    public static final int DEFAULT_BLACK_TEXT = 0xff1a1d21;

    private ThemeColors() {
    }

    public static int[] createDefaultColors() {
        int[] defaultColors = new int[Theme.colorsCount];
        defaultColors[key_windowBackgroundWhite] = 0xffffffff;
        defaultColors[key_windowBackgroundGray] = 0xfff1f1f3;
        defaultColors[key_windowBackgroundWhiteBlackText] = DEFAULT_BLACK_TEXT;
        defaultColors[key_windowBackgroundWhiteGrayText] = 0xff808384;
        defaultColors[key_windowBackgroundWhiteGrayText2] = 0xff82868a;
        defaultColors[key_windowBackgroundWhiteBlueText] = 0xff4092cd;
        defaultColors[key_divider] = 0xffd9d9d9;
        defaultColors[key_listSelector] = 0x0f000000;
        defaultColors[key_actionBarDefault] = 0xffffffff;
        defaultColors[key_actionBarDefaultSelector] = 0x121a1d21;
        defaultColors[key_actionBarDefaultIcon] = DEFAULT_BLACK_TEXT;
        defaultColors[key_actionBarDefaultTitle] = DEFAULT_BLACK_TEXT;
        defaultColors[key_actionBarDefaultSubtitle] = 0xff79817e;
        defaultColors[key_actionBarDefaultSubmenuItem] = DEFAULT_BLACK_TEXT;
        defaultColors[key_actionBarDefaultSubmenuItemIcon] = DEFAULT_BLACK_TEXT;
        defaultColors[key_actionBarDefaultSubmenuBackground] = 0xffffffff;
        defaultColors[key_actionBarDefaultSubmenuSeparator] = 0xfff5f5f5;
        defaultColors[key_glass_tabSelected] = TELEGRAM_COLOR;
        defaultColors[key_glass_tabSelectedText] = TELEGRAM_COLOR;
        defaultColors[key_glass_tabUnselected] = DEFAULT_BLACK_TEXT;
        defaultColors[key_telegram_color] = TELEGRAM_COLOR;
        defaultColors[key_iv_background] = 0xffffffff;
        defaultColors[key_iv_backgroundGray] = 0xfff0f0f0;
        defaultColors[key_iv_navigationBackground] = 0xffffffff;
        defaultColors[key_graySection] = 0xfff1f1f3;
        defaultColors[key_graySectionText] = 0xff82868a;
        defaultColors[key_windowBackgroundWhiteBlueHeader] = 0xff4092cd;
        defaultColors[key_windowBackgroundGrayShadow] = 0xffd9d9d9;
        defaultColors[key_dialogBackground] = 0xffffffff;

        // ActionBar action-mode + tab strip
        defaultColors[key_actionBarActionModeDefaultSelector] = 0xffe2e2e2;
        defaultColors[key_actionBarTabActiveText] = 0xff1e7755;
        defaultColors[key_actionBarTabLine] = 0xff1e7755;
        defaultColors[key_actionBarTabSelector] = 0x14000000;
        defaultColors[key_actionBarTabUnactiveText] = 0xff6b6f63;

        // Chat composer / panel
        defaultColors[key_chat_messagePanelBackground] = 0xffffffff;
        defaultColors[key_chat_messagePanelShadow] = 0xff000000;

        // Settings / fast scroll / progress
        defaultColors[key_settings_listSelector] = 0xffe8e8d8;
        defaultColors[key_fastScrollActive] = 0xff999999;
        defaultColors[key_fastScrollInactive] = 0xffc4c4c4;
        defaultColors[key_fastScrollText] = 0xffffffff;
        defaultColors[key_progressCircle] = 0xff1e7755;

        // Pull-to-refresh / archive
        defaultColors[key_avatar_backgroundArchivedHidden] = 0xffc6c9ce;
        defaultColors[key_chats_archivePullDownBackground] = 0xffc6c9ce;
        defaultColors[key_chats_archivePullDownBackgroundActive] = 0xff66a9e0;

        // DialogCell / TopicCell — text, badge, pinned dot, action background.
        defaultColors[key_chats_name] = 0xff20251f;
        defaultColors[key_chats_nameMessage] = 0xff1e7755;
        defaultColors[key_chats_message] = 0xff6b6f63;
        defaultColors[key_chats_date] = 0xff95968a;
        defaultColors[key_chats_pinnedIcon] = 0xff95968a;
        defaultColors[key_chats_pinnedOverlay] = 0xfff0eee5;
        defaultColors[key_chats_unreadCounter] = 0xff1e7755;
        defaultColors[key_chats_unreadCounterText] = 0xffffffff;
        defaultColors[key_chats_unreadCounterMuted] = 0xffbcbeb1;
        defaultColors[key_chats_actionBackground] = 0xff1e7755;

        // Avatar palette defaults — matches upstream
        // ThemeColors.defaultColors[key_avatar_background*].
        defaultColors[key_avatar_backgroundRed] = 0xffFF845E;
        defaultColors[key_avatar_background2Red] = 0xffD45246;
        defaultColors[key_avatar_backgroundOrange] = 0xffFEBB5B;
        defaultColors[key_avatar_background2Orange] = 0xffF68136;
        defaultColors[key_avatar_backgroundViolet] = 0xffB694F9;
        defaultColors[key_avatar_background2Violet] = 0xff6C61DF;
        defaultColors[key_avatar_backgroundGreen] = 0xff9AD164;
        defaultColors[key_avatar_background2Green] = 0xff46BA43;
        defaultColors[key_avatar_backgroundCyan] = 0xff5BCBE3;
        defaultColors[key_avatar_background2Cyan] = 0xff359AD4;
        defaultColors[key_avatar_backgroundBlue] = 0xff5CAFFA;
        defaultColors[key_avatar_background2Blue] = 0xff408ACF;
        defaultColors[key_avatar_backgroundPink] = 0xffFF8AAC;
        defaultColors[key_avatar_background2Pink] = 0xffD95574;
        defaultColors[key_avatar_text] = 0xffffffff;
        return defaultColors;
    }
}

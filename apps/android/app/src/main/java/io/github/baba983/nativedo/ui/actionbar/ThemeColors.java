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
package io.github.baba983.nativedo.ui.actionbar;

import static io.github.baba983.nativedo.ui.actionbar.Theme.*;

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
        return defaultColors;
    }
}

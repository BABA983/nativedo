// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/Components/SizeNotifierFrameLayout.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: compile-shim=L2, layout-host=L2
// ADAPTED:
//   - Minimal FrameLayout host with fitsSystemWindows disabled for the current navigation shell.
// OMITTED:
//   - Telegram keyboard size, inset notification, blur, and emoji panel layout behavior.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/SizeNotifierFrameLayout.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/SizeNotifierFrameLayout.java
 *
 * NativeDo milestone slice: compile-only FrameLayout stub for current navigation code;
 * omits Telegram keyboard size and inset notification behavior.
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
package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;

public class SizeNotifierFrameLayout extends FrameLayout {

    public SizeNotifierFrameLayout(Context context) {
        super(context);
        setFitsSystemWindows(false);
    }
}

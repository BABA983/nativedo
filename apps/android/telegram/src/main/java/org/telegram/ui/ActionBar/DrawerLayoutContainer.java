// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/DrawerLayoutContainer.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: compile-shim=L2
// ADAPTED:
//   - Minimal FrameLayout placeholder for copied navigation signatures.
// OMITTED:
//   - Telegram drawer gestures, previews, menu behavior, and drawer layout state.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/DrawerLayoutContainer.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/DrawerLayoutContainer.java
 *
 * NativeDo milestone slice: compile-only placeholder for copied navigation signatures;
 * omits Telegram drawer gestures, previews, and menu behavior.
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

import android.content.Context;
import android.widget.FrameLayout;

public class DrawerLayoutContainer extends FrameLayout {

    public DrawerLayoutContainer(Context context) {
        super(context);
    }
}

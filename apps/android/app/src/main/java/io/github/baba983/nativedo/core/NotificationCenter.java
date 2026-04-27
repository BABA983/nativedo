// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/messenger/NotificationCenter.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: compile-shim=L2, notification-constants=L2
// HOST-WIRING:
//   - Keeps constants referenced by copied UI/theme code.
// OMITTED:
//   - Telegram event bus, account/global instances, observers, and animation delivery deferral.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/NotificationCenter.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java
 *
 * NativeDo milestone slice: compile-only notification constants used by copied UI code;
 * omits Telegram's event bus.
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
package io.github.baba983.nativedo.core;

public final class NotificationCenter {

    public static final int didSetNewTheme = 1;

    private NotificationCenter() {
    }
}

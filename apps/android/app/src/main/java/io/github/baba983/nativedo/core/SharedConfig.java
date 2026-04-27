// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/messenger/SharedConfig.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: compile-shim=L2, config-flag=L2
// HOST-WIRING:
//   - Keeps flags read by the current ActionBar slice.
// OMITTED:
//   - Telegram persisted shared configuration and unrelated feature flags.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/SharedConfig.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java
 *
 * NativeDo milestone slice: keeps only flags read by the current ActionBar slice;
 * omits Telegram's persisted shared configuration.
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

public final class SharedConfig {

    public static boolean drawActionBarShadow = true;

    private SharedConfig() {
    }
}

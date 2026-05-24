// UPSTREAM-LEVEL: L0
// UPSTREAM-PATH:  org/telegram/messenger/GenericProvider.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: generic-utils=L0
// HOST-WIRING:
//   - Package changed from org.telegram.messenger to NativeDo core.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/messenger/GenericProvider.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package org.telegram.messenger;

public interface GenericProvider<F, T> {
    T provide(F obj);
}
